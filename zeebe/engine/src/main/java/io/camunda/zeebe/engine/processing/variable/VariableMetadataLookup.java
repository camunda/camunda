/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.variable;

import io.camunda.zeebe.protocol.impl.record.value.variable.VariableMetadataRecord;
import org.agrona.DirectBuffer;
import org.jspecify.annotations.Nullable;

/**
 * Provides the metadata of each variable that is written by {@link VariableBehavior}. A variable
 * without metadata is created with empty metadata, and an existing variable keeps the metadata it
 * has.
 */
@FunctionalInterface
public interface VariableMetadataLookup {

  /** For callers that provide no metadata for any variable. */
  VariableMetadataLookup NONE = name -> null;

  /**
   * @param name the name of the variable
   * @return the metadata to write on the variable, or {@code null} if there is none to provide
   */
  @Nullable VariableMetadataRecord forVariable(DirectBuffer name);
}
