/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.es;

import io.camunda.tasklist.data.conditionals.ElasticSearchCondition;
import io.camunda.tasklist.management.ILMPolicyUpdate;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.schema.v86.manager.ElasticsearchSchemaManager;
import java.io.IOException;
import java.util.Set;
import java.util.regex.Pattern;
import org.elasticsearch.client.indexlifecycle.GetLifecyclePolicyRequest;
import org.elasticsearch.client.indexlifecycle.GetLifecyclePolicyResponse;
import org.elasticsearch.common.settings.Settings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(ElasticSearchCondition.class)
public class ILMPolicyUpdateElasticSearch implements ILMPolicyUpdate {
  private static final String TASKLIST_DELETE_ARCHIVED_INDICES = "tasklist_delete_archived_indices";
  private static final String INDEX_LIFECYCLE_NAME = "index.lifecycle.name";

  private static final Logger LOGGER = LoggerFactory.getLogger(ILMPolicyUpdateElasticSearch.class);

  @Autowired private RetryElasticsearchClient retryElasticsearchClient;

  @Autowired private ElasticsearchSchemaManager schemaManager;

  @Autowired private TasklistProperties tasklistProperties;

  @Override
  public void applyIlmPolicyToAllIndices() throws IOException {
    final String taskListIndexWildCard =
        tasklistProperties.getElasticsearch().getIndexPrefix() + "-*";
    final String archiveTemplatePatterndNameRegex =
        "^"
            + tasklistProperties.getElasticsearch().getIndexPrefix()
            + "-.*-\\d+\\.\\d+\\.\\d+_\\d{4}-\\d{2}-\\d{2}$";
    LOGGER.info("Applying ILM policy to all existent indices");

    final GetLifecyclePolicyResponse policy =
        retryElasticsearchClient.getLifeCyclePolicy(
            new GetLifecyclePolicyRequest(TASKLIST_DELETE_ARCHIVED_INDICES));

    if (policy == null) {
      LOGGER.info("ILM policy not found, creating it");
      schemaManager.createIndexLifeCycles();
    }

    final Pattern indexNamePattern = Pattern.compile(archiveTemplatePatterndNameRegex);

    final Set<String> response = retryElasticsearchClient.getIndexNames(taskListIndexWildCard);
    for (final String indexName : response) {
      if (indexNamePattern.matcher(indexName).matches()) {
        final Settings settings =
            Settings.builder().put(INDEX_LIFECYCLE_NAME, TASKLIST_DELETE_ARCHIVED_INDICES).build();
        retryElasticsearchClient.setIndexSettingsFor(settings, indexName);
      }
    }
  }

  @Override
  public void removeIlmPolicyFromAllIndices() {
    final String taskListIndexWildCard =
        tasklistProperties.getElasticsearch().getIndexPrefix() + "-*";
    final String archiveTemplatePatterndNameRegex =
        "^"
            + tasklistProperties.getElasticsearch().getIndexPrefix()
            + "-.*-\\d+\\.\\d+\\.\\d+_\\d{4}-\\d{2}-\\d{2}$";
    LOGGER.info("Removing ILM policy to all existent indices");
    final Pattern indexNamePattern = Pattern.compile(archiveTemplatePatterndNameRegex);
    final Set<String> response = retryElasticsearchClient.getIndexNames(taskListIndexWildCard);
    for (final String indexName : response) {
      if (indexNamePattern.matcher(indexName).matches()) {
        final Settings settings = Settings.builder().putNull(INDEX_LIFECYCLE_NAME).build();
        retryElasticsearchClient.setIndexSettingsFor(settings, indexName);
      }
    }
  }
}
