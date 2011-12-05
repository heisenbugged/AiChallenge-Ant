/**
 * Represents a route from one tile to another.
 */
public class Route implements Comparable<Route> {
	private Tile start;
	private Tile end;	
	private int distance;
	
	public Route(Tile start, Tile end, int distance) {
		this.start = start;
		this.end = end;
		this.distance = distance;
	}
	
	public Tile getStart() {
		return start;
	}
	
	public Tile getEnd() {
		return end;
	}
	
	public int getDistance() {
		return distance;
	}
	
	public int compareTo(Route route) {
		return distance - route.distance;
	}
	
	public int hashCode() {
        return start.hashCode() * Ants.MAX_MAP_SIZE * Ants.MAX_MAP_SIZE + end.hashCode();
	}
	
	public boolean equals(Object o) {
		boolean result = false;
		if(o instanceof Route) {
			Route route = (Route) o;
			result = start.equals(route.start) && end.equals(route.end);
		}
		return result;
	}
	
}
