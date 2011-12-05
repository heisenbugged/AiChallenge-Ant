import java.io.IOException;
import java.util.*;

import javax.swing.plaf.basic.BasicInternalFrameTitlePane.MoveAction;

public class MyBot extends Bot {
	// determines the range in which an ant is 
	// considered to be "close" to an enemy hill.
	public static int ENEMY_HILL_PROXIMITY = 10;
	
	// range at which an ant is near friendly hill
	// to be considered eligible for defense.
	public static int DEFENSE_RANGE = 10;
	
	// range at which an enemy ant is considered to 
	// be near friendly hill
	public static int ENEMY_DEFENSE_RANGE = 15;
	
	// Turns that need to pass before last_seen
	// value is factored into the explore map equation.
	public static int UNSEEN_WEIGHT = 10;
	
	// Current turn.
	public int turn = 0;
	
	private Map<Tile, Tile> orders = new HashMap<Tile, Tile>();
	private int[][] ordersMap;
	
	private Set<Tile> unexploredTiles;
	private int[][] lastSeen;
	private Set<Tile> seenFood = new HashSet<Tile>(); 
	private Set<Tile> seenEnemyHills = new HashSet<Tile>();
	
	private List<Tile> ants;
	private List<Tile> defenseAnts;	
	private int antsSize;
	private Map<Tile, Aim> antMoveHistory = new HashMap<Tile, Aim>();
		
	
	// base bfs cost maps
	private int[][] myAntsCostMap;
	private int[][] myHillsCostMap;
	private int[][] enemyAntsCostMap;
	private int[][] enemyHillsCostMap;	
	private int[][] foodCostMap;         
	private int[][] exploreCostMap;
	private int[][] defenseCostMap;
	
	// composite bfs cost maps
	private int[][] explorerCostMap;
	private int[][] combatCostMap;
	private int[][] defendorCostMap;
	private int[][] enemyCircleCostMap;
	List<int[][]> cornerMaps;
		
	
    /**
     * Main method executed by the game engine for starting the bot.
     * 
     * @param args command line arguments
     * 
     * @throws IOException if an I/O error occurs
     */
    public static void main(String[] args) throws IOException {
        new MyBot().readSystemInput();
    }
    
    
    /**
     * Main function. All the action happens here!
     */
    public void doTurn() {
        Ants ai = getAnts();
        ants = new ArrayList<Tile>(ai.getMyAnts());
        antsSize = ants.size();
        turn++;
        
        // refresh stale data.
        orders.clear();               
        refreshData();
        calculateCostMaps();                
        
        for(Tile enemy : ai.getEnemyAnts()) {
        	// if not in range of any ally ants, don't calculate the battle.
        	if(myAntsCostMap[enemy.getRow()][enemy.getCol()] > 5) continue;
        	resolveCombat(enemy);
        }
        
        // Re-ordering these functions gives them
        // different priorities.
        food();
        defense();
        explore();
		hillRazers();
        analysis();        
               
        // execute orders.
        for(Tile newLoc : orders.keySet()) {
        	Tile currLoc = orders.get(newLoc);        	
        	ai.issueOrder(currLoc, ai.getDirection(currLoc, newLoc));
        }

        System.err.println("finish time is: " + ai.getTimeRemaining());
    }

    private void resolveCombat(Tile origin) {
    	
    }
    
    private boolean queueMove(Tile antLoc, Aim direction) {    	
    	Ants ants = getAnts();
    	// Track all moves, prevent collisions
    	Tile newLoc = ants.getTile(antLoc, direction);
    	    	    	
    	// if this ant has already been moved, ignore.
    	if(ordersMap[antLoc.getRow()][antLoc.getCol()] == 1) {
    		return false;
    	}
    	
    	if(ants.getIlk(newLoc).isUnoccupied() && !orders.containsKey(newLoc)) {
    		addOrder(antLoc, direction);    		    		
    		return true;
    	} else {
    		return false;
    	}
    }
    
