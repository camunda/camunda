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

    if (configuration.index.createTemplate) {
      createComponentTemplate();
      createIndexTemplate(version);
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

  private void createIndexTemplate(final String version) {
    if (!client.putIndexTemplate(version)) {
      LOG.warn(
          "Failed to acknowledge the creation or update of the combined index template for version {}",
          version);
    }
  }
}
