/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.qa.util;

import static io.camunda.tasklist.property.TasklistProperties.ELASTIC_SEARCH;

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
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("test")
public class TasklistIndexTemplateDescriptorsConfigurator {

  @Bean
  public DraftTaskVariableTemplate draftTaskVariableTemplate(
      final TasklistProperties tasklistProperties,
      final TasklistIndexPrefixHolder indexPrefixHolder) {
    return new DraftTaskVariableTemplate(
        getIndexPrefix(tasklistProperties), isElasticsearch(tasklistProperties)) {
      @Override
      public String getIndexPrefix() {
        return indexPrefixHolder.getIndexPrefix();
      }
    };
  }

  @Bean
  public FormIndex formIndex(
      final TasklistProperties tasklistProperties,
      final TasklistIndexPrefixHolder indexPrefixHolder) {
    return new FormIndex(getIndexPrefix(tasklistProperties), isElasticsearch(tasklistProperties)) {
      @Override
      public String getIndexPrefix() {
        return indexPrefixHolder.getIndexPrefix();
      }
    };
  }

  @Bean
  public TasklistMetricIndex tasklistMetricIndex(
      final TasklistProperties tasklistProperties,
      final TasklistIndexPrefixHolder indexPrefixHolder) {
    return new TasklistMetricIndex(
        getIndexPrefix(tasklistProperties), isElasticsearch(tasklistProperties)) {
      @Override
      public String getIndexPrefix() {
        return indexPrefixHolder.getIndexPrefix();
      }
    };
  }

  @Bean
  public UsageMetricTUTemplate usageMetricTUTemplate(
      final TasklistProperties tasklistProperties,
      final TasklistIndexPrefixHolder indexPrefixHolder) {
    return new UsageMetricTUTemplate(
        getIndexPrefix(tasklistProperties), isElasticsearch(tasklistProperties)) {
      @Override
      public String getIndexPrefix() {
        return indexPrefixHolder.getIndexPrefix();
      }
    };
  }

  @Bean
  public SnapshotTaskVariableTemplate snapshotTaskVariableTemplate(
      final TasklistProperties tasklistProperties,
      final TasklistIndexPrefixHolder indexPrefixHolder) {
    return new SnapshotTaskVariableTemplate(
        getIndexPrefix(tasklistProperties), isElasticsearch(tasklistProperties)) {
      @Override
      public String getIndexPrefix() {
        return indexPrefixHolder.getIndexPrefix();
      }
    };
  }

  @Bean
  public TaskTemplate taskTemplate(
      final TasklistProperties tasklistProperties,
      final TasklistIndexPrefixHolder indexPrefixHolder) {
    return new TaskTemplate(
        getIndexPrefix(tasklistProperties), isElasticsearch(tasklistProperties)) {
      @Override
      public String getIndexPrefix() {
        return indexPrefixHolder.getIndexPrefix();
      }
    };
  }

  @Bean
  public VariableTemplate variableTemplate(
      final TasklistProperties tasklistProperties,
      final TasklistIndexPrefixHolder indexPrefixHolder) {
    return new VariableTemplate(
        getIndexPrefix(tasklistProperties), isElasticsearch(tasklistProperties)) {
      @Override
      public String getIndexPrefix() {
        return indexPrefixHolder.getIndexPrefix();
      }
    };
  }

  @Bean
  public FlowNodeInstanceTemplate flowNodeInstanceTemplate(
      final TasklistProperties tasklistProperties,
      final TasklistIndexPrefixHolder indexPrefixHolder) {
    return new FlowNodeInstanceTemplate(
        getIndexPrefix(tasklistProperties), isElasticsearch(tasklistProperties)) {
      @Override
      public String getIndexPrefix() {
        return indexPrefixHolder.getIndexPrefix();
      }
    };
  }

  @Bean
  public ProcessIndex processIndex(
      final TasklistProperties tasklistProperties,
      final TasklistIndexPrefixHolder indexPrefixHolder) {
    return new ProcessIndex(
        getIndexPrefix(tasklistProperties), isElasticsearch(tasklistProperties)) {
      @Override
      public String getIndexPrefix() {
        return indexPrefixHolder.getIndexPrefix();
      }
    };
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
