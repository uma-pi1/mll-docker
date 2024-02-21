package mll;

import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.function.Function;

import org.knowm.xchart.BitmapEncoder;
import org.knowm.xchart.QuickChart;
import org.knowm.xchart.XYChart;

import guru.nidi.graphviz.engine.Format;
import guru.nidi.graphviz.engine.Graphviz;

public class Util {

	/**
	 * Plot a single function y(x) and store the result as a PNG file on disk.
	 */
	public static String savePlot(double[] x, double[] y, String plotName) {
		try {
			XYChart chart = getChart(x, y, plotName);
			Files.createDirectories(Paths.get("out/plots"));
			String filepath = Paths.get("out/plots", plotName + ".png").toString();
			BitmapEncoder.saveBitmapWithDPI(chart, filepath, BitmapEncoder.BitmapFormat.PNG, 300);
			System.out.println("Saved plot: " + filepath);
			return filepath;
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Plot two functions y1(x) and y2(x) and store the result as a PNG file on disk.
	 */
	public static String savePlot(double[] x, double[] y1, double[] y2, String plotName) {
		try {
			XYChart chart = getChart(x, y1, y2, plotName);
			Files.createDirectories(Paths.get("out/plots"));
			String filepath = Paths.get("out/plots", plotName + ".png").toString();
			BitmapEncoder.saveBitmapWithDPI(chart, filepath, BitmapEncoder.BitmapFormat.PNG, 300);
			System.out.println("Saved plot: " + filepath);
			return filepath;
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public static String saveDotPng(String dot, String name) throws IOException {
		String filepath = Paths.get("out/dot", name + ".png").toString();
		Graphviz.fromString(dot).render(Format.PNG).toFile(new File(filepath));
		System.out.println("Rendered dot file: " + filepath);
		return filepath;
	}

	public static BufferedImage viewDot(String dot) {
		return Graphviz.fromString(dot).render(Format.PNG).toImage();
	}

	/**
	 * Run the provided LLVM program.
	 */
	public static void runLLVM(String filename) {
		String[] command;
		if (isLLVMAvailable()) {
			command = new String[] { "lli", "out/llvm/" + filename + ".ll" };
		} else {
			command = new String[] { "docker", "exec", "-t", "mll_docker", "lli",
					"/home/jovyan/mll/out/llvm/" + filename + ".ll" };
		}
		runCommand(command);
	}

	/** Compile the specified LLVM program + bind it to main.c using clang */
   public static void clang(String filename) throws IOException {
        String[] command;
        System.out.println("Generating binary: " + filename);
        if (isLLVMAvailable()) {
        	Files.createDirectories(Paths.get("out/bin"));
            command = new String[] { 
                    "clang", "-O3", "-lm",
                    "src/main.c", 
                    "out/llvm/" + filename + ".ll",
                    "-o", "out/bin/" + filename};
        } else {
        	Files.createDirectories(Paths.get("home", "jovyan", "mll", "out", "bin"));
            command = new String[] { 
                    "docker", "exec", "-t", "mll_docker", 
                    "clang", "-O3", "-lm",
                    "/home/jovyan/mll/src/main.c", 
                    "/home/jovyan/mll/out/llvm/" + filename + ".ll",
                    "-o", "/home/jovyan/mll/out/bin/" + filename};
        }
        runCommand(command);
    }

   	/** Runs the specified program generated by clang */
   	public static void runBinary(String filename, String... args) {
   		var command = new ArrayList<String>();
   		if (isLLVMAvailable()) {
   			command.add("out/bin/" + filename);
   		} else {
   			command.addAll(Arrays.asList(new String[] { "docker", "exec", "-t", "mll_docker" }));
   			command.add("/home/jovyan/mll/out/bin/" + filename);
   		}
   		command.addAll(Arrays.asList(args));
        runCommand(command.toArray(new String[0]));
   	}
   
	/**
	 * Save LLVM optimization and saves output to disk.
	 */
	public static String saveLLVMOpt(String filename, int optLevel) throws IOException {
		Files.createDirectories(Paths.get("out/llvm"));
		String filepath = Paths.get("out/llvm", filename + getOptLevelCode(optLevel) + ".ll").toString();
		String[] command;
		if (isLLVMAvailable()) {
			command = new String[] { "opt", getOptLevelCode(optLevel), "out/llvm/" + filename + ".ll", "-So",
					"out/llvm/" + (filename + getOptLevelCode(optLevel) + ".ll") };
		} else {
			command = new String[] { "docker", "exec", "-t", "mll_docker", "opt", getOptLevelCode(optLevel),
					"/home/jovyan/mll/out/llvm/" + filename + ".ll", "-So",
					"/home/jovyan/mll/out/llvm/" + (filename + getOptLevelCode(optLevel) + ".ll") };
		}
		runCommand(command);
		System.out.println("Optimized LLVM code: " + filepath);

		return filepath;
	}

	/**
     * Save @p op's LLVM output to @p filename on disk.
     */
    public static void llvm(Op op, String filename) {
        filename = "out/llvm/" + filename + ".ll";
        try (var writer = new BufferedWriter(new FileWriter(filename))) {
            op.llvm(writer);
            System.out.println("Saved LLVM file: " + filename);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

	/**
	 * Display plot of a single function y(x) in a Jupyter notebook.
	 */
	public static BufferedImage viewPlot(double[] x, double[] y, String plotName) {
		XYChart chart = getChart(x, y, plotName);
		return BitmapEncoder.getBufferedImage(chart);
	}

	public static BufferedImage viewGradientPlot(double[] x, double[] y, double gradient, double x_0, double y_0, String plotName) {
		double[] y_grad = gety(x, x_ -> gradient * (x_ - x_0) + y_0);
		return viewPlot(x, y, y_grad, plotName);
	}

	public static String saveGradientPlot(double[] x, double[] y, double gradient, double x_0, double y_0, String plotName) {
		double[] y_grad = gety(x, x_ -> gradient * (x_ - x_0) + y_0);
		return savePlot(x, y, y_grad, plotName);
	}

	/**
	 * Display plot of a two functions y1(x) and y2(x) in a Jupyter notebook.
	 */
	public static BufferedImage viewPlot(double[] x, double[] y1, double[] y2, String plotName) {
		XYChart chart = getChart(x, y1, y2, plotName);
		return BitmapEncoder.getBufferedImage(chart);
	}

	public static double[] getx(double xmin, double xmax, int length) {
		var x = new double[length];
		double stepsize = (xmax-xmin)/length;
		for (int i = 0; i < length; i++) {
			x[i] = xmin + stepsize*i;
		}
		x[length-1]=xmax; // to avoid rounding errors
		return x;
	}
	
	public static double[] gety(double[] x, final Function<Double, Double> f) {
		int length = x.length;
		var y = new double[length];
		for (int i = 0; i < length; i++) {
			y[i] = f.apply(x[i]);
		}
		return y;
	}
	
	public static double[] gety(double[] x, final Op out) {
		return gety(x, x_ -> out.eval(x_));
	}

	public static double[] getTangent(double[] x, double at, final Op dout) {
		var env = new HashMap<Op,Double>();
		var f_at = dout.eval(env, at);
		var df_at = ((Grad)dout).results()[1];
		return Util.line(x, df_at, -at*df_at + f_at);
	}
	
	public static double[] line(double[] x, double slope, double offset)  {
		return gety(x, x_ -> x_*slope + offset);
	}
	
	private static XYChart getChart(double[] x, double[] y, String plotName) {
		return QuickChart.getChart(plotName, "", "", "f", x, y);
	}

	private static XYChart getChart(double[] x, double[] y1, double[] y2, String plotName) {
		XYChart chart = getChart(x, y1, plotName);
		chart.addSeries("g", x, y2);
		chart.getStyler().setMarkerSize(0);
		return chart;
	}

	private static String getOptLevelCode(int optLevel) {
		return switch (optLevel) {
		case 0 -> "-O0";
		case 1 -> "-O1";
		case 2 -> "-O2";
		default -> "-O3";
		};
	}

	private static boolean isLLVMAvailable() {
		try {
			var process = new ProcessBuilder("clang", "--version").start();
			int exitCode = process.waitFor();
			if (exitCode == 0) {
				return true;
			}

			return false;

		} catch (IOException | InterruptedException e) {
			return false;
		}
	}

	private static void printInputStream(InputStream inputStream) throws IOException {
		try (var isr = new InputStreamReader(inputStream); var reader = new BufferedReader(isr);) {
			String line;
			while ((line = reader.readLine()) != null) {
				System.out.println(line);
			}
		}
	}
	
	private static void runCommand(String[] command) {
		try {
			ProcessBuilder processBuilder = new ProcessBuilder(command);
			Process process = processBuilder.start();
			var inputStream = process.getInputStream();
			var errorStream = process.getErrorStream();

			printInputStream(inputStream);
			printInputStream(errorStream);

			int exitCode = process.waitFor();

			if (exitCode != 0) {
				System.err.println("Program execution failed with exit code " + exitCode);
			}

		} catch (IOException | InterruptedException e) {
			throw new RuntimeException(e);
		}
	}
}
