package org.jdbdt;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;


/**
 * JDBDT API facade.
 * 
 * <p>The facade defines the standard API
 * for use by test code for tasks like database 
 * verification, setup, and tear-down.
 * </p>
 *  
 * @since 0.1
 */
public final class JDBDT {

  /**
   * Private constructor to avoid unintended instantiation.
   */
  private JDBDT() { }
  
  /**
   * Version identifier.
   */
  private static final String VERSION = "0.1";
  
  /**
   * Get JDBDT version.
   * @return The version identifier for the JDBDT library in use.
   */
  public static String version() { 
    return VERSION;
  }

  /**
   * Create handle for given table name.
   * @param tableName Name of the database table.
   * @return A new {@link Table} instance.
   */
  public static Table table(String tableName) {
    return new Table(tableName); 
  }
  
  /**
   * Create typed handle for given table name
   * and conversion function.
   * @param <T> Type of data.
   * @param tableName Name of the database table.
   * @param conv Conversion function.
   * @return A new {@link TypedTable} instance.
   */
  public static <T> TypedTable<T> table(String tableName, Conversion<T> conv) {
    return new TypedTable<>(tableName, conv); 
  }
  
  /**
   * Create data set for a table.
   * @param t Table.
   * @return A new {@link DataSet} object.
   */
  public static DataSet data(Table t) {
    return new DataSet(t);
  }
  
  /**
   * Create query for a table.
   * @param t Table.
   * @return A new {@link TableQuery} object.
   */
  public static TableQuery selectFrom(Table t) {
    return new TableQuery(t); 
  }
  
  /**
   * Create query for a typed table.
   * @param <T> Type of objects.
   * @param t Table.
   * @return A new {@link TypedTableQuery} object.
   */
  public static <T> TypedTableQuery<T> selectFrom(TypedTable<T> t) {
    return new TypedTableQuery<>(t); 
  }
  
  /**
   * Create query from a previously prepared statement.
   * @param stmt Prepared statement.
   * @return A new {@link Query} object.
   */
  public static Query selectUsing(PreparedStatement stmt) {
    return new Query(stmt); 
  }
  
  /**
   * Create typed query from a previously prepared statement.
   * @param <T> Type of objects.
   * @param stmt Prepared statement.
   * @param conv Object conversion function.
   * @return A new {@link TypedQuery} object.
   */
  public static <T> TypedQuery<T> selectUsing(PreparedStatement stmt, Conversion<T> conv) {
    return new TypedCustomQuery<T>(stmt, conv);
  }

  /**
   * Create an observer for the given table.
   * <p>
   * The observer will monitor all entries in the database table.
   * A call to this method is functionally equivalent to
   * <code>observe(selectFrom(t))</code>.
   * </p>
   * @param t Table.
   * @return A new {@link Observer} instance.
   * @throws SQLException If an SQL error occurs.
   * @see #observe(Query,Object...)
   * @see #observe(TypedTable)
   * @see #observe(Table, DataSet)
   */
  public static Observer
  observe(Table t) throws SQLException {
    return logSetup(new Observer(t, null));
  }
  
  /**
   * Create an observer for the given table, assuming
   * an initial data set as observation state.
   * 
   * <p>
   * This method should be used for optimization purposes, 
   * whenever the database table has been  
   * previously setup using the given data set
   * by executing <code>insertInto.rows(ds)</code>. 
   * In this manner, an initial database query 
   * will not be issued during observer creation.
   * </p>
   * 
   * @param t Table.
   * @param ds Data set.
   * @return A new {@link Observer} instance.
   * @throws SQLException If an SQL error occurs.
   * @see #observe(Table)
   * @see #insertInto(Table)
   * @see Loader#rows(DataSet)
   */
  public static Observer
  observe(Table t, DataSet ds) throws SQLException {
    if (ds == null) {
      throw new InvalidUsageException("Null data set.");
    }
    return logSetup(new Observer(t, ds.getRowSet()));
  }
  
  /**
   * Create an observer for the given query.
   * 
   * <p>
   * The observer will monitor database entries 
   * returned by the database query.
   * </p>
   * 
   * @param q Query.
   * @param args Optional query arguments.
   * @return A new {@link Observer} instance.
   * @throws SQLException If an SQL error occurs.
   * @see #observe(Table)
   */
  @SafeVarargs
  public static Observer
  observe(Query q, Object... args) throws SQLException {
    return logSetup(new Observer(q, args, null));
  }
  
