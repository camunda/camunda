/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.zeebeimport;

import static io.camunda.tasklist.util.ElasticsearchUtil.joinWithAnd;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.tasklist.Metrics;
import io.camunda.tasklist.entities.meta.ImportPositionEntity;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.schema.indices.ImportPositionIndex;
import io.camunda.tasklist.util.Either;
import io.camunda.tasklist.util.ElasticsearchUtil;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.locks.ReentrantLock;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.xcontent.XContentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.DependsOn;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Component;

@Component
@DependsOn("schemaStartup")
public class ImportPositionHolder {

  private static final Logger LOGGER = LoggerFactory.getLogger(ImportPositionHolder.class);

  // this is the in-memory only storage
  private Map<String, ImportPositionEntity> lastScheduledPositions = new HashMap<>();

  private Map<String, ImportPositionEntity> pendingProcessedPositions = new HashMap<>();
  private Map<String, ImportPositionEntity> inflightProcessedPositions = new HashMap<>();

  private ScheduledFuture<?> scheduledTask;
  private ReentrantLock inflightImportPositionLock = new ReentrantLock();

  @Autowired private ImportPositionIndex importPositionType;

  @Autowired private RestHighLevelClient esClient;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private TasklistProperties tasklistProperties;

  @Autowired private Metrics metrics;

  @Autowired
  @Qualifier("importPositionUpdateThreadPoolExecutor")
  private ThreadPoolTaskScheduler importPositionUpdateExecutor;

  @PostConstruct
  private void init() {
    LOGGER.info("INIT: Start import position updater...");
    scheduleImportPositionUpdateTask();
  }

  public void scheduleImportPositionUpdateTask() {
    final var interval = tasklistProperties.getImporter().getImportPositionUpdateInterval();
    scheduledTask =
        importPositionUpdateExecutor.schedule(
            this::updateImportPositions,
            OffsetDateTime.now().plus(interval, ChronoUnit.MILLIS).toInstant());
  }

  public CompletableFuture<Void> cancelScheduledImportPositionUpdateTask() {
    final var future = new CompletableFuture<Void>();
    importPositionUpdateExecutor.submit(
        () -> {
          if (scheduledTask != null) {
            scheduledTask.cancel(false);
            scheduledTask = null;
          }

          future.complete(null);
        });
    return future;
  }

  public ImportPositionEntity getLatestScheduledPosition(String aliasTemplate, int partitionId)
      throws IOException {
    final String key = getKey(aliasTemplate, partitionId);
    if (lastScheduledPositions.containsKey(key)) {
      return lastScheduledPositions.get(key);
    } else {
      final ImportPositionEntity latestLoadedPosition =
          getLatestLoadedPosition(aliasTemplate, partitionId);
      lastScheduledPositions.put(key, latestLoadedPosition);
      return latestLoadedPosition;
    }
  }

  public void recordLatestScheduledPosition(
      String aliasName, int partitionId, ImportPositionEntity importPositionEntity) {
    lastScheduledPositions.put(getKey(aliasName, partitionId), importPositionEntity);
  }

  public ImportPositionEntity getLatestLoadedPosition(String aliasTemplate, int partitionId)
      throws IOException {
    final QueryBuilder queryBuilder =
        joinWithAnd(
            termQuery(ImportPositionIndex.ALIAS_NAME, aliasTemplate),
            termQuery(ImportPositionIndex.PARTITION_ID, partitionId));

    final SearchRequest searchRequest =
        new SearchRequest(importPositionType.getAlias())
            .source(new SearchSourceBuilder().query(queryBuilder).size(10));

    final SearchResponse searchResponse = esClient.search(searchRequest, RequestOptions.DEFAULT);

    final Iterator<SearchHit> hitIterator = searchResponse.getHits().iterator();

    ImportPositionEntity position =
        new ImportPositionEntity().setAliasName(aliasTemplate).setPartitionId(partitionId);

    if (hitIterator.hasNext()) {
      position =
          ElasticsearchUtil.fromSearchHit(
              hitIterator.next().getSourceAsString(), objectMapper, ImportPositionEntity.class);
    }
    LOGGER.debug(
        "Latest loaded position for alias [{}] and partitionId [{}]: {}",
        aliasTemplate,
        partitionId,
        position);

    return position;
  }

