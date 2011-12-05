public class Node implements Comparable<Node> {
	private Tile tile;
	private Integer distance;
	private boolean checked;
		
	
	public Node(Tile tile, Integer distance) {
		this.tile = tile;
		this.distance = distance;
	}
	
	public Node(Tile tile, boolean checked) {
		this.tile = tile;
		this.checked = checked;
	}
	
	public int compareTo(Node n) {
		return distance.compareTo(n.distance);
	}
	
	public boolean equals(Object o) {
		if(o instanceof Node) {
			Node n = (Node) o;
			return this.tile.equals(n.tile);
		}
		return false;
	}
	
	// Getters and setters
	public boolean isChecked() {
		return checked;
	}
	
	public void setChecked(boolean checked) {
		this.checked = checked;
	}
	
	public Integer getDistance() {
		return distance;
	}
	
	public void setDistance(Integer distance) {
		this.distance = distance;
	}
	
	public Tile getTile() {
		return tile;
	}	
	
	public void setTile(Tile tile) {
		this.tile = tile;
	}
}
