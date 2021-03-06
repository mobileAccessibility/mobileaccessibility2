package cs.washington.mobileaccessibility.color;

import java.io.*;
import java.util.*;

/*
 * This class implements a simplified version of decision-tree
 * learning based off of information gain.  It is similar to
 * something like CART, I suppose, except that sophisticated
 * algorithms usually have special rules to determine when to
 * stop the branching process, and I always go to a constant
 * depth.
 * 
 * The main method reads in a file (default is "peri.txt"),
 * in a format generated by the ImageProcessor class of the
 * Android application.  A user can collect data by pressing
 * shift + the identifier of a color, and the image being viewed
 * by the camera is appended to the peri.txt file.
 * 
 * My algorithm assumes that if a picture was labeled as, say,
 * "red," that ALL of its pixels are red.  Of course, this is
 * silly, but I didn't want to think much about the proper 
 * way to do this, and besides, decision tree learning is always
 * heuristic anyways, since perfect decision tree learning
 * turns out to be NP-complete iirc.
 */
public class ColorAnalyzer {
	
	// Here are some classes that I use to define the decision
	// tree data structure
	private interface DecisionNode {
		/**
		 * Take a pixel and return the classification according
		 * to the tree of which this is the root
		 * @param pd - the pixel to classify
		 * @return the string name of the color that pd is classified as
		 */
		public String classify(PixelData pd);

		/**
		 * Recursively returns a condensed string description of the tree
		 * of which this node is the root
		 * @return
		 */
		public String recursiveDescribe();
		/**
		 * Recursively prints out a representation of the tree rooted
		 * at this node, in a way that is visually understandable.
		 * Can print out properly formatted java code (for insertion into
		 * the DecisionTreeClassifier class), if desired.
		 * @param indent a string with as many \t's as the starting depth
		 * @param elaborate true to get properly formatted java, false to get more condensed
		 */
		public void prettyPrint(String indent, boolean elaborate);
	}
	
	// A terminal node in the tree, which assigns everything just
	// one color
	private static class TerminalNode implements DecisionNode {
		// the name of the color which labels this node
		public String color;
		// the number of data points which ended up in this node,
		// and were labeled as color
		public int weight;
		
		public String classify(PixelData pd) {
			return color;
		}
		public String toString() {
			return super.toString() + "- Terminal(" + color + ")";
		}
		public String recursiveDescribe() {
			return "Terminal(" + color + ")";
		}
		public TerminalNode(String color, int weight) {
			this.color = color;
			this.weight = weight;
		}
		public void prettyPrint(String indent, boolean elaborate) {
			if(elaborate)
				System.out.println(indent + "return " + color.toUpperCase() + ";");
			else
				System.out.println(indent + color + "  (" + weight + ")");
			
		}
	}
	
	// A branching node which looks at a parameter and sends to one of
	// two subtrees
	private static class BranchNode implements DecisionNode {
		// the index that specifies which parameter we are discriminating
		// based off... see PixelData.paramNames
		public int paramIndex;
		public int cutoff;
		// the destination tree if the parameter exceeds cutoff
		public DecisionNode hi;
		// the destination tree if the parameter doesn't exceed cutoff
		public DecisionNode lo;
		public String classify(PixelData pd) {
			if(pd.getParam(PixelData.paramNames[paramIndex]) > cutoff)
				return hi.classify(pd);
			else
				return lo.classify(pd);
		}
		
		public String toString() {
			return super.toString() + "- NonTerminal(" + PixelData.paramNames[paramIndex] + "," + cutoff + "," + hi + "," + lo;
		}
		
		public String recursiveDescribe() {
			return "NonTerminal(" + PixelData.paramNames[paramIndex] + ">" + cutoff + "?" + hi.recursiveDescribe() + ":" + lo.recursiveDescribe() + ")";
		}
		
		public BranchNode(int paramIndex, int cutoff, DecisionNode hi, DecisionNode lo) {
			this.paramIndex = paramIndex;
			this.cutoff = cutoff;
			this.hi = hi;
			this.lo = lo;
		}
		
		public void prettyPrint(String indent, boolean elaborate) {
			
			if(elaborate) {
			System.out.println(indent + "if(" + PixelData.paramNames[paramIndex] + " > " +
					cutoff + ") {");
			hi.prettyPrint(indent + "\t", elaborate);
			System.out.println(indent + "}");
			System.out.println(indent + "else {");
			lo.prettyPrint(indent + "\t", elaborate);
			System.out.println(indent + "}");
			}
			else {
				System.out.println(indent + PixelData.paramNames[paramIndex] + ">" + cutoff + "?");
				hi.prettyPrint(indent + "\t", elaborate);
				lo.prettyPrint(indent + "\t", elaborate);
			}
		}
				
	}
	
