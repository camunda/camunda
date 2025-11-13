/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.zeebeimport.es;

import static org.springframework.beans.factory.config.BeanDefinition.SCOPE_PROTOTYPE;

import io.camunda.tasklist.data.conditionals.ElasticSearchCondition;
import io.camunda.tasklist.entities.meta.ImportPositionEntity;
import io.camunda.tasklist.util.ElasticsearchUtil;
import io.camunda.tasklist.zeebeimport.ImportBatch;
import io.camunda.tasklist.zeebeimport.ImportJobAbstract;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.search.SearchHit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/** Import job for one batch of Zeebe data. */
@Component
@Scope(SCOPE_PROTOTYPE)
@Conditional(ElasticSearchCondition.class)
public class ImportJobElasticsearch extends ImportJobAbstract {

  @Autowired
  @Qualifier("tasklistZeebeEsClient")
  private RestHighLevelClient zeebeEsClient;

  public ImportJobElasticsearch(
      final ImportBatch importBatch, final ImportPositionEntity previousPosition) {
    this.importBatch = importBatch;
    this.previousPosition = previousPosition;
    creationTime = OffsetDateTime.now();
  }

  @Override
  public void refreshZeebeIndices() {
    final String indexPattern =
        importBatch
            .getImportValueType()
            .getIndicesPattern(tasklistProperties.getZeebeElasticsearch().getPrefix());
    ElasticsearchUtil.refreshIndicesFor(zeebeEsClient, indexPattern);
  }

  @Override
  public List<ImportBatch> createSizeLimitedSubBatchesPerIndexName() {
    final List<SearchHit> hits = importBatch.getHits();
    final long maxBatchSizeBytes = tasklistProperties.getImporter().getMaxBatchSizeBytes();
    final List<ImportBatch> subBatches = new ArrayList<>();
    final BatchFlusher<ImportBatchElasticSearch, SearchHit> flusher =
        new BatchFlusher<>(subBatches);
    ImportBatchElasticSearch currentBatch = null;

    List<SearchHit> currentHits = new ArrayList<>();
    int currentBatchSize = 0;
    String currentIndexName = null;

    for (final SearchHit hit : hits) {
      final String hitIndexName = hit.getIndex();
      final int hitSize = EntitySizeEstimator.estimateSize(hit);
      final boolean shouldFlush =
          currentBatch == null
              || !hitIndexName.equals(currentIndexName)
              || currentBatchSize + hitSize > maxBatchSizeBytes;
      if (shouldFlush) {
        // Flush the current batch if needed
        flusher.flush(currentBatch, currentHits, currentIndexName);
        // Start a new batch
        currentBatch =
            new ImportBatchElasticSearch(
                importBatch.getPartitionId(),
                importBatch.getImportValueType(),
                new ArrayList<>(),
                hitIndexName);
        currentHits = new ArrayList<>();
        currentBatchSize = 0;
        currentIndexName = hitIndexName;
      }
      currentHits.add(hit);
      currentBatchSize += hitSize;
    }
    // Flush the last batch if it has hits
    flusher.flush(currentBatch, currentHits, currentIndexName);
    return subBatches;
  }

  //  private record BatchFlusher(List<ImportBatch> subBatches) {
  //
  //    void flush(
  //        final ImportBatchElasticSearch batch, final List<SearchHit> batchHits, final String
  // index) {
  //      if (batch != null && batchHits != null && !batchHits.isEmpty()) {
  //        batch.setHits(batchHits);
  //        batch.setLastRecordIndexName(index);
  //        subBatches.add(batch);
  //      }
  //    }
  //  }
}
