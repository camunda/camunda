/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.impl.sqlite;

import static io.camunda.zeebe.db.impl.ZeebeDbConstants.ZB_DB_BYTE_ORDER;

import io.camunda.zeebe.db.DbKey;
import io.camunda.zeebe.db.KeyStructure;
import java.nio.ByteBuffer;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import org.agrona.ExpandableArrayBuffer;
import org.jspecify.annotations.Nullable;

/**
 * Maps the serialized form of a column family's keys onto the primary key columns of its table.
 *
 * <p>The layout is derived once from {@link DbKey#describe(KeyStructure)} of a key prototype. The
 * shared conformance suite pins RocksDB's unsigned bytewise key ordering as the specification, so
 * every column must sort exactly like the bytes it was cut from. Order-exactness is therefore the
 * default and queryability is an opt-in: a missed opt-in costs only ergonomics, a wrong encoding
 * silently changes engine behaviour.
 *
 * <ul>
 *   <li>a numeric component declared {@code nonNegative()} is an {@code INTEGER}: for non-negative
 *       values unsigned big-endian byte order equals numeric order. A value that violates the
 *       declaration is rejected instead of being stored out of order.
 *   <li>any other numeric component is a {@code BLOB} of its raw big-endian bytes. Negative values
 *       sort above positive ones bytewise but below them as a SQLite {@code INTEGER}.
 *   <li>a string is a {@code BLOB} of its serialized form including the 4-byte length prefix.
 *       RocksDB orders by length first, a {@code TEXT} column would order by content.
 *   <li>raw bytes are a {@code BLOB}.
 *   <li>an enum ordinal is an {@code INTEGER} holding the stored byte as an unsigned value, which
 *       is monotonic in both orderings.
 * </ul>
 *
 * <p>Column-wise comparison equals comparison of the concatenation because every variable-width
 * component is self-delimiting (length prefix) or last (raw bytes).
 *
 * <p>An omittable string is absent for some values of the preceding components. SQLite requires all
 * primary key columns to be {@code NOT NULL}, so the absent case is stored as the zero-length
 * {@code BLOB}, which is unambiguous because a present string is at least 4 bytes. This is only
 * sound if a preceding component discriminates the absent case and the omittable component is the
 * last one, so the end of the key identifies it. Otherwise the layout falls back to a single opaque
 * {@code BLOB} primary key holding the whole serialized key.
 *
 * <p>Naming: columns are {@code k<index>}, followed by {@code _<name>} when the component has a
 * name, lower-cased with every character outside {@code [a-z0-9]} replaced by {@code _}. The opaque
 * fallback column is {@code k}. The value column is {@code v}.
 */
final class KeyLayout {

  static final String VALUE_COLUMN = "v";

  private static final String OPAQUE_COLUMN = "k";
  private static final int LENGTH_PREFIX = Integer.BYTES;

  private final List<Component> components;
  private final List<String> columnNames;
  private final boolean opaque;

  private KeyLayout(final List<Component> components, final boolean opaque) {
    this.components = components;
    this.opaque = opaque;
    columnNames = opaque ? List.of(OPAQUE_COLUMN) : columnNames(components);
  }

  /**
   * @throws IllegalArgumentException if the key has no components at all
   */
  static KeyLayout of(final DbKey prototype) {
    final var recorder = new Recorder();
    prototype.describe(recorder);
    final var components = List.copyOf(recorder.components);
    if (components.isEmpty()) {
      throw new IllegalArgumentException(
          "Cannot derive a table for a key without components: " + prototype.getClass());
    }
    return new KeyLayout(components, !isSplittable(components));
  }

  private static boolean isSplittable(final List<Component> components) {
    for (int i = 0; i < components.size(); i++) {
      final var isOmittable = components.get(i).type() == Type.OMITTABLE_STRING;
      final var isDiscriminated = i > 0;
      final var isLast = i == components.size() - 1;
      if (isOmittable && !(isDiscriminated && isLast)) {
        return false;
      }
    }
    return true;
  }

  private static List<String> columnNames(final List<Component> components) {
    final var names = new ArrayList<String>(components.size());
    for (int i = 0; i < components.size(); i++) {
      final var name = components.get(i).name();
      names.add(name == null ? "k" + i : "k" + i + "_" + sanitize(name));
    }
    return List.copyOf(names);
  }

