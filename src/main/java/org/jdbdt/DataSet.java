package org.jdbdt;

import java.math.BigInteger;
import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.function.Function;

/**
 * Data set.
 * 
 * <p>
 * Objects of this kind provide a convenient manner to 
 * define data sets programmatically in association to a database table,
 * with the purpose of database setup and/or assertions 
 * (e.g., see
 * see {@link Loader#rows(DataSet)}, {@link Delta#before(DataSet)} 
 * and {@link Delta#after(DataSet)}). 
 * </p>
 * 
 * <p>
 * Data sets can be used as follows: 
 * <ul>
 * <li>
 * Instances are created with a call to {@link JDBDT#data(Table)}. 
 * </li>
 * <li>
 * Entries (rows) in the data set are specified by setting one 
 * column filler ({@link ColumnFiller} instance) per each column in the table, 
 * using the {@link #set(String, ColumnFiller)}
 * base method or one of several convenience methods e.g., 
 * the ones available for sequence or pseudo-random values.
 * </li>  
 * <li>
 * Each call to
 * {@link #generate(int)} causes a specified number of entries
 * to be associated to the data set, in line with the current
 * column fillers set.
 * </li>
 * <li>
 * {@link Log#write(DataSet)} may be used to produce a readable
 * XML representation of a data set.
 * </li>
 * </ul>
 * 
 * <p><b>Example</b></p>
 * <blockquote><pre>
 * import static org.jdbdt.JDBDT.*;
 * import org.jdbdt.Table;
 * import org.jdbdt.DataSet;
 * import java.sql.Connection;
 * import java.sql.Date;
 * ...
 * Connection conn = ...;
 * Table t = table("User")
 *          .columns("id", "login", "password", "since")
 *          .boundTo(c);
 * DataSet ds = 
 *     data(t)
 *    .sequence("id", 1) // 1, 2, 3, ...
 *    .sequence("login", "harry", "mark", "john")
 *    .nullValue("password")
 *    .random("since", Date.valueOf("2015-01-01"), Date.valueOf("2015-12-31"))
 *    .generate(3) // generate 3 rows
 *    .sequence("login", i -&gt; "user_" + i, 4)  // "user_4", ... , "user_7"
 *    .value("password", "dumbPass")
 *    .generate(4); // generate 4 more rows   
 *    
 * log(System.err).write(ds);
 * </pre></blockquote>
 * 
 * @since 0.1 
 * @see Loader#rows(DataSet)
 * @see Delta#before(DataSet)
 * @see Delta#after(DataSet)
 * @see Log#write(DataSet)
 *
 */
public final class DataSet implements Iterable<Row> {

  /**
   * Row generator.
   */
  private static class Generator {
    /**
     * Fillers.
     */
    final ColumnFiller<?>[] fillers;
    /**
     * Row count.
     */
    final int rowCount;
    /**
     * Constructor.
     * @param fillers Filler configuration.
     * @param rowCount Row count.
     */
    Generator(ColumnFiller<?>[] fillers, int rowCount) {
      this.fillers = fillers;
      this.rowCount = rowCount;
    }

    /**
     * Get row count.
     * @return The number of rows to be generated.
     */
    int rowCount() {
      return rowCount;
    }

    /**
     * Get fillers.
     * @return The filler configuration.
     */
    ColumnFiller<?>[] fillers() {
      return fillers;
    }
  }

  /**
   * Random number generator seed.
   */
  static final long RNG_SEED = 0xDA7ABA5EL;

  /**
   * Table.
   */
  private final Table table;

  /** 
   * Random number generator.
   */
  private final Random rng = new Random(RNG_SEED);

  /**
   * List of row generators.
   */
  private final ArrayList<Generator> generators = new ArrayList<>();

  /**
   * Map of database column names to column indexes
   */
  private final HashMap<String,Integer> columnIdx = new HashMap<>();

  /**
   * Current settings for fillers.
   */
  private final ColumnFiller<?>[] currentFillers; 

