package version_two;

import battlecode.common.*;

import java.util.*;

public class RobotPlayer {
	// Static Variables for the Match
	static RobotController rc;
	static Team my_team;
	static Team enemy_team;
	static int my_range;
	static Random rand;
	static Direction facing;
	static int X_RALLY_CHANNEL = 0;
	static int Y_RALLY_CHANNEL = 1;
	static int NUM_BEAVERS_CHANNEL = 2;
	static int NUM_ATTACKERS_CHANNEL = 3;
	static int NUM_SOLDIERS_CHANNEL = 4;
	static int X_CLOSEST_TOWER_CHANNEL = 10;
	static int Y_CLOSEST_TOWER_CHANNEL = 11;
	static Direction[] directions = { Direction.NORTH, Direction.NORTH_EAST,
			Direction.EAST, Direction.SOUTH_EAST, Direction.SOUTH,
			Direction.SOUTH_WEST, Direction.WEST, Direction.NORTH_WEST };
	static MapLocation[] enemy_towers;
	static List<MapLocation> attack_sequence = new ArrayList<MapLocation>();
	

	public static void run(RobotController controller) {
		rc = controller;
		rand = new Random(rc.getID());

		my_range = rc.getType().attackRadiusSquared;
		my_team = rc.getTeam();
		enemy_team = my_team.opponent();
		facing = get_random_direction();// randomize starting direction
		BaseBot bot;
		// DEBUG:
		// set_robot_string(rc);

		switch (rc.getType()) {
		case HQ:
			enemy_towers = rc.senseEnemyTowerLocations();
			bot = new HQ(rc,enemy_towers.length);
			List<MapLocation> enemy_locations = new ArrayList<MapLocation>();
			for (MapLocation t: enemy_towers) {
				enemy_locations.add(t);
			}
			enemy_locations.add(bot.theirHQ);
			
			MapLocation last_location = bot.myHQ;
			int shortest_distance = Integer.MAX_VALUE;
			MapLocation closest = enemy_locations.get(0);
			while(enemy_locations.isEmpty() != true) {
				for(MapLocation loc: enemy_locations) {
					int new_distance = last_location.distanceSquaredTo(loc);
					if(new_distance < shortest_distance) {
						shortest_distance = new_distance;
						closest = loc;
					}
				}
				last_location=closest;
				attack_sequence.add(closest);
				enemy_locations.remove(closest);
				shortest_distance = Integer.MAX_VALUE;
			}
			break;
		case TOWER:
			bot = new Tower(rc);
			break;
		case BASHER:
			bot = new Basher(rc);
			break;
		case SOLDIER:
			bot = new Soldier(rc);
			break;
		case BEAVER:
			bot = new Beaver(rc);
			break;
		case BARRACKS:
			bot = new Barracks(rc);
			break;
		case MINER:
			bot = new Miner(rc);
			break;
		case MINERFACTORY:
			bot = new MinerFactory(rc);
			break;
		default:
			bot = new BaseBot(rc);
			break;
		}

		while (true) {
			try {
				bot.go();
				transfer_supplies();
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
			//TODO: Have the HQ do this and broadcast it			
			this.myHQ = rc.senseHQLocation();
			this.theirHQ = rc.senseEnemyHQLocation();
			this.myTeam = rc.getTeam();
			this.theirTeam = this.myTeam.opponent();
		}

		/*
		 * 
		 * BASEBOT HELPER FUNCTIONS
		 * 
		 */
		public Direction[] getDirectionsToward(MapLocation dest) {
			Direction toDest = rc.getLocation().directionTo(dest);
			Direction[] dirs = { toDest, toDest.rotateLeft(),
					toDest.rotateRight(), toDest.rotateLeft().rotateLeft(),
					toDest.rotateRight().rotateRight() };

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
			RobotInfo[] allies = rc
					.senseNearbyRobots(Integer.MAX_VALUE, myTeam);
			return allies;
		}

		public RobotInfo[] getEnemiesInAttackingRange() {
			RobotInfo[] enemies = rc.senseNearbyRobots(
					RobotType.SOLDIER.attackRadiusSquared, theirTeam);
			return enemies;
		}

		public void attackLeastHealthEnemy(RobotInfo[] enemies)
				throws GameActionException {
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
	 * HQ LOGIC
	 */
	public static class HQ extends BaseBot {
		private int attack_counter;
		private int number_of_enemy_towers;
		
		public HQ(RobotController rc, int num_of_towers) {
			super(rc);
			this.attack_counter = 0;
			this.number_of_enemy_towers = num_of_towers;
		}

		public void execute() throws GameActionException {
			int numBeavers = rc.readBroadcast(NUM_BEAVERS_CHANNEL);

			if (rc.isCoreReady() && rc.getTeamOre() > 100 && numBeavers < 10) {
				Direction newDir = getSpawnDirection(RobotType.BEAVER);
				if (newDir != null) {
					rc.spawn(newDir, RobotType.BEAVER);
					rc.broadcast(NUM_BEAVERS_CHANNEL, numBeavers + 1);
				}
			}

			MapLocation rallyPoint = new MapLocation(
					(this.myHQ.x + this.theirHQ.x) / 2,
					(this.myHQ.y + this.theirHQ.y) / 2);
			rc.broadcast(X_RALLY_CHANNEL, rallyPoint.x);
			rc.broadcast(Y_RALLY_CHANNEL, rallyPoint.y);
//			if (rc.readBroadcast(NUM_ATTACKERS_CHANNEL) < 50) {
//				rallyPoint = new MapLocation(
//						(this.myHQ.x + this.theirHQ.x) / 2,
//						(this.myHQ.y + this.theirHQ.y) / 2);
//			} else {
//				// rallyPoint = this.theirHQ;
//				rallyPoint = new MapLocation(-1, -1);
//			}
//			rc.broadcast(X_RALLY_CHANNEL, rallyPoint.x);
//			rc.broadcast(Y_RALLY_CHANNEL, rallyPoint.y);
//			MapLocation closest_tower = closest_enemy_tower();
			int current_num_of_towers = rc.senseEnemyTowerLocations().length;
			if (current_num_of_towers < this.number_of_enemy_towers) {
				this.attack_counter += 1;
				this.number_of_enemy_towers = current_num_of_towers;
			}
			
			rc.broadcast(X_CLOSEST_TOWER_CHANNEL, attack_sequence.get(this.attack_counter).x);
			rc.broadcast(Y_CLOSEST_TOWER_CHANNEL, attack_sequence.get(this.attack_counter).y);
			
			RobotInfo[] rallied_troops = rc.senseNearbyRobots(rallyPoint, 30,this.myTeam);
			if (rallied_troops.length > 70) {
				for (RobotInfo bot: rallied_troops) {
					rc.broadcast(id_to_channel(bot.ID),1);
				}
			}
			

			RobotInfo[] enemies = getEnemiesInAttackingRange();
			if (enemies.length > 0) {
				// attack!
				if (rc.isWeaponReady()) {
					attackLeastHealthEnemy(enemies);
				}
			}

			rc.yield();
		}
	}

	/*
	 * 
	 * BEAVER LOGIC
	 */
	public static class Beaver extends BaseBot {
		public Beaver(RobotController rc) {
			super(rc);
		}

		public void execute() throws GameActionException {
			RobotInfo[] enemies = getEnemiesInAttackingRange();

			if (enemies.length > 0) {
				// attack!
				if (rc.isWeaponReady()) {
					attackLeastHealthEnemy(enemies);
				}
			}

			if (Clock.getRoundNum() < 300) {
				build_unit(RobotType.MINERFACTORY);
			} else {
				build_unit(RobotType.BARRACKS);
			}

			mine_and_move();

			rc.yield();
		}
	}

	/*
	 * 
	 * BARRACKS LOGIC
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
					rc.broadcast(NUM_SOLDIERS_CHANNEL,
							rc.readBroadcast(NUM_SOLDIERS_CHANNEL) + 1);
					rc.broadcast(NUM_ATTACKERS_CHANNEL,
							rc.readBroadcast(NUM_ATTACKERS_CHANNEL) + 1);
				}
			}

			rc.yield();
		}
	}

	/*
	 * 
	 * BASHER LOGIC
	 */
	public static class Basher extends BaseBot {
		public Basher(RobotController rc) {
			super(rc);
		}

		public void execute() throws GameActionException {
			try {
				// BASHERs attack automatically, so let's just move around
				// mostly randomly
				if (rc.isCoreReady()) {
					int fate = rand.nextInt(1000);
					if (fate < 800) {
						try_move(directions[rand.nextInt(8)]);
					} else {
						try_move(rc.getLocation().directionTo(
								rc.senseEnemyHQLocation()));
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
	 * SOLDIER LOGIC
	 */
	public static class Soldier extends BaseBot {
		public Soldier(RobotController rc) {
			super(rc);
		}

		public void execute() throws GameActionException {
			RobotInfo[] enemies = getEnemiesInAttackingRange();

			if (enemies.length > 0) {
				// attack!
				if (rc.isWeaponReady()) {
					attackLeastHealthEnemy(enemies);
				}
			} else if (rc.isCoreReady()) {
//				int rallyX = rc.readBroadcast(X_RALLY_CHANNEL);
//				int rallyY = rc.readBroadcast(Y_RALLY_CHANNEL);
//				MapLocation rallyPoint = new MapLocation(rallyX, rallyY);
				// if (rallyX == -1 && rallyY == -1) {
				// rallyPoint = closest_enemy_tower();
				// if (rallyPoint == null) {
				// rallyPoint = this.theirHQ;
				// }
				// }
				
				int id = rc.getID();
				MapLocation rallyPoint = get_rally_point(this, id);

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
	 * TOWER LOGIC
	 */
	public static class Tower extends BaseBot {
		public Tower(RobotController rc) {
			super(rc);
		}

		public void execute() throws GameActionException {
			try {
				RobotInfo[] enemies = getEnemiesInAttackingRange();

				if (enemies.length > 0) {
					// attack!
					if (rc.isWeaponReady()) {
						attackLeastHealthEnemy(enemies);
					}
				}
			} catch (Exception e) {
				System.out.println("Tower Exception");
				e.printStackTrace();
			}

			rc.yield();
		}
	}

	/*
	 * 
	 * MINER LOGIC
	 */
	public static class Miner extends BaseBot {
		public Miner(RobotController rc) {
			super(rc);
		}

		public void execute() throws GameActionException {
			RobotInfo[] enemies = getEnemiesInAttackingRange();

			if (enemies.length > 0) {
				// attack!
				if (rc.isWeaponReady()) {
					attackLeastHealthEnemy(enemies);
				}
			}

			mine_and_move();

			rc.yield();
		}
	}

	/*
	 * 
	 * MINERFACTORY LOGIC
	 */
	public static class MinerFactory extends BaseBot {
		public MinerFactory(RobotController rc) {
			super(rc);
		}

		public void execute() throws GameActionException {
			if (rc.isCoreReady() && rc.getTeamOre() > 300) {
				Direction newDir = getSpawnDirection(RobotType.MINER);
				if (newDir != null) {
					rc.spawn(newDir, RobotType.MINER);
				}
			}

			rc.yield();
		}
	}

	/*
	 * 
	 * HELPER FUNCTIONS
	 */
	private static void transfer_supplies() throws GameActionException {
		RobotInfo[] nearbyAllies = rc.senseNearbyRobots(rc.getLocation(),
				GameConstants.SUPPLY_TRANSFER_RADIUS_SQUARED, rc.getTeam());
		double lowestSupply = rc.getSupplyLevel();
		double transferAmount = 0;
		MapLocation suppliesToThisLocation = null;
		for (RobotInfo ri : nearbyAllies) {
			if (ri.supplyLevel < lowestSupply) {
				lowestSupply = ri.supplyLevel;
				transferAmount = (rc.getSupplyLevel() - ri.supplyLevel) / 2;
				suppliesToThisLocation = ri.location;
			}
		}
		if (suppliesToThisLocation != null) {
			rc.transferSupplies((int) transferAmount, suppliesToThisLocation);
		}
	}

	// This method will attack an enemy in sight, if there is one
	static void attack_something() throws GameActionException {
		RobotInfo[] enemies = rc.senseNearbyRobots(my_range, enemy_team);
		if (enemies.length > 0) {
			rc.attackLocation(enemies[0].location);
		}
	}

	// This method will attempt to move in Direction d (or as close to it as
	// possible)
	static void try_move(Direction d) throws GameActionException {
		int offset_index = 0;
		int[] offsets = { 0, 1, -1, 2, -2 };
		int dir_int = direction_to_int(d);
		while (offset_index < 5
				&& !rc.canMove(directions[(dir_int + offsets[offset_index] + 8) % 8])) {
			offset_index++;
		}
		if (offset_index < 5) {
			rc.move(directions[(dir_int + offsets[offset_index] + 8) % 8]);
		}
	}

	// This method will attempt to spawn in the given direction (or as close to
	// it as possible)
	static void try_spawn(Direction d, RobotType type)
			throws GameActionException {
		int offset_index = 0;
		int[] offsets = { 0, 1, -1, 2, -2, 3, -3, 4 };
		int dir_int = direction_to_int(d);
		while (offset_index < 8
				&& !rc.canSpawn(
						directions[(dir_int + offsets[offset_index] + 8) % 8],
						type)) {
			offset_index++;
		}
		if (offset_index < 8) {
			rc.spawn(directions[(dir_int + offsets[offset_index] + 8) % 8],
					type);
		}
	}

	// This method will attempt to build in the given direction (or as close to
	// it as possible)
	static void try_build(Direction d, RobotType type)
			throws GameActionException {
		int offset_index = 0;
		int[] offsets = { 0, 1, -1, 2, -2, 3, -3, 4 };
		int dir_int = direction_to_int(d);
		while (offset_index < 8
				&& !rc.canMove(directions[(dir_int + offsets[offset_index] + 8) % 8])) {
			offset_index++;
		}
		if (offset_index < 8) {
			rc.build(directions[(dir_int + offsets[offset_index] + 8) % 8],
					type);
		}
	}

	static void set_robot_string(RobotController rc) {
		try {
			rc.setIndicatorString(0, "I am a " + rc.getType());
		} catch (Exception e) {
			System.out.println("Unexpected exception");
			e.printStackTrace();
		}
	}

	private static Direction get_random_direction() {
		return Direction.values()[(int) (rand.nextDouble() * 8)];
	}

	private static void mine_and_move() throws GameActionException {
		if (rc.senseOre(rc.getLocation()) > 1) {// there is ore, so try to mine
			if (rc.isCoreReady() && rc.canMine()) {
				rc.mine();
			}
		} else {// no ore, so look for ore
			move_around();
		}
	}

	private static void move_around() throws GameActionException {
		if (rand.nextDouble() < 0.05) {
			if (rand.nextDouble() < 0.5) {
				facing = facing.rotateLeft();
			} else {
				facing = facing.rotateRight();
			}
		}
		MapLocation tileInFront = rc.getLocation().add(facing);

		// check that the direction in front is not a tile that can be attacked
		// by the enemy towers
		MapLocation[] enemyTowers = rc.senseEnemyTowerLocations();
		boolean tileInFrontSafe = true;
		for (MapLocation m : enemyTowers) {
			if (m.distanceSquaredTo(tileInFront) <= RobotType.TOWER.attackRadiusSquared) {
				tileInFrontSafe = false;
				break;
			}
		}

		// check that we are not facing off the edge of the map
		if (rc.senseTerrainTile(tileInFront) != TerrainTile.NORMAL
				|| !tileInFrontSafe) {
			facing = facing.rotateLeft();
		} else {
			// try to move in the facing direction
			if (rc.isCoreReady() && rc.canMove(facing)) {
				rc.move(facing);
			}
		}
	}

	private static void build_unit(RobotType type) throws GameActionException {
		if (rc.getTeamOre() > type.oreCost) {
			Direction buildDir = get_random_direction();
			if (rc.isCoreReady() && rc.canBuild(buildDir, type)) {
				rc.build(buildDir, type);
			}
		}
	}

	private static MapLocation closest_enemy_tower() {
		MapLocation[] enemy_towers = rc.senseEnemyTowerLocations();
		int distance = Integer.MAX_VALUE;
		MapLocation closest_tower = null;
		int new_distance;

		for (MapLocation m : enemy_towers) {
			new_distance = rc.getLocation().distanceSquaredTo(m);
			if (new_distance <= distance) {
				closest_tower = m;
				distance = new_distance;
			}
		}

		return closest_tower;
	}

	private static MapLocation get_rally_point(BaseBot bot, int id) {
		MapLocation rallyPoint = null;
		try {
			int orders = rc.readBroadcast(id_to_channel(id));
			if (orders == 0) {
				rallyPoint = new MapLocation((bot.myHQ.x + bot.theirHQ.x) / 2,
						(bot.myHQ.y + bot.theirHQ.y) / 2);
			}
			else if (orders == 1) {
				rallyPoint = new MapLocation(rc.readBroadcast(X_CLOSEST_TOWER_CHANNEL),rc.readBroadcast(Y_CLOSEST_TOWER_CHANNEL));
			} else if (rallyPoint == null || orders == 2) {
				rallyPoint = bot.theirHQ;
			}
		} catch (Exception e) {
			System.out.println("out of bounds");
			rallyPoint = new MapLocation((bot.myHQ.x + bot.theirHQ.x) / 2,
					(bot.myHQ.y + bot.theirHQ.y) / 2);	
		}
		return rallyPoint;
	}
	
	private static int id_to_channel(int id){
		return (id%3500) + 3000;
	}

	static int direction_to_int(Direction d) {
		switch (d) {
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