  /**
   * Create an observer for the given query, assuming
   * an initial data set as observation state.
   * 
   * <p>
   * This method should be used for optimization purposes, 
   * whenever the database table has been  
   * previously setup using the given data set
   * by executing <code>insertInto.rows(ds)</code>. 
   * In this manner, the database query 
   * will not be executed initially during observer creation.
   * </p>
   * 
   * @param q Query.
   * @param ds Data set.
   * @param args Optional query arguments.
   * @return A new {@link Observer} instance.
   * @throws SQLException If an SQL error occurs.
   * @see #observe(Table)
   * @see #insertInto(Table)
   * @see Loader#rows(DataSet)
   */
  @SafeVarargs
  public static Observer
  observe(Query q, DataSet ds, Object... args) throws SQLException {
    if (ds == null) {
      throw new InvalidUsageException("Null data set.");
    }
    return logSetup(new Observer(q, args,  ds.getRowSet()));
  }
  
  /**
   * Create an observer for a typed table.
   * 
   * <p>
   * The observer will monitor all entries in the database table.
   * A call to this method is functionally equivalent to
   * <code>observe(selectFrom(t))</code>.
   * </p>
   * @param <T> Type of objects.
   * @param t Table.
   * @return A new {@link TypedObserver} instance.
   * @throws SQLException If an SQL error occurs.
   * 
   * @see #observe(Table)
   * @see #selectFrom(TypedTable)
   * @see #observe(TypedTableQuery,Object...)
   */
  public static <T> TypedObserver<T>
  observe(TypedTable<T> t) throws SQLException {
    return logSetup(new TypedObserver<T>(t, null));
  }
  
  /**
   * Create an observer for the given typed table, assuming
   * an initial data set as observation state.
   * 
   * <p>
   * This method is the typed variant of {@link #observe(Table,DataSet)}.
   * </p>
   * 
   * @param <T> Type of objects.
   * @param t Table.
   * @param ds Data set.
   * @return A new {@link Observer} instance.
   * @throws SQLException If an SQL error occurs.
   * @see #observe(Table,DataSet)
   * @see #insertInto(TypedTable)
   */
  public static <T> TypedObserver<T>
  observe(TypedTable<T> t, DataSet ds) throws SQLException {
    if (ds == null) {
      throw new InvalidUsageException("Null data set.");
    }
    return logSetup(new TypedObserver<T>(t, ds.getRowSet()));
  }
  
  
  /**
   * Create an observer for the given typed table query.
   * The observer will monitor database entries returned by the database query.
   * @param <T> Object type associated to the query.
   * @param tq Typed table query.
   * @param args Optional query arguments.
   * @return A new {@link TypedObserver} instance.
   * @throws SQLException If an SQL error occurs.
   */
  public static <T> TypedObserver<T> 
  observe(TypedTableQuery<T> tq, Object... args) throws SQLException {
    return logSetup(new TypedObserver<>(tq, args, null));
  }
  
  /**
   * Create an observer for the given typed table query,
   * assuming an initial data set as observation state.
   * 
   * <p>
   * This method is the typed variant of {@link #observe(Query,DataSet,Object...)}.
   * </p>
   * @param <T> Object type associated to the query.
   * @param tq Typed table query.
   * @param ds Data set.
   * @param args Optional query arguments.
   * @return A new {@link TypedObserver} instance.
   * @throws SQLException If an SQL error occurs.
   */
  public static <T> TypedObserver<T> 
  observe(TypedTableQuery<T> tq, DataSet ds, Object... args) throws SQLException {
    if (ds == null) {
      throw new InvalidUsageException("Null data set.");
    }
    return logSetup(new TypedObserver<>(tq, args, ds.getRowSet()));
  }
  
  /**
   * Refresh observer state and obtain delta for verification.
   * 
   * <p>
   * A new query will be issued by the observer,
   * and the resulting delta will reflect the database changes
   * that took place.
   * </p>
   * 
   * @param obs Observer.
   * @return A new {@link Delta} object.
   */
  public static Delta delta(Observer obs) {
    return obs.getDelta();
  }
  
  /**
   * Refresh typed observer state and obtain delta for verification.
   * 
   * <p>
   * A new query will be issued by the observer,
   * and the resulting delta will reflect the database changes
   * that took place.
   * </p>
   * 
   * @param <T> Type of objects associated to the observer.
   * @param observer Observer.
   * @return A new {@link TypedDelta} object. 
   */
  public static <T> TypedDelta<T> delta(TypedObserver<T> observer) {
    return observer.getDelta();
  }

