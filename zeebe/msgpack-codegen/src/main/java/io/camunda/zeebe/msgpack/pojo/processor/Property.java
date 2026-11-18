/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.msgpack.pojo.processor;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * @param keyLiteral the key as an escaped Java string literal, to be pasted into generated code
 * @param javaType the type of the field in generated code; of the innermost elements for a list
 * @param inPlace a {@code final UnsafeBuffer} that is wrapped around the read bytes instead of
 *     being replaced by a new view
 * @param sanitized the value must not appear in a string representation
 * @param pojo a nested value is a generated POJO, whose {@code <Name>Ops} is called directly;
 *     otherwise it is a {@code BaseValue}
 * @param element the element of a {@link Type#LIST}, null for the other types
 */
record Property(
    String keyLiteral,
    Type type,
    String field,
    String javaType,
    boolean inPlace,
    boolean sanitized,
    boolean pojo,
    @Nullable ListElement element) {

  ListElement listElement() {
    return Objects.requireNonNull(element);
  }
}