	// this function, with its caveat for 0, appears enough to make this useful
	private static double xlogx(double x) {
		if(x == 0)
			return 0;
		return x*Math.log(x);
	}
	
	// given a set of Pixels, make the decision tree, to the given depth!
	// this works recursively
	private static DecisionNode buildClassifier(Set<PixelData> set, int depth) {
		if(depth < 1) {
			// time to terminate.
			
			// we need to figure out which of the colors in PixelData is
			// most popular
			int [] colorFrequencies = new int[PixelData.colorNames.length];
			int bestFreq = 0;
			int bestColor = 3; // gray
			for(PixelData pd : set) {
				int color = pd.colorIndex;
				colorFrequencies[color]++;
				int newFreq = colorFrequencies[color];
				if(newFreq > bestFreq) {
					bestFreq = newFreq;
					bestColor = color;
				}
			}
			return new TerminalNode(PixelData.colorNames[bestColor], set.size());
		}
		else {
			// now things are going to get really really complicated
			
			// this is a crude upper bound; the 5 has to do with 2^5 > PixelData.colorNames.length
			// bestEntropy is some shifted version of the information gain proper, whatever
			// that is...
			// just fiddle around with the equations, and this algorithm will fall out of them
			double bestEntropy = 5*set.size();
			int bestCutoff = 0;
			int bestParam = 0;

			int [] upperFrequencies = new int[PixelData.colorNames.length];
			for(PixelData pd : set) {
				int color = pd.colorIndex;
				upperFrequencies[color]++;
			}
			int [] lowerFrequencies = new int[PixelData.colorNames.length];
			int totalAbove = set.size();
			int totalBelow = 0;
			for(int i = 0; i < PixelData.paramNames.length; i++) {
				// for each paramter, we're going to vary the cutoff continuously
				// and see where entropy is maximized
				//
				// I wonder, is the function we're maximizing convex?  That would make
				// this very sub-optimal...
				final String paramName = PixelData.paramNames[i];
				ArrayList<PixelData> sorted = new ArrayList<PixelData>();
				sorted.addAll(set);
				Collections.sort(sorted, new Comparator<PixelData>() {
					public int compare(PixelData o1, PixelData o2) {
						return o1.getParam(paramName) - o2.getParam(paramName);
					}
				});
				for(PixelData pd : sorted) {
					// move it into the below set
					if(totalAbove == 0)
						continue;
					totalBelow++;
					totalAbove--;
					int color = pd.colorIndex;
					upperFrequencies[color]--;
					lowerFrequencies[color]++;
					double entropy = xlogx(totalBelow) + xlogx(totalAbove);
					for(int j = 0; j < PixelData.colorNames.length; j++) {
						entropy -= (xlogx(upperFrequencies[j]) + xlogx(lowerFrequencies[j]));
					}
					if(entropy < bestEntropy) {
						bestEntropy = entropy;
						bestParam = i;
						bestCutoff = pd.getParam(paramName);
					}
				}
				
				// prepare things for the next loop by resetting these variables
				upperFrequencies = lowerFrequencies;
				lowerFrequencies = new int[PixelData.colorNames.length];
				totalAbove = totalBelow;
				totalBelow = 0;
				
			}
			String bestParamName = PixelData.paramNames[bestParam];
			Set<PixelData> hi = new HashSet<PixelData>();
			Set<PixelData> lo = new HashSet<PixelData>();
			for(PixelData pd : set) {
				if(pd.getParam(bestParamName) > bestCutoff)
					hi.add(pd);
				else
					lo.add(pd);
			}
			
			// here's the recursion
			DecisionNode dHi = buildClassifier(hi, depth - 1);
			DecisionNode dLo = buildClassifier(lo, depth - 1);
			return new BranchNode(bestParam, bestCutoff, dHi, dLo);
			
		}
	}
	

	// This class is a data structure which represents a generic pixel
	// It includes both an rgb value AND a color name (which is GIVEN, in the input file)
	private static class PixelData {
		public int r;
		public int g;
		public int b;
		// since we're going to use all these other parameters,
		// might as well compute them when we make the structure
		public int h; // hue
		public int s; // saturation
		public int v; // value
		public int uvMag; // something like the magnitude squared of the UV component of YUV
		// which turns out to just be the s.dev of the three rgb values
		
		// there's also another parameter, cohue, which is just hue shifted by 180 degrees
		// we don't store it, because it's so close to hue
		// It's included since if we just used hue, it would encourage the decision trees
		// to use the arbitrary boundary of 0=360 degree hue, which is entirely a convention
		
		// the name of the color which this pixel is, but condensed using the
		// colorNames array
		public int colorIndex;
		
		// the eleven canonical colors
		public static String [] colorNames = {"black", "blue", "brown", "gray", "green", "orange", "pink", "purple", "red", "white", "yellow"};
		
