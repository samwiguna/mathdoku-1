package net.cactii.mathdoku.storage.database;

import net.cactii.mathdoku.developmentHelper.DevelopmentHelper;
import net.cactii.mathdoku.developmentHelper.DevelopmentHelper.Mode;
import net.cactii.mathdoku.grid.Grid;
import net.cactii.mathdoku.statistics.CumulativeStatistics;
import net.cactii.mathdoku.statistics.GridStatistics;
import net.cactii.mathdoku.statistics.HistoricStatistics;
import net.cactii.mathdoku.statistics.HistoricStatistics.Serie;
import net.cactii.mathdoku.storage.database.Projection.Aggregation;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteQueryBuilder;
import android.util.Log;

/**
 * The database adapter for the statistics table. For each grid zero or more
 * statistics records can exists in the database. Only for historic games no
 * statistics will exist. Multiple statistics will only exist in case a game is
 * replayed in order to try to improve the statistics for the grid.
 */
public class StatisticsDatabaseAdapter extends DatabaseAdapter {
	private static final String TAG = "MathDoku.StatisticsDatabaseAdapter";

	public static final boolean DEBUG_SQL = (DevelopmentHelper.mMode == Mode.DEVELOPMENT) && false;

	// Columns for table statistics
	private static final String TABLE = "statistics";
	private static final String KEY_ROWID = "_id";
	private static final String KEY_GRID_ID = "grid_id";
	private static final String KEY_REPLAY = "replay";
	private static final String KEY_FIRST_MOVE = "first_move";
	private static final String KEY_LAST_MOVE = "last_move";
	public static final String KEY_ELAPSED_TIME = "elapsed_time";
	public static final String KEY_CHEAT_PENALTY_TIME = "cheat_penalty_time";
	public static final String KEY_CELLS_FILLED = "cells_filled";
	public static final String KEY_CELLS_EMPTY = "cells_empty";
	public static final String KEY_CELLS_REVEALED = "cells_revealed";
	public static final String KEY_USER_VALUES_REPLACED = "user_value_replaced";
	public static final String KEY_POSSIBLES = "possibles";
	public static final String KEY_ACTION_UNDOS = "action_undos";
	public static final String KEY_ACTION_CLEAR_CELL = "action_clear_cells";
	public static final String KEY_ACTION_CLEAR_GRID = "action_clear_grid";
	public static final String KEY_ACTION_REVEAL_CELL = "action_reveal_cell";
	public static final String KEY_ACTION_REVEAL_OPERATOR = "action_reveal_operators";
	public static final String KEY_ACTION_CHECK_PROGRESS = "action_check_progress";
	public static final String KEY_CHECK_PROGRESS_INVALID_CELLS_FOUND = "check_progress_invalid_cells_found";
	private static final String KEY_ACTION_REVEAL_SOLUTION = "action_reveal_solution";
	private static final String KEY_SOLVED_MANUALLY = "solved_manually";
	private static final String KEY_FINISHED = "finished";

	// For each grid only the latest completed solving attempt should be
	// included in the statistics. Only in case no finished solving attempt
	// exists for a grid, the latest unfinished solving attempt should be used.
	// For ease and speed of retrieving it is stored whether this solving
	// attempt should be included or exlcuded from the statistics.
	private static final String KEY_INCLUDE_IN_STATISTICS = "include_in_statistics";

	private static final String[] allColumns = { KEY_ROWID, KEY_GRID_ID,
			KEY_REPLAY, KEY_FIRST_MOVE, KEY_LAST_MOVE, KEY_ELAPSED_TIME,
			KEY_CHEAT_PENALTY_TIME, KEY_CELLS_FILLED, KEY_CELLS_EMPTY,
			KEY_CELLS_REVEALED, KEY_USER_VALUES_REPLACED, KEY_POSSIBLES,
			KEY_ACTION_UNDOS, KEY_ACTION_CLEAR_CELL, KEY_ACTION_CLEAR_GRID,
			KEY_ACTION_REVEAL_CELL, KEY_ACTION_REVEAL_OPERATOR,
			KEY_ACTION_CHECK_PROGRESS, KEY_CHECK_PROGRESS_INVALID_CELLS_FOUND,
			KEY_ACTION_REVEAL_SOLUTION, KEY_SOLVED_MANUALLY, KEY_FINISHED,
			KEY_INCLUDE_IN_STATISTICS };

	// Projection for retrieve the cumulative and historic statistics
	private static Projection mCumulativeStatisticsProjection = null;
	private static Projection mHistoricStatisticsProjection = null;

	@Override
	protected String getTableName() {
		return TABLE;
	}