  /**
   * Number of column fillers set.
   */
  private int fillerCount;

  /**
   * Row count.
   */
  private int rowCount = 0;

  /**
   * Cached rows (unchanged until data set changes).
   */
  private RowSet theRows;

  /**
   * Constructs a new data set for the given table.
   * @param t Table for data set.
   */
  DataSet(Table t) {
    ensureArgNotNull(t);
    table = t;
    String[] columnNames = t.getColumnNames();
    currentFillers = new ColumnFiller[columnNames.length];
    fillerCount = 0;
    for (int idx=0; idx < columnNames.length; idx++) {
      columnIdx.put(columnNames[idx].toLowerCase(), idx);
    }
  }

  /**
   * Get number of columns with defined fillers.
   * @return A non-negative integer indicating how many columns 
   *   have an associated column filler.
   */
  int fillerCount() {
    return fillerCount;
  }

  /**
   * Associate a number of rows to the data set, according to 
   * the current column fillers' configuration. 
   * 
   * @param rows Number of rows (a positive integer)
   * @return The data set instance (for chained calls).
   * @throws InvalidUsageException for an invalid row count, or if
   *     there are columns with no associated fillers.
   */
  public DataSet generate(int rows) throws InvalidUsageException {
    ensureValid(rows, rows > 0);
    if (fillerCount < currentFillers.length) {
      for (int idx = 0; idx < table.getColumnCount(); idx++) {
        if (currentFillers[idx] == null) {
          throw new InvalidUsageException("No filler is set for column '" + 
              table.getColumnNames()[idx]);
        }
      }
      throw new JDBDTInternalError("Filler count does not match fillers set.");
    }
    addGenerator(currentFillers, rows);
    return this;
  }

  @SuppressWarnings("javadoc")
  private void addGenerator(ColumnFiller<?>[] fillers, int rows) {
    theRows = null; // cached rows (if defined) are no longer valid
    generators.add(new Generator(fillers.clone(), rows));
    rowCount += rows;
  }

  /**
   * Set filler for column.
   * @param column Column name.
   * @param filler Column filler. 
   * @return The data set instance (for chained calls).
   */
  public DataSet set(String column, ColumnFiller<?> filler) {
    ensureArgNotNull(column);
    ensureArgNotNull(filler);
    Integer idx = columnIdx.get(column.toLowerCase());
    if (idx == null) {
      throw new InvalidUsageException("Invalid column name: '" + column + "'.");
    }
    if (currentFillers[idx] == null) {
      fillerCount ++;
    }
    currentFillers[idx] = filler;
    return this;
  }

  /**
   * Disable all previous column filler settings.
   * 
   * <p>
   * After a call to this method, no column will have an associated filler.
   * </p>
   */
  public void reset() {
    Arrays.fill(currentFillers, null);
    fillerCount = 0;
  }

  /**
   * Get number of rows represented by this data set.
   * @return A non-negative integer.
   */
  public int size() {
    return rowCount;
  }


  /**
   * Get table associated to this data set.
   * @return A able instance.
   */
  Table getTable() {
    return table;
  }
  
  /**
   * Get a row iterator for the data set.
   * <p>Note that this class implements <code>Iterable&lt;Row&gt;</code>
   * hence all the rows in a data set may be iterated using
   * a for-each loop, if convenient for debugging or other purposes.
   * </p>
   * @return An iterator for the rows defined by this data set.
   */
  @Override
  public Iterator<Row> iterator() {
    return getRowSet().iterator();
  }
  /**
   * Get rows for this data set (package-protected, JDBDT-usable version).
   * 
   * <p>
   * The rows are generated only on demand and cached for use in future calls.
   * The cached rows are discarded only if {@link #generate(int)} is subsequently
   * called.
   * </p>
   * 
   * @return An array of all rows. Each row is an array of column values.
   */
   RowSet getRowSet() {   
    if (theRows == null) {
      final RowSet rows = new RowSet();
      rng.setSeed(RNG_SEED);
      final String[] colNames = table.getColumnNames();
      for (Generator g : generators) {
        final ColumnFiller<?>[] fillers = g.fillers();
        final int n = g.rowCount();
        for (int r=0; r < n; r++) {
          final Object[] colData = new Object[colNames.length];
          for (int c = 0; c < colNames.length; c++) {
            colData[c] = fillers[c].next();
          }
          rows.addRow(new RowImpl(colData));
        }
      }
      theRows = rows;
    }
    return theRows;
  }

