package version_one;

import battlecode.common.*;
import java.util.*;

public class RobotPlayer {
	// Static Variables for the Match
	static RobotController rc;
	static Team my_team;
	static Team enemy_team;
	static int my_range;
	static Random rand;
	static Direction[] directions = {Direction.NORTH, Direction.NORTH_EAST, Direction.EAST, Direction.SOUTH_EAST, Direction.SOUTH, Direction.SOUTH_WEST, Direction.WEST, Direction.NORTH_WEST};
	static int LARGE_NUM = 999999;
	
	public static void run(RobotController controller) {
        rc = controller;
		rand = new Random(rc.getID());

		my_range = rc.getType().attackRadiusSquared;
		my_team = rc.getTeam();
		enemy_team = my_team.opponent();

		// UNUSED:
		// MapLocation enemy_loc = rc.senseEnemyHQLocation();
  		// Direction last_direction = null;
            
        // DEBUG:
        // set_robot_string(rc);
		
		BaseBot bot;
		
		switch (rc.getType()) {
			case HQ: bot = new HQ(rc);
				break;
			case TOWER: bot = new Tower(rc);
				break;
			case BASHER: bot = new Basher(rc);
				break;
			case SOLDIER: bot = new Soldier(rc);
				break;
			case BEAVER: bot = new Beaver(rc);
				break;
			case BARRACKS: bot = new Barracks(rc);
				break;
			default: bot = new BaseBot(rc);
				break;
		}
		
		while (true) {
            try {
                bot.go();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
	}
	
	public static class BaseBot {
        protected RobotController rc;
        protected MapLocation myHQ, theirHQ;
        protected Team myTeam, theirTeam;

        public BaseBot(RobotController rc) {
            this.rc = rc;
            this.myHQ = rc.senseHQLocation();
            this.theirHQ = rc.senseEnemyHQLocation();
            this.myTeam = rc.getTeam();
            this.theirTeam = this.myTeam.opponent();
        }

        /*
         * 
         * 			BASEBOT HELPER FUNCTIONS
         * 
         */
        public Direction[] getDirectionsToward(MapLocation dest) {
            Direction toDest = rc.getLocation().directionTo(dest);
            Direction[] dirs = {toDest,
		    		toDest.rotateLeft(), toDest.rotateRight(),
				toDest.rotateLeft().rotateLeft(), toDest.rotateRight().rotateRight()};

            return dirs;
        }

        public Direction getMoveDir(MapLocation dest) {
            Direction[] dirs = getDirectionsToward(dest);
            for (Direction d : dirs) {
                if (rc.canMove(d)) {
                    return d;
                }
            }
            return null;
        }

        public Direction getSpawnDirection(RobotType type) {
            Direction[] dirs = getDirectionsToward(this.theirHQ);
            for (Direction d : dirs) {
                if (rc.canSpawn(d, type)) {
                    return d;
                }
            }
            return null;
        }

        public Direction getBuildDirection(RobotType type) {
            Direction[] dirs = getDirectionsToward(this.theirHQ);
            for (Direction d : dirs) {
                if (rc.canBuild(d, type)) {
                    return d;
                }
            }
            return null;
        }

        public RobotInfo[] getAllies() {
            RobotInfo[] allies = rc.senseNearbyRobots(Integer.MAX_VALUE, myTeam);
            return allies;
        }

        public RobotInfo[] getEnemiesInAttackingRange() {
            RobotInfo[] enemies = rc.senseNearbyRobots(RobotType.SOLDIER.attackRadiusSquared, theirTeam);
            return enemies;
        }

        public void attackLeastHealthEnemy(RobotInfo[] enemies) throws GameActionException {
            if (enemies.length == 0) {
                return;
            }

            double minEnergon = Double.MAX_VALUE;
            MapLocation toAttack = null;
            for (RobotInfo info : enemies) {
                if (info.health < minEnergon) {
                    toAttack = info.location;
                    minEnergon = info.health;
                }
            }

            rc.attackLocation(toAttack);
        }

        public void beginningOfTurn() {
            if (rc.senseEnemyHQLocation() != null) {
                this.theirHQ = rc.senseEnemyHQLocation();
            }
        }

        public void endOfTurn() {
        }

        public void go() throws GameActionException {
            beginningOfTurn();
            execute();
            endOfTurn();
        }

        public void execute() throws GameActionException {
            rc.yield();
        }
    }
	
	/*
	 * 
	 * 			HQ LOGIC
	 * 
	 */
    public static class HQ extends BaseBot {
        public HQ(RobotController rc) {
            super(rc);
        }

        public void execute() throws GameActionException {
            int numBeavers = rc.readBroadcast(2);

            if (rc.isCoreReady() && rc.getTeamOre() > 100 && numBeavers < 10) {
                Direction newDir = getSpawnDirection(RobotType.BEAVER);
                if (newDir != null) {
                    rc.spawn(newDir, RobotType.BEAVER);
                    rc.broadcast(2, numBeavers + 1);
                }
            }
            MapLocation rallyPoint;
            if (Clock.getRoundNum() < 600) {
                rallyPoint = new MapLocation( (this.myHQ.x + this.theirHQ.x) / 2,
                                              (this.myHQ.y + this.theirHQ.y) / 2);
            }
            else {
                rallyPoint = this.theirHQ;
            }
            rc.broadcast(0, rallyPoint.x);
            rc.broadcast(1, rallyPoint.y);

            rc.yield();
        }
    }
    
    /*
	 * 
	 * 			BEAVER LOGIC
	 * 
	 */
    public static class Beaver extends BaseBot {
        public Beaver(RobotController rc) {
            super(rc);
        }

        public void execute() throws GameActionException {
            if (rc.isCoreReady()) {
                if (rc.getTeamOre() < 500) {
                    //mine
                    if (rc.senseOre(rc.getLocation()) > 0) {
                        rc.mine();
                    }
                    else {
                        Direction newDir = getMoveDir(this.theirHQ);

                        if (newDir != null) {
                            rc.move(newDir);
                        }
                    }
                }
                else {
                    //build barracks
                    Direction newDir = getBuildDirection(RobotType.BARRACKS);
                    if (newDir != null) {
                        rc.build(newDir, RobotType.BARRACKS);
                    }
                }
            }

            rc.yield();
        }
    }
    
    /*
	 * 
	 * 			BARRACKS LOGIC
	 * 
	 */
    public static class Barracks extends BaseBot {
        public Barracks(RobotController rc) {
            super(rc);
        }

        public void execute() throws GameActionException {
            if (rc.isCoreReady() && rc.getTeamOre() > 200) {
                Direction newDir = getSpawnDirection(RobotType.SOLDIER);
                if (newDir != null) {
                    rc.spawn(newDir, RobotType.SOLDIER);
                }
            }

            rc.yield();
        }
    }
    
    /*
	 * 
	 * 			BASHER LOGIC
	 * 
	 */
    public static class Basher extends BaseBot {
        public Basher(RobotController rc) {
            super(rc);
        }

        public void execute() throws GameActionException {
        	try {
                // BASHERs attack automatically, so let's just move around mostly randomly
    			if (rc.isCoreReady()) {
    				int fate = rand.nextInt(1000);
    				if (fate < 800) {
    					try_move(directions[rand.nextInt(8)]);
    				} else {
    					try_move(rc.getLocation().directionTo(rc.senseEnemyHQLocation()));
    				}
    			}
            } catch (Exception e) {
    			System.out.println("Basher Exception");
    			e.printStackTrace();
            }

            rc.yield();
        }
    }
    
    /*
	 * 
	 * 			SOLDIER LOGIC
	 * 
	 */
    public static class Soldier extends BaseBot {
        public Soldier(RobotController rc) {
            super(rc);
        }

        public void execute() throws GameActionException {
            RobotInfo[] enemies = getEnemiesInAttackingRange();

            if (enemies.length > 0) {
                //attack!
                if (rc.isWeaponReady()) {
                    attackLeastHealthEnemy(enemies);
                }
            }
            else if (rc.isCoreReady()) {
                int rallyX = rc.readBroadcast(0);
                int rallyY = rc.readBroadcast(1);
                MapLocation rallyPoint = new MapLocation(rallyX, rallyY);

                Direction newDir = getMoveDir(rallyPoint);

                if (newDir != null) {
                    rc.move(newDir);
                }
            }
            rc.yield();
        }
    }
    
    /*
	 * 
	 * 			TOWER LOGIC
	 * 
	 */
    public static class Tower extends BaseBot {
        public Tower(RobotController rc) {
            super(rc);
        }

        public void execute() throws GameActionException {
            rc.yield();
        }
    }

/*
 *  
 *           LOGIC FUNCTIONS
 *   
 */
 
	static void hq_logic(RobotController rc){
		try {					
			int fate = rand.nextInt(10000);
			RobotInfo[] my_robots = rc.senseNearbyRobots(LARGE_NUM, my_team);
			int my_soldiers = 0;
			int my_bashers = 0;
			int my_beavers = 0;
			int my_barracks = 0;
			for (RobotInfo r : my_robots) {
				RobotType type = r.type;
				if (type == RobotType.SOLDIER) {
					my_soldiers++;
				} else if (type == RobotType.BASHER) {
					my_bashers++;
				} else if (type == RobotType.BEAVER) {
					my_beavers++;
				} else if (type == RobotType.BARRACKS) {
					my_barracks++;
				}
			}
			rc.broadcast(0, my_beavers);
			rc.broadcast(1, my_soldiers);
			rc.broadcast(2, my_bashers);
			rc.broadcast(3, my_barracks);
			
			if (rc.isWeaponReady()) {
				attack_something();
			}

			if (rc.isCoreReady() && rc.getTeamOre() >= 100 && fate < Math.pow(1.2,12-my_beavers)*10000) {
				try_spawn(directions[rand.nextInt(8)], RobotType.BEAVER);
			}
		} catch (Exception e) {
			System.out.println("HQ Exception");
            e.printStackTrace();
		}
	}

	static void tower_logic(RobotController rc) {
		try {					
			if (rc.isWeaponReady()) {
				attack_something();
			}
		} catch (Exception e) {
			System.out.println("Tower Exception");
            e.printStackTrace();
		}
	}

	static void soldier_logic(RobotController rc) {
		try {
            if (rc.isWeaponReady()) {
				attack_something();
			}
			if (rc.isCoreReady()) {
				int fate = rand.nextInt(1000);
				if (fate < 800) {
					try_move(directions[rand.nextInt(8)]);
				} else {
					try_move(rc.getLocation().directionTo(rc.senseEnemyHQLocation()));
				}
			}
        } catch (Exception e) {
			System.out.println("Soldier Exception");
			e.printStackTrace();
        }
	}

	static void beaver_logic(RobotController rc) {
		try {
			if (rc.isWeaponReady()) {
				attack_something();
			}
			if (rc.isCoreReady()) {
				int fate = rand.nextInt(1000);
				if (fate < 8 && rc.getTeamOre() >= 300) {
					try_build(directions[rand.nextInt(8)],RobotType.BARRACKS);
				} else if (fate < 600) {
					rc.mine();
				} else if (fate < 900) {
					try_move(directions[rand.nextInt(8)]);
				} else {
					try_move(rc.senseHQLocation().directionTo(rc.getLocation()));
				}
			}
		} catch (Exception e) {
			System.out.println("Beaver Exception");
            e.printStackTrace();
		}
	}

	static void barracks_logic(RobotController rc) {
		try {
			int fate = rand.nextInt(10000);
			
            // get information broadcasted by the HQ
			int my_beavers = rc.readBroadcast(0);
			int my_soldiers = rc.readBroadcast(1);
			int my_bashers = rc.readBroadcast(2);
			
			if (rc.isCoreReady() && rc.getTeamOre() >= 60 && fate < Math.pow(1.2,15-my_soldiers-my_bashers+my_beavers)*10000) {
				if (rc.getTeamOre() > 80 && fate % 2 == 0) {
					try_spawn(directions[rand.nextInt(8)],RobotType.BASHER);
				} else {
					try_spawn(directions[rand.nextInt(8)],RobotType.SOLDIER);
				}
			}
		} catch (Exception e) {
			System.out.println("Barracks Exception");
            e.printStackTrace();
		}
	}
	
	/*
	 * 
	 *                 HELPER FUNCTIONS
	 * 
	 */
	
    // This method will attack an enemy in sight, if there is one
	static void attack_something() throws GameActionException {
		RobotInfo[] enemies = rc.senseNearbyRobots(my_range, enemy_team);
		if (enemies.length > 0) {
			rc.attackLocation(enemies[0].location);
		}
	}
	
    // This method will attempt to move in Direction d (or as close to it as possible)
	static void try_move(Direction d) throws GameActionException {
		int offset_index = 0;
		int[] offsets = {0,1,-1,2,-2};
		int dir_int = direction_to_int(d);
		while (offset_index < 5 && !rc.canMove(directions[(dir_int+offsets[offset_index]+8)%8])) {
			offset_index++;
		}
		if (offset_index < 5) {
			rc.move(directions[(dir_int+offsets[offset_index]+8)%8]);
		}
	}
	
    // This method will attempt to spawn in the given direction (or as close to it as possible)
	static void try_spawn(Direction d, RobotType type) throws GameActionException {
		int offset_index = 0;
		int[] offsets = {0,1,-1,2,-2,3,-3,4};
		int dir_int = direction_to_int(d);
		while (offset_index < 8 && !rc.canSpawn(directions[(dir_int+offsets[offset_index]+8)%8], type)) {
			offset_index++;
		}
		if (offset_index < 8) {
			rc.spawn(directions[(dir_int+offsets[offset_index]+8)%8], type);
		}
	}
	
    // This method will attempt to build in the given direction (or as close to it as possible)
	static void try_build(Direction d, RobotType type) throws GameActionException {
		int offset_index = 0;
		int[] offsets = {0,1,-1,2,-2,3,-3,4};
		int dir_int = direction_to_int(d);
		while (offset_index < 8 && !rc.canMove(directions[(dir_int+offsets[offset_index]+8)%8])) {
			offset_index++;
		}
		if (offset_index < 8) {
			rc.build(directions[(dir_int+offsets[offset_index]+8)%8], type);
		}
	}

	static void set_robot_string(RobotController rc){
		try {
                rc.setIndicatorString(0, "I am a " + rc.getType());
            } catch (Exception e) {
                System.out.println("Unexpected exception");
                e.printStackTrace();
            }
	}
	
	static int direction_to_int(Direction d) {
		switch(d) {
			case NORTH:
				return 0;
			case NORTH_EAST:
				return 1;
			case EAST:
				return 2;
			case SOUTH_EAST:
				return 3;
			case SOUTH:
				return 4;
			case SOUTH_WEST:
				return 5;
			case WEST:
				return 6;
			case NORTH_WEST:
				return 7;
			default:
				return -1;
		}
	}
}
