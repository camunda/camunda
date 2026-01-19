/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.qa.backup.generator;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.Script;
import co.elastic.clients.elasticsearch._types.ScriptLanguage;
import co.elastic.clients.elasticsearch._types.query_dsl.MatchAllQuery;
import io.camunda.tasklist.data.conditionals.ElasticSearchCondition;
import io.camunda.tasklist.qa.backup.BackupRestoreTestContext;
import io.camunda.webapps.schema.descriptors.template.TaskTemplate;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(ElasticSearchCondition.class)
public class BackupRestoreDataGeneratorElasticSearch extends AbstractBackupRestoreDataGenerator {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(BackupRestoreDataGeneratorElasticSearch.class);

  private ElasticsearchClient esClient;

  @Override
  protected void initClient(final BackupRestoreTestContext testContext) {
    esClient = testContext.getEsClient();
  }

  @Override
  protected void refreshIndices() {
    try {
      esClient.indices().refresh(r -> r.index(indexPrefix + "-tasklist-*"));
    } catch (final IOException e) {
      LOGGER.error("Error in refreshing ElasticSearch indices", e);
    }
  }

  @Override
  protected void claimAllTasks() {
    final Script updateScript =
        Script.of(s -> s.lang(ScriptLanguage.Painless).source("ctx._source.assignee = 'demo'"));
    try {
      esClient.updateByQuery(
          qr ->
              qr.index(getMainIndexNameFor(TaskTemplate.INDEX_NAME))
                  .query(new MatchAllQuery.Builder().build()._toQuery())
                  .script(updateScript)
                  .refresh(true));
    } catch (final ElasticsearchException | IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  protected long countEntitiesForAlias(final String alias) throws IOException {
    return esClient.count(c -> c.index(alias)).count();
  }
}