  /**
   * Standard sequence filler.
   * @param <T> Datum type.
   */
  private static class StdSeqFiller<T> implements ColumnFiller<T> {
    /** Next value */
    private T nextValue;
    /** Step function. */
    private final Function<T,T> stepFunction;
    /**
     * Constructor.
     * @param initial Initial value.
     * @param stepFunction Step function.
     */
    StdSeqFiller(T initial, Function<T,T> stepFunction) {
      nextValue = initial;
      this.stepFunction = stepFunction;
    }
    @Override
    public T next() {
      T prev = nextValue;
      nextValue = stepFunction.apply(nextValue);
      return prev;
    }
  }

  /**
   * Constant value filler.
   *
   * @param <T> Datum type.
   * @see DataSet#value(String, Object)
   * @see DataSet#nullValue(String)
   */
  private static class ConstantFiller<T> implements ColumnFiller<T> {
    /** Constant value. */
    private final T value;
    /** 
     * Constructor.
     * @param value Value to use
     */
    ConstantFiller(T value) {
      this.value = value;
    }
    @Override
    public T next() {
      return value;
    }
  }

  /** NULL value filler */
  private static final ColumnFiller<?> NULL_FILLER = new ConstantFiller<Object>(null);

  /**
   * Set the NULL value filler for a column.
   * 
   * @param column Column name.
   * @return The data set instance (for chained calls).
   * @see #value(String, Object)
   */
  public DataSet nullValue(String column) {
    return set(column, NULL_FILLER);
  }

  /**
   * Set the NULL value filler for all remaining columns. 
   * 
   * <p>
   * A call to this method sets the {@link #nullValue} filler
   * for all columns without an associated filler. 
   * </p>
   * @return  The data set instance (for chained calls).
   * @see #nullValue(String)
   */
  public DataSet remainingColumnsNull() {
    for (int i = 0; i < currentFillers.length; i++) {
      if (currentFillers[i] == null) {
        currentFillers[i] = NULL_FILLER;
        fillerCount++;
      }
    }
    return this;
  }

  /**
   * Set a fixed value filler for a column.
   * @param column Column name.
   * @param value Value to use.
   * @return The data set instance (for chained calls).
   */
  public DataSet value(String column, Object value) {
    return set(column, new ConstantFiller<Object>(value)); 
  }

  /**
   * Set a sequence filler using a step-function.
   * 
   * <p>
   * The sequence of values generated by the filler starts
   * with the specified initial value, and subsequent values 
   * are generated using the step function which takes as input the previous value.
   * The sequence will then be
   * <code>s(0), s(1), ...</code> where <code>s(0) = initial</code>
   * and <code>s(n+1) = step.apply(s(n))</code> for
   * all <code>n &gt;= 0</code> .
   * </p>
   * 
   * @param <T> Column datum type.
   * @param column Column name.
   * @param initial Initial value.
   * @param step Step function.
   * @return The data set instance (for chained calls).
   * @see #sequence(String, Function)
   */
  public <T> DataSet sequence(String column, T initial, Function<T,T> step) {
    ensureArgNotNull(initial);
    ensureArgNotNull(step);
    return set(column, new StdSeqFiller<T>(initial, step));
  }

