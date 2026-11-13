/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.immutable;

import io.camunda.zeebe.engine.state.policy.PersistedPolicyUpdate;
import io.camunda.zeebe.protocol.impl.record.value.policyupdate.PolicyUpdateRecord;
import java.util.Optional;
import org.jspecify.annotations.NullMarked;

/**
 * The buffer of policy updates on partition 1: the chunks of an update are buffered until all of
 * them are there, and are then applied one after another.
 */
@NullMarked
public interface PolicyUpdateState {

  /**
   * The update that is buffering or being applied, if there is one. Only one update is open at a
   * time.
   */
  Optional<PersistedPolicyUpdate> getOpenUpdate();

  /** Whether the chunk of the update is buffered. */
  boolean hasChunk(String updateId, int chunkIndex);

  /** The buffered chunk of the update, if it is there. */
  Optional<PolicyUpdateRecord> getChunk(String updateId, int chunkIndex);
}
