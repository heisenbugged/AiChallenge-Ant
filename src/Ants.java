import java.util.*;

/**
 * Holds all game data and current game state.
 */
public class Ants {
    /** Maximum map size. */
    public static final int MAX_MAP_SIZE = 256 * 2;

    private final int loadTime;
    private final int turnTime;

    private final int rows;
    private final int cols;

    private final int turns;

    private final int viewRadius2;
    private final int attackRadius2;
    private final int extendedAttackRadius2;
    private final int spawnRadius2;

    private final boolean visible[][];

    private final Set<Tile> visionOffsets;
    private Set<Tile> combatOffsets;
    private Set<Tile> extendedCombatOffsets;

    private long turnStartTime;

    private final TileData map[][];    

    private final Set<Tile> myAnts = new HashSet<Tile>();
    private final Set<Tile> enemyAnts = new HashSet<Tile>();
    private final Set<Tile> myHills = new HashSet<Tile>();
    private final Set<Tile> enemyHills = new HashSet<Tile>();
    private final Set<Tile> foodTiles = new HashSet<Tile>();
    private final Set<Order> orders = new HashSet<Order>();
    private HashMap<Tile, List<Tile>> nearbyEnemies;

    /**
     * Creates new {@link Ants} object.
     * 
     * @param loadTime timeout for initializing and setting up the bot on turn 0
     * @param turnTime timeout for a single game turn, starting with turn 1
     * @param rows game map height
     * @param cols game map width
     * @param turns maximum number of turns the game will be played
     * @param viewRadius2 squared view radius of each ant
     * @param attackRadius2 squared attack radius of each ant
     * @param spawnRadius2 squared spawn radius of each ant
     */
    public Ants(int loadTime, int turnTime, int rows, int cols, int turns, int viewRadius2,
            int attackRadius2, int spawnRadius2) {
        this.loadTime = loadTime;
        this.turnTime = turnTime;
        this.rows = rows;
        this.cols = cols;
        this.turns = turns;
        this.viewRadius2 = viewRadius2;
        this.attackRadius2 = attackRadius2;
        this.extendedAttackRadius2 = (int) Math.pow((Math.sqrt(attackRadius2)+2) , 2);
        this.spawnRadius2 = spawnRadius2;
        map = new TileData[rows][cols];
        for (TileData[] row : map) {
            Arrays.fill(row, new TileData(Ilk.LAND));
        }
                
        visible = new boolean[rows][cols];
        for (boolean[] row : visible) {
            Arrays.fill(row, false);
        }
        
        // calculate offsets
        visionOffsets = getTilesFromRadius(viewRadius2);
        combatOffsets = getTilesFromRadius(attackRadius2);
        extendedCombatOffsets = getTilesFromRadius(extendedAttackRadius2);
    }
    
    public HashSet<Tile> getTilesFromRadius(int radius) {
    	return getTilesFromRadius(radius, false);
    }
    
    public HashSet<Tile> getTilesFromRadius(int radius, boolean outlineOnly) {
        HashSet<Tile> offsets = new HashSet<Tile>();
        int mx = (int)Math.sqrt(radius);
        for (int row = -mx; row <= mx; ++row) {
            for (int col = -mx; col <= mx; ++col) {
                int d = row * row + col * col;                
                if(outlineOnly) {
                	if (d == radius || d == (radius-1)) {
                        offsets.add(new Tile(row, col));
                    }                	
                } else {
                    if (d <= radius) {
                        offsets.add(new Tile(row, col));
                    }
                }              
            }
        }
        
        return offsets;
    }

