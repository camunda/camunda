/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.msgpack.pojo.processor;

enum Type {
  LONG(false),
  INT(false),
  BOOLEAN(false),
  STRING(false),
  RAW(false),
  BINARY(false),
  DOCUMENT(false),
  ENUM(false),
  /**
   * A {@code java.util.List}, or an agrona {@code IntArrayList} or {@code LongArrayList}, whose
   * elements are described by a {@link ListElement}.
   */
  LIST(true),
  NESTED(true);

  /** Read into the instance the field holds, so the field must be final. */
  final boolean container;

  Type(final boolean container) {
    this.container = container;
  }
}
