package version_four;

import battlecode.common.*;

import java.util.*;
import java.lang.Math;

/*
 * Ideas:
 * 	-better moving
 * 	-sensing ore
 *  -building out other robots
 *  -timing waves
 *  -sensing land obstacles
 *  -sending troop packs to different sides of the base
 *  -detecting for hole in defense to get to the hq
 * 
 * 
 */
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
	static int NUM_BARRACKS_CHANNEL = 5;
	static int NUM_MINERFACTORY_CHANNEL = 6;
	static int NUM_MINERS_CHANNEL = 7;
	static int NUM_TANKFACTORY_CHANNEL = 8;
	static int X_CLOSEST_TOWER_CHANNEL = 10;
	static int Y_CLOSEST_TOWER_CHANNEL = 11;
	static Direction[] directions = { Direction.NORTH, Direction.NORTH_EAST,
			Direction.EAST, Direction.SOUTH_EAST, Direction.SOUTH,
			Direction.SOUTH_WEST, Direction.WEST, Direction.NORTH_WEST };
	static MapLocation[] enemy_towers;
	static List<MapLocation> attack_sequence = new ArrayList<MapLocation>();

	// LOGIC CONSTANTS
	static int MINERFACTORY_LIMIT = 1;
	static int BARRACKS_LIMIT = 4;
	static int MINER_LIMIT = 12;
	static int BEAVER_LIMIT = 10;
	static int TANKFACTORY_LIMIT = 3;
	static int FINAL_PUSH_ROUND = 1700;

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
			bot = new HQ(rc, enemy_towers.length);
			generate_attack_sequence(bot);
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
		case TANK:
			bot = new Tank(rc);
			break;
		case TANKFACTORY:
			bot = new TankFactory(rc);
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
		protected List<Direction> direction_path;

		public BaseBot(RobotController rc) {
			this.rc = rc;
			// TODO: Have the HQ do this and broadcast it
			this.myHQ = rc.senseHQLocation();
			this.theirHQ = rc.senseEnemyHQLocation();
			this.myTeam = rc.getTeam();
			this.theirTeam = this.myTeam.opponent();
			this.direction_path = null;
		}

		/*
		 * 
		 * BASEBOT HELPER FUNCTIONS
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

		public void move_to_location(BaseBot bot, MapLocation loc)
				throws GameActionException {
//			Direction dir = facing;
//			if (rand.nextDouble() < 0.2) {
//				dir = get_random_direction();
//			} else {
//				dir = getMoveDir(loc);
//			}
//
//			if (dir == null) {
//				dir = facing;
//			}
//
//			MapLocation tileInFront = rc.getLocation().add(dir);
//
//			// check that we are not facing off the edge of the map
//			if (rc.senseTerrainTile(tileInFront) != TerrainTile.NORMAL) {
//				dir = dir.rotateLeft();
//			} else {
//				// try to move in the facing direction
//				if (rc.isCoreReady() && rc.canMove(dir)) {
//					rc.move(dir);
//				}
//			}
			
			 
			if (bot.direction_path.isEmpty()){
				bot.direction_path = a_star_search(rc.getLocation(), loc, Math.abs((bot.myHQ.x -bot.theirHQ.x) * (bot.myHQ.y - bot.theirHQ.y)));
			}
			Direction dir = bot.direction_path.remove(0);
			if (rc.isCoreReady() && rc.canMove(dir)) {
				rc.move(dir);
			}
		}

		public Direction get_spawn_direction(RobotType type) {
			Direction[] dirs = getDirectionsToward(this.theirHQ);
			for (Direction d : dirs) {
				if (rc.canSpawn(d, type)) {
					return d;
				}
			}
			return null;
		}

		public Direction get_build_direction(RobotType type) {
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

			if (rc.isCoreReady() && rc.getTeamOre() > 100
					&& numBeavers < BEAVER_LIMIT) {
				Direction newDir = get_spawn_direction(RobotType.BEAVER);
				if (newDir != null) {
					rc.spawn(newDir, RobotType.BEAVER);
					rc.broadcast(NUM_BEAVERS_CHANNEL, numBeavers + 1);
				}
			}

			int current_num_of_towers = rc.senseEnemyTowerLocations().length;
			if (current_num_of_towers < this.number_of_enemy_towers) {
				this.attack_counter += 1;
				this.number_of_enemy_towers = current_num_of_towers;
			}

			MapLocation rallyPoint = generate_rally_point(this);
			rc.broadcast(X_RALLY_CHANNEL, rallyPoint.x);
			rc.broadcast(Y_RALLY_CHANNEL, rallyPoint.y);

			rc.broadcast(X_CLOSEST_TOWER_CHANNEL,
					attack_sequence.get(this.attack_counter).x);
			rc.broadcast(Y_CLOSEST_TOWER_CHANNEL,
					attack_sequence.get(this.attack_counter).y);

			RobotInfo[] rallied_troops = rc.senseNearbyRobots(rallyPoint, 30,
					this.myTeam);
			if (rallied_troops.length > 40) {
				for (RobotInfo bot : rallied_troops) {
					rc.broadcast(id_to_channel(bot.ID), 1);
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

			if (Clock.getRoundNum() < 300
					&& rc.readBroadcast(NUM_MINERFACTORY_CHANNEL) < MINERFACTORY_LIMIT) {
				build_unit(RobotType.MINERFACTORY, get_random_direction(),
						NUM_MINERFACTORY_CHANNEL);
			} else if (Clock.getRoundNum() < 1000
					&& rc.readBroadcast(NUM_BARRACKS_CHANNEL) < BARRACKS_LIMIT) {
				build_unit(RobotType.BARRACKS, get_random_direction(),
						NUM_BARRACKS_CHANNEL);
			} else if (rc.readBroadcast(NUM_TANKFACTORY_CHANNEL) < TANKFACTORY_LIMIT) {
				build_unit(RobotType.TANKFACTORY, get_random_direction(),
						NUM_TANKFACTORY_CHANNEL);
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
			if (rc.getTeamOre() > 200) {
				// Build Soldier if game is early
				if (Clock.getRoundNum() < 1000
						|| rc.readBroadcast(NUM_TANKFACTORY_CHANNEL) == TANKFACTORY_LIMIT) {
					spawn_unit(RobotType.SOLDIER,
							get_spawn_direction(RobotType.SOLDIER),
							NUM_ATTACKERS_CHANNEL);
				} else {
					build_unit(RobotType.TANKFACTORY,
							get_build_direction(RobotType.TANKFACTORY));
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
					int id = rc.getID();
					MapLocation rallyPoint = get_rally_point(this, id);
					move_to_location(this, rallyPoint);
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
				int id = rc.getID();
				MapLocation rallyPoint = get_rally_point(this, id);
				move_to_location(this, rallyPoint);
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
			int num_of_miners = rc.readBroadcast(NUM_MINERS_CHANNEL);
			if (rc.getTeamOre() > 300 && num_of_miners < MINER_LIMIT) {
				spawn_unit(RobotType.MINER,
						get_spawn_direction(RobotType.MINER),
						NUM_MINERS_CHANNEL);
			}

			rc.yield();
		}
	}

	/*
	 * 
	 * TANK LOGIC
	 */
	public static class Tank extends BaseBot {
		public Tank(RobotController rc) {
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
				int id = rc.getID();
				MapLocation rallyPoint = get_rally_point(this, id);
				move_to_location(this, rallyPoint);
			}
			rc.yield();
		}
	}

	/*
	 * 
	 * TANKFACTORY LOGIC
	 */
	public static class TankFactory extends BaseBot {
		public TankFactory(RobotController rc) {
			super(rc);
		}

		public void execute() throws GameActionException {
			if (rc.getTeamOre() > 200) {
				spawn_unit(RobotType.TANK, get_spawn_direction(RobotType.TANK));
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

	private static void build_unit(RobotType type, Direction d)
			throws GameActionException {
		if (d != null && rc.getTeamOre() > type.oreCost) {
			if (rc.isCoreReady() && rc.canBuild(d, type)) {
				rc.build(d, type);
			}
		}
	}

	private static void build_unit(RobotType type, Direction d, int channel)
			throws GameActionException {
		if (d != null && rc.getTeamOre() > type.oreCost) {
			if (rc.isCoreReady() && rc.canBuild(d, type)) {
				rc.build(d, type);
				rc.broadcast(channel, rc.readBroadcast(channel) + 1);
			}
		}
	}

	private static void spawn_unit(RobotType type, Direction d)
			throws GameActionException {
		if (d != null && rc.isCoreReady() && rc.canSpawn(d, type)) {
			rc.spawn(d, type);
		}
	}

	private static void spawn_unit(RobotType type, Direction d, int channel)
			throws GameActionException {
		if (d != null && rc.isCoreReady() && rc.canSpawn(d, type)) {
			rc.spawn(d, type);
			rc.broadcast(channel, rc.readBroadcast(channel) + 1);
		}
	}

	private static MapLocation get_rally_point(BaseBot bot, int id)
			throws GameActionException {
		MapLocation rallyPoint = null;
		try {
			if (Clock.getRoundNum() > FINAL_PUSH_ROUND) {
				rallyPoint = bot.theirHQ;
			} else {
				int orders = rc.readBroadcast(id_to_channel(id));
				if (orders == 0) {
					rallyPoint = get_build_rally_point(bot);
				} else if (orders == 1) {
					if (low_attack_density(bot)) {
						rallyPoint = get_build_rally_point(bot);
						rc.broadcast(id_to_channel(id), 0);

					} else {
						rallyPoint = new MapLocation(
								rc.readBroadcast(X_CLOSEST_TOWER_CHANNEL),
								rc.readBroadcast(Y_CLOSEST_TOWER_CHANNEL));
					}
				} else if (rallyPoint == null || orders == 2) {
					rallyPoint = bot.theirHQ;
				}
			}
		} catch (Exception e) {
			System.out.println("out of bounds");
			rallyPoint = get_build_rally_point(bot);
		}

		return rallyPoint;
	}

	private static void generate_attack_sequence(BaseBot bot) {
		List<MapLocation> enemy_locations = new ArrayList<MapLocation>();
		for (MapLocation t : enemy_towers) {
			enemy_locations.add(t);
		}
		enemy_locations.add(bot.theirHQ);

		MapLocation last_location = bot.myHQ;
		int shortest_distance = Integer.MAX_VALUE;
		MapLocation closest = enemy_locations.get(0);
		while (enemy_locations.isEmpty() != true) {
			for (MapLocation loc : enemy_locations) {
				int new_distance = last_location.distanceSquaredTo(loc) + 2
						* bot.myHQ.distanceSquaredTo(loc);
				if (new_distance < shortest_distance) {
					shortest_distance = new_distance;
					closest = loc;
				}
			}
			last_location = closest;
			attack_sequence.add(closest);
			enemy_locations.remove(closest);
			shortest_distance = Integer.MAX_VALUE;
		}
	}

	private static MapLocation generate_rally_point(HQ bot) {
		return new MapLocation((bot.myHQ.x + bot.theirHQ.x) / 2,
				(bot.myHQ.y + bot.theirHQ.y) / 2);
		// return new MapLocation(
		// (int) (0.25 * bot.myHQ.x + 0.75 * attack_sequence
		// .get(bot.attack_counter).x),
		// (int) (0.25 * bot.myHQ.y + 0.75 * attack_sequence
		// .get(bot.attack_counter).y));
	}

	private static boolean low_attack_density(BaseBot bot) {
		return rc.senseNearbyRobots(rc.getLocation(), 30, bot.myTeam).length < 10;
	}

	private static MapLocation get_build_rally_point(BaseBot bot)
			throws GameActionException {
		return new MapLocation(rc.readBroadcast(X_RALLY_CHANNEL),
				rc.readBroadcast(Y_RALLY_CHANNEL));
	}

	private static int id_to_channel(int id) {
		return (id % 3500) + 3000;
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

	static private int manhattan_distance(MapLocation a, MapLocation b) {
		return Math.abs(a.x - b.x) + Math.abs(a.y - b.y);
	}

	static private MapLocation[] get_neighbors(MapLocation loc) {
		MapLocation[] neighbors = new MapLocation[8];
		for (int i = 0; i < directions.length; i++) {
			neighbors[i] = loc.add(directions[i]);
		}
		return neighbors;
	}

	static private List<Direction> a_star_search(
			MapLocation start, MapLocation finish, int max_nodes) {
		PriorityQueue<Tuple<MapLocation, Integer>> frontier = new PriorityQueue<Tuple<MapLocation, Integer>>(
				max_nodes, new Comparator<Tuple<MapLocation, Integer>>() {

					@Override
					public int compare(Tuple<MapLocation, Integer> o1,
							Tuple<MapLocation, Integer> o2) {
						if (o1.y < o2.y) {
							return -1;
						} else if (o1.y > o2.y) {
							return 1;
						} else {
							return 0;
						}
					}
				});

		frontier.add(new Tuple<MapLocation, Integer>(start, 0));
		List<Direction> direction_path = new ArrayList<Direction>();
		Map<MapLocation, Integer> cost_so_far = new HashMap<MapLocation, Integer>();
		cost_so_far.put(start, 0);

		MapLocation current;
		while (!frontier.isEmpty()) {
			current = frontier.poll().x;

			if (current.equals(finish)) {
				break;
			}

			MapLocation[] neighbors = get_neighbors(current);
			MapLocation next;
			int new_cost, priority;
			for (int i=0; i<neighbors.length; i++) {
				next = neighbors[i];
				new_cost = cost_so_far.get(current) + 1;
				if (!cost_so_far.containsKey(next)
						|| new_cost < cost_so_far.get(next)) {
					cost_so_far.put(next, new_cost);
					priority = new_cost + manhattan_distance(next, finish);
					frontier.add(new Tuple<MapLocation, Integer>(next, priority));
					direction_path.add(directions[i]);
				}

			}

		}

		return direction_path;
	}
}

class Tuple<X, Y> {
	public final X x;
	public final Y y;

	public Tuple(X x, Y y) {
		this.x = x;
		this.y = y;
	}
}