  /**
   * Set sequence filler using a index-based step-function.
   * 
   * <p>
   * A call to this method is equivalent to
   * <code>sequence(column, step, 0)</code>.
   * </p>
   * 
   * @param column Column name.
   * @param step Step function.
   * @return The data set instance (for chained calls).
   * @see #sequence(String, Function, int)
   */
  public DataSet sequence(String column, Function<Integer,?> step) {
    return sequence(column, step, 0);
  }
  
  /**
   * Set sequence filler using a index-based step-function.
   * 
   * <p>
   * The sequence of values generated by the filler starts
   * with the specified initial value, and subsequent values 
   * are generated using the step function which takes as input the 
   * index of the row being generated, starting from the initial value.
   * The sequence will then be
   * <code>s(start), s(start+1), ...</code> where <code>s(i) = step.apply(i)</code>
   * for all <code>i &gt;= start</code>.
   * </p>
   * 
   * @param column Column name.
   * @param step Step function.
   * @param initial Initial value fed to step function.
   * @return The data set instance (for chained calls).
   * @see #sequence(String, Object, Function)
   */
  public DataSet sequence(String column, Function<Integer,?> step, int initial) {
    ensureArgNotNull(step);
    return set(column, new ColumnFiller<Object>() {
      int count = initial;
      @Override
      public Object next() {
        return step.apply(count++);
      }
    });
  }
  
  /**
   * Set sequence filler using array values.
   * 
   * <p>
   * A call to this method is shorthand for 
   * <code>sequence(column, i -&gt; values[i % values.length])</code>.
   * </p>
   * @param <T> Type of data.
   * @param column Column name.
   * @param values Sequence of values to use.
   * @return The data set instance (for chained calls).
   * 
   * @see #sequence(String, List)
   * @see #sequence(String,Function)
   * 
   */
  @SafeVarargs
  public final <T> DataSet sequence(String column, T... values) {
    ensureValidArray(values);
    return sequence(column,  i -> values [i % values.length]);
  }
  
  /**
   * Set sequence filler using a list of values.
   * 
   * <p>
   * A call to this method is shorthand for 
   * <code>sequence(column, i -&gt; values.get(i % values.size()))</code>.
   * </p>
   * 
   * @param column Column name.
   * @param values Sequence of values to use.
   * @return The data set instance (for chained calls).
   * 
   * @see #sequence(String,Function)
   */
  public DataSet sequence(String column, List<?> values) { 
    ensureValidList(values);
    return sequence(column,  i -> values.get(i % values.size()));
  }

  /**
   * Set <code>int</code> value sequence filler for column.
   * 
   * <p>
   * A call to this method is shorthand for 
   * <code>sequence(column, initial, 1)</code>.
   * </p>
   * 
   * @param column Column name.
   * @param initial Initial sequence value.
   * @return The data set instance (for chained calls).
   * @see #sequence(String, int, int)
   */
  public DataSet sequence(String column, int initial) {
    return sequence(column, initial, 1);
  }

  /**
   * Set <code>int</code> sequence filler for column
   * with a specified step.
   * 
   * <p>
   * A call to this method is shorthand for 
   * <code>sequence(column, initial, n -&gt; n + step)</code>.
   * </p>
   * 
   * @param column Column name.
   * @param initial Initial sequence value.
   * @param step Sequence step.
   * @return The data set instance (for chained calls).
   * @see #sequence(String, Object, Function)
   * @see #sequence(String, int)
   */
  public DataSet sequence(String column, int initial, int step) {
    return sequence(column, (Integer) initial, n -> n + step);
  }

  /**
   * Set <code>long</code> value sequence filler for column.
   * 
   * <p>
   * A call to this method is shorthand for 
   * <code>sequence(column, initial, 1L)</code>.
   * </p>
   * 
   * @param column Column name.
   * @param initial Initial sequence value.
   * @return The data set instance (for chained calls).
   * @see #sequence(String, Object, Function)
   * @see #sequence(String, long, long)
   */
  public DataSet sequence(String column, long initial) {
    return sequence(column, initial, 1L);
  }

