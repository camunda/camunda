/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.zeebeimport.es;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.camunda.tasklist.zeebe.ImportValueType;
import io.camunda.tasklist.zeebe.ZeebeESConstants;
import io.camunda.tasklist.zeebeimport.ImportBatch;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;
import org.elasticsearch.search.SearchHit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * One batch for importing Zeebe data. Contains list of records as well as partition id and value
 * type of the records.
 */
public class ImportBatchElasticSearch implements ImportBatch {

  private static final Logger LOGGER = LoggerFactory.getLogger(ImportBatchElasticSearch.class);

  private int partitionId;

  private ImportValueType importValueType;

  private List<SearchHit> hits;

  private String lastRecordIndexName;

  private int finishedWiCount = 0;

  public ImportBatchElasticSearch(
      int partitionId,
      ImportValueType importValueType,
      List<SearchHit> hits,
      String lastRecordIndexName) {
    this.partitionId = partitionId;
    this.importValueType = importValueType;
    this.hits = hits;
    this.lastRecordIndexName = lastRecordIndexName;
  }

  public int getPartitionId() {
    return partitionId;
  }

  public void setPartitionId(int partitionId) {
    this.partitionId = partitionId;
  }

  public ImportValueType getImportValueType() {
    return importValueType;
  }

  public void setImportValueType(ImportValueType importValueType) {
    this.importValueType = importValueType;
  }

  public List<SearchHit> getHits() {
    return hits;
  }

  public void setHits(List hits) {
    this.hits = hits;
  }

  public int getRecordsCount() {
    return hits.size();
  }

  public void incrementFinishedWiCount() {
    finishedWiCount++;
  }

  public int getFinishedWiCount() {
    return finishedWiCount;
  }

  public String getLastRecordIndexName() {
    return lastRecordIndexName;
  }

  public void setLastRecordIndexName(String lastRecordIndexName) {
    this.lastRecordIndexName = lastRecordIndexName;
  }

  public long getLastProcessedPosition(ObjectMapper objectMapper) {
    return getLastProcessed(ZeebeESConstants.POSITION_FIELD_NAME, objectMapper, 0L);
  }

  public Long getLastProcessedSequence(ObjectMapper objectMapper) {
    return getLastProcessed(ZeebeESConstants.SEQUENCE_FIELD_NAME, objectMapper, 0L);
  }

  private long getLastProcessed(
      final String fieldName, final ObjectMapper objectMapper, final Long defaultValue) {
    try {
      if (hits != null && hits.size() != 0) {
        final ObjectNode node =
            objectMapper.readValue(hits.get(hits.size() - 1).getSourceAsString(), ObjectNode.class);
        if (node.has(fieldName)) {
          return node.get(fieldName).longValue();
        }
      }
    } catch (IOException e) {
      LOGGER.warn(
          String.format(
              "Unable to parse Zeebe object for getting field %s : %s", fieldName, e.getMessage()),
          e);
    }
    return defaultValue;
  }

  public String getAliasName() {
    return importValueType.getAliasTemplate();
  }

  @Override
  public Boolean hasMoreThanOneUniqueHitId() {
    return hits.stream().map(SearchHit::getIndex).collect(Collectors.toSet()).size() > 1;
  }

  @Override
  public int hashCode() {
    int result = partitionId;
    result = 31 * result + (importValueType != null ? importValueType.hashCode() : 0);
    result = 31 * result + (hits != null ? hits.hashCode() : 0);
    result = 31 * result + (lastRecordIndexName != null ? lastRecordIndexName.hashCode() : 0);
    result = 31 * result + finishedWiCount;
    return result;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    final ImportBatchElasticSearch that = (ImportBatchElasticSearch) o;

    if (partitionId != that.partitionId) {
      return false;
    }
    if (finishedWiCount != that.finishedWiCount) {
      return false;
    }
    if (importValueType != that.importValueType) {
      return false;
    }
    if (hits != null ? !hits.equals(that.hits) : that.hits != null) {
      return false;
    }
    return lastRecordIndexName != null
        ? lastRecordIndexName.equals(that.lastRecordIndexName)
        : that.lastRecordIndexName == null;
  }
}