    private void refreshOrdersMap() {
    	Ants ai = getAnts();
    	ordersMap = new int[ai.getRows()][];
	    for(int row = 0; row < ordersMap.length; row++) {
	    	ordersMap[row] = new int[ai.getCols()];
	    }    	
    }
    
    
    /**
     * Adds an order to the order queue and antMoveHistory list.
     * @param ant to be moved.
     * @param direction ant is moved in.
     */
    private void addOrder(Tile ant, Aim direction) {
    	Ants ai = getAnts();
    	Tile newLoc = ai.getTile(ant, direction);
    	orders.put(newLoc, ant);
    	ordersMap[ant.getRow()][ant.getCol()] = 1;
    	antMoveHistory.put(newLoc, direction.opposite());
    	// ants.issueOrder(antLoc, direction);
    }
    /**
     * Removes order from queue.
     * @param t is the tile being moved to.
     */
    private void removeOrder(Tile t) {
    	Tile currLoc = orders.get(t);
    	orders.remove(t);
    	if(currLoc != null) 
    		ordersMap[currLoc.getRow()][currLoc.getCol()] = 0;
    }
       
    
    /** 
     * Returns the best direction for an ant based on the passed in BFS map.

     * @param a Ant to be moved
     * @param costMap BFS costMap used.
     * 
     * @param strict when on prevents the ant from moving
     *        if his current standing location has a better score
     *        than any of the 4 directions around him.
     *        
     * @return Direction chosen.
     */
    private Aim bfsMoveDir(Tile a, int[][] costMap, boolean strict) {
    	Ants ai = getAnts();    	
    	int lowestCost = -1;
    	Aim selectedDir = null;
    	boolean historySkipped = false;
    	
    	List<Aim> directions = Arrays.asList(Aim.values());    	
    	//Collections.shuffle(directions);
    	// find neighbor with lowest cost based on costMap
    	for(Aim direction : directions) {
    		Tile neighbor = ai.getTile(a, direction);
    		Ilk ilk = ai.getIlk(neighbor);    		
    		int cost = costMap[neighbor.getRow()][neighbor.getCol()];
    		
    		// 0 means unexplored, so skip.        	
    		if(cost == 0) continue;
    		
    		// ignore directions that are not passable.
    		if(!ilk.isUnoccupied() || !ilk.isPassable()) continue;    		
    		    		
    		// ignore direction if it goes to the square
    		// that this ant came from last turn. This
    		// helps avoid ants dancing back and forth between
    		// two squares because of a conflict in objectives.
    		if(antMoveHistory.get(a) == direction) {
    			historySkipped = true;
    			continue;
    		}		
    		
    		
    		// if lowestCost is undefined,
    		// use current direction and cost as baseline
    		if(lowestCost == -1) {    			
    			selectedDir = direction;
    			lowestCost = cost;
    			continue;
    		}
    		
    		// if current cost is lower than previous
    		// lowestCost, use it as the new lowestCost
    		if(cost < lowestCost) {
    			lowestCost = cost;
    			selectedDir = direction;
    		}
    	}
    	
    	// when strict mode is enabled, moves are only considered
    	// if they have a lower cost than the location where the ant
    	// is currently standing on.
    	int standingCost = ai.getBfsCost(a, costMap);
    	if(strict && standingCost != 0 && standingCost < lowestCost) {
    		return null;
    	}
    	    	
    	// if there are no other directions to go to except
    	// the one we came from, go ahead and choose it to 
    	// avoid ants getting stuck in corners forever.
    	if(selectedDir == null && historySkipped == true)
    		 selectedDir = antMoveHistory.get(a);    		    
    	
    	return selectedDir;
    }
    
    /**
     * Overload of BFS move (see below).
     * Strict = false when not passed in.
     */
    private boolean bfsMove(Tile a, int[][] costMap) {
    	return bfsMove(a, costMap, false);
    }
   