  /**
   * Set <code>long</code> sequence filler for column
   * with a specified step.
   * 
   * <p>
   * A call to this method is shorthand for 
   * <code>sequence(column, initial, n -&gt; n + step)</code>.
   * </p>
   * 
   * @param column Column name.
   * @param initial Initial sequence value.
   * @param step Sequence step.
   * @return The data set instance (for chained calls).
   * @see #sequence(String, Object, Function)
   * @see #sequence(String, long)
   */
  public DataSet sequence(String column, long initial, long step) {
    return sequence(column, (Long) initial, n -> n + step);
  }

  /**
   * Set {@link BigInteger} sequence filler for column.
   * 
   * <p>
   * A call to this method is shorthand for
   * <code>sequence(column, initial, BigInteger.ONE)</code>.
   * </p>
   * 
   * @param column Column name.
   * @param initial Initial sequence value.
   * @return The data set instance (for chained calls).
   * @see #sequence(String, Object, Function)
   * @see #sequence(String, BigInteger, BigInteger)
   */
  public DataSet sequence(String column, BigInteger initial) {
    ensureArgNotNull(initial);
    return sequence(column, initial, BigInteger.ONE);
  }

  /**
   * Set {@link BigInteger} sequence filler for column
   * with a specified step.
   * 
   * <p>
   * A call to this method is shorthand for 
   * <code>sequence(column, initial, n -&gt; n.add(step))</code>.
   * </p>
   * 
   * @param column Column name.
   * @param initial Initial sequence value.
   * @param step Sequence step.
   * @return The data set instance (for chained calls).
   * @see #sequence(String, Object, Function)
   * @see #sequence(String, BigInteger)
   */
  public DataSet sequence(String column, BigInteger initial, BigInteger step) {
    ensureArgNotNull(initial);
    ensureArgNotNull(step);
    return sequence(column, initial, n -> n.add(step));
  }

  /**
   * Set <code>float</code> sequence filler for column
   * with a specified step.
   * 
   * <p>
   * A call to this method is shorthand for 
   * <code>sequence(column, initial, x -&gt; x + step)</code>.
   * </p>
   * 
   * @param column Column name.
   * @param initial Initial sequence value.
   * @param step Sequence step.
   * @return The data set instance (for chained calls).
   * @see #sequence(String, Object, Function)
   * @see #sequence(String, double, double)
   */
  public DataSet sequence(String column, float initial, float step) {
    return sequence(column, (Float) initial, x -> x + step);
  }
  /**
   * Set <code>double</code> sequence filler for column
   * with a specified step.
   * 
   * <p>
   * A call to this method is shorthand for 
   * <code>sequence(column, initial, x -&gt; x + step)</code>.
   * </p>
   * 
   * @param column Column name.
   * @param initial Initial sequence value.
   * @param step Sequence step.
   * @return The data set instance (for chained calls).
   * @see #sequence(String, Object, Function)
   * @see #sequence(String, float, float)
   */
  public DataSet sequence(String column, double initial, double step) {
    return sequence(column, (Double) initial, x -> x + step);
  }

  /**
   * Milliseconds per day.
   */
  public static final long MILLIS_PER_DAY = 86400000L;

  /**
   * Set {@link Date} sequence filler for column
   * with a specified step in days.
   * 
   * <p>
   * A call to this method is shorthand for 
   * <code>sequence(column, initial, d -&gt; new Date(x.getTime() + step * MILLIS_PER_DAY))</code>.
   * </p>
   * 
   * @param column Column day.
   * @param initial Initial date.
   * @param step Step in days.
   * @return The data set instance (for chained calls).
   * 
   * @see #sequence(String, Time, int)
   * @see #sequence(String, Timestamp, long)
   * @see #sequence(String, Object, Function)
   */
  public DataSet sequence(String column, Date initial, int step) {
    ensureArgNotNull(initial);
    return sequence(column, initial, d -> new Date(d.getTime() + step * MILLIS_PER_DAY));
  }

