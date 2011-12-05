/** 
 * Holds information on a tile 
 */
public class TileData {
	int owner = -1;
	Ilk type;
	
	public TileData(Ilk type) {		
		this.type = type;		
	}
	
	public TileData(Ilk type, int owner) {
		this(type);
		this.owner = owner;
	}
	
	public Ilk getType() {
		return type;
	}
	
	public int getOwner() {
		return owner;
	}
	
	public boolean isAnt() {
		return (type == Ilk.MY_ANT || type == Ilk.ENEMY_ANT);
	}
	
	public boolean equals(Object o) {
		if(o instanceof TileData) {
			TileData td = (TileData) o;
			return type.equals(td.type);
		} else if(o instanceof Ilk) {
			return type.equals((Ilk) o);
		} else {
			return false;
		}
	}
}