    /** 
     * Queues an order for passed-in ant based on a BFS cost map.
     * Uses the direction that yields the lowest BFS cost.
     * 
     * @param a Ant to be ordered.
     * @param costMap BFS Cost map used.
     * 
     * @param strict when on prevents the ant from moving
     *        if his current standing location has a better score
     *        than any of the 4 directions around him.
     *        
     *        strict when off makes the ant move no matter what.
     *        
     * @return If order-queue was successful or not.268
     */
    private boolean bfsMove(Tile a, int[][] costMap, boolean strict) {
    	Ants ai = getAnts();
    	Aim selectedDir = bfsMoveDir(a, costMap, strict);
    	
    	// if no direction was available, return false.
    	if(selectedDir == null) return false;
    	
    	// if neighbor was found that could be used
    	// move in that direction.
    	if(queueMove(a, selectedDir)) {
    		return true;    	
    	} else {
    		return false;
    	}    	
    }
    
    private void analysis() {
    	Ants ai = getAnts();
        // construct prediction map based on current set of orders.
    	TileData[][] map = ai.constructPredictionMap(orders);
        HashMap<Tile, List<Tile>> nearbyEnemies = ai.generateNearbyEnemies(map, ai.getAttackRadius2());
        
        // new orders to be appended based on adjustments.
        Map<Tile, Aim> newOrders = new HashMap<Tile, Aim>();
        
        
        for(Tile t : ai.getEnemyAnts()) {        	        	
        	// if not in range of any ally ants, don't calculate the battle.
        	if(myAntsCostMap[t.getRow()][t.getCol()] > 5) continue;        	
        	int score = ai.simulateBattleForArea(t, map, nearbyEnemies, ai.getAttackRadius2());
        	if(score < 0) {
        		// cancel orders for all ants nearby!
        		for(Tile offset : ai.getTilesFromRadius(ai.getAttackRadius2())) {
        			Tile combatLoc = ai.getTile(t, offset);
        			TileData td = map[combatLoc.getRow()][combatLoc.getCol()];
        			if(td.getType() == Ilk.MY_ANT) {
        				Tile currLoc = orders.get(combatLoc);
        				removeOrder(combatLoc);
        				/*
        				if(currLoc != null) {
        					Aim dir = bfsMoveDir(currLoc, enemyCircleCostMap, true);
        					if(dir != null) {
        						queueMove(currLoc, dir);	
        					}
        					
        				}
        				*/
        			}
        		}
        	}   
        	     	
        }
        
        // append adjusted orders.
        for(Tile ant : newOrders.keySet()) {
        	queueMove(ant, newOrders.get(ant));
        } 
    }
    
    // ------------------------------------------
    // Ant turn functions for different behaviors.
    // ------------------------------------------    
    private void food() {
    	Ants ai = getAnts();
        for(Tile food : seenFood) {
        	Tile nearestAnt = ai.bfsNearestTileType(food, Ilk.MY_ANT);
        	bfsMove(nearestAnt, foodCostMap);
        	ants.remove(nearestAnt);
        }    	
    }
    
    // BUGGY!!! NEED TO FIX!
    private void defense() {
    	Ants ai = getAnts();    	
        // sort by nearest to base to pick defenders.
        Collections.sort(ants, new BfsComparator(myHillsCostMap));       
        int enemyAntsNearHill = 0;
        int defendAllocation = 0;        
        // find number of enemy ants near my hill
        for(Tile ant : ai.getEnemyAnts()) {
        	if(myHillsCostMap[ant.getRow()][ant.getCol()] < ENEMY_DEFENSE_RANGE)
        		enemyAntsNearHill++;
        }
        
        System.err.println("Enemy ants near hill: " + enemyAntsNearHill);
                
        // assign an ant to defend base for every enemy ant near base
        for(Iterator<Tile> antIter = ants.iterator(); antIter.hasNext();) {
        	// break out of loop if all defenders have been allocated!
        	if(defendAllocation >= (enemyAntsNearHill*1.5)) break;
        	
        	Tile ant = antIter.next();
        	// Only choose this ant if it is close enough to the hill
        	// in order to defend it. If too far away, just skip.
        	if(ai.getBfsCost(ant, myHillsCostMap) > DEFENSE_RANGE) continue;
        	if(bfsMove(ant, defendorCostMap)) {
        	
        		System.err.println(ant + " is a defendor");
        		defenseAnts.add(ant);
        		antIter.remove();
                defendAllocation++;
        	}        	
        } 	
    }
    