    /**
     * Returns the cost of a tile on a bfs map.
     * 
     * @param tile to be found on costmap
     * @param costMap bfs map used to find cost.
     * @return cost of tile on bfs map.
     */
    public int getBfsCost(Tile tile, int[][] costMap) {
    	return costMap[tile.getRow()][tile.getCol()];
    }
    
    
    /**
     * Returns one or two orthogonal directions from one location to the another.
     * 
     * @param t1 one location on the game map
     * @param t2 another location on the game map
     * 
     * @return orthogonal directions from <code>t1</code> to <code>t2</code>
     */
    public List<Aim> getDirections(Tile t1, Tile t2) {
        List<Aim> directions = new ArrayList<Aim>();
        if (t1.getRow() < t2.getRow()) {
            if (t2.getRow() - t1.getRow() >= rows / 2) {
                directions.add(Aim.NORTH);
            } else {
                directions.add(Aim.SOUTH);
            }
        } else if (t1.getRow() > t2.getRow()) {
            if (t1.getRow() - t2.getRow() >= rows / 2) {
                directions.add(Aim.SOUTH);
            } else {
                directions.add(Aim.NORTH);
            }
        }        
        if (t1.getCol() < t2.getCol()) {
            if (t2.getCol() - t1.getCol() >= cols / 2) {
                directions.add(Aim.WEST);
            } else {
                directions.add(Aim.EAST);
            }
        } else if (t1.getCol() > t2.getCol()) {
            if (t1.getCol() - t2.getCol() >= cols / 2) {
                directions.add(Aim.EAST);
            } else {
                directions.add(Aim.WEST);
            }
        }
        return directions;
    }

    /**
     * Calculates visible information
     */
    public void setVision() {
        for (Tile antLoc : myAnts) {
            for (Tile locOffset : visionOffsets) {
                Tile newLoc = getTile(antLoc, locOffset);
                visible[newLoc.getRow()][newLoc.getCol()] = true;
            }
        }
    }

    public void update(Ilk ilk, Tile tile) {
    	// -1 = no owner.
    	update(ilk, tile, -1);
    }
    
    /**
     * Updates game state information about new ants and food locations.
     * 
     * @param ilk ilk to be updated
     * @param tile location on the game map to be updated
     * @param owner of this tile.
     */
    public void update(Ilk ilk, Tile tile, int owner) {
    	map[tile.getRow()][tile.getCol()] = new TileData(ilk, owner);
    	
        switch (ilk) {
            case FOOD:
                foodTiles.add(tile);
            break;
            case MY_ANT:
                myAnts.add(tile);
            break;
            case ENEMY_ANT:
                enemyAnts.add(tile);
            break;
        }
    }
    
    /**
     * Updates game state information about hills locations.
     *
     * @param owner owner of hill
     * @param tile location on the game map to be updated
     */
    public void updateHills(int owner, Tile tile) {
        if (owner > 0)
            enemyHills.add(tile);
        else
            myHills.add(tile);
    }

    /**
     * Issues an order by sending it to the system output.
     * 
     * @param myAnt map tile with my ant
     * @param direction direction in which to move my ant
     */
    public void issueOrder(Tile myAnt, Aim direction) {
        Order order = new Order(myAnt, direction);
        orders.add(order);
        System.out.println(order);
    }
    
    /** Returns ArrayList of defense tile offsets. **/
    public ArrayList<Tile> getDefensePoints() {
        ArrayList<Tile> defensePoints = new ArrayList<Tile>();
        for(Tile hill : getMyHills()) {	
        	for(int i = 1; i <= 1; i++) {        	
        		defensePoints.add( getTile(hill, new Tile( +i, +i) ));
        		defensePoints.add( getTile(hill, new Tile( -i, -i) ));
        		defensePoints.add( getTile(hill, new Tile( +i, -i) ));
        		defensePoints.add( getTile(hill, new Tile( -i, +i) ));
        	}
        }
                
        return defensePoints;
    }
    
    // ------------------- 
    // Clearing functions.
    // -------------------
    
    /**
     * Clears game state information about my ants locations.
     */
    public void clearMyAnts() {
        for (Tile myAnt : myAnts) {
            map[myAnt.getRow()][myAnt.getCol()] = new TileData(Ilk.LAND);
        }
        myAnts.clear();
    }

