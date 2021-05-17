/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.camunda.optimize.dto.optimize.importing.index.PositionBasedImportIndexDto;
import org.camunda.optimize.service.importing.page.PositionBasedImportPage;

import javax.annotation.PostConstruct;
import java.time.OffsetDateTime;

import static org.camunda.optimize.service.importing.TimestampBasedImportIndexHandler.BEGINNING_OF_TIME;

@RequiredArgsConstructor
@Data
public abstract class PositionBasedImportIndexHandler
  implements ZeebeImportIndexHandler<PositionBasedImportPage, PositionBasedImportIndexDto> {

  private OffsetDateTime lastImportExecutionTimestamp = BEGINNING_OF_TIME;
  private long persistedPositionOfLastEntity = 0;
  private long pendingPositionOfLastEntity = 0;
  protected int partitionId;

  @Override
  public PositionBasedImportIndexDto getIndexStateDto() {
    PositionBasedImportIndexDto indexToStore = new PositionBasedImportIndexDto();
    indexToStore.setPartitionId(partitionId);
    indexToStore.setLastImportExecutionTimestamp(lastImportExecutionTimestamp);
    indexToStore.setPositionOfLastEntity(persistedPositionOfLastEntity);
    indexToStore.setEsTypeIndexRefersTo(getElasticsearchDocID());
    return indexToStore;
  }

  @PostConstruct
  protected void init() {
    // readIndexFromElasticsearch();
  }

  @Override
  public void resetImportIndex() {
    lastImportExecutionTimestamp = BEGINNING_OF_TIME;
    persistedPositionOfLastEntity = 0;
  }

  @Override
  public PositionBasedImportPage getNextPage() {
    PositionBasedImportPage page = new PositionBasedImportPage();
    page.setPosition(pendingPositionOfLastEntity);
    return page;
  }

  /**
   * States the Elasticsearch document name where the index information should be stored.
   */
  protected abstract String getElasticsearchDocID();

  public void updateLastPersistedEntityPosition(final long position) {
    this.persistedPositionOfLastEntity = position;
  }

  public void updatePendingLastEntityPosition(final long position) {
    this.pendingPositionOfLastEntity = position;
  }

  public void updateLastImportExecutionTimestamp(final OffsetDateTime timestamp) {
    this.lastImportExecutionTimestamp = timestamp;
  }

}