		// the parameters that our decision tree can discriminate off of
		public static String [] paramNames = {"red","green","blue","hue","cohue","sat","val","chrome"};
		
		
		// the inverse of paramNames array
		// couldn't this have been written better?
		public int getParam(String name) {
			if(name.equals("red"))
				return r;
			else if(name.equals("green"))
				return g;
			else if(name.equals("blue"))
				return b;
			else if(name.equals("hue"))
				return h;
			else if(name.equals("cohue"))
				return (h + 180) % 360;
			else if(name.equals("sat"))
				return s;
			else if(name.equals("val"))
				return v;
			else
				return uvMag;
		}
		
		
		public PixelData(String name, int r, int g, int b) {
			// first, convert name to a numerical form
			int z;
			for(z = 0; z < colorNames.length; z++) {
				if(colorNames[z].equals(name))
					break;
			}
			if(z == colorNames.length) {
				System.out.println("Some unknown color happened: " + name);
				z = 3; // gray
			}
			
			this.colorIndex = z;
			this.r = r;
			this.g = g;
			this.b = b;
			
			// now, we need to computer h, s, v, and all the others
			// there should be a bunch of overlap between this and
			// some code in DecisionTreeClassifier.java
			int max = Math.max(r,g);
			max = Math.max(max,b);
			int min = Math.min(r,g);
			min = Math.min(min,b);
			if(max == min)
				h = 0;
			else if(max == r)
				h = 60*(g - b)/(max - min) + 360;
			else if(max == g)
				h = 60*(b - r)/(max - min) + 120;
			else
				h = 60*(r - g)/(max - min) + 240;
			h %= 360;
			if(max == 0)
				s = 0;
			else
				s = 100*(max - min)/max;
			v = max*100/256;
			double total = r + g + b;
			double totalsq = r*r + g*g + b*b;
			total /= 3;
			totalsq /= 3;
			totalsq -= total*total;
			uvMag = (int) totalsq;
			
			
		}
		
	}
	
	// A message to print out if the user goofs up the command line parameters
	private static void printUsage() {
		System.err.println("usage: \n");
		System.err.println("\tjava ColorAnalyzer [filename.txt] [briefness] [depth]");
		System.err.println("\t\tbriefness is either");
		System.err.println("\t\t * \"java\", or \"verbose\" for a " +
				"tree that could be put into the DecisionTreeClassifier class");
		System.err.println("\t\t * \"condensed\", or \"brief\" for a " +
				"slightly more compact form, which also lists the" +
				" number of pixels of each classification that ended up in each category");
		System.err.println("\t\t * \"super-brief\" for a highly " +
				"condensed format");
		System.err.println("\t\t(default is java)");
		System.err.println();
		System.err.println("\t\tdepth is the depth of the tree that the" +
				" algorithm produces.  Default is 4");
		System.err.println();
		System.err.println("\t\tfilename.txt is the name of a file " +
				"with the color data.  It must end with .txt suffix.  " +
				"The default is \"peri.txt\", which is what " +
				"ImageProcessor will generate.");
	}
	
	public static void main(String[] args) {
		// first, get the command line parameters
		int briefness = 0;
		int depth = 4;
		String filename = "peri.txt";
		for(String s : args) {
			if(s.equals("java") || s.equals("verbose"))
				briefness = 0;
			else if(s.equals("condensed") || s.equals("brief"))
				briefness = 1;
			else if(s.equals("super-brief"))
				briefness = 2;
			else if(s.endsWith(".txt"))
				filename = s;
			else {
				try {
					depth = Integer.parseInt(s);
				}
				catch (NumberFormatException nfe) {
					printUsage();
					return;
				}
			}
		}
		// now, read in the file...
		try {
			Scanner s = new Scanner(new File(filename));
			Set<PixelData> pixels = new HashSet<PixelData>();
			while(s.hasNextLine()) {
				String colorName = s.nextLine();
				int width = s.nextInt();
				int height = s.nextInt();
				int length = width*height;
				for(int i = 0; i < length; i++) {
					int red = s.nextInt();
					int green = s.nextInt();
					int blue = s.nextInt();
					pixels.add(new PixelData(colorName, red, green, blue));
				}
				
				s.nextLine(); // clear the final newline
			}
			System.out.println("Collected " + pixels.size() + " pixels!");
			
			// And then build the tree
			DecisionNode dn = buildClassifier(pixels, depth);
			
			// And finally, display it
			if(briefness == 2)
				System.out.println(dn.recursiveDescribe());
			else
				dn.prettyPrint("", briefness == 0);
			
		}
		catch(IOException ioe) {
			System.out.println("Error getting the file " + filename);
		}
	}
}
