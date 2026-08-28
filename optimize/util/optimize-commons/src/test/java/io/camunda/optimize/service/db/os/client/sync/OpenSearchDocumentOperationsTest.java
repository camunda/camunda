/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.os.client.sync;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.optimize.service.db.schema.OptimizeIndexNameService;
import io.camunda.optimize.service.exceptions.OptimizeByQueryFailureException;
import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.BulkByScrollFailure;
import org.opensearch.client.opensearch._types.Script;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.UpdateByQueryRequest;
import org.opensearch.client.opensearch.core.UpdateByQueryResponse;

@ExtendWith(MockitoExtension.class)
class OpenSearchDocumentOperationsTest {

  private OpenSearchClient openSearchClient;
  private OpenSearchDocumentOperations documentOperations;

  @BeforeEach
  void init() {
    openSearchClient = mock(OpenSearchClient.class);
    final OptimizeIndexNameService indexNameService = mock(OptimizeIndexNameService.class);
    when(indexNameService.getOptimizeIndexAliasForIndex(any(String.class)))
        .thenAnswer(i -> i.getArgument(0));
    documentOperations = new OpenSearchDocumentOperations(openSearchClient, indexNameService);
  }

  @Test
  void shouldNotFailWhenVersionConflictsAreProceededByDefault() throws IOException {
    // given
    when(openSearchClient.updateByQuery(any(UpdateByQueryRequest.class)))
        .thenReturn(UpdateByQueryResponse.of(b -> b.updated(0L).timedOut(false)));
    final Query query = Query.of(q -> q.matchAll(m -> m));
    final Script script = mock(Script.class);

    // when / then
    documentOperations.updateByQuery("test-index", query, script);
  }

  @Test
  void shouldThrowWhenAbortedUpdateReportsAVersionConflictFailure() throws IOException {
    // given
    final BulkByScrollFailure conflictFailure =
        BulkByScrollFailure.of(
            f ->
                f.id("report-1")
                    .index("test-index")
                    .cause(c -> c.type("version_conflict_engine_exception").reason("conflict"))
                    .status(409));
    when(openSearchClient.updateByQuery(any(UpdateByQueryRequest.class)))
        .thenReturn(
            UpdateByQueryResponse.of(b -> b.updated(0L).timedOut(false).failures(conflictFailure)));
    final Query query = Query.of(q -> q.matchAll(m -> m));
    final Script script = mock(Script.class);

    // when / then
    assertThatThrownBy(() -> documentOperations.updateByQuery("test-index", query, script, true))
        .isInstanceOf(OptimizeByQueryFailureException.class);
  }
}
