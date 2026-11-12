/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.rdbms;

import java.util.List;

/**
 * The parts of a column family's key, in the order the engine writes them, so that a view can show
 * each part in a column of its own. {@code DbCompositeKey} writes its first key, then its second;
 * {@code DbTenantAwareKey} writes the tenant id before or after its key; {@code DbForeignKey}
 * writes the key it wraps, and {@code DbEnumValue} a byte.
 *
 * <p>Only the views use it; the engine never does. A wrong layout shows wrong values in a view, and
 * changes nothing else.
 */
record KeyLayout(List<Part> parts) {

  static final String TENANT_ID = "TENANT_ID";

  KeyLayout {
    parts = List.copyOf(parts);
  }

  /** A {@code DbLong}: 8 bytes, most significant first. */
  static Part dbLong(final String column) {
    return new Part(Type.LONG, column, Long.BYTES);
  }

  /** A {@code DbInt}: 4 bytes, most significant first. */
  static Part dbInt(final String column) {
    return new Part(Type.INT, column, Integer.BYTES);
  }

  /** A {@code DbShort}: 2 bytes, most significant first. */
  static Part dbShort(final String column) {
    return new Part(Type.SHORT, column, Short.BYTES);
  }

  /** A {@code DbByte} or a {@code DbEnumValue}: one byte. */
  static Part dbByte(final String column) {
    return new Part(Type.BYTE, column, Byte.BYTES);
  }

  /** A {@code DbString}: its length in 4 bytes, then its UTF-8 bytes. */
  static Part dbString(final String column) {
    return new Part(Type.STRING, column, 0);
  }

  /** The tenant id of a {@code DbTenantAwareKey}, a {@code DbString}. */
  static Part tenantId() {
    return dbString(TENANT_ID);
  }

  /**
   * Bytes shown as they're stored, for a value that the engine changes before storing it, such as
   * the job priority, stored as {@code Integer.MAX_VALUE - priority}.
   */
  static Part bytes(final String column, final int length) {
    return new Part(Type.BYTES, column, length);
  }

  /**
   * One part of a key.
   *
   * @param column the view's column for it
   * @param length its number of bytes; unused for a {@link Type#STRING}, whose length comes first
   */
  record Part(Type type, String column, int length) {}

  enum Type {
    LONG,
    INT,
    SHORT,
    BYTE,
    STRING,
    BYTES
  }
}
