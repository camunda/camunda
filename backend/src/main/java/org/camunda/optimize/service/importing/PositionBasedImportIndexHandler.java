/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.camunda.optimize.dto.optimize.datasource.ZeebeDataSourceDto;
import org.camunda.optimize.dto.optimize.index.PositionBasedImportIndexDto;
import org.camunda.optimize.service.es.reader.importindex.PositionBasedImportIndexReader;
import org.camunda.optimize.service.importing.page.PositionBasedImportPage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;

import javax.annotation.PostConstruct;
import java.time.OffsetDateTime;
import java.util.Optional;

import static org.camunda.optimize.service.importing.TimestampBasedImportIndexHandler.BEGINNING_OF_TIME;

@Getter
@RequiredArgsConstructor
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public abstract class PositionBasedImportIndexHandler
  implements ZeebeImportIndexHandler<PositionBasedImportPage, PositionBasedImportIndexDto> {

  private OffsetDateTime lastImportExecutionTimestamp = BEGINNING_OF_TIME;
  private OffsetDateTime timestampOfLastPersistedEntity = BEGINNING_OF_TIME;
  private long persistedPositionOfLastEntity = 0;
  private long pendingPositionOfLastEntity = 0;
  protected ZeebeDataSourceDto dataSource;

  @Autowired
  private PositionBasedImportIndexReader positionBasedImportIndexReader;

  @Override
  public PositionBasedImportIndexDto getIndexStateDto() {
    PositionBasedImportIndexDto indexToStore = new PositionBasedImportIndexDto();
    indexToStore.setDataSource(dataSource);
    indexToStore.setLastImportExecutionTimestamp(lastImportExecutionTimestamp);
    indexToStore.setPositionOfLastEntity(persistedPositionOfLastEntity);
    indexToStore.setTimestampOfLastEntity(timestampOfLastPersistedEntity);
    indexToStore.setEsTypeIndexRefersTo(getElasticsearchDocID());
    return indexToStore;
  }

  @PostConstruct
  protected void init() {
    final Optional<PositionBasedImportIndexDto> dto = positionBasedImportIndexReader
      .getImportIndex(getElasticsearchDocID(), dataSource);
    if (dto.isPresent()) {
      PositionBasedImportIndexDto loadedImportIndex = dto.get();
      updateLastPersistedEntityPosition(loadedImportIndex.getPositionOfLastEntity());
      updatePendingLastEntityPosition(loadedImportIndex.getPositionOfLastEntity());
      updateLastImportExecutionTimestamp(loadedImportIndex.getLastImportExecutionTimestamp());
      updateTimestampOfLastPersistedEntity(loadedImportIndex.getTimestampOfLastEntity());
    }
  }

  @Override
  public void resetImportIndex() {
    lastImportExecutionTimestamp = BEGINNING_OF_TIME;
    timestampOfLastPersistedEntity = BEGINNING_OF_TIME;
    persistedPositionOfLastEntity = 0;
    pendingPositionOfLastEntity = 0;
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

  public void updateTimestampOfLastPersistedEntity(final OffsetDateTime timestamp) {
    this.timestampOfLastPersistedEntity = timestamp;
  }

}
