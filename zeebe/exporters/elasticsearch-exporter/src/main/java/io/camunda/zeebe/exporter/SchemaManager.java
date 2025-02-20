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
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SchemaManager {
  private static final Logger LOG = LoggerFactory.getLogger(SchemaManager.class.getPackageName());
  private final ElasticsearchClient client;
  private final ElasticsearchExporterConfiguration configuration;
  private boolean indexTemplatesCreated = false;

  /**
   * Creates a new schema manager, and it is to be used by the exporter to manage the Elasticsearch
   * schema.
   *
   * @param client the Elasticsearch client
   * @param configuration the exporter configuration
   */
  public SchemaManager(
      final ElasticsearchClient client, final ElasticsearchExporterConfiguration configuration) {
    this.client = client;
    this.configuration = configuration;
  }

  /**
   * Creates a new schema manager, and is only used by the StandaloneSchemaManager.
   *
   * @param configuration the exporter configuration
   */
  public SchemaManager(final ElasticsearchExporterConfiguration configuration) {
    this.configuration = configuration;
    final MeterRegistry registry = new SimpleMeterRegistry();
    client = new ElasticsearchClient(configuration, registry);
  }

  public void createSchema() {
    if (!indexTemplatesCreated) {
      createIndexTemplates();
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

  private void createIndexTemplates() {
    if (configuration.retention.isEnabled()) {
      createIndexLifecycleManagementPolicy();
    }

    final IndexConfiguration index = configuration.index;

    if (index.createTemplate) {
      createComponentTemplate();

      if (index.deployment) {
        createValueIndexTemplate(ValueType.DEPLOYMENT);
      }
      if (index.process) {
        createValueIndexTemplate(ValueType.PROCESS);
      }
      if (index.error) {
        createValueIndexTemplate(ValueType.ERROR);
      }
      if (index.incident) {
        createValueIndexTemplate(ValueType.INCIDENT);
      }
      if (index.job) {
        createValueIndexTemplate(ValueType.JOB);
      }
      if (index.jobBatch) {
        createValueIndexTemplate(ValueType.JOB_BATCH);
      }
      if (index.message) {
        createValueIndexTemplate(ValueType.MESSAGE);
      }
      if (index.messageBatch) {
        createValueIndexTemplate(ValueType.MESSAGE_BATCH);
      }
      if (index.messageSubscription) {
        createValueIndexTemplate(ValueType.MESSAGE_SUBSCRIPTION);
      }
      if (index.variable) {
        createValueIndexTemplate(ValueType.VARIABLE);
      }
      if (index.variableDocument) {
        createValueIndexTemplate(ValueType.VARIABLE_DOCUMENT);
      }
      if (index.processInstance) {
        createValueIndexTemplate(ValueType.PROCESS_INSTANCE);
      }
      if (index.processInstanceBatch) {
        createValueIndexTemplate(ValueType.PROCESS_INSTANCE_BATCH);
      }
      if (index.processInstanceCreation) {
        createValueIndexTemplate(ValueType.PROCESS_INSTANCE_CREATION);
      }
      if (index.processInstanceModification) {
        createValueIndexTemplate(ValueType.PROCESS_INSTANCE_MODIFICATION);
      }
      if (index.processMessageSubscription) {
        createValueIndexTemplate(ValueType.PROCESS_MESSAGE_SUBSCRIPTION);
      }
      if (index.decisionRequirements) {
        createValueIndexTemplate(ValueType.DECISION_REQUIREMENTS);
      }
      if (index.decision) {
        createValueIndexTemplate(ValueType.DECISION);
      }
      if (index.decisionEvaluation) {
        createValueIndexTemplate(ValueType.DECISION_EVALUATION);
      }
      if (index.checkpoint) {
        createValueIndexTemplate(ValueType.CHECKPOINT);
      }
      if (index.timer) {
        createValueIndexTemplate(ValueType.TIMER);
      }
      if (index.messageStartEventSubscription) {
        createValueIndexTemplate(ValueType.MESSAGE_START_EVENT_SUBSCRIPTION);
      }
      if (index.processEvent) {
        createValueIndexTemplate(ValueType.PROCESS_EVENT);
      }
      if (index.deploymentDistribution) {
        createValueIndexTemplate(ValueType.DEPLOYMENT_DISTRIBUTION);
      }
      if (index.escalation) {
        createValueIndexTemplate(ValueType.ESCALATION);
      }
      if (index.signal) {
        createValueIndexTemplate(ValueType.SIGNAL);
      }
      if (index.signalSubscription) {
        createValueIndexTemplate(ValueType.SIGNAL_SUBSCRIPTION);
      }
      if (index.resourceDeletion) {
        createValueIndexTemplate(ValueType.RESOURCE_DELETION);
      }
      if (index.commandDistribution) {
        createValueIndexTemplate(ValueType.COMMAND_DISTRIBUTION);
      }
      if (index.form) {
        createValueIndexTemplate(ValueType.FORM);
      }
      if (index.userTask) {
        createValueIndexTemplate(ValueType.USER_TASK);
      }
      if (index.processInstanceMigration) {
        createValueIndexTemplate(ValueType.PROCESS_INSTANCE_MIGRATION);
      }
      if (index.compensationSubscription) {
        createValueIndexTemplate(ValueType.COMPENSATION_SUBSCRIPTION);
      }
      if (index.messageCorrelation) {
        createValueIndexTemplate(ValueType.MESSAGE_CORRELATION);
      }
      if (index.user) {
        createValueIndexTemplate(ValueType.USER);
      }

      if (index.authorization) {
        createValueIndexTemplate(ValueType.AUTHORIZATION);
      }
    }

    indexTemplatesCreated = true;
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

  private void createValueIndexTemplate(final ValueType valueType) {
    if (!client.putIndexTemplate(valueType)) {
      LOG.warn(
          "Failed to acknowledge the creation or update of the index template for value type {}",
          valueType);
    }
  }
}
