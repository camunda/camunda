/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.zeebeimport.os;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.camunda.tasklist.zeebe.ImportValueType;
import io.camunda.tasklist.zeebe.ZeebeESConstants;
import io.camunda.tasklist.zeebeimport.ImportBatch;
import java.util.List;
import java.util.stream.Collectors;
import org.opensearch.client.opensearch.core.search.Hit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * One batch for importing Zeebe data. Contains list of records as well as partition id and value
 * type of the records.
 */
public class ImportBatchOpenSearch implements ImportBatch {

  private static final Logger LOGGER = LoggerFactory.getLogger(ImportBatchOpenSearch.class);

  private int partitionId;

  private ImportValueType importValueType;

  private List<Hit> hits;

  private String lastRecordIndexName;

  private int finishedWiCount = 0;

  public ImportBatchOpenSearch(
      int partitionId,
      ImportValueType importValueType,
      List<Hit> hits,
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

  public List<Hit> getHits() {
    return hits;
  }

  @Override
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

    if (hits != null && hits.size() != 0) {

      final ObjectNode node =
          objectMapper.convertValue(hits.get(hits.size() - 1).source(), ObjectNode.class);
      if (node.has(fieldName)) {
        return node.get(fieldName).longValue();
      }
    }

    return defaultValue;
  }

  public String getAliasName() {
    return importValueType.getAliasTemplate();
  }

  @Override
  public Boolean hasMoreThanOneUniqueHitId() {
    return hits.stream().map(m -> m.index()).collect(Collectors.toSet()).size() > 1;
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

    final ImportBatchOpenSearch that = (ImportBatchOpenSearch) o;

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
