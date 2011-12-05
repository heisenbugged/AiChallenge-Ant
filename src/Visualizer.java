/** Class for sending visualization commands to output **/
public class Visualizer {
	public static void setLineWidth(double width) {
		System.out.println("v setLindeWidth " + width);
	}
	
	public static void setLineColor(int r, int g, int b, int a) {
		System.out.println("v setLineColor " + r + " " + g + " " + b + " " + a);
	}
	
	public static void setFillColor(int r, int g, int b, double a) {
		System.out.println("v setFillColor " + r + " " + g + " " + b + " " + a);
	}
	
	public static void arrow(int row1, int col1, int row2, int col2) {
		System.out.println("v arrow " + row1 + " " + col1 + " " + row2 + " " + col2);
	}
	
	public static void circle(int row, int col, double radius, boolean fill) {		
		System.out.println("v circle " + row + " " + col + " " + radius +  " " + fill);
	}
	
	public static void line(int row1, int col1, int row2, int col2) {
		System.out.println("v line " + row1 + " " + col1 + " " + row2 + " " + col2);
	}
	
	public static void rect(int row, int col, int width, int height, boolean fill) {
		System.out.println("v rect " + row + " " + col + " " + width + " " + height + " " + fill);
	}
	
	public static void tile(int row, int col) {
		System.out.println("v tile " + row + " " + col);
	}
}