    private void explore() {    	
        // number of ants to be assigned to exploring.
        // gradually decreases as more ants are created.
        double explorerAnts = Math.ceil(antsSize * (1 / Math.pow( (antsSize * .2), .35 )));        
        int explorerAllocation = 0;
        
        Collections.sort(ants, new BfsComparator(explorerCostMap));
        for(Iterator<Tile> explorers = ants.iterator(); explorers.hasNext();) {
        	Tile ant = explorers.next();
        	if(explorerAllocation >= explorerAnts) break;
        	
        	// ignore ants that are very close to enemy hills. 
        	// as these should always be assigned to combat.
    		int enemyHillCost = enemyHillsCostMap[ant.getRow()][ant.getCol()];
    		if(enemyHillCost <= ENEMY_HILL_PROXIMITY && enemyHillCost != 0) 
    			continue;
    		    	
        	if(bfsMove(ant, exploreCostMap)) {        		
        		explorers.remove();
        		explorerAllocation++;
        	}
        }    	
    }
    
    private void hillRazers() {
    	Ants ai = getAnts();
        for(Tile ant : ants) {
        	int cost = ai.getBfsCost(ant, enemyHillsCostMap);        	
        	if(cost > 10 || cost == 0) {
        		bfsMove(ant, combatCostMap);
        		
        	} else {
        		bfsMove(ant, enemyHillsCostMap);
        	}
        	
        }    	
    }
    
    
    /**
     * Clears out old data and adds new data.
     */
    private void refreshData() {
    	Ants ai = getAnts();
        refreshOrdersMap();
    	initializeUnexploredTiles(); 
    	refreshUnexploredTiles();
    	refreshEnemyHills(ants);
    	refreshFood();
        // add new foods found
        seenFood.addAll(ai.getFoodTiles()); 
        // add new hills found
        seenEnemyHills.addAll(ai.getEnemyHills());
        defenseAnts = new ArrayList<Tile>();
    }

    /** 
     * Calculates all BFS cost maps used for each turn. 
     */
    private void calculateCostMaps() {
    	Ants ai = getAnts();
    	    	    	
    	/**  base cost maps. **/
    	myAntsCostMap = ai.bfs(ai.getMyAnts(), null);
    	myHillsCostMap = ai.bfs(ai.getMyHills(), null);
    	
    	
    	
        enemyAntsCostMap = ai.combatBfs(new ArrayList<Tile>(ai.getEnemyAnts()), null);
        enemyHillsCostMap = ai.bfs(seenEnemyHills, null);        
        defenseCostMap = ai.bfs(ai.getDefensePoints(), null);
        foodCostMap = ai.bfs(seenFood, null);

        List<Tile> unseenTiles = getUnseenTiles();
        int[][] exploreMap = ai.createEmptyCostMap();
        for(Tile t : unseenTiles) {        	
        	exploreMap[t.getRow()][t.getCol()] = lastSeen[t.getRow()][t.getCol()];
        }
        for(Tile t : unexploredTiles) {
        	exploreMap[t.getRow()][t.getCol()] = 0;
        }
        exploreCostMap = ai.bfs(unseenTiles, exploreMap, true);
                                                      	            	      
        enemyCircleCostMap = ai.bfs(ai.getEnemyAnts(), null);
        for(Tile enemy : ai.getEnemyAnts()) {
        	for(Tile offset : ai.getTilesFromRadius(ai.getAttackRadius2())) {
        		Tile combatLoc = ai.getTile(enemy, offset);
        		enemyCircleCostMap[combatLoc.getRow()][combatLoc.getCol()] = 0;
        	}
        }
        
        /** composite cost maps. **/       
        explorerCostMap = ai.constructCompositeMap(new int[][][] {  exploreCostMap },
				  							       new int[]     {       1         });
            
        combatCostMap = ai.constructCompositeMap(new int[][][] { enemyHillsCostMap, enemyAntsCostMap, explorerCostMap},
        									     new int[]     {        10, 			   0,	    		1	     });
        
        defendorCostMap = ai.constructCompositeMap(new int[][][] { enemyAntsCostMap, myHillsCostMap },
				  								   new int[]     {       1, 			    1 	    });
                
    }
          
    
    /**
     * Clears out old food tiles that have been taken.
     */
    private void refreshFood() {
    	Ants ais = getAnts();
    	for(Iterator<Tile> foods = seenFood.iterator(); foods.hasNext();) {
    		Tile food = foods.next();
    		if(ais.getIlk(food) != Ilk.FOOD) foods.remove();
    	}    	
    }
    