	/**
	 * Builds the SQL create statement for this table.
	 * 
	 * @return The SQL create statement for this table.
	 */
	protected static String buildCreateSQL() {
		return createTable(
				TABLE,
				createColumn(KEY_ROWID, "integer", "primary key autoincrement"),
				createColumn(KEY_GRID_ID, "integer", " not null"),
				createColumn(KEY_REPLAY, "integer", " not null default 0"),
				createColumn(KEY_FIRST_MOVE, "datetime", "not null"),
				createColumn(KEY_LAST_MOVE, "datetime", "not null"),
				createColumn(KEY_ELAPSED_TIME, "long", "not null default 0"),
				createColumn(KEY_CHEAT_PENALTY_TIME, "long",
						"not null default 0"),
				createColumn(KEY_CELLS_FILLED, "integer", " not null default 0"),
				createColumn(KEY_CELLS_EMPTY, "integer", " not null default 0"),
				createColumn(KEY_CELLS_REVEALED, "integer",
						" not null default 0"),
				createColumn(KEY_USER_VALUES_REPLACED, "integer",
						" not null default 0"),
				createColumn(KEY_POSSIBLES, "integer", " not null default 0"),
				createColumn(KEY_ACTION_UNDOS, "integer", " not null default 0"),
				createColumn(KEY_ACTION_CLEAR_CELL, "integer",
						" not null default 0"),
				createColumn(KEY_ACTION_CLEAR_GRID, "integer",
						" not null default 0"),
				createColumn(KEY_ACTION_REVEAL_CELL, "integer",
						" not null default 0"),
				createColumn(KEY_ACTION_REVEAL_OPERATOR, "integer",
						" not null default 0"),
				createColumn(KEY_ACTION_CHECK_PROGRESS, "integer",
						" not null default 0"),
				createColumn(KEY_CHECK_PROGRESS_INVALID_CELLS_FOUND, "integer",
						" not null default 0"),
				createColumn(KEY_ACTION_REVEAL_SOLUTION, "string",
						" not null default `false`"),
				createColumn(KEY_SOLVED_MANUALLY, "string",
						" not null default `false`"),
				createColumn(KEY_FINISHED, "string",
						" not null default `false`"),
				createColumn(KEY_INCLUDE_IN_STATISTICS, "string",
						" not null default `false`"),
				createForeignKey(KEY_GRID_ID, GridDatabaseAdapter.TABLE,
						GridDatabaseAdapter.KEY_ROWID));
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see net.cactii.mathdoku.storage.database.DatabaseAdapter#getCreateSQL()
	 */
	@Override
	protected String getCreateSQL() {
		return buildCreateSQL();
	}

	/**
	 * Creates the table.
	 * 
	 * @param db
	 *            The database in which the table has to be created.
	 */
	protected static void create(SQLiteDatabase db) {
		String sql = buildCreateSQL();
		if (DevelopmentHelper.mMode == Mode.DEVELOPMENT) {
			Log.i(TAG, sql);
		}

		// Execute create statement
		db.execSQL(sql);
	}

	/**
	 * Upgrades the table to an other version.
	 * 
	 * @param db
	 *            The database in which the table has to be updated.
	 * @param oldVersion
	 *            The old version of the database. Use the app revision number
	 *            to identify the database version.
	 * @param newVersion
	 *            The new version of the database. Use the app revision number
	 *            to identify the database version.
	 */
	protected static void upgrade(SQLiteDatabase db, int oldVersion,
			int newVersion) {
		if (oldVersion < 438 && newVersion >= 438) {
			// In development and beta revisions the table is simply dropped and
			// recreated.
			try {
				String sql = "DROP TABLE " + TABLE;
				if (DEBUG_SQL) {
					Log.i(TAG, sql);
				}
				db.execSQL(sql);
			} catch (SQLiteException e) {
				if (DevelopmentHelper.mMode == Mode.DEVELOPMENT) {
					e.printStackTrace();
				}
			}
			create(db);
		}
	}

	/**
	 * Inserts a new statistics record for a grid into the database.
	 * 
	 * @param grid
	 *            The grid for which a new statistics record has to be inserted.
	 * @return The grid statistics created. Null in case of an error.
	 */
	public GridStatistics insert(Grid grid) {
		java.sql.Timestamp now = new java.sql.Timestamp(
				new java.util.Date().getTime());
		// Determine the number of solving attempts (excluding the attempt
		// currently loaded in the grid)which exist for this grid.
		// Note: replay == 0 means it is the first attempt to solve this grid.
		int countSolvingAttemptsForGrid = new SolvingAttemptDatabaseAdapter()
				.countSolvingAttemptForGrid(grid.getRowId())
				- (grid.getSolvingAttemptId() > 0 ? 1 : 0);

		ContentValues initialValues = new ContentValues();
		initialValues.put(KEY_GRID_ID, grid.getRowId());
		initialValues.put(KEY_REPLAY, countSolvingAttemptsForGrid);
		initialValues.put(KEY_CELLS_EMPTY,
				grid.getGridSize() * grid.getGridSize());
		initialValues.put(KEY_FIRST_MOVE, now.toString());
		initialValues.put(KEY_LAST_MOVE, now.toString());
		initialValues.put(KEY_INCLUDE_IN_STATISTICS, DatabaseAdapter
				.toSQLiteBoolean(countSolvingAttemptsForGrid == 0));

		long id = -1;
		try {
			id = mSqliteDatabase.insertOrThrow(TABLE, null, initialValues);
		} catch (SQLiteException e) {
			if (DevelopmentHelper.mMode == Mode.DEVELOPMENT) {
				e.printStackTrace();
			}
			return null;
		}

		if (id < 0) {
			return null;
		}

		// Retrieve the record created.
		return get((int) id);
	}

	/**
	 * Get the statistics for the given (row) id.
	 * 
	 * @param id
	 *            The unique row id of the statistics to be found.
	 * @return The grid statistics for the given id. Null in case of an error.
	 */
	public GridStatistics get(int id) {
		GridStatistics gridStatistics = null;
		Cursor cursor = null;
		try {
			cursor = mSqliteDatabase.query(true, TABLE, allColumns, KEY_ROWID
					+ "=" + id, null, null, null, null, null);
			gridStatistics = toGridStatistics(cursor);
		} catch (SQLiteException e) {
			if (DevelopmentHelper.mMode == Mode.DEVELOPMENT) {
				e.printStackTrace();
			}
			return null;
		} finally {
			if (cursor != null) {
				cursor.close();
			}
		}
		return gridStatistics;
	}

	/**
	 * Get most recent statistics for a given grid id.
	 * 
	 * @param gridId
	 *            The grid id for which the most recent statistics have to be
	 *            determined.
	 * @return The most recent grid statistics for the grid.
	 */
	public GridStatistics getMostRecent(int gridId) {
		GridStatistics gridStatistics = null;
		Cursor cursor = null;
		try {
			cursor = mSqliteDatabase.query(true, TABLE, allColumns, KEY_GRID_ID
					+ "=" + gridId, null, null, null, KEY_ROWID + " DESC", "1");
			gridStatistics = toGridStatistics(cursor);
		} catch (SQLiteException e) {
			if (DevelopmentHelper.mMode == Mode.DEVELOPMENT) {
				e.printStackTrace();
			}
			return null;
		} finally {
			if (cursor != null) {
				cursor.close();
			}
		}
		return gridStatistics;
	}

	/**
	 * Convert first record in the given cursor to a GridStatistics object.
	 * 
	 * @param cursor
	 *            The cursor to be converted.
	 * 
	 * @return A GridStatistics object for the first statistics record stored in
	 *         the given cursor. Null in case of an error.
	 */
	private GridStatistics toGridStatistics(Cursor cursor) {
		if (cursor == null || !cursor.moveToFirst()) {
			// No statistics records found for this grid.
			return null;
		}

		// Convert cursor record to a grid statics object.
		GridStatistics gridStatistics = new GridStatistics();
		gridStatistics.mId = cursor.getInt(cursor
				.getColumnIndexOrThrow(KEY_ROWID));
		gridStatistics.mGridId = cursor.getInt(cursor
				.getColumnIndexOrThrow(KEY_GRID_ID));
		gridStatistics.mReplayCount = cursor.getInt(cursor
				.getColumnIndexOrThrow(KEY_REPLAY));
		gridStatistics.mFirstMove = toSQLTimestamp(cursor.getString(cursor
				.getColumnIndexOrThrow(KEY_FIRST_MOVE)));
		gridStatistics.mLastMove = toSQLTimestamp(cursor.getString(cursor
				.getColumnIndexOrThrow(KEY_LAST_MOVE)));
		gridStatistics.mElapsedTime = cursor.getLong(cursor
				.getColumnIndexOrThrow(KEY_ELAPSED_TIME));
		gridStatistics.mCheatPenaltyTime = cursor.getLong(cursor
				.getColumnIndexOrThrow(KEY_CHEAT_PENALTY_TIME));
		gridStatistics.mCellsFilled = cursor.getInt(cursor
				.getColumnIndexOrThrow(KEY_CELLS_FILLED));
		gridStatistics.mCellsEmtpty = cursor.getInt(cursor
				.getColumnIndexOrThrow(KEY_CELLS_EMPTY));
		gridStatistics.mCellsRevealed = cursor.getInt(cursor
				.getColumnIndexOrThrow(KEY_CELLS_REVEALED));
		gridStatistics.mUserValueReplaced = cursor.getInt(cursor
				.getColumnIndexOrThrow(KEY_USER_VALUES_REPLACED));
		gridStatistics.mMaybeValue = cursor.getInt(cursor
				.getColumnIndexOrThrow(KEY_POSSIBLES));
		gridStatistics.mActionUndoMove = cursor.getInt(cursor
				.getColumnIndexOrThrow(KEY_ACTION_UNDOS));
		gridStatistics.mActionClearCell = cursor.getInt(cursor
				.getColumnIndexOrThrow(KEY_ACTION_CLEAR_CELL));
		gridStatistics.mActionClearGrid = cursor.getInt(cursor
				.getColumnIndexOrThrow(KEY_ACTION_CLEAR_GRID));
		gridStatistics.mActionRevealCell = cursor.getInt(cursor
				.getColumnIndexOrThrow(KEY_ACTION_REVEAL_CELL));
		gridStatistics.mActionRevealOperator = cursor.getInt(cursor
				.getColumnIndexOrThrow(KEY_ACTION_REVEAL_OPERATOR));
		gridStatistics.mActionCheckProgress = cursor.getInt(cursor
				.getColumnIndexOrThrow(KEY_ACTION_CHECK_PROGRESS));
		gridStatistics.mCheckProgressInvalidCellsFound = cursor.getInt(cursor
				.getColumnIndexOrThrow(KEY_CHECK_PROGRESS_INVALID_CELLS_FOUND));
		gridStatistics.mSolutionRevealed = Boolean.valueOf(cursor
				.getString(cursor
						.getColumnIndexOrThrow(KEY_ACTION_REVEAL_SOLUTION)));
		gridStatistics.mSolvedManually = Boolean.valueOf(cursor
				.getString(cursor.getColumnIndexOrThrow(KEY_SOLVED_MANUALLY)));
		gridStatistics.mFinished = Boolean.valueOf(cursor.getString(cursor
				.getColumnIndexOrThrow(KEY_FINISHED)));
		gridStatistics.mIncludedInStatistics = Boolean.valueOf(cursor
				.getString(cursor
						.getColumnIndexOrThrow(KEY_INCLUDE_IN_STATISTICS)));

		return gridStatistics;
	}

	/**
	 * Update the given statistics. It is required that the record already
	 * exists. The id should never be changed.
	 * 
	 * @param gridStatistics
	 *            The statistics to be updated.
	 * 
	 * @return True in case the statistics have been updated. False otherwise.
	 */
	public boolean update(GridStatistics gridStatistics) {
		ContentValues newValues = new ContentValues();
		newValues.put(KEY_ROWID, gridStatistics.mId);
		newValues.put(KEY_FIRST_MOVE, gridStatistics.mFirstMove.toString());
		newValues.put(KEY_LAST_MOVE, gridStatistics.mLastMove.toString());
		newValues.put(KEY_ELAPSED_TIME, gridStatistics.mElapsedTime);
		newValues.put(KEY_CHEAT_PENALTY_TIME, gridStatistics.mCheatPenaltyTime);
		newValues.put(KEY_CELLS_FILLED, gridStatistics.mCellsFilled);
		newValues.put(KEY_CELLS_EMPTY, gridStatistics.mCellsEmtpty);
		newValues.put(KEY_CELLS_REVEALED, gridStatistics.mCellsRevealed);
		newValues.put(KEY_USER_VALUES_REPLACED,
				gridStatistics.mUserValueReplaced);
		newValues.put(KEY_POSSIBLES, gridStatistics.mMaybeValue);
		newValues.put(KEY_ACTION_UNDOS, gridStatistics.mActionUndoMove);
		newValues.put(KEY_ACTION_CLEAR_CELL, gridStatistics.mActionClearCell);
		newValues.put(KEY_ACTION_CLEAR_GRID, gridStatistics.mActionClearGrid);
		newValues.put(KEY_ACTION_REVEAL_CELL, gridStatistics.mActionRevealCell);
		newValues.put(KEY_ACTION_REVEAL_OPERATOR,
				gridStatistics.mActionRevealOperator);
		newValues.put(KEY_ACTION_CHECK_PROGRESS,
				gridStatistics.mActionCheckProgress);
		newValues.put(KEY_CHECK_PROGRESS_INVALID_CELLS_FOUND,
				gridStatistics.mCheckProgressInvalidCellsFound);
		newValues.put(KEY_ACTION_REVEAL_SOLUTION,
				Boolean.toString(gridStatistics.mSolutionRevealed));
		newValues.put(KEY_SOLVED_MANUALLY,
				Boolean.toString(gridStatistics.mSolvedManually));
		newValues.put(KEY_FINISHED, Boolean.toString(gridStatistics.mFinished));
		newValues.put(KEY_INCLUDE_IN_STATISTICS,
				Boolean.toString(gridStatistics.mIncludedInStatistics));

		return (mSqliteDatabase.update(TABLE, newValues, KEY_ROWID + " = "
				+ gridStatistics.mId, null) == 1);
	}

	/**
	 * Get cumulative statistics for all grids with a given grid size.
	 * 
	 * @param minGridSize
	 *            The minimum size of the grid for which the cumulative
	 *            statistics have to be determined.
	 * @param maxGridSize
	 *            The maximum size of the grid for which the cumulative
	 *            statistics have to be determined. Use same value as minimum
	 *            grid size to retrieve statistics for 1 specific grid size.
	 * @return The cumulative statistics for the given grid size.
	 */
	public CumulativeStatistics getCumulativeStatistics(int minGridSize,
			int maxGridSize) {
		// Build projection if not yet done
		if (mCumulativeStatisticsProjection == null) {
			mCumulativeStatisticsProjection = new Projection();

			// Grid size minimum and maximum
			mCumulativeStatisticsProjection.put(Aggregation.MIN,
					GridDatabaseAdapter.TABLE,
					GridDatabaseAdapter.KEY_GRID_SIZE);
			mCumulativeStatisticsProjection.put(Aggregation.MAX,
					GridDatabaseAdapter.TABLE,
					GridDatabaseAdapter.KEY_GRID_SIZE);

			// First and last move
			mCumulativeStatisticsProjection.put(Aggregation.MIN, TABLE,
					KEY_FIRST_MOVE);
			mCumulativeStatisticsProjection.put(Aggregation.MAX, TABLE,
					KEY_LAST_MOVE);

			// Total, minimum, average, and maximum elapsed time
			mCumulativeStatisticsProjection.put(Aggregation.SUM, TABLE,
					KEY_ELAPSED_TIME);
			mCumulativeStatisticsProjection.put(Aggregation.MIN, TABLE,
					KEY_ELAPSED_TIME);
			mCumulativeStatisticsProjection.put(Aggregation.AVG, TABLE,
					KEY_ELAPSED_TIME);
			mCumulativeStatisticsProjection.put(Aggregation.MAX, TABLE,
					KEY_ELAPSED_TIME);

			// Total, minimum, average, and maximum penalty time
			mCumulativeStatisticsProjection.put(Aggregation.SUM, TABLE,
					KEY_CHEAT_PENALTY_TIME);
			mCumulativeStatisticsProjection.put(Aggregation.MIN, TABLE,
					KEY_CHEAT_PENALTY_TIME);
			mCumulativeStatisticsProjection.put(Aggregation.AVG, TABLE,
					KEY_CHEAT_PENALTY_TIME);
			mCumulativeStatisticsProjection.put(Aggregation.MAX, TABLE,
					KEY_CHEAT_PENALTY_TIME);

			// not (yet) used KEY_CELLS_USER_VALUE_FILLED,
			// not (yet) used KEY_CELLS_USER_VALUES_EMPTY
			// not (yet) used KEY_CELLS_USER_VALUES_REPLACED,

			// Totals of avoidable moves
			mCumulativeStatisticsProjection.put(Aggregation.SUM, TABLE,
					KEY_POSSIBLES);
			mCumulativeStatisticsProjection.put(Aggregation.SUM, TABLE,
					KEY_ACTION_UNDOS);
			mCumulativeStatisticsProjection.put(Aggregation.SUM, TABLE,
					KEY_ACTION_CLEAR_CELL);
			mCumulativeStatisticsProjection.put(Aggregation.SUM, TABLE,
					KEY_ACTION_CLEAR_GRID);

			// Totals per cheat
			mCumulativeStatisticsProjection.put(Aggregation.SUM, TABLE,
					KEY_ACTION_REVEAL_CELL);
			mCumulativeStatisticsProjection.put(Aggregation.SUM, TABLE,
					KEY_ACTION_REVEAL_OPERATOR);
			mCumulativeStatisticsProjection.put(Aggregation.SUM, TABLE,
					KEY_ACTION_CHECK_PROGRESS);
			mCumulativeStatisticsProjection.put(Aggregation.SUM, TABLE,
					KEY_CHECK_PROGRESS_INVALID_CELLS_FOUND);

			// Totals per status of game'
			mCumulativeStatisticsProjection.put(Aggregation.COUNTIF_TRUE,
					TABLE, KEY_ACTION_REVEAL_SOLUTION);
			mCumulativeStatisticsProjection.put(Aggregation.COUNTIF_TRUE,
					TABLE, KEY_SOLVED_MANUALLY);
			mCumulativeStatisticsProjection.put(Aggregation.COUNTIF_TRUE,
					TABLE, KEY_FINISHED);

			// Total games
			mCumulativeStatisticsProjection.put(Aggregation.COUNT, TABLE,
					KEY_ROWID);
		}

		SQLiteQueryBuilder sqliteQueryBuilder = new SQLiteQueryBuilder();
		sqliteQueryBuilder.setProjectionMap(mCumulativeStatisticsProjection);
		sqliteQueryBuilder.setTables(GridDatabaseAdapter.TABLE
				+ " INNER JOIN "
				+ TABLE
				+ " ON "
				+ GridDatabaseAdapter
						.getPrefixedColumnName(GridDatabaseAdapter.KEY_ROWID)
				+ " = " + getPrefixedColumnName(KEY_GRID_ID));
		String selection = GridDatabaseAdapter
				.getPrefixedColumnName(GridDatabaseAdapter.KEY_GRID_SIZE)
				+ " BETWEEN "
				+ minGridSize
				+ " AND "
				+ maxGridSize
				+ " AND "
				+ KEY_INCLUDE_IN_STATISTICS + " = 'true'";

		if (DEBUG_SQL) {
			String sql = sqliteQueryBuilder.buildQuery(
					mCumulativeStatisticsProjection.getAllColumnNames(),
					selection, null, null, null, null);
			Log.i(TAG, sql);
		}

		Cursor cursor = null;
		try {
			cursor = sqliteQueryBuilder.query(mSqliteDatabase,
					mCumulativeStatisticsProjection.getAllColumnNames(),
					selection, null, null, null, null);
		} catch (SQLiteException e) {
			if (cursor != null) {
				cursor.close();
			}
			if (DevelopmentHelper.mMode == Mode.DEVELOPMENT) {
				e.printStackTrace();
			}
			return null;
		}

		if (cursor == null || !cursor.moveToFirst()) {
			// Record can not be processed.
			return null;
		}

		// Convert cursor record to a grid statics object.
		CumulativeStatistics cumulativeStatistics = new CumulativeStatistics();

		// Grid size minimum and maximum
		cumulativeStatistics.mMinGridSize = cursor.getInt(cursor
				.getColumnIndexOrThrow(mCumulativeStatisticsProjection
						.getAggregatedKey(Aggregation.MIN,
								GridDatabaseAdapter.KEY_GRID_SIZE)));
		cumulativeStatistics.mMaxGridSize = cursor.getInt(cursor
				.getColumnIndexOrThrow(mCumulativeStatisticsProjection
						.getAggregatedKey(Aggregation.MAX,
								GridDatabaseAdapter.KEY_GRID_SIZE)));

		// First and last move
		cumulativeStatistics.mMinFirstMove = toSQLTimestamp(cursor
				.getString(cursor
						.getColumnIndexOrThrow(mCumulativeStatisticsProjection
								.getAggregatedKey(Aggregation.MIN,
										KEY_FIRST_MOVE))));
		cumulativeStatistics.mMaxLastMove = toSQLTimestamp(cursor
				.getString(cursor
						.getColumnIndexOrThrow(mCumulativeStatisticsProjection
								.getAggregatedKey(Aggregation.MAX,
										KEY_LAST_MOVE))));

		// Total, minimum, average, and maximum elapsed time
		cumulativeStatistics.mSumElapsedTime = cursor.getInt(cursor
				.getColumnIndexOrThrow(mCumulativeStatisticsProjection
						.getAggregatedKey(Aggregation.SUM, KEY_ELAPSED_TIME)));
		cumulativeStatistics.mAvgElapsedTime = cursor.getInt(cursor
				.getColumnIndexOrThrow(mCumulativeStatisticsProjection
						.getAggregatedKey(Aggregation.AVG, KEY_ELAPSED_TIME)));
		cumulativeStatistics.mMinElapsedTime = cursor.getInt(cursor
				.getColumnIndexOrThrow(mCumulativeStatisticsProjection
						.getAggregatedKey(Aggregation.MIN, KEY_ELAPSED_TIME)));
		cumulativeStatistics.mMaxElapsedTime = cursor.getInt(cursor
				.getColumnIndexOrThrow(mCumulativeStatisticsProjection
						.getAggregatedKey(Aggregation.MAX, KEY_ELAPSED_TIME)));

		// Total, minimum, average, and maximum penalty time
		cumulativeStatistics.mSumCheatPenaltyTime = cursor.getInt(cursor
				.getColumnIndexOrThrow(mCumulativeStatisticsProjection
						.getAggregatedKey(Aggregation.SUM,
								KEY_CHEAT_PENALTY_TIME)));
		cumulativeStatistics.mAvgCheatPenaltyTime = cursor.getInt(cursor
				.getColumnIndexOrThrow(mCumulativeStatisticsProjection
						.getAggregatedKey(Aggregation.AVG,
								KEY_CHEAT_PENALTY_TIME)));
		cumulativeStatistics.mMinCheatPenaltyTime = cursor.getInt(cursor
				.getColumnIndexOrThrow(mCumulativeStatisticsProjection
						.getAggregatedKey(Aggregation.MIN,
								KEY_CHEAT_PENALTY_TIME)));
		cumulativeStatistics.mMaxCheatPenaltyTime = cursor.getInt(cursor
				.getColumnIndexOrThrow(mCumulativeStatisticsProjection
						.getAggregatedKey(Aggregation.MAX,
								KEY_CHEAT_PENALTY_TIME)));

		// not (yet) used KEY_CELLS_USER_VALUE_FILLED,
		// not (yet) used KEY_CELLS_USER_VALUES_EMPTY
		// not (yet) used KEY_CELLS_USER_VALUES_REPLACED,

		// Totals of avoidable moves
		cumulativeStatistics.mSumMaybeValue = cursor.getInt(cursor
				.getColumnIndexOrThrow(mCumulativeStatisticsProjection
						.getAggregatedKey(Aggregation.SUM, KEY_POSSIBLES)));
		cumulativeStatistics.mSumActionUndoMove = cursor.getInt(cursor
				.getColumnIndexOrThrow(mCumulativeStatisticsProjection
						.getAggregatedKey(Aggregation.SUM, KEY_ACTION_UNDOS)));
		cumulativeStatistics.mSumActionClearCell = cursor.getInt(cursor
				.getColumnIndexOrThrow(mCumulativeStatisticsProjection
						.getAggregatedKey(Aggregation.SUM,
								KEY_ACTION_CLEAR_CELL)));
		cumulativeStatistics.mSumActionClearGrid = cursor.getInt(cursor
				.getColumnIndexOrThrow(mCumulativeStatisticsProjection
						.getAggregatedKey(Aggregation.SUM,
								KEY_ACTION_CLEAR_GRID)));

		// Totals per cheat
		cumulativeStatistics.mSumActionRevealCell = cursor.getInt(cursor
				.getColumnIndexOrThrow(mCumulativeStatisticsProjection
						.getAggregatedKey(Aggregation.SUM,
								KEY_ACTION_REVEAL_CELL)));
		cumulativeStatistics.mSumActionRevealOperator = cursor.getInt(cursor
				.getColumnIndexOrThrow(mCumulativeStatisticsProjection
						.getAggregatedKey(Aggregation.SUM,
								KEY_ACTION_REVEAL_OPERATOR)));
		cumulativeStatistics.mSumActionCheckProgress = cursor.getInt(cursor
				.getColumnIndexOrThrow(mCumulativeStatisticsProjection
						.getAggregatedKey(Aggregation.SUM,
								KEY_ACTION_CHECK_PROGRESS)));
		cumulativeStatistics.mSumcheckProgressInvalidCellsFound = cursor
				.getInt(cursor.getColumnIndexOrThrow(mCumulativeStatisticsProjection
						.getAggregatedKey(Aggregation.SUM,
								KEY_CHECK_PROGRESS_INVALID_CELLS_FOUND)));

		// Totals per status of game
		cumulativeStatistics.mCountSolutionRevealed = cursor.getInt(cursor
				.getColumnIndexOrThrow(mCumulativeStatisticsProjection
						.getAggregatedKey(Aggregation.COUNTIF_TRUE,
								KEY_ACTION_REVEAL_SOLUTION)));
		cumulativeStatistics.mCountSolvedManually = cursor.getInt(cursor
				.getColumnIndexOrThrow(mCumulativeStatisticsProjection
						.getAggregatedKey(Aggregation.COUNTIF_TRUE,
								KEY_SOLVED_MANUALLY)));
		cumulativeStatistics.mCountFinished = cursor.getInt(cursor
				.getColumnIndexOrThrow(mCumulativeStatisticsProjection
						.getAggregatedKey(Aggregation.COUNTIF_TRUE,
								KEY_FINISHED)));
		cumulativeStatistics.mCountStarted = cursor.getInt(cursor
				.getColumnIndexOrThrow(mCumulativeStatisticsProjection
						.getAggregatedKey(Aggregation.COUNT, KEY_ROWID)));

		cursor.close();
		return cumulativeStatistics;
	}

	/**
	 * Get the historic statistics for the given column for all grids with a
	 * given grid size.
	 * 
	 * @param minGridSize
	 *            The minimum size of the grid for which the cumulative
	 *            statistics have to be determined.
	 * @param maxGridSize
	 *            The maximum size of the grid for which the cumulative
	 *            statistics have to be determined. Use same value as minimum
	 *            grid size to retireve statistics for 1 specific grid size.
	 * @return The cumulative statistics for the given grid size.
	 */
	public HistoricStatistics getHistoricData(int minGridSize, int maxGridSize) {

		// Build projection if not yet done. As this projection is only build
		// once, it has to contain all base columns and all columns for which
		// the historic data can be retrieved.
		if (mHistoricStatisticsProjection == null) {
			mHistoricStatisticsProjection = new Projection();

			// Add base columns to the projection
			mHistoricStatisticsProjection.put(HistoricStatistics.DATA_COL_ID,
					TABLE, KEY_ROWID);
			mHistoricStatisticsProjection.put(
					stringBetweenBackTicks(HistoricStatistics.DATA_COL_SERIES), // Explicit
																				// back
																				// ticks
																				// needed
																				// here!
					"CASE WHEN "
							+ stringBetweenBackTicks(KEY_FINISHED)
							+ " <> "
							+ stringBetweenQuotes("true")
							+ " THEN "
							+ stringBetweenQuotes(Serie.UNFINISHED.toString())
							+ " WHEN "
							+ KEY_ACTION_REVEAL_SOLUTION
							+ " = "
							+ stringBetweenQuotes("true")
							+ " THEN "
							+ stringBetweenQuotes(Serie.SOLUTION_REVEALED
									.toString()) + " ELSE "
							+ stringBetweenQuotes(Serie.SOLVED.toString())
							+ " END");

			// Add data columns to the projection.
			mHistoricStatisticsProjection.put(KEY_ELAPSED_TIME, TABLE,
					KEY_ELAPSED_TIME);
			mHistoricStatisticsProjection.put(KEY_CHEAT_PENALTY_TIME, TABLE,
					KEY_CHEAT_PENALTY_TIME);
			mHistoricStatisticsProjection.put(KEY_CELLS_FILLED, TABLE,
					KEY_CELLS_FILLED);
			mHistoricStatisticsProjection.put(KEY_CELLS_EMPTY, TABLE,
					KEY_CELLS_EMPTY);
			mHistoricStatisticsProjection.put(KEY_CELLS_REVEALED, TABLE,
					KEY_CELLS_REVEALED);
			mHistoricStatisticsProjection.put(KEY_USER_VALUES_REPLACED, TABLE,
					KEY_USER_VALUES_REPLACED);
			mHistoricStatisticsProjection.put(KEY_POSSIBLES, TABLE,
					KEY_POSSIBLES);
			mHistoricStatisticsProjection.put(KEY_ACTION_UNDOS, TABLE,
					KEY_ACTION_UNDOS);
			mHistoricStatisticsProjection.put(KEY_ACTION_CLEAR_CELL, TABLE,
					KEY_ACTION_CLEAR_CELL);
			mHistoricStatisticsProjection.put(KEY_ACTION_CLEAR_GRID, TABLE,
					KEY_ACTION_CLEAR_GRID);
			mHistoricStatisticsProjection.put(KEY_ACTION_REVEAL_CELL, TABLE,
					KEY_ACTION_REVEAL_CELL);
			mHistoricStatisticsProjection.put(KEY_ACTION_REVEAL_OPERATOR,
					TABLE, KEY_ACTION_REVEAL_OPERATOR);
			mHistoricStatisticsProjection.put(KEY_ACTION_CHECK_PROGRESS, TABLE,
					KEY_ACTION_CHECK_PROGRESS);
			mHistoricStatisticsProjection.put(
					KEY_CHECK_PROGRESS_INVALID_CELLS_FOUND, TABLE,
					KEY_CHECK_PROGRESS_INVALID_CELLS_FOUND);
		}

		// Build query
		SQLiteQueryBuilder sqliteQueryBuilder = new SQLiteQueryBuilder();
		sqliteQueryBuilder.setProjectionMap(mHistoricStatisticsProjection);
		sqliteQueryBuilder.setTables(GridDatabaseAdapter.TABLE
				+ " INNER JOIN "
				+ TABLE
				+ " ON "
				+ GridDatabaseAdapter
						.getPrefixedColumnName(GridDatabaseAdapter.KEY_ROWID)
				+ " = " + getPrefixedColumnName(KEY_GRID_ID));

		// Retrieve all data. Note: in case column is not added to the
		// projection, no data will be retrieved!
		String[] columnsData = {
				// Statistics id
				stringBetweenBackTicks(HistoricStatistics.DATA_COL_ID),

				// Elapsed time excluding the cheat penalty
				stringBetweenBackTicks(KEY_ELAPSED_TIME)
						+ " - "
						+ stringBetweenBackTicks(KEY_CHEAT_PENALTY_TIME)
						+ " AS "
						+ HistoricStatistics.DATA_COL_ELAPSED_TIME_EXCLUDING_CHEAT_PENALTY,

				// Cheat penalty
				stringBetweenBackTicks(KEY_CHEAT_PENALTY_TIME) + " AS "
						+ HistoricStatistics.DATA_COL_CHEAT_PENALTY,

				// Series
				stringBetweenBackTicks(HistoricStatistics.DATA_COL_SERIES) };

		String selection = GridDatabaseAdapter
				.getPrefixedColumnName(GridDatabaseAdapter.KEY_GRID_SIZE)
				+ " BETWEEN "
				+ minGridSize
				+ " AND "
				+ maxGridSize
				+ " AND "
				+ KEY_INCLUDE_IN_STATISTICS + " = 'true'";

		if (DEBUG_SQL) {
			String sql = sqliteQueryBuilder.buildQuery(columnsData, selection,
					null, null, KEY_GRID_ID, null);
			Log.i(TAG, sql);
		}

		Cursor cursor = null;
		try {
			cursor = sqliteQueryBuilder.query(mSqliteDatabase, columnsData,
					selection, null, null, null, KEY_GRID_ID);
		} catch (SQLiteException e) {
			if (cursor != null) {
				cursor.close();
			}
			if (DevelopmentHelper.mMode == Mode.DEVELOPMENT) {
				e.printStackTrace();
			}
			return null;
		}

		HistoricStatistics historicStatistics = new HistoricStatistics(cursor);
		if (cursor != null) {
			cursor.close();
		}

		return historicStatistics;
	}

	/**
	 * Prefix the given column name with the table name.
	 * 
	 * @param column
	 *            The column name which has to be prefixed.
	 * 
	 * @return The prefixed column name.
	 */
	public static String getPrefixedColumnName(String column) {
		return TABLE + "." + column;
	}

	/**
	 * Set the new solving attempt which has to be included for a specific grid
	 * in case the cumulative or historic statistics are retrieved.
	 * 
	 * @param gridId
	 *            The grid id for which the solving attempts have to changed.
	 * @param solvingAttemptId
	 *            The solving attempt which has to be included for the grid when
	 *            retrieving the cumulative or historic statistics are
	 *            retrieved.
	 */
	public void updateSolvingAttemptToBeIncludedInStatistics(int gridId,
			int solvingAttemptId) {
		String sql = "UPDATE " + TABLE + " SET " + KEY_INCLUDE_IN_STATISTICS
				+ " = " + " CASE WHEN " + KEY_ROWID + " = " + solvingAttemptId
				+ " THEN " + stringBetweenQuotes(toSQLiteBoolean(true))
				+ " ELSE " + stringBetweenQuotes(toSQLiteBoolean(false))
				+ " END " + " WHERE " + KEY_GRID_ID + " = " + gridId + " AND ("
				+ KEY_ROWID + " = " + solvingAttemptId + " OR "
				+ KEY_INCLUDE_IN_STATISTICS + " = "
				+ stringBetweenQuotes(toSQLiteBoolean(true)) + ")";
		if (DEBUG_SQL) {
			Log.i(TAG, sql);
		}
		try {
			mSqliteDatabase.execSQL(sql);
		} catch (SQLiteException e) {
			if (DevelopmentHelper.mMode == Mode.DEVELOPMENT) {
				e.printStackTrace();
			}
		}
	}
}
