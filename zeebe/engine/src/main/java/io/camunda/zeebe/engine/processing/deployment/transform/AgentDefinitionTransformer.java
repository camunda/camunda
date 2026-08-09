/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.deployment.transform;

import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableJobWorkerElement;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableProcess;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import io.camunda.zeebe.protocol.impl.record.value.deployment.ProcessMetadata;
import io.camunda.zeebe.protocol.record.intent.AgentDefinitionIntent;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import io.camunda.zeebe.util.buffer.BufferUtil;

/**
 * Mints {@code AgentDefinition} records for elements carrying a recognized agent marker (explicit
 * {@code zeebe:agentDefinition}, or a {@code zeebe:modelerTemplate} fallback — see {@code
 * AgentElementTypeTransformer}) at deploy time.
 */
final class AgentDefinitionTransformer {

  private final KeyGenerator keyGenerator;
  private final StateWriter stateWriter;

  AgentDefinitionTransformer(final KeyGenerator keyGenerator, final StateWriter stateWriter) {
    this.keyGenerator = keyGenerator;
    this.stateWriter = stateWriter;
  }

  /**
   * Mints an {@code AgentDefinition} for every element of {@code process} for which {@link
   * ExecutableJobWorkerElement#isAgentDefinition()} is {@code true}, appending each to {@code
   * deployment.agentDefinitionsMetadata()} and emitting an {@code AgentDefinition:CREATED}
   * follow-up event keyed with a freshly minted {@code agentDefinitionKey}.
   *
   * @param deployment the deployment record to append minted agent definitions to
   * @param process the executable process to scan for agent-marked elements
   * @param processMetadata the (already finalized) metadata of the process version these agent
   *     definitions belong to
   */
  void writeRecords(
      final DeploymentRecord deployment,
      final ExecutableProcess process,
      final ProcessMetadata processMetadata) {
    process.getFlowElements().stream()
        .filter(ExecutableJobWorkerElement.class::isInstance)
        .map(ExecutableJobWorkerElement.class::cast)
        .filter(ExecutableJobWorkerElement::isAgentDefinition)
        .forEach(element -> mintAgentDefinition(deployment, processMetadata, element));
  }

  private void mintAgentDefinition(
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
