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

public interface MutablePolicyUpdateState extends PolicyUpdateState {

  void setCurrentVersion(long policyVersion);

  /** Stores the chunk and opens the assembly for its update if it is the first chunk. */
  void appendChunk(PolicyUpdateRecord chunk);

  /**
   * Records that a chunk was applied. The last chunk sets the current version and removes the
   * assembly with its stored chunks.
   */
  void markChunkApplied(String envelopeId, long policyVersion, int chunkIndex, boolean lastPage);

  /** Deletes the assembly and all of its stored chunks. */
  void removeAssembly(String envelopeId);
}
