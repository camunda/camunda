/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.mutable;

import io.camunda.zeebe.engine.state.immutable.PolicyUpdateState;
import io.camunda.zeebe.protocol.impl.record.value.policyupdate.PolicyUpdateRecord;
import org.jspecify.annotations.NullMarked;

@NullMarked
public interface MutablePolicyUpdateState extends PolicyUpdateState {

  /**
   * Buffers the chunk, and opens its update with the chunk's identifiers if it isn't open yet. A
   * chunk that is already buffered is replaced and not counted again.
   */
  void bufferChunk(PolicyUpdateRecord chunk);

  /** Removes the update and all its buffered chunks. */
  void removeUpdate(String updateId);
}
