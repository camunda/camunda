/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.importing;

import lombok.RequiredArgsConstructor;
import org.camunda.optimize.dto.optimize.datasource.DataSourceDto;
import org.camunda.optimize.dto.optimize.index.TimestampBasedImportIndexDto;
import org.camunda.optimize.service.es.reader.importindex.TimestampBasedImportIndexReader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;

import javax.annotation.PostConstruct;
import java.time.OffsetDateTime;
import java.util.Optional;

@RequiredArgsConstructor
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public abstract class TimestampBasedDataSourceImportIndexHandler<T extends DataSourceDto>
  extends TimestampBasedImportIndexHandler<TimestampBasedImportIndexDto> {

  @Autowired
  protected TimestampBasedImportIndexReader importIndexReader;

  protected OffsetDateTime lastImportExecutionTimestamp = BEGINNING_OF_TIME;
  private OffsetDateTime persistedTimestampOfLastEntity = BEGINNING_OF_TIME;

  @Override
  public TimestampBasedImportIndexDto getIndexStateDto() {
    TimestampBasedImportIndexDto indexToStore = new TimestampBasedImportIndexDto();
    indexToStore.setLastImportExecutionTimestamp(lastImportExecutionTimestamp);
    indexToStore.setTimestampOfLastEntity(persistedTimestampOfLastEntity);
    indexToStore.setDataSource(getDataSource());
    indexToStore.setEsTypeIndexRefersTo(getElasticsearchDocID());
    return indexToStore;
  }

  @PostConstruct
  protected void init() {
    final Optional<TimestampBasedImportIndexDto> dto =
      importIndexReader.getImportIndex(getElasticsearchDocID(), getDataSource());
    if (dto.isPresent()) {
      TimestampBasedImportIndexDto loadedImportIndex = dto.get();
      updateLastPersistedEntityTimestamp(loadedImportIndex.getTimestampOfLastEntity());
      updatePendingLastEntityTimestamp(loadedImportIndex.getTimestampOfLastEntity());
      updateLastImportExecutionTimestamp(loadedImportIndex.getLastImportExecutionTimestamp());
    }
  }

  protected abstract String getElasticsearchDocID();

  protected abstract T getDataSource();

  @Override
  protected void updateLastPersistedEntityTimestamp(final OffsetDateTime timestamp) {
    this.persistedTimestampOfLastEntity = timestamp;
  }

  @Override
  protected void updateLastImportExecutionTimestamp(final OffsetDateTime timestamp) {
    this.lastImportExecutionTimestamp = timestamp;
  }

}
