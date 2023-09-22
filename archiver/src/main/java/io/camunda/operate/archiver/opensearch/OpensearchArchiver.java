/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.archiver.opensearch;

import io.camunda.operate.Metrics;
import io.camunda.operate.archiver.AbstractArchiver;
import io.camunda.operate.conditions.OpensearchCondition;
import io.camunda.operate.store.opensearch.client.sync.OpenSearchDocumentOperations;
import io.camunda.operate.store.opensearch.client.sync.RichOpenSearchClient;
import org.opensearch.client.opensearch._types.Conflicts;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static io.camunda.operate.schema.SchemaManager.OPERATE_DELETE_ARCHIVED_INDICES;
import static io.camunda.operate.store.opensearch.dsl.QueryDSL.stringTerms;
import static io.camunda.operate.store.opensearch.dsl.RequestDSL.deleteByQueryRequestBuilder;
import static io.camunda.operate.store.opensearch.dsl.RequestDSL.reindexRequestBuilder;
import static io.camunda.operate.store.opensearch.dsl.RequestDSL.time;
import static io.camunda.operate.util.FutureHelper.withTimer;
import static java.lang.String.format;

@Component
@DependsOn("schemaStartup")
@Conditional(OpensearchCondition.class)
public class OpensearchArchiver extends AbstractArchiver {
  private static final Logger logger = LoggerFactory.getLogger(OpensearchArchiver.class);

  @Autowired
  private RichOpenSearchClient richOpenSearchClient;

  @Autowired
  private Metrics metrics;

  @Override
  protected Logger getLogger() {
    return logger;
  }

  private long getAutoSlices(){
    return operateProperties.getOpensearch().getNumberOfShards();
  }

  @Override
  protected void setIndexLifeCycle(final String destinationIndexName){
    try {
      if ( operateProperties.getArchiver().isIlmEnabled() ) {
        richOpenSearchClient.index().setIndexLifeCycle(destinationIndexName, OPERATE_DELETE_ARCHIVED_INDICES);
      }
    } catch (Exception e){
      logger.warn("Could not set ILM policy {} for index {}: {}", OPERATE_DELETE_ARCHIVED_INDICES, destinationIndexName, e.getMessage());
    }
  }

  @Override
  protected CompletableFuture<Void> deleteDocuments(final String sourceIndexName, final String idFieldName,
      final List<Object> processInstanceKeys) {
    var deleteByQueryRequestBuilder = deleteByQueryRequestBuilder(sourceIndexName)
      .query(stringTerms(idFieldName, processInstanceKeys.stream().map(Object::toString).toList()))
      .waitForCompletion(false)
      .slices(getAutoSlices())
      .conflicts(Conflicts.Proceed);

    return withTimer(metrics.getTimer(Metrics.TIMER_NAME_ARCHIVER_DELETE_QUERY), () ->
      richOpenSearchClient.async().doc().delete(deleteByQueryRequestBuilder, e -> "Failed to delete asynchronously from " + sourceIndexName)
        .thenAccept(response -> richOpenSearchClient.async().task().totalImpactedByTask(response.task(), archiverExecutor))
    );
  }

  @Override
  protected CompletableFuture<Void> reindexDocuments(final String sourceIndexName, final String destinationIndexName,
      final String idFieldName, final List<Object> processInstanceKeys) {
    final String errorMessage = format("Failed to reindex asynchronously from %s to %s!", sourceIndexName, destinationIndexName);
    final Query sourceQuery  = stringTerms(idFieldName, processInstanceKeys.stream().map(Object::toString).toList());
    final var reindexRequest = reindexRequestBuilder(sourceIndexName, sourceQuery, destinationIndexName)
      .waitForCompletion(false)
      .scroll(time(OpenSearchDocumentOperations.INTERNAL_SCROLL_KEEP_ALIVE_MS))
      .slices(getAutoSlices())
      .conflicts(Conflicts.Proceed);

    return withTimer(metrics.getTimer(Metrics.TIMER_NAME_ARCHIVER_REINDEX_QUERY), () ->
      richOpenSearchClient.async().index().reindex(reindexRequest, e -> errorMessage)
        .thenAccept(response -> richOpenSearchClient.async().task().totalImpactedByTask(response.task(), archiverExecutor))
    );
  }
}
