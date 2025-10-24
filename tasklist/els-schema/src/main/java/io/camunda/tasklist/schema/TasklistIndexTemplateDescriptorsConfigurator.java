/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.schema;

import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.webapps.schema.descriptors.index.FormIndex;
import io.camunda.webapps.schema.descriptors.index.ProcessIndex;
import io.camunda.webapps.schema.descriptors.index.TasklistMetricIndex;
import io.camunda.webapps.schema.descriptors.template.DraftTaskVariableTemplate;
import io.camunda.webapps.schema.descriptors.template.FlowNodeInstanceTemplate;
import io.camunda.webapps.schema.descriptors.template.SnapshotTaskVariableTemplate;
import io.camunda.webapps.schema.descriptors.template.TaskTemplate;
import io.camunda.webapps.schema.descriptors.template.UsageMetricTUTemplate;
import io.camunda.webapps.schema.descriptors.template.VariableTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("!test")
public class TasklistIndexTemplateDescriptorsConfigurator {

  @Autowired private TasklistProperties tasklistProperties;

  @Bean
  public DraftTaskVariableTemplate draftTaskVariableTemplate() {
    return new DraftTaskVariableTemplate(
        tasklistProperties.getIndexPrefix(), tasklistProperties.isElasticsearchDB());
  }

  @Bean
  public FormIndex formIndex() {
    return new FormIndex(
        tasklistProperties.getIndexPrefix(), tasklistProperties.isElasticsearchDB());
  }

  @Bean
  public TasklistMetricIndex tasklistMetricIndex() {
    return new TasklistMetricIndex(
        tasklistProperties.getIndexPrefix(), tasklistProperties.isElasticsearchDB());
  }

  @Bean
  public UsageMetricTUTemplate usageMetricTUTemplate() {
    return new UsageMetricTUTemplate(
        tasklistProperties.getIndexPrefix(), tasklistProperties.isElasticsearchDB());
  }

  @Bean
  @Profile("!operate")
  public SnapshotTaskVariableTemplate getSnapshotTaskVariableTemplate() {
    return new SnapshotTaskVariableTemplate(
        tasklistProperties.getIndexPrefix(), tasklistProperties.isElasticsearchDB());
  }

  @Bean
  @Profile("!operate")
  public TaskTemplate getTaskTemplate() {
    return new TaskTemplate(
        tasklistProperties.getIndexPrefix(), tasklistProperties.isElasticsearchDB());
  }

  @Bean
  @Profile("!operate")
  public VariableTemplate getVariableTemplate() {
    return new VariableTemplate(
        tasklistProperties.getIndexPrefix(), tasklistProperties.isElasticsearchDB());
  }

  @Bean
  @Profile("!operate")
  public FlowNodeInstanceTemplate getFlowNodeInstanceTemplate() {
    return new FlowNodeInstanceTemplate(
        tasklistProperties.getIndexPrefix(), tasklistProperties.isElasticsearchDB());
  }

  @Bean
  @Profile("!operate")
  public ProcessIndex getProcessIndex() {
    return new ProcessIndex(
        tasklistProperties.getIndexPrefix(), tasklistProperties.isElasticsearchDB());
  }
}
