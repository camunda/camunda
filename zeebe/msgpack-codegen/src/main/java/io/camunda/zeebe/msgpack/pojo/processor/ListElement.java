/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.msgpack.pojo.processor;

import io.camunda.zeebe.msgpack.pojo.annotation.MsgpackPojo;
import java.util.Objects;
import javax.tools.Diagnostic.Kind;
import org.jspecify.annotations.Nullable;

/**
 * The element of a list. The element of a list of lists is a list, which has an element again, so
 * the generators recurse on it.
 *
 * @param javaType the type of the element in generated code, such as {@code java.lang.Long} or
 *     {@code java.util.List<java.lang.Long>}
 * @param unboxed a primitive in an agrona {@code IntArrayList} or {@code LongArrayList}, which has
 *     methods that do not box it
 * @param element the element of an element that is a list, null for the other kinds
 */
record ListElement(Kind kind, String javaType, boolean unboxed, @Nullable ListElement element) {

  ListElement inner() {
    return Objects.requireNonNull(element);
  }

  /** The innermost element, which is not a list. */
  ListElement leaf() {
    return kind == Kind.LIST ? inner().leaf() : this;
  }

  /** The innermost elements are items, which a codec reads and writes. */
  boolean items() {
    return leaf().kind == Kind.ITEM;
  }

  String get() {
    return !unboxed ? "get" : kind == Kind.INT ? "getInt" : "getLong";
  }

  String add() {
    return !unboxed ? "add" : kind == Kind.INT ? "addInt" : "addLong";
  }

  /** The expression of the element {@code i<depth>} of the list {@code list}. */
  String at(final String list, final int depth) {
    return list + "." + get() + "(i" + depth + ")";
  }

  enum Kind {
    STRING,
    LONG,
    /** Read with the range check of an {@code int} property. */
    INT,
    /** A {@link MsgpackPojo} or a {@code BaseValue}, read and written by its codec. */
    ITEM,
    LIST
  }
}
