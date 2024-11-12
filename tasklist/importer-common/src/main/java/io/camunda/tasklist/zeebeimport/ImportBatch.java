/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.zeebeimport;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.tasklist.zeebe.ImportValueType;
import java.util.List;

public interface ImportBatch {

  public int getPartitionId();

  public void setPartitionId(int partitionId);

  public ImportValueType getImportValueType();

  public void setImportValueType(ImportValueType importValueType);

  public List getHits();

  public void setHits(List hits);

  public int getRecordsCount();

  public void incrementFinishedWiCount();

  public int getFinishedWiCount();

  public String getLastRecordIndexName();

  public void setLastRecordIndexName(String lastRecordIndexName);

  public long getLastProcessedPosition(ObjectMapper objectMapper);

  public Long getLastProcessedSequence(ObjectMapper objectMapper);

  public String getAliasName();

  public Boolean hasMoreThanOneUniqueHitId();
}
