/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.qa.backup.generator;

import io.camunda.tasklist.data.conditionals.ElasticSearchCondition;
import io.camunda.tasklist.qa.backup.BackupRestoreTestContext;
import io.camunda.tasklist.schema.v86.templates.TaskTemplate;
import java.io.IOException;
import java.util.Collections;
import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.action.admin.indices.refresh.RefreshRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.reindex.UpdateByQueryRequest;
import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(ElasticSearchCondition.class)
public class BackupRestoreDataGeneratorElasticSearch extends AbstractBackupRestoreDataGenerator {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(BackupRestoreDataGeneratorElasticSearch.class);

  private RestHighLevelClient esClient;

  @Override
  protected void initClient(BackupRestoreTestContext testContext) {
    this.esClient = testContext.getEsClient();
  }

  @Override
  protected void refreshIndices() {
    try {
      esClient.indices().refresh(new RefreshRequest("tasklist-*"), RequestOptions.DEFAULT);
    } catch (IOException e) {
      LOGGER.error("Error in refreshing ElasticSearch indices", e);
    }
  }

  @Override
  protected void claimAllTasks() {
    final UpdateByQueryRequest updateRequest =
        new UpdateByQueryRequest(getMainIndexNameFor(TaskTemplate.INDEX_NAME))
            .setQuery(QueryBuilders.matchAllQuery())
            .setScript(
                new Script(
                    ScriptType.INLINE,
                    "painless",
                    "ctx._source.assignee = 'demo'",
                    Collections.emptyMap()))
            .setRefresh(true);
    try {
      esClient.updateByQuery(updateRequest, RequestOptions.DEFAULT);
    } catch (ElasticsearchException | IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  protected long countEntitiesForAlias(String alias) throws IOException {
    final SearchRequest searchRequest = new SearchRequest(alias);
    searchRequest.source().size(1000);
    final SearchResponse searchResponse = esClient.search(searchRequest, RequestOptions.DEFAULT);
    return searchResponse.getHits().getTotalHits().value;
  }
}
