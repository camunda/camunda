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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/** Import job for one batch of Zeebe data. */
@Component
@Scope(SCOPE_PROTOTYPE)
@Conditional(ElasticSearchCondition.class)
public class ImportJobElasticSearch extends ImportJobAbstract {

  private static final Logger LOGGER = LoggerFactory.getLogger(ImportJobElasticSearch.class);

  @Autowired
  @Qualifier("tasklistZeebeEsClient")
  private RestHighLevelClient zeebeEsClient;

  public ImportJobElasticSearch(ImportBatch importBatch, ImportPositionEntity previousPosition) {
    this.importBatch = importBatch;
    this.previousPosition = previousPosition;
    this.creationTime = OffsetDateTime.now();
  }

  public List<ImportBatch> createSubBatchesPerIndexName() {
    final List<ImportBatch> subBatches = new ArrayList<>();
    if (importBatch.getHits().size() <= 1) {
      subBatches.add(importBatch);
      return subBatches;
    } else {
      String previousIndexName = null;
      List<SearchHit> subBatchHits = new ArrayList<>();
      final List<SearchHit> importResult = importBatch.getHits();
      for (SearchHit hit : importResult) {
        final String indexName = hit.getIndex();
        if (previousIndexName != null && !indexName.equals(previousIndexName)) {
          // start new sub-batch
          subBatches.add(
              new ImportBatchElasticSearch(
                  importBatch.getPartitionId(),
                  importBatch.getImportValueType(),
                  subBatchHits,
                  previousIndexName));
          subBatchHits = new ArrayList<>();
        }
        subBatchHits.add(hit);
        previousIndexName = indexName;
      }
      subBatches.add(
          new ImportBatchElasticSearch(
              importBatch.getPartitionId(),
              importBatch.getImportValueType(),
              subBatchHits,
              previousIndexName));
      return subBatches;
    }
  }

  public void refreshZeebeIndices() {
    final String indexPattern =
        importBatch
            .getImportValueType()
            .getIndicesPattern(tasklistProperties.getZeebeElasticsearch().getPrefix());
    ElasticsearchUtil.refreshIndicesFor(zeebeEsClient, indexPattern);
  }
}