  /**
   * Assert that no changes were made to the database.
   * 
   * <p>
   * A call to this method is shorthand for
   * <code>delta(obs).end()</code>.
   * </p>
   * 
   * @param obs Observer.
   * @throws DeltaAssertionError if the observer yields a non-empty delta.
   * @see #assertChanged(Observer,DataSet,DataSet)
   * @see #assertDeleted(Observer,DataSet)
   * @see #assertInserted(Observer,DataSet)
   * @see Delta#end()
   */
  public static void assertNoChanges(Observer obs) throws DeltaAssertionError {
    delta(obs).end(); 
  }
  
  /**
   * Assert that database changed only by removal of a given
   * data set.
   * 
   * <p>
   * A call to this method is shorthand for
   * <code>delta(obs).before(ds).end()</code>.
   * </p>
   * 
   * @param obs Observer.
   * @param ds data set.
   * @throws DeltaAssertionError if the assertion fails.
   * @see #assertNoChanges(Observer)
   * @see #assertDeleted(Observer,DataSet)
   * @see #assertInserted(Observer,DataSet)
   * @see Delta#end()
   */
  public static void assertDeleted(Observer obs, DataSet ds) throws DeltaAssertionError {
    delta(obs).before(ds).end(); 
  }
  
  /**
   * Assert that database changed only by addition of a given
   * data set.
   * 
   * <p>
   * A call to this method is shorthand for
   * <code>delta(obs).after(ds).end()</code>.
   * </p>
   * 
   * @param obs Observer.
   * @param ds data set.
   * @throws DeltaAssertionError if the assertion fails.
   * @see #assertNoChanges(Observer)
   * @see #assertDeleted(Observer,DataSet)
   * @see #assertChanged(Observer,DataSet,DataSet)
   * @see Delta#end()
   */
  public static void assertInserted(Observer obs, DataSet ds) throws DeltaAssertionError {
    delta(obs).after(ds).end(); 
  }
  
  /**
   * Assert that database changed according
   * to given 'before' and 'after' data sets. 
   * 
   * <p>
   * A call to this method is shorthand for
   * <code>delta(obs).after(a).before(b).end()</code>.
   * </p>
   * 
   * @param obs Observer.
   * @param a 'after' set.
   * @param b 'before' set.
   * @throws DeltaAssertionError if the assertion fails.
   *
   * @see #assertNoChanges(Observer)
   * @see #assertDeleted(Observer,DataSet)
   * @see #assertInserted(Observer,DataSet)
   * @see Delta#end()
   */
  public static void assertChanged(Observer obs, DataSet a, DataSet b) throws DeltaAssertionError {
    delta(obs).after(a).before(b).end(); 
  }
  
  
  /**
   * Assert no changes were made to the database. 
   * 
   * <p>
   * A call to this method is shorthand for
   * <code>delta(obs).end()</code>.
   * </p>
   * 
   * @param obs Typed observer.
   * @throws DeltaAssertionError if the observer yields a non-empty delta.
   * @see TypedDelta#end()
   */
  public static void assertNoChanges(TypedObserver<?> obs) {
    delta(obs).end(); 
  }

  /**
   * Create a database loader to insert data onto a table.
   * 
   * @param table Database table.
   * @return A new {@link Loader} instance. 
   */
  public static Loader insertInto(Table table) {
    return new Loader(table);
  }
  
  /**
   * Create a database loader to insert data onto a typed table.
   * 
   * @param <T> Type of objects.
   * @param table Database table.
   * @return A new {@link TypedLoader} instance. 
   */
  public static <T> TypedLoader<T> insertInto(TypedTable<T> table) {
    return new TypedLoader<>(table);
  }

  /**
   * Delete all data from a table. 
   *
   * @param t Table.
   * @return Number of deleted entries.
   * @throws SQLException if a database error occurs.
   * @see #truncate(Table)
   * @see #deleteAll(TableQuery,Object...)
   */
  public static int deleteAll(Table t) throws SQLException  {
    return StatementPool.delete(t).executeUpdate();
  }
  
  
  /**
   * Delete all data returned by a table query. 
   * 
   * <p>
   * The query's where clause will 
   * be used for the DELETE statement that is issued.
   * 
   * This method cannot be used with 
   * queries that have GROUP BY or HAVING clauses set.
   * </p>
   *
   * @param q query.
   * @param args Optional query arguments.
   * @return Number of deleted entries.
   * @throws SQLException if a database error occurs.
   * @throws InvalidUsageException if the query has 
   *    a GROUP BY or HAVING clause set, or if no WHERE
   *    clause is set for it.
   * @see #deleteAll(Table)
   * @see #truncate(Table)
   */
  public static int deleteAll(TableQuery q, Object... args) throws SQLException  {
    if (q.groupByClause() != null) {
      throw new InvalidUsageException("GROUP BY clause is set!");
    }
    if (q.havingClause() != null) {
      throw new InvalidUsageException("HAVING clause is set!");
    }
    String whereClause = q.whereClause();
    
    if (whereClause == null) {
      throw new InvalidUsageException("WHERE clause is not set!");
    }
    PreparedStatement deleteStmt = 
      StatementPool.compile(q.getStatement().getConnection(), 
          "DELETE FROM " + q.getTable().getName() 
          + " WHERE " + whereClause);
    if (args != null && args.length > 0) {
      for (int i=0; i < args.length; i++) {
        deleteStmt.setObject(i + 1, args[i]);
      }
    }
    return deleteStmt.executeUpdate();
  }
  
