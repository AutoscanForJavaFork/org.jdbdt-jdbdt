package org.jdbdt;


/**
 * Typed table query.

 * <p>
 * Objects of this kind represents a typed table query.
 * They are created using {@link JDBDT#selectFrom(TypedTable)}.
 * </p>
 * 
 * <p><b>Illustration</b></p>
 * 
 * <blockquote><pre>
 * import static org.jdbdt.JDBDT.*;
 * import org.jdbdt.TypedTable;
 * import org.jdbdt.TypedTableQuery;
 * import java.sql.Connection;
 * ...
 * Connection conn = ...;
 * Conversion&lt;MyClass&gt; conv = ... ; 
 * TypedTable&lt;MyClass&gt; table
 *   = table("MyTable", conv)
 *    .columns("id", "login", "password")
 *    .boundTo(conn);
 * TypedTableQuery&lt;MyClass&gt; q 
 *   = selectFrom(table).where("login LIKE 'mark%'");        
 * </pre></blockquote>
 * 
 * @param <T> Type of objects for the query.
 * 
 * @see TypedTable
 * @see JDBDT#selectFrom(TypedTable)
 * @see TableQuery
 * 
 * @since 0.1
 */
public final class TypedTableQuery<T> extends TableQuery {

  /**
   * Constructs a new type query.
   * @param table Typed database table.
   */
  TypedTableQuery(TypedTable<T> table) {
    super(table);
  }

  /**
   * Get table associated to query.
   * @return Table set for the query.
   */
  @SuppressWarnings("unchecked")
  final TypedTable<T> getTable() {
    return (TypedTable<T>) super.getTable();
  }

  /**
   * Get conversion function
   * @return Conversion function.
   */
  Conversion<T> conversion() {
    return getTable().conversion();
  }

  @Override
  public TypedTableQuery<T> where(String clause) {
    super.where(clause);
    return this;
  }

  @Override
  public TypedTableQuery<T>  groupBy(String... fields) {
    super.groupBy(fields);
    return this;
  }

  @Override
  public TypedTableQuery<T>  having(String clause) {
    super.having(clause);
    return this;
  }

}