  /**
   * Set {@link Time} sequence filler for column
   * with a specified step in seconds.
   * 
   * <p>
   * A call to this method is shorthand for 
   * <code>sequence(column, initial, t -&gt; new Time(t.getTime() + step * 1000L))</code> .
   * </p>
   * 
   * @param column Column day.
   * @param initial Initial date.
   * @param step Step in seconds.
   * @return The data set instance (for chained calls).
   * 
   * @see #sequence(String, Date, int)
   * @see #sequence(String, Timestamp, long)
   * @see #sequence(String, Object, Function)
   */
  public DataSet sequence(String column, Time initial, int step) {
    ensureArgNotNull(initial);
    return sequence(column, initial, t -> new Time(t.getTime() + step * 1000L));
  }

  /**
   * Set {@link Timestamp} sequence filler for column
   * with a specified step in milliseconds.
   * 
   * <p>
   * A call to this method is shorthand for 
   * <code>sequence(column, initial, ts -&gt; new Timestamp(ts.getTime() + step))</code>.
   * </p>
   * 
   * @param column Column day.
   * @param initial Initial date.
   * @param step Step in milliseconds.
   * @return The data set instance (for chained calls).
   * 
   * @see #sequence(String, Date, int)
   * @see #sequence(String, Time, int)
   * @see #sequence(String, Object, Function)
   */
  public DataSet sequence(String column, Timestamp initial, long step) {
    ensureArgNotNull(initial);
    return sequence(column, initial, ts -> new Timestamp(ts.getTime() + step));
  }
  
  /**
   * Set random filler for column using an array of values.
   * 
   * <p>
   * The specified column will be filled with values that 
   * are uniformly sampled from the given array. 
   * </p>
   * 
   * @param <T> Type of data.
   * @param column Column name.
   * @param values  Values to use.
   * @return The data set instance (for chained calls).
   * 
   * @see #random(String, List)
   * @see #sequence(String, Object...)
   */
  @SafeVarargs
  public final <T> DataSet random(String column, T... values) {
    ensureValidArray(values);
    return set(column, () -> values[rng.nextInt(values.length)]);
  }

  /**
   * Set random filler for column using a list of values.
   * 
   * <p>
   * The specified column will be filled with values that 
   * are uniformly sampled from the given list. 
   * </p>
   * 
   * @param column Column name.
   * @param values  Values to use.
   * @return The data set instance (for chained calls).
   * 
   * @see #random(String, Object...)
   * @see #sequence(String, List)
   */
  public DataSet random(String column, List<?> values) {
    ensureValidList(values);
    return set(column,() -> values.get(rng.nextInt(values.size())));
  }

  /**
   * Set random filler using <code>int</code> values.
   * 
   * <p>
   * The specified column will be filled with values that 
   * are uniformly sampled from the interval <code>[min,max]</code>.
   * </p>
   * 
   * @param column Column name.
   * @param min Minimum value.
   * @param max Maximum value.
   * @return The data set instance (for chained calls).
   * 
   * @see #random(String, long, long)
   * @see #random(String, float, float)
   * @see #random(String, double, double)
   */
  public  DataSet random(String column, int min, int max) {
    ensureValidRange(min, max);
    final int n = max - min + 1;
    return set(column, () -> min + rng.nextInt(n));
  }

  /**
   * Set random filler using <code>long</code> values.
   * 
   * <p>
   * The specified column will be filled with values that 
   * are uniformly sampled from the interval <code>[min,max]</code>.
   * </p>
   * 
   * @param column Column name.
   * @param min Minimum value.
   * @param max Maximum value.
   * @return The data set instance (for chained calls).
   * 
   * @see #random(String, int, int)
   * @see #random(String, float, float)
   * @see #random(String, double, double)
   */
  public  DataSet random(String column, long min, long max) {
    ensureValidRange(min, max);
    return set(column, () ->  nextRandomLong(min, max));
  }

