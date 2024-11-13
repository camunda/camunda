/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.qa.backup.generator;

import io.camunda.tasklist.data.conditionals.OpenSearchCondition;
import io.camunda.tasklist.qa.backup.BackupRestoreTestContext;
import io.camunda.tasklist.schema.v86.templates.TaskTemplate;
import java.io.IOException;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.OpenSearchException;
import org.opensearch.client.opensearch._types.query_dsl.MatchAllQuery;
import org.opensearch.client.opensearch.indices.RefreshRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(OpenSearchCondition.class)
public class BackupRestoreDataGeneratorOpenSearch extends AbstractBackupRestoreDataGenerator {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(BackupRestoreDataGeneratorOpenSearch.class);

  private OpenSearchClient openSearchClient;

  @Override
  protected void initClient(BackupRestoreTestContext testContext) {
    this.openSearchClient = testContext.getOsClient();
  }

  @Override
  protected void refreshIndices() {
    try {
      openSearchClient.indices().refresh(RefreshRequest.of(r -> r.index("tasklist-*")));
    } catch (IOException e) {
      LOGGER.error("Error in refreshing OpenSearch indices", e);
    }
  }

  @Override
  protected void claimAllTasks() {
    try {
      openSearchClient.updateByQuery(
          qr ->
              qr.index(getMainIndexNameFor(TaskTemplate.INDEX_NAME))
                  .query(new MatchAllQuery.Builder().build()._toQuery())
                  .script(
                      s ->
                          s.inline(
                              is -> is.lang("painless").source("ctx._source.assignee = 'demo'")))
                  .refresh(true));
    } catch (OpenSearchException | IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  protected long countEntitiesForAlias(String alias) throws IOException {
    return openSearchClient
        .search(sr -> sr.index(alias).size(1000), Void.class)
        .hits()
        .hits()
        .size();
  }
}
