/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.deployment;

import org.agrona.DirectBuffer;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Reads persisted processes from the store that owns them, for the {@link ProcessDefinitionCache}
 * to load on a cache miss.
 *
 * <p>Reads see the changes of the current transaction. A returned {@link PersistedProcess} may be
 * backed by a buffer that the next read reuses, so callers must copy it to keep it.
 */
@NullMarked
public interface PersistedProcessLoader {

  /** Returns the process with the given key, or {@code null} if it doesn't exist. */
  @Nullable PersistedProcess findProcessByKey(String tenantId, long processDefinitionKey);

  /**
   * Returns the current state of the given version of a process, or {@code null} if it doesn't
   * exist.
   */
  @Nullable PersistedProcess findProcessByProcessIdAndVersion(
      String tenantId, DirectBuffer bpmnProcessId, long version);

  /**
   * Returns the key of the process with the given id deployed by the given deployment, or {@code
   * null} if there is none.
   */
  @Nullable Long findProcessDefinitionKeyByDeploymentKey(
      String tenantId, DirectBuffer bpmnProcessId, long deploymentKey);
}
