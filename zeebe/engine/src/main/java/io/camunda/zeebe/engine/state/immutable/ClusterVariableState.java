/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.immutable;

import io.camunda.zeebe.engine.state.variable.ClusterVariableInstance;
import java.util.Optional;
import org.agrona.DirectBuffer;

public interface ClusterVariableState {

  Optional<ClusterVariableInstance> getTenantScopedClusterVariable(
      DirectBuffer variableNameBuffer, String tenantId);

  Optional<ClusterVariableInstance> getGloballyScopedClusterVariable(
      DirectBuffer variableNameBuffer);

  boolean existsAtTenantScope(final DirectBuffer variableNameBuffer, final String tenantId);

  boolean existsAtGlobalScope(final DirectBuffer variableNameBuffer);
}