  public void recordLatestLoadedPosition(ImportPositionEntity lastProcessedPosition) {
    withInflightImportPositionLock(
        () -> {
          final var aliasName = lastProcessedPosition.getAliasName();
          final var partition = lastProcessedPosition.getPartitionId();
          inflightProcessedPositions.put(getKey(aliasName, partition), lastProcessedPosition);
        });
  }

  public void updateImportPositions() {
    withInflightImportPositionLock(
        () -> {
          pendingProcessedPositions.putAll(inflightProcessedPositions);
          inflightProcessedPositions.clear();
        });

    final var result = updateImportPositions(pendingProcessedPositions);

    if (result.getOrElse(false)) {
      // clear only map when updating the import positions
      // succeeded, otherwise, it may result in lost updates
      pendingProcessedPositions.clear();
    }

    // self scheduling just for the case the interval is set too short
    scheduleImportPositionUpdateTask();
  }

  private Either<Throwable, Boolean> updateImportPositions(
      final Map<String, ImportPositionEntity> positions) {
    final var preparedBulkRequest = prepareBulkRequest(positions);

    if (preparedBulkRequest.isLeft()) {
      final var e = preparedBulkRequest.getLeft();
      return Either.left(e);
    }

    try {
      final var bulkRequest = preparedBulkRequest.get();

      withImportPositionTimer(
          () -> {
            ElasticsearchUtil.processBulkRequest(esClient, bulkRequest);
            return null;
          });

      return Either.right(true);
    } catch (final Throwable e) {
      LOGGER.error("Error occurred while persisting latest loaded position", e);
      return Either.left(e);
    }
  }

  private Either<Exception, BulkRequest> prepareBulkRequest(
      final Map<String, ImportPositionEntity> positions) {
    final var bulkRequest = new BulkRequest();

    if (positions.size() > 0) {
      final var preparedUpdateRequests =
          positions.values().stream()
              .map(this::prepareUpdateRequest)
              .collect(Either.collectorFoldingLeft());

      if (preparedUpdateRequests.isLeft()) {
        final var e = preparedUpdateRequests.getLeft();
        return Either.left(e);
      }

      preparedUpdateRequests.get().forEach(bulkRequest::add);
    }

    return Either.right(bulkRequest);
  }

  private Either<Exception, UpdateRequest> prepareUpdateRequest(
      final ImportPositionEntity position) {
    try {
      final var index = importPositionType.getFullQualifiedName();
      final var source = objectMapper.writeValueAsString(position);
      final var updateFields = new HashMap<String, Object>();

      updateFields.put(ImportPositionIndex.POSITION, position.getPosition());
      updateFields.put(ImportPositionIndex.FIELD_INDEX_NAME, position.getIndexName());
      updateFields.put(ImportPositionIndex.SEQUENCE, position.getSequence());

      final UpdateRequest updateRequest =
          new UpdateRequest()
              .index(index)
              .id(position.getId())
              .upsert(source, XContentType.JSON)
              .doc(updateFields);

      return Either.right(updateRequest);

    } catch (final Exception e) {
      LOGGER.error(
          String.format(
              "Error occurred while preparing request to update processed position for %s",
              position.getAliasName()),
          e);
      return Either.left(e);
    }
  }

  public void clearCache() {
    lastScheduledPositions.clear();
    pendingProcessedPositions.clear();
    withInflightImportPositionLock(() -> inflightProcessedPositions.clear());
  }

  private String getKey(String aliasTemplate, int partitionId) {
    return String.format("%s-%d", aliasTemplate, partitionId);
  }

  private void withImportPositionTimer(final Callable<Void> action) throws Exception {
    metrics.getTimer(Metrics.TIMER_NAME_IMPORT_POSITION_UPDATE).recordCallable(action);
  }

  private void withInflightImportPositionLock(final Runnable action) {
    try {
      inflightImportPositionLock.lock();
      action.run();
    } finally {
      inflightImportPositionLock.unlock();
    }
  }
}
