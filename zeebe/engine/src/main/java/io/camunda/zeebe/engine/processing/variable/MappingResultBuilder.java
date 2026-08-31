/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.variable;

import java.util.List;
import org.agrona.DirectBuffer;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Accumulates the results of variable mappings evaluated one by one in modeling order. Each
 * mapping's result is stored under its (possibly nested) target path and can be looked up by its
 * top-level variable name, so later mappings can reference what earlier ones produced.
 *
 * <p>The two implementations differ in how a nested target relates to the value already in scope,
 * and they share no such logic: {@link InputMappingResultBuilder} writes only what was mapped and
 * layers the scope value in on read; {@link OutputMappingResultBuilder} merges the scope value in
 * while accumulating and reads back plainly.
 */
@NullMarked
public sealed interface MappingResultBuilder
    permits InputMappingResultBuilder, OutputMappingResultBuilder {

  /** Puts a copy of the given MsgPack value at the nested target path. */
  void put(List<String> targetPath, DirectBuffer value);

  /**
   * Returns the accumulated value of the given top-level variable, or {@code null} if no mapping
   * has produced it yet — {@code null} tells the caller to fall back to the scope lookup.
   */
  @Nullable DirectBuffer get(String name);

  /** Returns all accumulated results as a single MsgPack document (a map). */
  DirectBuffer toDocument();
}
