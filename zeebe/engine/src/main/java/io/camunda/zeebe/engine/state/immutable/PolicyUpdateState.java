/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.immutable;

import io.camunda.zeebe.engine.state.policy.PersistedPolicyAssembly;
import io.camunda.zeebe.protocol.impl.record.value.policyupdate.PolicyUpdateRecord;
import java.util.Optional;

public interface PolicyUpdateState {

  /** The policy version that is currently in effect, 0 if none was applied yet. */
  long getCurrentVersion();

  Optional<PersistedPolicyAssembly> getAssembly(String envelopeId);

  /** The assembly that is still open, if any. There is at most one open assembly. */
  Optional<PersistedPolicyAssembly> findOpenAssembly();

  boolean hasChunk(String envelopeId, int chunkIndex);

  /** Returns a copy of a stored chunk. The chunk must exist. */
  PolicyUpdateRecord getChunk(String envelopeId, int chunkIndex);
}