  /**
   * Set random filler using <code>float</code> values.
   * 
   * <p>
   * The specified column will be filled with values that 
   * are uniformly sampled from the interval <code>[min,max]</code>.
   * </p>
   * 
   * @param column Column name.
   * @param min Minimum value.
   * @param max Maximum value.
   * @return The data set instance (for chained calls).
   * 
   * @see #random(String, int, int)
   * @see #random(String, long, long)
   * @see #random(String, double, double)
   */
  public  DataSet random(String column, float min, float max) {
    ensureValidRange(min, max);
    final float diff = max - min;
    return set(column, () -> min + rng.nextFloat() * diff);
  }

  /**
   * Set random filler using <code>double</code> values.
   * 
   * <p>
   * The specified column will be filled with values that 
   * are uniformly sampled from the interval <code>[min,max]</code>.
   * </p>
   * 
   * @param column Column name.
   * @param min Minimum value.
   * @param max Maximum value.
   * @return The data set instance (for chained calls).
   * 
   * @see #random(String, int, int)
   * @see #random(String, long, long)
   * @see #random(String, float, float)
   */
  public  DataSet random(String column, double min, double max) {
    ensureValidRange(min, max);
    final double diff = max - min;
    return set(column, () -> min + rng.nextDouble() * diff);
  }

  /**
   * Set random filler using {@link Date} values.
   * 
   * <p>
   * The specified column will be filled with values that 
   * are uniformly sampled from the interval <code>[min,max]</code>.
   * </p>
   * 
   * @param column Column name.
   * @param min Minimum value.
   * @param max Maximum value.
   * @return The data set instance (for chained calls).
   * 
   * @see #random(String, Time, Time)
   * @see #random(String, Timestamp, Timestamp)
   * 
   */
  public  DataSet random(String column, Date min, Date max) {
    ensureValidRange(min, max);
    final long a = min.getTime() / MILLIS_PER_DAY,
               b = max.getTime() / MILLIS_PER_DAY ;
    return set(column, () -> new Date(nextRandomLong( a, b) * MILLIS_PER_DAY));
  }
  
  /**
   * Auxiliary method that generates a pseudo-random long
   * within a given internal
   * @param a Lower bound
   * @param b Upper bound
   * @return A long value in the interval <code>[a, b]</code>
   */
  private long nextRandomLong(long a, long b) {
    return a + (rng.nextLong() & Long.MAX_VALUE) % (b - a + 1);
  }
  
  /**
   * Set random filler using {@link Time} values.
   * 
   * <p>
   * The specified column will be filled with values that 
   * are uniformly sampled from the interval <code>[min,max]</code>.
   * </p>
   * 
   * @param column Column name.
   * @param min Minimum value.
   * @param max Maximum value.
   * @return The data set instance (for chained calls).
   * 
   * @see #random(String, Timestamp, Timestamp)
   * @see #random(String, Date, Date)
   */
  public  DataSet random(String column, Time min, Time max) {
    ensureValidRange(min, max);
    final long a = min.getTime(),
               b = max.getTime();
    return set(column, () -> new Time(nextRandomLong( a, b)));
  }

  /**
   * Nanoseconds per second.
   */
  private static final int NANO_PER_SEC = 1_000_000_000; 
  
  /**
   * Nano seconds per millisecond.
   */
  private static final int NANO_PER_MSEC = 1_000_000; 


