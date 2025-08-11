/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.schema;

import static io.camunda.tasklist.property.TasklistProperties.ELASTIC_SEARCH;

import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.webapps.schema.descriptors.index.FormIndex;
import io.camunda.webapps.schema.descriptors.index.ProcessIndex;
import io.camunda.webapps.schema.descriptors.index.TasklistImportPositionIndex;
import io.camunda.webapps.schema.descriptors.index.TasklistMetricIndex;
import io.camunda.webapps.schema.descriptors.index.UsageMetricTUIndex;
import io.camunda.webapps.schema.descriptors.template.DraftTaskVariableTemplate;
import io.camunda.webapps.schema.descriptors.template.FlowNodeInstanceTemplate;
import io.camunda.webapps.schema.descriptors.template.SnapshotTaskVariableTemplate;
import io.camunda.webapps.schema.descriptors.template.TaskTemplate;
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
        getIndexPrefix(tasklistProperties), isElasticsearch(tasklistProperties));
  }

  @Bean
  public FormIndex formIndex() {
    return new FormIndex(getIndexPrefix(tasklistProperties), isElasticsearch(tasklistProperties));
  }

  @Bean
  public TasklistMetricIndex tasklistMetricIndex() {
    return new TasklistMetricIndex(
        getIndexPrefix(tasklistProperties), isElasticsearch(tasklistProperties));
  }

  @Bean
  public UsageMetricTUIndex usageMetricTUIndex() {
    return new UsageMetricTUIndex(
        getIndexPrefix(tasklistProperties), isElasticsearch(tasklistProperties));
  }

  @Bean("tasklistSnapshotTaskVariableTemplate")
  public SnapshotTaskVariableTemplate snapshotTaskVariableTemplate() {
    return new SnapshotTaskVariableTemplate(
        getIndexPrefix(tasklistProperties), isElasticsearch(tasklistProperties));
  }

  @Bean
  public TaskTemplate taskTemplate() {
    return new TaskTemplate(
        getIndexPrefix(tasklistProperties), isElasticsearch(tasklistProperties));
  }

  @Bean("tasklistVariableTemplate")
  public VariableTemplate tasklistVariableTemplate() {
    return new VariableTemplate(
        getIndexPrefix(tasklistProperties), isElasticsearch(tasklistProperties));
  }

  @Bean("tasklistFlowNodeInstanceTemplate")
  public FlowNodeInstanceTemplate flowNodeInstanceTemplate() {
    return new FlowNodeInstanceTemplate(
        getIndexPrefix(tasklistProperties), isElasticsearch(tasklistProperties));
  }

  @Bean
  public TasklistImportPositionIndex tasklistImportPositionIndex() {
    return new TasklistImportPositionIndex(
        getIndexPrefix(tasklistProperties), isElasticsearch(tasklistProperties));
  }

  @Bean("tasklistProcessIndex")
  public ProcessIndex processIndex() {
    return new ProcessIndex(
        getIndexPrefix(tasklistProperties), isElasticsearch(tasklistProperties));
  }

  private boolean isElasticsearch(final TasklistProperties tasklistProperties) {
    return ELASTIC_SEARCH.equals(tasklistProperties.getDatabase());
  }

  private String getIndexPrefix(final TasklistProperties tasklistProperties) {
    return isElasticsearch(tasklistProperties)
        ? tasklistProperties.getElasticsearch().getIndexPrefix()
        : tasklistProperties.getOpenSearch().getIndexPrefix();
  }
}