    /**
     * Removes any "old" (captured) hills from the the list
     * of seen hills.
     */
    private void refreshEnemyHills(List<Tile> ants) {
        // remove stale hills
        for(Iterator<Tile> hills = seenEnemyHills.iterator(); hills.hasNext();) {
        	Tile hill = hills.next(); 
        	// if any ants are stepping on the enemy hill,
        	// the enemy hill has been razed and needs to be
        	// removed.
        	if(ants.contains(hill)) hills.remove();
        }
    }
    
    /**
     * Initializes empty hash-set of unexplored tiles.
     * Should only be run once at setup time.
     */
    private void initializeUnexploredTiles() {
    	Ants ants = getAnts();
        // add all locations to unexplored tiles set, run once
        if(unexploredTiles == null) {
        	unexploredTiles = new HashSet<Tile>();
        	for(int row = 0; row < ants.getRows(); row++) {
        		for(int col = 0; col < ants.getCols(); col++) {
        			unexploredTiles.add(new Tile(row, col));
        		}
        	}
        }                
    }
    
    /**
     * Calculates and compiles list of unexplored tiles for this turn.
     * The difference between this function and "getUnseenTiles"
     * is that getUnseenTiles() is based on the current FOW.
     * On the other hand: unexploredTiles is based on tiles 
     * that have never been seen at all.
     */   
    private void refreshUnexploredTiles() {
    	Ants ants = getAnts();
        // remove any tiles that can be seen, run each turn.
        for(Iterator<Tile> locIter = unexploredTiles.iterator(); locIter.hasNext();) {
        	Tile next = locIter.next();
        	if(ants.isVisible(next)) {
        		locIter.remove();
        	}
        }
    }
        
    
    /**
     * Calculates and compiles list of unseen tiles for this turn.
     * @return List of unseen tiles.
     */
    private List<Tile> getUnseenTiles() {
    	Ants ants = getAnts();
    	    	
    	// initialize last seen if it doesn't exist!
    	// (one time only)
    	if(lastSeen == null) {
    		lastSeen = new int[ants.getRows()][];
        	for(int row = 0; row < ants.getRows(); row++) {
        		lastSeen[row] = new int[ants.getCols()];
        		for(int col = 0; col < ants.getCols(); col++) {
        			lastSeen[row][col] = UNSEEN_WEIGHT;
        		}
        	}
    	}
    	
    	LinkedList<Tile> tiles = new LinkedList<Tile>();
    	for(int row = 0; row < ants.getRows(); row++) {
    		for(int col = 0; col < ants.getCols(); col++) {
    			Tile t = new Tile(row, col);
    			if(!ants.isVisible(t)) { 
    				tiles.add(t);    				
    				if(lastSeen[row][col] > 0) {
    					lastSeen[row][col]--;	
    				}    				
    			} else {
    				lastSeen[row][col] = UNSEEN_WEIGHT;
    			}
    		}
    	}
        return tiles;
    }    
}