  /**
   * Set random filler using {@link Timestamp} values.
   * 
   * <p>
   * The specified column will be filled with values that 
   * are uniformly sampled from the interval <code>[min,max]</code>.
   * </p>
   * 
   * @param column Column name.
   * @param min Minimum value.
   * @param max Maximum value.
   * @return The data set instance (for chained calls).
   * 
   * @see #random(String, Time, Time)
   * @see #random(String, Date, Date)
   */
  public  DataSet random(String column, Timestamp min, Timestamp max) {
    ensureValidRange(min, max);
    final long a = min.getTime() * NANO_PER_MSEC  + min.getNanos(), 
        n = max.getTime() * NANO_PER_MSEC + max.getNanos() - a + 1;
    return set(column, 
       () -> {
        long v = a + rng.nextLong() % n;
        Timestamp ts = new Timestamp(v / NANO_PER_SEC);
        ts.setNanos((int) (v % NANO_PER_SEC));
        return ts;
      }
    );
  }

  /**
   * Set customized random filler.
   * 
   * <p>
   * The specified column will be filled with values that are obtained
   * from a generator function. The generator function
   * takes a {@link Random} instance as an argument and returns a value: 
   * it should use the generator to produce column values in deterministic
   * fashion (in particular, the random number generator argument should not 
   * be re-seeded).
   * </p>
   * 
   * <p><b>Illustration of use</b></p>
   * <p>The filler below will yield strings
   * <code>"ID_0", ..., "ID_9"</code> with an uniform
   * distribution:</p>
   * <blockquote><pre>
   * DataSet ds = ...;
   * ...
   * ds.random("SomeColumn", rng -&gt; "ID_" + rng.nextInt(10));
   * </pre></blockquote>
   * @param column Column name.
   * @param gen Generator function.
   *
   * @return The data set instance (for chained calls).
   *
   * @see java.util.Random
   * @see #random(String, int, int)
   * @see #random(String, long, long)
   * @see #random(String, float, float)
   * @see #random(String, double, double)
   * @see #random(String, Date, Date)
   * @see #random(String, Time, Time)
   * @see #random(String, Timestamp, Timestamp)
   */
  public DataSet random(String column, Function<Random,?> gen) {
    return set(column, () -> gen.apply(rng));
  }
  
  /**
   * Non-null validation utility method.
   * @param o Object reference.
   * @throws InvalidUsageException if <code>o == null</code>.
   */
  private static void ensureArgNotNull(Object o) 
  throws InvalidUsageException {
    if (o == null) {
      throw new InvalidUsageException("Null argument.");
    }
  }
  
  /**
   * Array validation utility method.
   * @param array Array reference.
   * @throws InvalidUsageException if the array is <code>null</code> or empty.
   */
  private static void ensureValidArray(Object[] array)
  throws InvalidUsageException {
    if (array == null) {
      throw new InvalidUsageException("Null array argument.");
    }
    if (array.length == 0) {
      throw new InvalidUsageException("Empty array argument.");

    }
  }
  
  /**
   * List validation utility method.
   * @param list Array reference.
   * @throws InvalidUsageException if the list is <code>null</code> or empty.
   */
  private static void ensureValidList(List<?> list) 
  throws InvalidUsageException {
    if (list == null) {
      throw new InvalidUsageException("Null list argument.");
    }
    if (list.size() == 0) {
      throw new InvalidUsageException("Empty list argument.");
    }
  }
  /**
   * Condition validation utility method.
   * @param o Object reference.
   * @param condition Boolean value for condition.
   * @throws InvalidUsageException if <code>condition == false</code>.
   */
  private static void ensureValid(Object o, boolean condition) 
  throws InvalidUsageException {
    if (! condition) {
      throw new InvalidUsageException("Invalid value for parameter: " + o);
    }
  }

  /**
   * Range validation utility method.
   * @param <T> Type of data.
   * @param min Minimum value.
   * @param max Maximum value.
   * @throws InvalidUsageException if the range is not valid.
   */
  private static <T extends Comparable<T>> void ensureValidRange(T min, T max) {
    if (min == null) {
      throw new InvalidUsageException("Null value for minimum.");
    }
    if (max == null) {
      throw new InvalidUsageException("Null value for maximum.");
    }
    if (min.compareTo(max) >= 0) {
      throw new InvalidUsageException("Invalid range: " + min + " >= " + max);
    }
  }
  

}