    /**
     * Clears game state information about enemy ants locations.
     */
    public void clearEnemyAnts() {
        for (Tile enemyAnt : enemyAnts) {
            map[enemyAnt.getRow()][enemyAnt.getCol()] = new TileData(Ilk.LAND);
        }
        enemyAnts.clear();
    }
    

    /**
     * Clears game state information about food locations.
     */
    public void clearFood() {
        for (Tile food : foodTiles) {
            map[food.getRow()][food.getCol()] = new TileData(Ilk.LAND);
        }
        foodTiles.clear();
    }

    /**
     * Clears game state information about my hills locations.
     */
    public void clearMyHills() {
        myHills.clear();
    }

    /**
     * Clears game state information about enemy hills locations.
     */
    public void clearEnemyHills() {
        enemyHills.clear();
    }

    /**
     * Clears game state information about dead ants locations.
     */
    public void clearDeadAnts() {
        //currently we do not have list of dead ants, so iterate over all map
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                if (map[row][col].getType() == Ilk.DEAD) {
                    map[row][col] = new TileData(Ilk.LAND);
                }
            }
        }
    }

    /**
     * Clears visible information
     */
    public void clearVision() {
        for (int row = 0; row < rows; ++row) {
            for (int col = 0; col < cols; ++col) {
                visible[row][col] = false;
            }
        }
    }
    
    
    // ------------------
    // Utility Functions.
    // ------------------
    /**
     * Finds all nearby enemies for the standard map of ants with attackRadius2.
     */
    public void loadNearbyEnemies() {
    	nearbyEnemies = generateNearbyEnemies(map, attackRadius2);
    }
    
    /**
     * Finds all nearby enemies for the specified region only.
     * (uses attackRadius2)
     */
    public HashMap<Tile, List<Tile>> generateNearbyEnemiesArea(TileData[][] map, 
    														   Tile origin, Set<Tile> offsets) {
    	HashMap<Tile, List<Tile>> nearbyEnemies = new HashMap<Tile, List<Tile>>();
    	for(Tile offset : offsets) {
    		Tile t = getTile(origin, offset);
    		TileData td = map[t.getRow()][t.getCol()];
    		if(td.isAnt()) {
    			List<Tile> enemies = findNearbyEnemies(t, map, attackRadius2);
    			nearbyEnemies.put(t, enemies);
    		}
    	}
    	return nearbyEnemies;
    }
    
    public HashMap<Tile, List<Tile>> generateNearbyEnemies(TileData[][] map, int radius2) {
    	HashMap<Tile, List<Tile>> nearbyEnemies = new HashMap<Tile, List<Tile>>();

    	for(int row = 0; row < rows; row++) {
    		for(int col = 0; col < cols; col++) {
    			Tile tile = new Tile(row, col);
    			TileData td = map[row][col];
    			if(td.isAnt()) {
    				List<Tile> enemies = findNearbyEnemies(tile, map, radius2);
    				nearbyEnemies.put(tile, enemies);
    			}
    		}
    	}
    	
    	return nearbyEnemies;
    }
    
    /**
     * Finds all nearby enemies that are at "radius2" distance from origin tile.
     */
    public List<Tile> findNearbyEnemies(Tile origin, TileData[][] map, int radius2) {
    	int owner = map[origin.getRow()][origin.getCol()].getOwner();    	
    	List<Tile> enemies = new ArrayList<Tile>();
    	
    	for(Tile offset : getTilesFromRadius(radius2)) {
    		Tile battleLoc = getTile(origin, offset);
    		TileData td = map[battleLoc.getRow()][battleLoc.getCol()];
    		if(td.isAnt() && td.getOwner() != owner) {
    			enemies.add(battleLoc);
    		}    		
    	}
    	    	
    	return enemies;
    }
    
    
    public int simulateBattleForArena(Tile origin, int radius2) {
    	return simulateBattleForArea(origin, map, nearbyEnemies, radius2);
    }
    /**
     * Simulates battle for a given area.
     * @return score of enemy deaths - friend deaths.
     */
    public int simulateBattleForArea(Tile origin, TileData[][] map, 
    		HashMap<Tile, List<Tile>> nearbyEnemies, int radius2) {
    	int friendDeaths = 0;
    	int enemyDeaths = 0;
    	
    	for(Tile offset : getTilesFromRadius(radius2)) {
    		Tile combatLoc = getTile(origin, offset);
    		TileData td = map[combatLoc.getRow()][combatLoc.getCol()];
    		if(td.getType() == Ilk.MY_ANT) {
    			if(!simulateBattleForAnt(combatLoc, nearbyEnemies))
    				friendDeaths++;
    			
    		} else if(td.getType() == Ilk.ENEMY_ANT) {
    			if(!simulateBattleForAnt(combatLoc, nearbyEnemies))
    				enemyDeaths++;
    		}
    	}
    	   	
    	return enemyDeaths - (friendDeaths*2);
    }
    
    /**
     * Simulates battle for any ant.     * 
     * @return true if ant survives, false if ant dies.
     */
    public boolean simulateBattleForAnt(Tile ant, HashMap<Tile, List<Tile>> nearbyEnemies) {
    	List<Tile> nearby = nearbyEnemies.get(ant);    	
    	int weakness = nearby.size();
    	
    	// an ant with no enemies can't be attacked
    	if(weakness == 0) return true;
    	
    	int minEnemyWeakness = -1;
    	// determine the most powerful nearby enemy
    	for(Tile enemy : nearby) {
    		int currEnemyWeakness = nearbyEnemies.get(enemy).size(); 
    		if(minEnemyWeakness == -1) {
    			minEnemyWeakness = currEnemyWeakness;
    		} else {
    			minEnemyWeakness = Math.min(currEnemyWeakness, minEnemyWeakness);
    		}
    	}
    	
        // ant dies if it is weak as or weaker than an enemy weakness
        if(minEnemyWeakness <= weakness) {
        	return false;
        } else {
        	return true;
        }    	    
    }
        


    /**
     * Constructs map with the new ant locations based on orders passed in.
     */
    public TileData[][] constructPredictionMap(Map<Tile, Tile> orders) {
    	TileData[][] predictionMap = copyMap();

    	for(Tile newLoc : orders.keySet()) {    		
    		Tile currLoc = orders.get(newLoc);
    		TileData antData = predictionMap[currLoc.getRow()][currLoc.getCol()];
    		predictionMap[currLoc.getRow()][currLoc.getCol()] = new TileData(Ilk.LAND);
    		predictionMap[newLoc.getRow()][newLoc.getCol()] = antData; 
    	}
    	return predictionMap;
    }
    
    
    /**
     * Generates a complete copy of the current map.
     * @return copy
     */
    public TileData[][] copyMap() {
    	TileData[][] copy = new TileData[rows][];
    	for(int row = 0; row < rows; row++) {
    		copy[row] = new TileData[cols];
    		for(int col = 0; col < cols; col++) {
    			copy[row][col] = map[row][col];
    		}
    	}
    	return copy;
    }
    
    /** 
     * Instantiates empty map of ints. 
     **/
    public int[][] createEmptyCostMap() {    	
    	int[][] map = new int[getRows()][];
    	for(int row = 0; row < getRows(); row++) {
    		map[row] = new int[getCols()];    		
    	}
    	
    	return map;
    }
    
    /** 
     * Constructs one BFS map by adding many together.
     * @param Array of BFS maps to be added.
     * @param modifiers array of multipliers to each 
     * 		  BFS map for weighting.
     * @return the composite BFS cost map.
     */
    public int[][] constructCompositeMap(int[][][] costMaps, int[] modifiers) {        	
    	int[][] compositeMap = new int[rows][];
        for(int row=0; row < rows; row++) {
        	compositeMap[row] = new int[cols];
        	for(int col=0; col < cols; col++) {        		
        		// add all maps into composite.
        		for(int i = 0; i < costMaps.length; i++) {
        			compositeMap[row][col] += costMaps[i][row][col] * modifiers[i];
        		}
        	}
        }
            	                                           
    	return compositeMap;
    }
            
    
    // --------------------------------
    // Pathfinding / Search Algorithms.
    // --------------------------------
    
    /**
     * Uses a BFS map to trace the path to the nearest tile
     * of the specified type.
     * 
     * @param start is the tile from where the search begins
     * @param costMap is the BFS map used for searching.int owner
     * @param ilkType is the type of tile being searched for.
     */
    public Tile bfsNearestTileType(Tile start, Ilk ilkType) {      	
    	// Initialize checkedMap array with 0's.
    	// 0 = unchecked tile.
    	// Anything above 0 = already checked tile
    	int[][] checkedMap = new int[rows][];
    	for(short i = 0; i < checkedMap.length; i++) {
    		checkedMap[i] = new int[cols];
    	}
    	
    	ArrayList<Tile> queue = new ArrayList<Tile>();
    	queue.add(start);
    	checkedMap[start.getRow()][start.getCol()] = 1;
    	
    	
    	while(queue.size() > 0) {
    		Tile tile = queue.remove(0);
    		// if our ilk has been found.
    		if(!tile.equals(start) && getIlk(tile) == ilkType) {
    			return tile;	
    		}
    		
    		for(Aim direction : Aim.values()) {
    			Tile neighbor = getTile(tile, direction);
    			
    			// Skip this tile if it has already been checked.
    			if(checkedMap[neighbor.getRow()][neighbor.getCol()] != 0)
    				continue;
    				    		    			
    			// Skip tile if it isn't a walkable tile
    			if(!getIlk(neighbor).isPassable())
    				continue;
    			
    			
    			// add cost of tile to costMap
    			checkedMap[neighbor.getRow()][neighbor.getCol()] = 1;
    			// add to queue
    			queue.add(neighbor);
    		}	
    	}
    	return null; 	
    }
    
    /** 
     * Uses BFS search to return list of tiles with specified ilk.
     * 
     * @param start is the tile from where the BFS is started
     * @param cap limits the max depth of the BFS search.
     * @param targetIlk is the tile type being searched for.
     * @return List of tiles found with the targetIlk type/
     */
	public ArrayList<Tile> bfsIlkSearch(Tile start, int cap, Ilk targetIlk) {
    	ArrayList<Tile> found = new ArrayList<Tile>();
    	
    	LinkedList<Tile> queue = new LinkedList<Tile>();
    	queue.add(start);
    	    	
    	byte[][] costMap = new byte[rows][];
    	for(short i = 0; i < costMap.length; i++) {
    		costMap[i] = new byte[cols];
    	}
    	costMap[start.getRow()][start.getCol()] = 1;
    	
    	while(queue.size() > 0) {
    		// fifo queue.
    		Tile tile = queue.pollLast();
    		Ilk ilk = getIlk(tile);
    		// skip if cap has been reached
    		if(costMap[tile.getRow()][tile.getCol()] > cap) continue;
    		
			if(ilk == targetIlk) { found.add(tile); }
			    		
    		for(Aim direction : Aim.values()) {
    			Tile neighbor = getTile(tile, direction);
    			
    			// Skip this tile if it has already been checked.
    			if(costMap[neighbor.getRow()][neighbor.getCol()] != 0)
    				continue;
    				    		    			
    			// Skip tile if it isn't a walkable tile
    			if(!getIlk(neighbor).isPassable())
    				continue;
    			    			
    			// add cost of tile to costMap
    			costMap[neighbor.getRow()][neighbor.getCol()] = (byte) (costMap[tile.getRow()][tile.getCol()] + 1);
    			
    			// add to queue
    			queue.add(neighbor);    			
    		}
    	}
    	
    	return found;
    }
	
	
    public int[][] bfs(List<Tile> queue, int[][] costMap) {
    	return bfs(queue, costMap, false);
    }
	
	/** Overloaded version of "BFS" that accepts a Set
	 *  instead of a List.
	 */
    public int[][] bfs(Set<Tile> set, int[][] costMap) {
    	return bfs(new ArrayList<Tile>(set), costMap, true);
    }
    
    /** 
     * Overloaded version of "bfs" that accepts a single start tile
     * instead of a initial open set.
     */
    public int[][] bfs(Tile start, int[][] costMap) {
    	ArrayList<Tile> queue = new ArrayList<Tile>();
    	queue.add(start);
    	return bfs(queue, costMap, true);
    }
    
    /**
     * Standard breadth-first search.
     * 
     * @param queue is the initial openSet used.
     * @return two-dimensional int array with distance costs. 
     */
    public int[][] bfs(List<Tile> queue, int[][] weights, boolean isCopy) {
    	// copies the contents of the array to not
    	// modify the original.
    	if(!isCopy) {
    		queue = new ArrayList<Tile>(queue);
    	}
    	    	
    	// Initialize costMap array with 0's.
    	// 0 = unchecked tile.
    	// Anything above 0 = already checked tile 
    	// (since cost has been assigned to it) 
    	int[][] costMap = createEmptyCostMap();

    	// loop through open set and assign their positions 
    	// in costMap a value of 1 (if they don't have a 
    	// value assigned already). Since 0 = unchecked tile,
    	// assigning an initial value of 1 sets the
    	// initial queue to already be 'checked'.  
    	for(Tile t : queue) { 
    		costMap[t.getRow()][t.getCol()] = 1;    		
    	}
    	
    	// initialize weights map to a bunch of 0's if it is null.
    	if(weights == null) {
    		weights = createEmptyCostMap();
    	}
    	    	
    	// While there are still nodes left to be evaluated.
    	while(queue.size() > 0) {
    		Tile tile = queue.remove(0);
    		for(Aim direction : Aim.values()) {
    			Tile neighbor = getTile(tile, direction);
    			int currCost = costMap[neighbor.getRow()][neighbor.getCol()];
    			int newCost = costMap[tile.getRow()][tile.getCol()] + 1;    			
    			
    			// Skip this tile if it already has a lower cost
    			// assigned to it.
    			if(currCost != 0 && currCost <= newCost)
    				continue;
    				    		    			
    			// Skip tile if it isn't a walkable tile.
    			if(!getIlk(neighbor).isPassable())
    				continue;
    			
    			// add cost of tile to costMap
    			costMap[neighbor.getRow()][neighbor.getCol()] = newCost;
    			
    			// add weights!
    			costMap[neighbor.getRow()][neighbor.getCol()] += weights[neighbor.getRow()][neighbor.getCol()];

    			// add to queue
    			if(currCost == 0)
    				queue.add(neighbor);
    		}    		
    	}
    	
    	return costMap;
    }
    
    
    
    
    
    public int[][] combatBfs(List<Tile> queue, int[][] weights) {    	 
    	// Initialize costMap array with 0's.
    	// 0 = unchecked tile.
    	// Anything above 0 = already checked tile 
    	// (since cost has been assigned to it) 
    	int[][] costMap = createEmptyCostMap();

    	// loop through open set and assign their positions 
    	// in costMap a value of 1 (if they don't have a 
    	// value assigned already). Since 0 = unchecked tile,
    	// assigning an initial value of 1 sets the
    	// initial queue to already be 'checked'.  
    	for(Tile t : queue) { 
    		costMap[t.getRow()][t.getCol()] = 1;    		
    	}
    	
    	// initialize weights map to a bunch of 0's if it is null.
    	if(weights == null) {
    		weights = createEmptyCostMap();
    	}
    	    	
    	// While there are still nodes left to be evaluated.
    	while(queue.size() > 0) {
    		Tile tile = queue.remove(0);
    		for(Aim direction : Aim.values()) {
    			Tile neighbor = getTile(tile, direction);
    			int currCost = costMap[neighbor.getRow()][neighbor.getCol()];
    			int newCost = costMap[tile.getRow()][tile.getCol()] + 1;    			
    			
    			// Skip this tile if it already has a lower cost
    			// assigned to it.
    			if(currCost != 0 && currCost <= newCost)
    				continue;
    				    		    			
    			// Skip tile if it isn't a walkable tile.
    			if(!getIlk(neighbor).isPassable())
    				continue;
    			
    			// add cost of tile to costMap
    			costMap[neighbor.getRow()][neighbor.getCol()] = newCost;
    			
    			// add weights!
    			costMap[neighbor.getRow()][neighbor.getCol()] += weights[neighbor.getRow()][neighbor.getCol()];

    			
    			// clustering
    			if(getIlk(neighbor) == Ilk.MY_ANT) {
    				costMap[neighbor.getRow()][neighbor.getCol()] -= 2; 
    			}
    			
    			
    			// add to queue
    			if(currCost == 0)
    				queue.add(neighbor);
    		}    		
    	}
    	
    	return costMap;
    }
    
    
    // -------------------
    // Getters and Setters
    // -------------------    

    /**
     * Returns timeout for initializing and setting up the bot on turn 0.
     * 
     * @return timeout for initializing and setting up the bot on turn 0
     */
    public int getLoadTime() {
        return loadTime;
    }

    /**
     * Returns timeout for a single game turn, starting with turn 1.
     * 
     * @return timeout for a single game turn, starting with turn 1
     */
    public int getTurnTime() {
        return turnTime;
    }

    /**
     * Returns game map height.
     * 
     * @return game map height
     */
    public int getRows() {
        return rows;
    }

    /**
     * Returns game map width.
     * 
     * @return game map width
     */
    public int getCols() {
        return cols;
    }

    /**
     * Returns maximum number of turns the game will be played.
     * 
     * @return maximum number of turns the game will be played
     */
    public int getTurns() {
        return turns;
    }

    /**
     * Returns squared view radius of each ant.
     * 
     * @return squared view radius of each ant
     */
    public int getViewRadius2() {
        return viewRadius2;
    }

    /**
     * Returns squared attack radius of each ant.
     * 
     * @return squared attack radius of each ant
     */
    public int getAttackRadius2() {
        return attackRadius2;
    }
    
    public int getExtendedAttackRadius2() {
    	return extendedAttackRadius2;
    }

    /**
     * Returns squared spawn radius of each ant.
     * 
     * @return squared spawn radius of each ant
     */
    public int getSpawnRadius2() {
        return spawnRadius2;
    }

    /**
     * Sets turn start time.
     * 
     * @param turnStartTime turn start time
     */
    public void setTurnStartTime(long turnStartTime) {
        this.turnStartTime = turnStartTime;
    }

    /**
     * Returns how much time the bot has still has to take its turn before timing out.
     * 
     * @return how much time the bot has still has to take its turn before timing out
     */
    public int getTimeRemaining() {
        return turnTime - (int)(System.currentTimeMillis() - turnStartTime);
    }

    /**
     * Returns ilk at the specified location.
     * 
     * @param tile location on the game map
     * 
     * @return ilk at the <cod>tile</code>
     */
    public Ilk getIlk(Tile tile) {
        return map[tile.getRow()][tile.getCol()].getType();
    }

    /**
     * Sets tile data at the specified location.
     * 
     * @param tile location on the game map
     * @param td tile data to be set at <code>tile</code>
     */
    public void setTileData(Tile tile, TileData td) {
        map[tile.getRow()][tile.getCol()] = td;
    }

    /**
     * Returns ilk at the location in the specified direction from the specified location.
     * 
     * @param tile location on the game map
     * @param direction direction to look up
     * 
     * @return ilk at the location in <code>direction</code> from <cod>tile</code>
     */
    public Ilk getIlk(Tile tile, Aim direction) {
        Tile newTile = getTile(tile, direction);
        return map[newTile.getRow()][newTile.getCol()].getType();
    }
    
    
    public TileData getTileData(Tile tile) {
    	return map[tile.getRow()][tile.getCol()];
    }

    /**
     * Returns location in the specified direction from the specified location.
     * 
     * @param tile location on the game map
     * @param direction direction to look up
     * 
     * @return location in <code>direction</code> from <cod>tile</code>
     */
    public Tile getTile(Tile tile, Aim direction) {
        int row = (tile.getRow() + direction.getRowDelta()) % rows;
        if (row < 0) {
            row += rows;
        }
        int col = (tile.getCol() + direction.getColDelta()) % cols;
        if (col < 0) {
            col += cols;
        }
        return new Tile(row, col);
    }

    /**
     * Returns location with the specified offset from the specified location.
     * 
     * @param tile location on the game map
     * @param offset offset to look up
     * 
     * @return location with <code>offset</code> from <cod>tile</code>
     */
    public Tile getTile(Tile tile, Tile offset) {
        int row = (tile.getRow() + offset.getRow()) % rows;
        if (row < 0) {
            row += rows;
        }
        int col = (tile.getCol() + offset.getCol()) % cols;
        if (col < 0) {
            col += cols;
        }
        return new Tile(row, col);
    }

    /**
     * Returns a set containing all my ants locations.
     * 
     * @return a set containing all my ants locations
     */
    public Set<Tile> getMyAnts() {
        return myAnts;
    }
    
    public Set<Tile> getCombatOffsets() {
    	return combatOffsets;
    }
    
    public Set<Tile> getExtendedCombatOffsets() {
    	return extendedCombatOffsets;
    }
    
    /**
     * Returns a set containing all enemy ants locations.
     * 
     * @return a set containing all enemy ants locations
     */
    public Set<Tile> getEnemyAnts() {
        return enemyAnts;
    }

    /**
     * Returns a set containing all my hills locations.
     * 
     * @return a set containing all my hills locations
     */
    public Set<Tile> getMyHills() {
        return myHills;
    }

    /**
     * Returns a set containing all enemy hills locations.
     * 
     * @return a set containing all enemy hills locations
     */
    public Set<Tile> getEnemyHills() {
        return enemyHills;
    }

    /**
     * Returns a set containing all food locations.
     * 
     * @return a set containing all food locations
     */
    public Set<Tile> getFoodTiles() {
        return foodTiles;
    }
    
    /**
     * Returns all orders sent so far.
     * 
     * @return all orders sent so far
     */
    public Set<Order> getOrders() {
        return orders;
    }
    
    /**
     * Returns true if a location is visible this turn
     *
     * @param tile location on the game map
     *
     * @return true if the location is visible
     */
    public boolean isVisible(Tile tile) {
        return visible[tile.getRow()][tile.getCol()];
    }
    
    /** 
     * Returns direction required to get from "t1" to "t2".
     * If no direction can take you from "t1" to "t2", null
     * is returned.
     */
    public Aim getDirection(Tile t1, Tile t2) {
    	for(Aim direction : Aim.values()) {
    		Tile neighbor = getTile(t1, direction); 
    		if(neighbor.equals(t2)) {
    			return direction;
    		}
    	}
    	return null;
    }
    
    public HashMap<Tile, List<Tile>> getNearbyEnemies() {
    	return nearbyEnemies;
    }
    
    /**
     * Calculates distance between two locations on the game map.
     * 
     * @param t1 one location on the game map
     * @param t2 another location on the game map
     * 
     * @return distance between <code>t1</code> and <code>t2</code>
     */
    public int getDistance(Tile t1, Tile t2) {
        int rowDelta = Math.abs(t1.getRow() - t2.getRow());
        int colDelta = Math.abs(t1.getCol() - t2.getCol());
        rowDelta = Math.min(rowDelta, rows - rowDelta);
        colDelta = Math.min(colDelta, cols - colDelta);
        return rowDelta * rowDelta + colDelta * colDelta;
    }
}