  /**
   * Truncate table.
   * @param t Table.
   * @throws SQLException if a database error occurs.
   * @see #deleteAll(Table)
   * @see #deleteAll(TableQuery,Object...)
   */
  public static void truncate(Table t) throws SQLException  {
     StatementPool.truncate(t).execute();
  }
  
  /**
   * Execute an arbitrary SQL statement.
   * 
   * <p>
   * Calling this method differs from
   * <code>c.prepareStatement(sql).execute()</code>
   * as follows: an internal, JDBC-driver independent, pool
   * of statements ensures that the SQL code is compiled
   * only once for the given connection, i.e., the 
   * associated {@link PreparedStatement} is generated
   * only once. For SQL code that executes repeatedly,
   * this may be more efficient. This also ensures
   * that SQL code executed in this manner
   * minimizes the use of the actual JDBC driver statement
   * pool (if any), and possible impact on the
   * the SUT components' performance.
   * </p>
   * 
   * @param c Database connection.
   * @param sqlCode SQL code.
   * @throws SQLException If a database error occurs.
   */
  public static void sql(Connection c, String sqlCode) throws SQLException {
    StatementPool.compile(c, sqlCode).execute();
  }
  
  /**
   * Create a log that is written to a file.
   * 
   * <p>
   * A log can be used for reporting or debugging purposes,
   * and has an XML format.
   * Various types of JDBDT objects may be written to a log
   * e.g. {@link DataSet} or {@link Delta}.  
   * Note that output data is only written when {@link Log#close()}
   * is called on the returned log instance.
   * </p>
   * @param file Output file.
   * @return A log instance.
   * @throws FileNotFoundException If the output file cannot be created.
   * 
   * @see #log(PrintStream)
   * @see #logErrorsTo(Log)
   * @see #logErrorsTo(PrintStream)
   */
  public static Log log(File file) throws FileNotFoundException {
    return new Log(file);
  }
  
  /**
   * Create a log that is written to a stream.
   * 
   * <p>
   * This method has a similar functionality to {@link #log(File)},
   * but redirects log output to the given stream. 
   * </p>
   * 
   * @param out Output stream.
   * @return A log instance.
   * 
   * @see #log(File)
   * @see #logErrorsTo(Log)
   * @see #logErrorsTo(PrintStream)
   */
  public static Log log(PrintStream out) {
    return new Log(out);
  }
  
  /**
   * Log delta assertion errors to given log instance.
   * 
   * <p>
   * For a non-null log instance argument, whenever an assertion error is 
   * thrown by the JDBDT API ({@link DeltaAssertionError}),
   * the unverified portion of the delta object at stake ({@link Delta})
   * will be logged. A null argument disables logging of this kind,
   * causing the previous log to be closed.
   * </p>
   * 
   * <p>
   * To configure logging per observer, use {@link Observer#logErrorsTo(Log)}
   * instead.
   * </p>
   * @param out Output log.
   *
   * @see #logErrorsTo(PrintStream)
   * @see #log(File)
   * @see #log(PrintStream)
   * @see Log#close()
   * @see Observer#logErrorsTo(Log)
   */
  public static void logErrorsTo(Log out) {
    if (errorLog != null) {
      errorLog.close();
    }
    errorLog = out;
  }
  
  /**
   * Log assertion errors to given output stream.
   * 
   * <p>
   * A call to this method is shorthand for 
   * <code>logErrorsTo(log(out)))</code>
   * @param out Output stream.
   * 
   * @see #logErrorsTo(Log)
   * @see #log(PrintStream)
   * @see #log(File)
   * @see Observer#logErrorsTo(Log)
   */
  public static void logErrorsTo(PrintStream out) {
    logErrorsTo(log(out));
  }
  
  /**
   * Error log in use (initially null).
   */
  private static Log errorLog = null;

  @SuppressWarnings("javadoc")
  private static <T extends Observer> T logSetup(T obs) {
    if (errorLog != null) {
      obs.logErrorsTo(errorLog);
    }
    return obs;
  }
}
