/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.zeebeimport.os;

import static org.springframework.beans.factory.config.BeanDefinition.SCOPE_PROTOTYPE;

import io.camunda.tasklist.data.conditionals.OpenSearchCondition;
import io.camunda.tasklist.util.OpenSearchUtil;
import io.camunda.tasklist.zeebeimport.ImportBatch;
import io.camunda.tasklist.zeebeimport.ImportJobAbstract;
import io.camunda.webapps.schema.entities.ImportPositionEntity;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch.core.search.Hit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Component
@Scope(SCOPE_PROTOTYPE)
@Conditional(OpenSearchCondition.class)
public class ImportJobOpenSearch extends ImportJobAbstract {

  @Autowired
  @Qualifier("tasklistZeebeOsClient")
  private OpenSearchClient zeebeOsClient;

  public ImportJobOpenSearch(
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
    OpenSearchUtil.refreshIndicesFor(zeebeOsClient, indexPattern);
  }

  @Override
  public List<ImportBatch> createSizeLimitedSubBatchesPerIndexName() {
    final List<Hit> hits = importBatch.getHits();
    final long maxBatchSizeBytes = tasklistProperties.getImporter().getMaxBatchSizeBytes();
    final List<ImportBatch> subBatches = new ArrayList<>();
    ImportBatchOpenSearch currentBatch = null;
    int currentBatchSize = 0;
    String currentIndex = null;
    List<Hit> currentHits = null;
    for (final Hit hit : hits) {
      final String hitIndex = hit.index();
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
            new ImportBatchOpenSearch(
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
