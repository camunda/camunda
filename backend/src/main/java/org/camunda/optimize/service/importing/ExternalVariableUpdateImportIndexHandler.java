/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing;

import lombok.RequiredArgsConstructor;
import org.camunda.optimize.dto.optimize.index.TimestampBasedImportIndexDto;
import org.camunda.optimize.service.es.reader.TimestampBasedImportIndexReader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.time.OffsetDateTime;
import java.util.Optional;

import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.ENGINE_ALIAS_OPTIMIZE;

@RequiredArgsConstructor
@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class ExternalVariableUpdateImportIndexHandler
  extends TimestampBasedImportIndexHandler<TimestampBasedImportIndexDto> {

  public static final String EXTERNAL_VARIABLE_UPDATE_IMPORT_INDEX_DOC_ID = "externalVariableUpdateImportIndex";

  private OffsetDateTime lastImportExecutionTimestamp = BEGINNING_OF_TIME;
  private OffsetDateTime persistedTimestampOfLastEntity = BEGINNING_OF_TIME;

  @Autowired
  private TimestampBasedImportIndexReader importIndexReader;

  @Override
  public String getEngineAlias() {
    return ENGINE_ALIAS_OPTIMIZE;
  }

  @Override
  public TimestampBasedImportIndexDto getIndexStateDto() {
    return new TimestampBasedImportIndexDto(
      lastImportExecutionTimestamp, persistedTimestampOfLastEntity, getElasticsearchDocId(), ENGINE_ALIAS_OPTIMIZE
    );
  }

  @PostConstruct
  protected void init() {
    final Optional<TimestampBasedImportIndexDto> dto = importIndexReader
      .getImportIndex(getElasticsearchDocId(), ENGINE_ALIAS_OPTIMIZE);
    if (dto.isPresent()) {
      TimestampBasedImportIndexDto loadedImportIndex = dto.get();
      updateLastPersistedEntityTimestamp(loadedImportIndex.getTimestampOfLastEntity());
      updatePendingLastEntityTimestamp(loadedImportIndex.getTimestampOfLastEntity());
      updateLastImportExecutionTimestamp(loadedImportIndex.getLastImportExecutionTimestamp());
    }
  }

  private String getElasticsearchDocId() {
    return EXTERNAL_VARIABLE_UPDATE_IMPORT_INDEX_DOC_ID;
  }

  @Override
  protected void updateLastPersistedEntityTimestamp(final OffsetDateTime timestamp) {
    this.persistedTimestampOfLastEntity = timestamp;
  }

  @Override
  protected void updateLastImportExecutionTimestamp(final OffsetDateTime timestamp) {
    this.lastImportExecutionTimestamp = timestamp;
  }

}
