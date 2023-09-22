/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.archiver.elasticsearch;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.Metrics;
import io.camunda.operate.archiver.AbstractArchiver;
import io.camunda.operate.conditions.ElasticsearchCondition;
import io.camunda.operate.util.ElasticsearchUtil;
import io.micrometer.core.instrument.Timer;
import org.elasticsearch.action.admin.indices.settings.put.UpdateSettingsRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.core.TimeValue;
import org.elasticsearch.index.reindex.ReindexRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static io.camunda.operate.schema.SchemaManager.INDEX_LIFECYCLE_NAME;
import static io.camunda.operate.schema.SchemaManager.OPERATE_DELETE_ARCHIVED_INDICES;
import static io.camunda.operate.util.ElasticsearchUtil.deleteAsyncWithConnectionRelease;
import static org.elasticsearch.index.query.QueryBuilders.termsQuery;
import static org.elasticsearch.index.reindex.AbstractBulkByScrollRequest.AUTO_SLICES;

@Component
@DependsOn("schemaStartup")
@Conditional(ElasticsearchCondition.class)
public class ElasticsearchArchiver extends AbstractArchiver {
  public static final int INTERNAL_SCROLL_KEEP_ALIVE_MS = 30000;    //this scroll timeout value is used for reindex and delete queries
  private static final Logger logger = LoggerFactory.getLogger(ElasticsearchArchiver.class);

  @Autowired
  private RestHighLevelClient esClient;

  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private Metrics metrics;

  @Override
  protected Logger getLogger(){
    return logger;
  }

  @Override
  protected void setIndexLifeCycle(final String destinationIndexName){
    try {
      if ( operateProperties.getArchiver().isIlmEnabled() ) {
        esClient.indices().putSettings(new UpdateSettingsRequest(destinationIndexName).settings(
            Settings.builder().put(INDEX_LIFECYCLE_NAME, OPERATE_DELETE_ARCHIVED_INDICES).build()), RequestOptions.DEFAULT);
      }
    } catch (Exception e){
      logger.warn("Could not set ILM policy {} for index {}: {}", OPERATE_DELETE_ARCHIVED_INDICES, destinationIndexName, e.getMessage());
    }
  }

  @Override
  protected CompletableFuture<Void> deleteDocuments(final String sourceIndexName, final String idFieldName,
      final List<Object> processInstanceKeys) {
    final var deleteFuture = new CompletableFuture<Void>();

    final var startTimer = Timer.start();
    deleteAsyncWithConnectionRelease(archiverExecutor, sourceIndexName, idFieldName, processInstanceKeys, objectMapper,
        esClient).thenAccept(ignore -> {
      final var deleteTimer = getArchiverDeleteQueryTimer();
      startTimer.stop(deleteTimer);
      deleteFuture.complete(null);
    }).exceptionally((e) -> {
      deleteFuture.completeExceptionally(e);
      return null;
    });
    return deleteFuture;
  }

  @Override
  protected CompletableFuture<Void> reindexDocuments(final String sourceIndexName, final String destinationIndexName,
      final String idFieldName, final List<Object> processInstanceKeys) {
    final var reindexFuture = new CompletableFuture<Void>();
    final var reindexRequest = createReindexRequestWithDefaults().setSourceIndices(sourceIndexName)
        .setDestIndex(destinationIndexName).setSourceQuery(termsQuery(idFieldName, processInstanceKeys));

    final var startTimer = Timer.start();

    ElasticsearchUtil.reindexAsyncWithConnectionRelease(archiverExecutor, reindexRequest, sourceIndexName, esClient)
        .thenAccept(ignore -> {
          final var reindexTimer = getArchiverReindexQueryTimer();
          startTimer.stop(reindexTimer);
          reindexFuture.complete(null);
        }).exceptionally((e) -> {
          reindexFuture.completeExceptionally(e);
          return null;
        });
    return reindexFuture;
  }

  private ReindexRequest createReindexRequestWithDefaults() {
    final var reindexRequest = new ReindexRequest().setScroll(TimeValue.timeValueMillis(INTERNAL_SCROLL_KEEP_ALIVE_MS))
        .setAbortOnVersionConflict(false)
        .setSlices(AUTO_SLICES);
    return reindexRequest;
  }

  private Timer getArchiverReindexQueryTimer() {
    return metrics.getTimer(Metrics.TIMER_NAME_ARCHIVER_REINDEX_QUERY);
  }

  private Timer getArchiverDeleteQueryTimer() {
    return metrics.getTimer(Metrics.TIMER_NAME_ARCHIVER_DELETE_QUERY);
  }

}
