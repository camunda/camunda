/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing;

import lombok.RequiredArgsConstructor;
import org.camunda.optimize.dto.optimize.importing.index.TimestampBasedImportIndexDto;
import org.camunda.optimize.service.es.reader.TimestampBasedImportIndexReader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;

import javax.annotation.PostConstruct;
import java.time.OffsetDateTime;
import java.util.Optional;

@RequiredArgsConstructor
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public abstract class TimestampBasedEngineImportIndexHandler
  extends TimestampBasedImportIndexHandler<TimestampBasedImportIndexDto> {

  @Autowired
  private TimestampBasedImportIndexReader importIndexReader;

  private OffsetDateTime lastImportExecutionTimestamp = BEGINNING_OF_TIME;
  private OffsetDateTime persistedTimestampOfLastEntity = BEGINNING_OF_TIME;

  @PostConstruct
  protected void init() {
    readIndexFromElasticsearch();
  }

  /**
   * States the Elasticsearch document name where the index information should be stored.
   */
  protected abstract String getElasticsearchDocID();

  @Override
  protected void updateLastPersistedEntityTimestamp(final OffsetDateTime timestamp) {
    this.persistedTimestampOfLastEntity = timestamp;
  }

  @Override
  protected void updateLastImportExecutionTimestamp(final OffsetDateTime timestamp) {
    this.lastImportExecutionTimestamp = timestamp;
  }

  private void readIndexFromElasticsearch() {
    final Optional<TimestampBasedImportIndexDto> dto = importIndexReader
      .getImportIndex(getElasticsearchDocID(), getEngineAlias());
    if (dto.isPresent()) {
      TimestampBasedImportIndexDto loadedImportIndex = dto.get();
      updateLastPersistedEntityTimestamp(loadedImportIndex.getTimestampOfLastEntity());
      updatePendingLastEntityTimestamp(loadedImportIndex.getTimestampOfLastEntity());
      updateLastImportExecutionTimestamp(loadedImportIndex.getLastImportExecutionTimestamp());
    }
  }

  @Override
  public TimestampBasedImportIndexDto getIndexStateDto() {
    TimestampBasedImportIndexDto indexToStore = new TimestampBasedImportIndexDto();
    indexToStore.setLastImportExecutionTimestamp(lastImportExecutionTimestamp);
    indexToStore.setTimestampOfLastEntity(persistedTimestampOfLastEntity);
    indexToStore.setEngine(getEngineAlias());
    indexToStore.setEsTypeIndexRefersTo(getElasticsearchDocID());
    return indexToStore;
  }

}
