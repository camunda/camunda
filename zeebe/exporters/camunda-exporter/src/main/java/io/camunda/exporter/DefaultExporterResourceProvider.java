/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter;

import io.camunda.exporter.config.ConnectionTypes;
import io.camunda.exporter.config.ExporterConfiguration;
import io.camunda.exporter.handlers.AuthorizationRecordValueExportHandler;
import io.camunda.exporter.handlers.DecisionHandler;
import io.camunda.exporter.handlers.ExportHandler;
import io.camunda.exporter.handlers.UserRecordValueExportHandler;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;
import io.camunda.webapps.schema.descriptors.IndexTemplateDescriptor;
import io.camunda.webapps.schema.descriptors.operate.index.DecisionIndex;
import io.camunda.webapps.schema.descriptors.operate.index.DecisionRequirementsIndex;
import io.camunda.webapps.schema.descriptors.operate.index.MetricIndex;
import io.camunda.webapps.schema.descriptors.operate.index.ProcessIndex;
import java.util.Set;

/**
 * This is the class where teams should make their components such as handlers, and index/index
 * template descriptors available
 */
public class DefaultExporterResourceProvider implements ExporterResourceProvider {

  private String operateIndexPrefix;
  private boolean isElasticsearch;

  @Override
  public void init(final ExporterConfiguration configuration) {
    operateIndexPrefix = configuration.getIndex().getPrefix();
    isElasticsearch =
        ConnectionTypes.from(configuration.getConnect().getType())
            .equals(ConnectionTypes.ELASTICSEARCH);
  }

  @Override
  public Set<IndexDescriptor> getIndexDescriptors() {
    return Set.of(
        new DecisionIndex(operateIndexPrefix, isElasticsearch),
        new DecisionRequirementsIndex(operateIndexPrefix, isElasticsearch),
        new MetricIndex(operateIndexPrefix, isElasticsearch),
        new ProcessIndex(operateIndexPrefix, isElasticsearch));
  }

  @Override
  public Set<IndexTemplateDescriptor> getIndexTemplateDescriptors() {
    return Set.of();
    // TODO uncomment this to start creating Operate schema from exporter
    //    return Set.of(
    //        new DecisionInstanceTemplate(operateIndexPrefix, true),
    //        new EventTemplate(operateIndexPrefix, true),
    //        new FlowNodeInstanceTemplate(operateIndexPrefix, true),
    //        new IncidentTemplate(operateIndexPrefix, true),
    //        new JobTemplate(operateIndexPrefix, true),
    //        new ListViewTemplate(operateIndexPrefix, true),
    //        new MessageTemplate(operateIndexPrefix, true),
    //        new PostImporterQueueTemplate(operateIndexPrefix, true),
    //        new SequenceFlowTemplate(operateIndexPrefix, true),
    //        new UserTaskTemplate(operateIndexPrefix, true),
    //        new VariableTemplate(operateIndexPrefix, true));
  }

  @Override
  public Set<ExportHandler> getExportHandlers() {
    // Register all handlers here
    return Set.of(
        new UserRecordValueExportHandler(),
        new AuthorizationRecordValueExportHandler(),
        // TODO: reuse DecisionIndex created in getIndexDescriptors
        new DecisionHandler(
            new DecisionIndex(operateIndexPrefix, isElasticsearch).getFullQualifiedName()));
  }
}
