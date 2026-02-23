/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter;

import io.camunda.zeebe.exporter.ElasticsearchExporterConfiguration.IndexConfiguration;
import io.camunda.zeebe.protocol.record.ValueType;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.HashSet;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ElasticsearchExporterSchemaManager {

  private static final Logger LOG =
      LoggerFactory.getLogger(ElasticsearchExporterSchemaManager.class.getPackageName());
  private final ElasticsearchExporterClient client;
  private final ElasticsearchExporterConfiguration configuration;
  private final Set<String> indexTemplatesCreated = new HashSet<>();

  /**
   * Creates a new schema manager, and it is to be used by the exporter to manage the Elasticsearch
   * schema.
   *
   * @param client the Elasticsearch client
   * @param configuration the exporter configuration
   */
  public ElasticsearchExporterSchemaManager(
      final ElasticsearchExporterClient client,
      final ElasticsearchExporterConfiguration configuration) {
    this.client = client;
    this.configuration = configuration;
  }

  /**
   * Creates a new schema manager, and is only used by the StandaloneSchemaManager.
   *
   * @param configuration the exporter configuration
   */
  public ElasticsearchExporterSchemaManager(
      final ElasticsearchExporterConfiguration configuration) {
    this(new ElasticsearchExporterClient(configuration, new SimpleMeterRegistry()), configuration);
  }

  public void createSchema(final String brokerVersion) {
    if (!indexTemplatesCreated.contains(brokerVersion)) {
      createIndexTemplates(brokerVersion);
      updateRetentionPolicyForExistingIndices();
    }
  }

  private void updateRetentionPolicyForExistingIndices() {
    final boolean acknowledged;
    if (configuration.retention.isEnabled()) {
      acknowledged = client.bulkPutIndexLifecycleSettings(configuration.retention.getPolicyName());
    } else {
      acknowledged = client.bulkPutIndexLifecycleSettings(null);
    }

    if (!acknowledged) {
      LOG.warn("Failed to acknowledge the the update of retention policy for existing indices");
    }
  }

  private void createIndexTemplates(final String version) {
    if (configuration.retention.isEnabled()) {
      createIndexLifecycleManagementPolicy();
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

  private void createIndexLifecycleManagementPolicy() {
    if (!client.putIndexLifecycleManagementPolicy()) {
      LOG.warn(
          "Failed to acknowledge the creation or update of the Index Lifecycle Management Policy");
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
}
