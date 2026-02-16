/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.opensearch;

import io.camunda.zeebe.exporter.opensearch.OpensearchExporterConfiguration.IndexConfiguration;
import io.camunda.zeebe.protocol.record.ValueType;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.HashSet;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OpensearchExporterSchemaManager {
  private static final Logger LOG =
      LoggerFactory.getLogger(OpensearchExporterSchemaManager.class.getPackageName());
  private final OpensearchClient client;
  private final OpensearchExporterConfiguration configuration;
  private final Set<String> indexTemplatesCreated = new HashSet<>();

  public OpensearchExporterSchemaManager(
      final OpensearchClient client, final OpensearchExporterConfiguration configuration) {
    this.client = client;
    this.configuration = configuration;
  }

  public OpensearchExporterSchemaManager(final OpensearchExporterConfiguration configuration) {
    this(new OpensearchClient(configuration, new SimpleMeterRegistry()), configuration);
  }

  public void createSchema(final String brokerVersion) {
    if (!indexTemplatesCreated.contains(brokerVersion)) {
      createIndexTemplates(brokerVersion);
      updateRetentionPolicyForExistingIndices();
    }
  }

  private void createIndexTemplates(final String version) {
    if (configuration.retention.isEnabled()) {
      createIndexStateManagementPolicy();
    } else {
      deleteIndexStateManagementPolicy();
    }

    final IndexConfiguration index = configuration.index;

    if (index.createTemplate) {
      createComponentTemplate();

      if (index.deployment) {
        createValueIndexTemplate(ValueType.DEPLOYMENT, version);
      }
      if (index.process) {
        createValueIndexTemplate(ValueType.PROCESS, version);
      }
      if (index.error) {
        createValueIndexTemplate(ValueType.ERROR, version);
      }
      if (index.incident) {
        createValueIndexTemplate(ValueType.INCIDENT, version);
      }
      if (index.job) {
        createValueIndexTemplate(ValueType.JOB, version);
      }
      if (index.jobBatch) {
        createValueIndexTemplate(ValueType.JOB_BATCH, version);
      }
      if (index.message) {
        createValueIndexTemplate(ValueType.MESSAGE, version);
      }
      if (index.messageBatch) {
        createValueIndexTemplate(ValueType.MESSAGE_BATCH, version);
      }
      if (index.messageSubscription) {
        createValueIndexTemplate(ValueType.MESSAGE_SUBSCRIPTION, version);
      }
      if (index.variable) {
        createValueIndexTemplate(ValueType.VARIABLE, version);
      }
      if (index.variableDocument) {
        createValueIndexTemplate(ValueType.VARIABLE_DOCUMENT, version);
      }
      if (index.processInstance) {
        createValueIndexTemplate(ValueType.PROCESS_INSTANCE, version);
      }
      if (index.processInstanceBatch) {
        createValueIndexTemplate(ValueType.PROCESS_INSTANCE_BATCH, version);
      }
      if (index.processInstanceCreation) {
        createValueIndexTemplate(ValueType.PROCESS_INSTANCE_CREATION, version);
      }
      if (index.processInstanceModification) {
        createValueIndexTemplate(ValueType.PROCESS_INSTANCE_MODIFICATION, version);
      }
      if (index.processMessageSubscription) {
        createValueIndexTemplate(ValueType.PROCESS_MESSAGE_SUBSCRIPTION, version);
      }
      if (index.adHocSubProcessInstruction) {
        createValueIndexTemplate(ValueType.AD_HOC_SUB_PROCESS_INSTRUCTION, version);
      }
      if (index.decisionRequirements) {
        createValueIndexTemplate(ValueType.DECISION_REQUIREMENTS, version);
      }
      if (index.decision) {
        createValueIndexTemplate(ValueType.DECISION, version);
      }
      if (index.decisionEvaluation) {
        createValueIndexTemplate(ValueType.DECISION_EVALUATION, version);
      }
      if (index.checkpoint) {
        createValueIndexTemplate(ValueType.CHECKPOINT, version);
      }
      if (index.timer) {
        createValueIndexTemplate(ValueType.TIMER, version);
      }
      if (index.messageStartEventSubscription) {
        createValueIndexTemplate(ValueType.MESSAGE_START_EVENT_SUBSCRIPTION, version);
      }
      if (index.processEvent) {
        createValueIndexTemplate(ValueType.PROCESS_EVENT, version);
      }
      if (index.deploymentDistribution) {
        createValueIndexTemplate(ValueType.DEPLOYMENT_DISTRIBUTION, version);
      }
      if (index.escalation) {
        createValueIndexTemplate(ValueType.ESCALATION, version);
      }
      if (index.signal) {
        createValueIndexTemplate(ValueType.SIGNAL, version);
      }
      if (index.signalSubscription) {
        createValueIndexTemplate(ValueType.SIGNAL_SUBSCRIPTION, version);
      }
      if (index.resourceDeletion) {
        createValueIndexTemplate(ValueType.RESOURCE_DELETION, version);
      }
      if (index.commandDistribution) {
        createValueIndexTemplate(ValueType.COMMAND_DISTRIBUTION, version);
      }
      if (index.form) {
        createValueIndexTemplate(ValueType.FORM, version);
      }
      if (index.userTask) {
        createValueIndexTemplate(ValueType.USER_TASK, version);
      }
      if (index.processInstanceMigration) {
        createValueIndexTemplate(ValueType.PROCESS_INSTANCE_MIGRATION, version);
      }
      if (index.compensationSubscription) {
        createValueIndexTemplate(ValueType.COMPENSATION_SUBSCRIPTION, version);
      }
      if (index.messageCorrelation) {
        createValueIndexTemplate(ValueType.MESSAGE_CORRELATION, version);
      }
      if (index.asyncRequest) {
        createValueIndexTemplate(ValueType.ASYNC_REQUEST, version);
      }
      if (index.runtimeInstruction) {
        createValueIndexTemplate(ValueType.RUNTIME_INSTRUCTION, version);
      }
      if (index.clusterVariable) {
        createValueIndexTemplate(ValueType.CLUSTER_VARIABLE, version);
      }
      if (index.conditionalSubscription) {
        createValueIndexTemplate(ValueType.CONDITIONAL_SUBSCRIPTION, version);
      }
      if (index.conditionalEvaluation) {
        createValueIndexTemplate(ValueType.CONDITIONAL_EVALUATION, version);
      }
      if (index.globalListenerBatch) {
        createValueIndexTemplate(ValueType.GLOBAL_LISTENER_BATCH, version);
      }
      if (index.globalListener) {
        createValueIndexTemplate(ValueType.GLOBAL_LISTENER, version);
      }
    }

    indexTemplatesCreated.add(version);
  }

  private void createIndexStateManagementPolicy() {
    final var policyOptional = client.getIndexStateManagementPolicy();

    // Create the policy if it doesn't exist yet
    if (policyOptional.isEmpty()) {
      if (!client.createIndexStateManagementPolicy()) {
        LOG.warn("Failed to acknowledge the creation of the Index State Management Policy");
      }
      return;
    }

    // Update the policy if it exists and is different from the configuration
    final var policy = policyOptional.get();
    if (!policy.equalsConfiguration(configuration)) {
      if (!client.updateIndexStateManagementPolicy(policy.seqNo(), policy.primaryTerm())) {
        LOG.warn("Failed to acknowledge the update of the Index State Management Policy");
      }
    }
  }

  private void deleteIndexStateManagementPolicy() {
    final var policyOptional = client.getIndexStateManagementPolicy();
    if (policyOptional.isEmpty()) {
      return;
    }

    if (!client.deleteIndexStateManagementPolicy()) {
      LOG.warn("Failed to acknowledge the deletion of the Index State Management Policy");
    }
  }

  private void createComponentTemplate() {
    if (!client.putComponentTemplate()) {
      LOG.warn("Failed to acknowledge the creation or update of the component template");
    }
  }

  private void createValueIndexTemplate(final ValueType valueType, final String version) {
    if (!client.putIndexTemplate(valueType, version)) {
      LOG.warn(
          "Failed to acknowledge the creation or update of the index template for value type {}",
          valueType);
    }
  }

  private void updateRetentionPolicyForExistingIndices() {
    final boolean successful;
    if (configuration.retention.isEnabled()) {
      successful = client.bulkAddISMPolicyToAllZeebeIndices();
    } else {
      successful = client.bulkRemoveISMPolicyFromAllZeebeIndices();
    }

    if (!successful) {
      LOG.warn("Failed to acknowledge the update of retention policy for existing indices");
    }
  }
}
