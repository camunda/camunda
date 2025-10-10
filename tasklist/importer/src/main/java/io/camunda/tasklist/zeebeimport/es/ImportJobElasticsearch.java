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
import io.camunda.tasklist.util.ElasticsearchUtil;
import io.camunda.tasklist.zeebeimport.ImportBatch;
import io.camunda.tasklist.zeebeimport.ImportJobAbstract;
import io.camunda.webapps.schema.entities.ImportPositionEntity;
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
    ImportBatchElasticSearch currentBatch = null;
    int currentBatchSize = 0;
    String currentIndex = null;
    List<SearchHit> currentHits = null;
    for (final SearchHit hit : hits) {
      final String hitIndex = hit.getIndex();
      final int hitSize = EntitySizeEstimator.estimateSize(hit);
      if (currentBatch == null
          || !hitIndex.equals(currentIndex)
          || currentBatchSize + hitSize > maxBatchSizeBytes) {
        if (currentBatch != null) {
          currentBatch.setHits(currentHits);
          currentBatch.setLastRecordIndexName(currentIndex);
          subBatches.add(currentBatch);
        }
        currentBatch =
            new ImportBatchElasticSearch(
                importBatch.getPartitionId(),
                importBatch.getImportValueType(),
                new ArrayList<>(),
                hitIndex);
        currentHits = new ArrayList<>();
        currentBatchSize = 0;
        currentIndex = hitIndex;
      }
      currentHits.add(hit);
      currentBatchSize += hitSize;
    }
    if (currentBatch != null && currentHits != null && !currentHits.isEmpty()) {
      currentBatch.setHits(currentHits);
      currentBatch.setLastRecordIndexName(currentIndex);
      subBatches.add(currentBatch);
    }
    return subBatches;
  }
}
