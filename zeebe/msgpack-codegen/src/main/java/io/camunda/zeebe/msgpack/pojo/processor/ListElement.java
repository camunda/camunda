/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.msgpack.pojo.processor;

import java.util.Objects;
import javax.tools.Diagnostic.Kind;
import org.jspecify.annotations.Nullable;

/**
 * The element of a list. The element of a list of lists is a list, which has an element again, so
 * the generators recurse on it.
 *
 * @param javaType the type of the element in generated code, such as {@code java.lang.Long} or
 *     {@code java.util.List<java.lang.Long>}
 * @param element the element of an element that is a list, null for the other kinds
 */
record ListElement(Kind kind, String javaType, @Nullable ListElement element) {

  ListElement inner() {
    return Objects.requireNonNull(element);
  }

  /** The expression of the element {@code i<depth>} of the list {@code list}. */
  String at(final String list, final int depth) {
    return list + ".get(i" + depth + ")";
  }

  enum Kind {
    STRING,
    LONG,
    /** Read with the range check of an {@code int} property. */
    INT,
    LIST
  }
}
