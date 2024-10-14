/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter;

import io.camunda.exporter.config.ExporterConfiguration;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;
import io.camunda.webapps.schema.descriptors.IndexTemplateDescriptor;
import java.util.Set;

/**
 * This is the class where teams should make their components such as handlers, and index/index
 * template descriptors available
 */
public class DefaultExporterResourceProvider implements ExporterResourceProvider {

  private String operateIndexPrefix;

  @Override
  public void init(final ExporterConfiguration configuration) {
    operateIndexPrefix = configuration.getIndex().getPrefix();
  }

  @Override
  public Set<IndexDescriptor> getIndexDescriptors() {
    return Set.of();
    // TODO uncomment this to start creating Operate schema from exporter
    //    return Set.of(
    //        new DecisionIndex(operateIndexPrefix, true),
    //        new DecisionRequirementsIndex(operateIndexPrefix, true),
    //        new MetricIndex(operateIndexPrefix, true),
    //        new ProcessIndex(operateIndexPrefix, true));
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
}