  /** Lower-cases and replaces every character outside {@code [a-z0-9]} with an underscore. */
  static String sanitize(final String name) {
    return name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "_");
  }

  /** Whether the whole key is stored in one opaque column because it cannot be split safely. */
  boolean isOpaque() {
    return opaque;
  }

  /** The same key stored as one opaque {@code BLOB} column holding the serialized key. */
  KeyLayout asOpaque() {
    return opaque ? this : new KeyLayout(components, true);
  }

  /**
   * Whether keys of the other layout can be bound to the columns of this one: same component types
   * in the same order. Component names are irrelevant, they only name columns.
   */
  boolean sameShape(final KeyLayout other) {
    if (opaque != other.opaque || components.size() != other.components.size()) {
      return false;
    }
    for (int i = 0; i < components.size(); i++) {
      final var mine = components.get(i);
      final var theirs = other.components.get(i);
      if (mine.type() != theirs.type() || mine.nonNegative() != theirs.nonNegative()) {
        return false;
      }
    }
    return true;
  }

  List<String> columnNames() {
    return columnNames;
  }

  /** The SQL type of the column at the given index. */
  String columnType(final int column) {
    return !opaque && components.get(column).isInteger() ? "INTEGER" : "BLOB";
  }

  String createTable(final String table) {
    final var sql = new StringBuilder("CREATE TABLE IF NOT EXISTS ").append(table).append(" (\n");
    for (int i = 0; i < columnNames.size(); i++) {
      sql.append("  ")
          .append(columnNames.get(i))
          .append(' ')
          .append(columnType(i))
          .append(" NOT NULL,\n");
    }
    sql.append("  ").append(VALUE_COLUMN).append(" BLOB NOT NULL,\n");
    sql.append("  PRIMARY KEY (").append(String.join(", ", columnNames)).append(")\n");
    return sql.append(") WITHOUT ROWID").toString();
  }

  /**
   * Binds the columns of a complete serialized key to consecutive parameters.
   *
   * @return the index of the next free parameter
   */
  int bindKey(
      final PreparedStatement statement,
      final int firstParameter,
      final byte[] key,
      final int length)
      throws SQLException {
    return bindKeyColumns(statement, firstParameter, key, length, 0, columnNames.size());
  }

  /**
   * Binds {@code count} columns of a complete serialized key, starting at column {@code from}, to
   * consecutive parameters. The whole key is still validated.
   *
   * @return the index of the next free parameter
   */
  int bindKeyColumns(
      final PreparedStatement statement,
      final int firstParameter,
      final byte[] key,
      final int length,
      final int from,
      final int count)
      throws SQLException {
    if (opaque) {
      statement.setBytes(firstParameter, Arrays.copyOf(key, length));
      return firstParameter + 1;
    }

    int position = 0;
    int parameter = firstParameter;
    for (int i = 0; i < components.size(); i++) {
      final var component = components.get(i);
      final int width = component.width(key, position, length);
      if (width < 0 || position + width > length) {
        throw new IllegalStateException(
            "Expected a complete %s component at offset %d of the %d byte key, but it is truncated"
                .formatted(component.type(), position, length));
      }
      if (i >= from && i < from + count) {
        bind(statement, parameter++, component, key, position, width);
      }
      position += width;
    }
    if (position != length) {
      throw new IllegalStateException(
          "Expected the key to end at offset %d, but it has %d bytes".formatted(position, length));
    }
    return parameter;
  }

  /**
   * Rebuilds the serialized key from the key columns of the current row, which start at the given
   * result set column.
   *
   * @return a fresh array holding the serialized key
   */
  byte[] readKey(final ResultSet row, final int firstColumn) throws SQLException {
    if (opaque) {
      return bytes(row, firstColumn);
    }

    final var buffer = new ExpandableArrayBuffer();
    int position = 0;
    int column = firstColumn;
    for (final var component : components) {
      if (component.isInteger()) {
        final long value = row.getLong(column++);
        switch (component.type()) {
          case INT64 -> {
            buffer.putLong(position, value, ZB_DB_BYTE_ORDER);
            position += Long.BYTES;
          }
          case INT32 -> {
            buffer.putInt(position, (int) value, ZB_DB_BYTE_ORDER);
            position += Integer.BYTES;
          }
          case INT16 -> {
            buffer.putShort(position, (short) value, ZB_DB_BYTE_ORDER);
            position += Short.BYTES;
          }
          default -> buffer.putByte(position++, (byte) value);
        }
      } else {
        final var bytes = bytes(row, column++);
        buffer.putBytes(position, bytes);
        position += bytes.length;
      }
    }
    return Arrays.copyOf(buffer.byteArray(), position);
  }

  private static byte[] bytes(final ResultSet row, final int column) throws SQLException {
    final var bytes = row.getBytes(column);
    return bytes == null ? new byte[0] : bytes;
  }

  /** The key columns, followed by the value column if requested, as selected by a scan. */
  private String selectList(final boolean withValue) {
    final var columns = new ArrayList<>(columnNames);
    if (withValue) {
      columns.add(VALUE_COLUMN);
    }
    return String.join(", ", columns);
  }

  /**
   * The statement of a scan, which returns the key columns and optionally the value column in key
   * order, ascending for a forward scan and descending for a reverse one.
   *
   * <p>A forward scan with a prefix compares the columns the prefix covers by equality, a prefix
   * that ends inside a {@code BLOB} column by range, and a start key on the remaining columns. The
   * first remaining column is also compared on its own if there are several, because a row value
   * comparison next to equality terms stops SQLite from using the index for the range.
   */
  String scanSql(final String table, final ScanSpec spec) {
    final var conditions = new ArrayList<String>();
    final String order;
    if (spec.reverse()) {
      conditions.add(rowValue(0) + " <= " + placeholders(columnNames.size()));
      order = String.join(" DESC, ", columnNames) + " DESC";
    } else {
      final var shape = spec.prefix();
      for (int i = 0; i < shape.equalColumns(); i++) {
        conditions.add(columnNames.get(i) + " = ?");
      }
      if (shape.partialBytes() > 0) {
        final var column = columnNames.get(shape.equalColumns());
        conditions.add(column + " >= ?");
        if (spec.hasUpperBound()) {
          conditions.add(column + " < ?");
        }
      }
      if (spec.hasStart()) {
        addStartConditions(conditions, shape.equalColumns());
      }
      order = String.join(", ", columnNames);
    }
    final var where = conditions.isEmpty() ? "" : " WHERE " + String.join(" AND ", conditions);
    return "SELECT %s FROM %s%s ORDER BY %s"
        .formatted(selectList(spec.withValue()), table, where, order);
  }

  private void addStartConditions(final List<String> conditions, final int first) {
    final int remaining = columnNames.size() - first;
    if (remaining == 1) {
      conditions.add(columnNames.get(first) + " >= ?");
    } else if (remaining > 1) {
      if (first > 0) {
        conditions.add(columnNames.get(first) + " >= ?");
      }
      conditions.add(rowValue(first) + " >= " + placeholders(remaining));
    }
  }

  private String rowValue(final int from) {
    final var columns = columnNames.subList(from, columnNames.size());
    return columns.size() == 1 ? columns.get(0) : "(" + String.join(", ", columns) + ")";
  }

  private static String placeholders(final int count) {
    return count == 1 ? "?" : "(" + String.join(", ", Collections.nCopies(count, "?")) + ")";
  }

  /**
   * Binds the parameters of {@link #scanSql(String, ScanSpec)}.
   *
   * @param prefix the serialized prefix, ignored unless the spec has one
   * @param start the serialized start key, ignored unless the spec has one or is a reverse scan
   */
  void bindScan(
      final PreparedStatement statement,
      final ScanSpec spec,
      final byte[] prefix,
      final int prefixLength,
      final byte[] start,
      final int startLength)
      throws SQLException {
    if (spec.reverse()) {
      bindKey(statement, 1, start, startLength);
      return;
    }

    final var shape = spec.prefix();
    bindPrefix(statement, shape, prefix, prefixLength);
    int parameter = 1 + shape.equalColumns();
    if (shape.partialBytes() > 0) {
      parameter++;
      if (spec.hasUpperBound()) {
        final var partial =
            Arrays.copyOfRange(prefix, prefixLength - shape.partialBytes(), prefixLength);
        statement.setBytes(parameter++, upperBound(partial));
      }
    }
    if (spec.hasStart()) {
      final int first = shape.equalColumns();
      final int remaining = columnNames.size() - first;
      if (remaining > 1 && first > 0) {
        parameter = bindKeyColumns(statement, parameter, start, startLength, first, 1);
      }
      if (remaining > 0) {
        bindKeyColumns(statement, parameter, start, startLength, first, remaining);
      }
    }
  }

  /**
   * The smallest byte string that is greater than every string starting with the prefix, or {@code
   * null} if there is none because the prefix consists of {@code 0xFF} bytes only.
   */
  static byte @Nullable [] upperBound(final byte[] prefix) {
    int length = prefix.length;
    while (length > 0 && prefix[length - 1] == (byte) 0xFF) {
      length--;
    }
    if (length == 0) {
      return null;
    }
    final var bound = Arrays.copyOf(prefix, length);
    bound[length - 1]++;
    return bound;
  }

  /**
   * Determines which columns a serialized key prefix constrains: the first {@link
   * PrefixShape#equalColumns} columns by equality, and if the prefix ends inside a {@code BLOB}
   * column, the leading {@link PrefixShape#partialBytes} bytes of the next one.
   */
  PrefixShape prefixShape(final byte[] prefix, final int length) {
    if (opaque) {
      return new PrefixShape(0, length);
    }

    int position = 0;
    int equalColumns = 0;
    for (final var component : components) {
      if (position == length) {
        break;
      }
      final int width = component.width(prefix, position, length);
      if (width < 0 || position + width > length) {
        if (component.isInteger()) {
          throw new UnsupportedOperationException(
              "A prefix ending inside the INTEGER column %s is not supported"
                  .formatted(columnNames.get(equalColumns)));
        }
        return new PrefixShape(equalColumns, length - position);
      }
      position += width;
      equalColumns++;
    }
    if (position != length) {
      throw new IllegalStateException(
          "Expected the prefix to end at offset %d, but it has %d bytes"
              .formatted(position, length));
    }
    return new PrefixShape(equalColumns, 0);
  }

  /** The {@code WHERE} conditions for a prefix of the given shape, empty if it matches all. */
  String prefixCondition(final PrefixShape shape) {
    final var conditions = new ArrayList<String>();
    for (int i = 0; i < shape.equalColumns(); i++) {
      conditions.add(columnNames.get(i) + " = ?");
    }
    if (shape.partialBytes() > 0) {
      final var column = columnNames.get(shape.equalColumns());
      conditions.add("substr(%s, 1, %d) = ?".formatted(column, shape.partialBytes()));
    }
    return String.join(" AND ", conditions);
  }

  /** Binds the parameters of {@link #prefixCondition(PrefixShape)}. */
  void bindPrefix(
      final PreparedStatement statement,
      final PrefixShape shape,
      final byte[] prefix,
      final int length)
      throws SQLException {
    if (opaque) {
      if (shape.partialBytes() > 0) {
        statement.setBytes(1, Arrays.copyOf(prefix, length));
      }
      return;
    }

    int position = 0;
    int parameter = 1;
    for (int i = 0; i < shape.equalColumns(); i++) {
      final var component = components.get(i);
      final int width = component.width(prefix, position, length);
      bind(statement, parameter++, component, prefix, position, width);
      position += width;
    }
    if (shape.partialBytes() > 0) {
      statement.setBytes(parameter, Arrays.copyOfRange(prefix, position, length));
    }
  }

  private static void bind(
      final PreparedStatement statement,
      final int parameter,
      final Component component,
      final byte[] key,
      final int offset,
      final int width)
      throws SQLException {
    if (component.isInteger()) {
      statement.setLong(parameter, component.integerValue(key, offset));
    } else {
      statement.setBytes(parameter, Arrays.copyOfRange(key, offset, offset + width));
    }
  }

  /**
   * Which columns a key prefix constrains, see {@link KeyLayout#prefixShape(byte[], int)}.
   *
   * @param equalColumns the number of leading columns that must equal the prefix
   * @param partialBytes the number of leading bytes of the next column that must equal the rest of
   *     the prefix, zero if the prefix ends on a column boundary
   */
  record PrefixShape(int equalColumns, int partialBytes) {}

  /**
   * The variable parts of a scan statement, which is therefore cacheable by it.
   *
   * @param prefix which columns the prefix of a forward scan constrains
   * @param hasUpperBound whether a prefix that ends inside a column has a range end, which it has
   *     unless the prefix consists of {@code 0xFF} bytes only
   * @param hasStart whether a forward scan starts at a key
   * @param reverse whether the scan is a reverse scan from a start key, which has no prefix
   * @param withValue whether the value column is selected
   */
  record ScanSpec(
      PrefixShape prefix,
      boolean hasUpperBound,
      boolean hasStart,
      boolean reverse,
      boolean withValue) {}

  enum Type {
    INT64(8),
    INT32(4),
    INT16(2),
    INT8(1),
    STRING(-1),
    OMITTABLE_STRING(-1),
    BYTES(-1),
    ENUM_ORDINAL(1);

    private final int fixedWidth;

    Type(final int fixedWidth) {
      this.fixedWidth = fixedWidth;
    }
  }

  record Component(Type type, @Nullable String name, boolean nonNegative) {

    boolean isInteger() {
      return switch (type) {
        case INT64, INT32, INT16, INT8 -> nonNegative;
        case ENUM_ORDINAL -> true;
        default -> false;
      };
    }

    /**
     * The width of this component at the given position of a possibly truncated key, or -1 if the
     * width cannot be determined because the length prefix is incomplete.
     */
    int width(final byte[] key, final int position, final int length) {
      return switch (type) {
        case STRING, OMITTABLE_STRING -> {
          if (type == Type.OMITTABLE_STRING && position == length) {
            yield 0;
          }
          if (length - position < LENGTH_PREFIX) {
            yield -1;
          }
          final int stringLength = ByteBuffer.wrap(key).order(ZB_DB_BYTE_ORDER).getInt(position);
          yield LENGTH_PREFIX + stringLength;
        }
        case BYTES -> length - position;
        default -> type.fixedWidth;
      };
    }

    long integerValue(final byte[] key, final int offset) {
      final var buffer = ByteBuffer.wrap(key).order(ZB_DB_BYTE_ORDER);
      final long value =
          switch (type) {
            case INT64 -> buffer.getLong(offset);
            case INT32 -> buffer.getInt(offset);
            case INT16 -> buffer.getShort(offset);
            case INT8 -> buffer.get(offset);
            case ENUM_ORDINAL -> buffer.get(offset) & 0xFF;
            default -> throw new IllegalStateException("Not an integer component: " + type);
          };
      if (value < 0) {
        throw new IllegalStateException(
            "Expected a non-negative %s component, but got %d; the key declares it nonNegative()"
                .formatted(type, value));
      }
      return value;
    }
  }

  private static final class Recorder implements KeyStructure {
    private final List<Component> components = new ArrayList<>();

    @Override
    public void int64(final @Nullable String name, final boolean nonNegative) {
      components.add(new Component(Type.INT64, name, nonNegative));
    }

    @Override
    public void int32(final @Nullable String name, final boolean nonNegative) {
      components.add(new Component(Type.INT32, name, nonNegative));
    }

    @Override
    public void int16(final @Nullable String name, final boolean nonNegative) {
      components.add(new Component(Type.INT16, name, nonNegative));
    }

    @Override
    public void int8(final @Nullable String name, final boolean nonNegative) {
      components.add(new Component(Type.INT8, name, nonNegative));
    }

    @Override
    public void string(final @Nullable String name) {
      components.add(new Component(Type.STRING, name, false));
    }

    @Override
    public void omittableString(final @Nullable String name) {
      components.add(new Component(Type.OMITTABLE_STRING, name, false));
    }

    @Override
    public void bytes(final @Nullable String name) {
      components.add(new Component(Type.BYTES, name, false));
    }

    @Override
    public void enumOrdinal(final @Nullable String name) {
      components.add(new Component(Type.ENUM_ORDINAL, name, false));
    }
  }
}
