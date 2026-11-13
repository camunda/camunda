/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.rdbms;

import io.camunda.zeebe.db.rdbms.KeyLayout.Part;
import io.camunda.zeebe.db.rdbms.KeyLayout.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * Writes the statement that creates the view of a state table, which shows each part of the keys in
 * a column of its own, using only each database's built-in functions.
 *
 * <p>Where a part starts depends on the lengths of the strings before it, so it's computed from
 * each row's key. A part that doesn't fit into the key shows {@code NULL}. A string's length is
 * kept between zero and the key's length, so that a key that doesn't match its layout shows wrong
 * values instead of failing the query. Only PostgreSQL still fails, on bytes that aren't UTF-8.
 */
enum StateViews {
  H2 {
    @Override
    String number(final Type type, final String bytes) {
      final var sqlType =
          switch (type) {
            case LONG -> "BIGINT";
            case INT -> "INTEGER";
            case SHORT -> "SMALLINT";
            case BYTE -> "TINYINT";
            default -> throw new IllegalArgumentException("Expected a number, but got " + type);
          };
      return "CAST(%s AS %s)".formatted(bytes, sqlType);
    }

    @Override
    String text(final String bytes) {
      return "UTF8TOSTRING(%s)".formatted(bytes);
    }
  },

  POSTGRESQL {
    @Override
    String number(final Type type, final String bytes) {
      final var bits = "('x' || ENCODE(%s, 'hex'))::BIT".formatted(bytes);
      // 16 bits and a byte read as unsigned: flipping the sign bit, then taking it away, signs them
      return switch (type) {
        case LONG -> bits + "(64)::BIGINT";
        case INT -> bits + "(32)::INTEGER";
        case SHORT -> "((%s(16)::INTEGER # 32768) - 32768)::SMALLINT".formatted(bits);
        case BYTE -> "((GET_BYTE(%s, 0) # 128) - 128)::SMALLINT".formatted(bytes);
        default -> throw new IllegalArgumentException("Expected a number, but got " + type);
      };
    }

    @Override
    String text(final String bytes) {
      return "CONVERT_FROM(%s, 'UTF8')".formatted(bytes);
    }
  };

  private static final String KEY = "STATE_KEY";
  private static final String KEY_LENGTH = "OCTET_LENGTH(" + KEY + ")";

  /** Returns {@code CREATE VIEW view AS SELECT … FROM table}, with the parts of the layout. */
  String createView(final String view, final String table, final KeyLayout layout) {
    final var columns = new ArrayList<String>();
    columns.add("PARTITION_ID");
    var start = Start.FIRST;
    for (final var part : layout.parts()) {
      columns.add(decode(part, start) + " AS " + part.column());
      start =
          part.type() == Type.STRING
              ? start.plus(Integer.BYTES).plus(stringLength(start))
              : start.plus(part.length());
    }
    columns.addAll(List.of("UPDATED_POSITION", KEY, "STATE_VALUE"));
    return """
        CREATE VIEW %s AS
        SELECT
          %s
        FROM %s"""
        .formatted(view, String.join(",\n  ", columns), table);
  }

  /** Converts bytes, as many as the type has, to a number, most significant first. */
  abstract String number(Type type, String bytes);

  /** Converts UTF-8 bytes to text. */
  abstract String text(String bytes);

  private String decode(final Part part, final Start start) {
    if (part.type() == Type.STRING) {
      final var length = stringLength(start);
      return ifInKey(
          start.plus(Integer.BYTES - 1).plus(length),
          text(substring(start.plus(Integer.BYTES), length)));
    }
    final var bytes = substring(start, String.valueOf(part.length()));
    return ifInKey(
        start.plus(part.length() - 1),
        part.type() == Type.BYTES ? bytes : number(part.type(), bytes));
  }

  /**
   * The length that a string starting here gives itself, kept between zero and the key's length, so
   * that adding up the lengths can't overflow. It's zero when the key ends before the length.
   */
  private String stringLength(final Start start) {
    final var length = number(Type.INT, substring(start, String.valueOf(Integer.BYTES)));
    return "CASE WHEN %s >= %s THEN LEAST(GREATEST(%s, 0), %s) ELSE 0 END"
        .formatted(KEY_LENGTH, start.plus(Integer.BYTES - 1), length, KEY_LENGTH);
  }

  private static String substring(final Start start, final String length) {
    return "SUBSTRING(%s FROM %s FOR %s)".formatted(KEY, start, length);
  }

  private static String ifInKey(final Start lastByte, final String value) {
    return "CASE WHEN %s >= %s THEN %s END".formatted(KEY_LENGTH, lastByte, value);
  }

  /** Where a part starts in the key, counted from 1: a number of bytes plus string lengths. */
  private record Start(int bytes, List<String> lengths) {

    static final Start FIRST = new Start(1, List.of());

    Start plus(final int count) {
      return new Start(bytes + count, lengths);
    }

    Start plus(final String length) {
      final var all = new ArrayList<>(lengths);
      all.add(length);
      return new Start(bytes, List.copyOf(all));
    }

    @Override
    public String toString() {
      final var terms = new ArrayList<String>();
      terms.add(String.valueOf(bytes));
      terms.addAll(lengths);
      return String.join(" + ", terms);
    }
  }
}
