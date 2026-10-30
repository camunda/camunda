/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.deployment.model.element;

import io.camunda.zeebe.protocol.record.value.AgentDefinitionType;
import java.util.Map;
import java.util.Set;

/**
 * A representation of an element that is based on a job and should be processed by a job worker.
 * For example, a service task.
 */
public interface ExecutableJobWorkerElement extends ExecutableFlowElement {

  JobWorkerProperties getJobWorkerProperties();

  void setJobWorkerProperties(JobWorkerProperties jobWorkerProperties);

  /**
   * Returns the input-mapping secret references detected at deploy time, keyed by the JSON pointer
   * (RFC 6901) where each secret is injected. Every job-worker element is a flow node, so this
   * mirrors {@link ExecutableFlowNode#getSecretReferences()}. Empty when no input mapping
   * references a secret.
   */
  Map<String, Set<SecretReference>> getSecretReferences();

  /**
   * Returns the input-mapping cluster-variable references detected at deploy time, keyed by the
   * JSON pointer (RFC 6901) where each reference is injected. Every job-worker element is a flow
   * node, so this mirrors {@link ExecutableFlowNode#getClusterVariableReferences()}. Empty when no
   * input mapping references a cluster variable.
   */
  Map<String, Set<ClusterVariableReference>> getClusterVariableReferences();

  /**
   * Returns the agent definition type detected at deploy time from the explicit {@code
   * zeebe:agentDefinition} marker. Defaults to {@link AgentDefinitionType#UNSPECIFIED} when the
   * marker is absent.
   */
  default AgentDefinitionType getAgentDefinitionType() {
    return AgentDefinitionType.UNSPECIFIED;
  }

  /** Sets the agent definition type detected at deploy time. */
  default void setAgentDefinitionType(final AgentDefinitionType agentDefinitionType) {}

  /**
   * @return {@code true} if this element was detected as an agent element at deploy time, i.e. its
   *     {@link #getAgentDefinitionType()} is not {@link AgentDefinitionType#UNSPECIFIED}.
   */
  default boolean isAgentDefinition() {
    return getAgentDefinitionType() != AgentDefinitionType.UNSPECIFIED;
  }
}
