/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.msgpack.pojo.processor;

enum Type {
  LONG(false, false),
  INT(false, false),
  BOOLEAN(false, false),
  /** A {@code @Nullable Boolean}: null is unset, and boxing a Boolean does not allocate. */
  BOXED_BOOLEAN(false, true),
  /** A {@code @Nullable Long}: null is unset. Allocates when read, unlike RequiredLong. */
  BOXED_LONG(false, true),
  /** A {@code @Nullable Integer}: null is unset. Allocates when read, unlike RequiredInt. */
  BOXED_INT(false, true),
  /** A {@link io.camunda.zeebe.msgpack.pojo.RequiredLong}, unset until a value is set. */
  REQUIRED_LONG(true, false),
  /** A {@link io.camunda.zeebe.msgpack.pojo.RequiredInt}, unset until a value is set. */
  REQUIRED_INT(true, false),
  STRING(false, true),
  RAW(false, true),
  BINARY(false, true),
  DOCUMENT(false, true),
  ENUM(false, true),
  /**
   * A {@code java.util.List}, or an agrona {@code IntArrayList} or {@code LongArrayList}, whose
   * elements are described by a {@link ListElement}.
   */
  LIST(true, false),
  NESTED(true, false);

  /** Read into the instance the field holds, so the field must be final. */
  final boolean container;

  /** The field may be {@code @Nullable}, null meaning that the property is unset. */
  final boolean nullable;

  Type(final boolean container, final boolean nullable) {
    this.container = container;
    this.nullable = nullable;
  }
}
