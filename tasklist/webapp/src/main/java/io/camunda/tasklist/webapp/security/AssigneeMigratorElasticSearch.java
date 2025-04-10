/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.webapp.security;

import static io.camunda.webapps.schema.descriptors.index.TasklistMetricIndex.VALUE;

import io.camunda.tasklist.data.conditionals.ElasticSearchCondition;
import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.webapps.schema.descriptors.index.TasklistMetricIndex;
import java.io.IOException;
import java.util.Collections;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.reindex.BulkByScrollResponse;
import org.elasticsearch.index.reindex.UpdateByQueryRequest;
import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Conditional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
@Conditional(ElasticSearchCondition.class)
public class AssigneeMigratorElasticSearch implements AssigneeMigrator {

  private static final Logger LOGGER = LoggerFactory.getLogger(AssigneeMigratorElasticSearch.class);

  @Autowired
  @Qualifier("tasklistEsClient")
  private RestHighLevelClient esClient;

  @Autowired private TasklistMetricIndex metricIndex;
  @Autowired private TasklistProperties tasklistProperties;

  @Override
  public void migrateUsageMetrics(final String newAssignee) {
    if (!tasklistProperties.isFixUsernames()) {
      LOGGER.debug("Migration of usernames is disabled.");
      return;
    }
    final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (!(authentication instanceof OldUsernameAware)) {
      LOGGER.debug("No migration of usernames possible.");
      return;
    }
    final OldUsernameAware oldUsernameAware = (OldUsernameAware) authentication;
    final String oldAssignee = oldUsernameAware.getOldName();
    LOGGER.debug("Migrate old assignee {} to new assignee {}", oldAssignee, newAssignee);
    final QueryBuilder oldAssigneeQuery = QueryBuilders.termsQuery(VALUE, oldAssignee);
    final Script updateScript =
        new Script(
            ScriptType.INLINE,
            "painless",
            "ctx._source." + VALUE + " = '" + newAssignee + "'",
            Collections.emptyMap());
    final long migrated =
        updateByQuery(metricIndex.getFullQualifiedName(), oldAssigneeQuery, updateScript);
    if (migrated > 0) {
      LOGGER.info(
          "Migrated {} usage metric entries from old assignee {} to new assignee {}.",
          migrated,
          oldAssignee,
          newAssignee);
    }
  }

  public long updateByQuery(
      final String indexPattern, final QueryBuilder query, final Script updateScript) {
    try {
      final UpdateByQueryRequest request =
          new UpdateByQueryRequest(indexPattern).setQuery(query).setScript(updateScript);
      final BulkByScrollResponse response = esClient.updateByQuery(request, RequestOptions.DEFAULT);
      return response.getTotal();
    } catch (final IOException e) {
      throw new TasklistRuntimeException(
          "Error while trying to update entities for query " + query);
    }
  }
}
