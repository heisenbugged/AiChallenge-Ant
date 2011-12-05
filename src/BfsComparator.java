import java.util.Comparator;


public class BfsComparator implements Comparator<Tile> {
	int[][] costMap;
	
	public BfsComparator(int[][] costMap) {
		this.costMap = costMap;
	}
	
	public int compare(Tile t1, Tile t2) {
		return new Integer(costMap[t1.getRow()][t1.getCol()]).compareTo(costMap[t2.getRow()][t2.getCol()]);
	}

}
