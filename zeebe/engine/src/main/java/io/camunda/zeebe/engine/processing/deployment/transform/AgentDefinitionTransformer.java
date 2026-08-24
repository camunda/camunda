/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.deployment.transform;

import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableFlowElement;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableJobWorkerElement;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableMultiInstanceBody;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableProcess;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import io.camunda.zeebe.protocol.impl.record.value.deployment.ProcessMetadata;
import io.camunda.zeebe.protocol.record.intent.AgentDefinitionIntent;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.Objects;

/**
 * Creates {@code AgentDefinition} records for elements carrying an explicit {@code
 * zeebe:agentDefinition} marker (see {@code AgentElementTypeTransformer}) at deploy time.
 */
final class AgentDefinitionTransformer {

  private final KeyGenerator keyGenerator;
  private final StateWriter stateWriter;

  AgentDefinitionTransformer(final KeyGenerator keyGenerator, final StateWriter stateWriter) {
    this.keyGenerator = keyGenerator;
    this.stateWriter = stateWriter;
  }

  /**
   * Creates an {@code AgentDefinition} for every element of {@code process} for which {@link
   * ExecutableJobWorkerElement#isAgentDefinition()} is {@code true}, appending each to {@code
   * deployment.agentDefinitionsMetadata()} and emitting an {@code AgentDefinition:CREATED}
   * follow-up event keyed with a freshly generated {@code agentDefinitionKey}.
   *
   * <p>A multi-instance activity is represented in {@link ExecutableProcess#getFlowElements()} by
   * its wrapping {@link ExecutableMultiInstanceBody}, which replaces the original element outright
   * rather than merely decorating it, so the marker must be looked up on {@link
   * ExecutableMultiInstanceBody#getInnerActivity()} instead of the body itself.
   *
   * @param deployment the deployment record to append created agent definitions to
   * @param process the executable process to scan for agent-marked elements
   * @param processMetadata the (already finalized) metadata of the process version these agent
   *     definitions belong to
   */
  void writeRecords(
      final DeploymentRecord deployment,
      final ExecutableProcess process,
      final ProcessMetadata processMetadata) {
    process.getFlowElements().stream()
        .map(AgentDefinitionTransformer::resolveJobWorkerElement)
        .filter(Objects::nonNull)
        .filter(ExecutableJobWorkerElement::isAgentDefinition)
        .forEach(element -> createAgentDefinition(deployment, processMetadata, element));
  }

  /**
   * Resolves {@code element} to the {@link ExecutableJobWorkerElement} it should be checked for an
   * agent marker on, unwrapping a multi-instance body to its inner activity first.
   *
   * @return the resolved job-worker element, or {@code null} if neither {@code element} nor (for a
   *     multi-instance body) its inner activity is a job-worker element
   */
  private static ExecutableJobWorkerElement resolveJobWorkerElement(
      final ExecutableFlowElement element) {
    final var activity =
        element instanceof final ExecutableMultiInstanceBody multiInstanceBody
            ? multiInstanceBody.getInnerActivity()
            : element;
    return activity instanceof final ExecutableJobWorkerElement jobWorkerElement
        ? jobWorkerElement
        : null;
  }

  private void createAgentDefinition(
      final DeploymentRecord deployment,
      final ProcessMetadata processMetadata,
      final ExecutableJobWorkerElement element) {
    final var agentDefinitionKey = keyGenerator.nextKey();
    // elements are not required to carry a name in BPMN, so fall back to the element id, which is
    // always present, to still give the AgentDefinition a meaningful name
    final var name =
        element.getName().capacity() > 0
            ? BufferUtil.bufferAsString(element.getName())
            : BufferUtil.bufferAsString(element.getId());
    final var agentDefinition =
        deployment
            .agentDefinitionsMetadata()
            .add()
            .setAgentDefinitionKey(agentDefinitionKey)
            .setAgentType(element.getAgentDefinitionType())
            .setName(name)
            .setElementId(BufferUtil.bufferAsString(element.getId()))
            .setBpmnProcessId(processMetadata.getBpmnProcessId())
            .setProcessDefinitionKey(processMetadata.getKey())
            .setProcessDefinitionVersion(processMetadata.getVersion())
            .setProcessDefinitionVersionTag(processMetadata.getVersionTag())
            .setTenantId(deployment.getTenantId());

    // ArrayValue#add() allocates a fresh, independent instance for every element (unlike, say, a
    // reused cursor), so it is safe to hand this same instance to the state writer directly.
    stateWriter.appendFollowUpEvent(
        agentDefinitionKey, AgentDefinitionIntent.CREATED, agentDefinition);
  }
}
