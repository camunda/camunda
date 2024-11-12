/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.importing.ingested.handler;

import static io.camunda.optimize.service.db.DatabaseConstants.EXTERNAL_DATA_SOURCE_ALIAS;

import io.camunda.optimize.dto.optimize.datasource.IngestedDataSourceDto;
import io.camunda.optimize.dto.optimize.index.TimestampBasedImportIndexDto;
import io.camunda.optimize.service.db.reader.importindex.TimestampBasedImportIndexReader;
import io.camunda.optimize.service.importing.ImportIndexHandler;
import io.camunda.optimize.service.importing.page.TimestampBasedImportPage;
import io.camunda.optimize.service.security.util.LocalDateUtil;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import jakarta.annotation.PostConstruct;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class ExternalVariableUpdateImportIndexHandler
    implements ImportIndexHandler<TimestampBasedImportPage, TimestampBasedImportIndexDto> {

  public static final String EXTERNAL_VARIABLE_UPDATE_IMPORT_INDEX_DOC_ID =
      "externalVariableUpdateImportIndex";
  public static final OffsetDateTime BEGINNING_OF_TIME =
      OffsetDateTime.ofInstant(Instant.EPOCH, ZoneId.systemDefault());

  @Autowired protected ConfigurationService configurationService;
  @Autowired protected TimestampBasedImportIndexReader importIndexReader;
  protected Logger logger = LoggerFactory.getLogger(getClass());
  protected IngestedDataSourceDto dataSource;
  private OffsetDateTime lastImportExecutionTimestamp = BEGINNING_OF_TIME;

  private OffsetDateTime timestampOfLastEntity = BEGINNING_OF_TIME;
  private OffsetDateTime persistedTimestampOfLastEntity = BEGINNING_OF_TIME;

  public ExternalVariableUpdateImportIndexHandler() {}

  public TimestampBasedImportIndexDto getIndexStateDto() {
    final TimestampBasedImportIndexDto indexToStore = new TimestampBasedImportIndexDto();
    indexToStore.setLastImportExecutionTimestamp(lastImportExecutionTimestamp);
    indexToStore.setTimestampOfLastEntity(persistedTimestampOfLastEntity);
    indexToStore.setDataSource(getDataSource());
    indexToStore.setEsTypeIndexRefersTo(getDatabaseDocID());
    return indexToStore;
  }

  @PostConstruct
  protected void init() {
    final Optional<TimestampBasedImportIndexDto> dto =
        importIndexReader.getImportIndex(getDatabaseDocID(), getDataSource());
    if (dto.isPresent()) {
      final TimestampBasedImportIndexDto loadedImportIndex = dto.get();
      updateLastPersistedEntityTimestamp(loadedImportIndex.getTimestampOfLastEntity());
      updatePendingLastEntityTimestamp(loadedImportIndex.getTimestampOfLastEntity());
      updateLastImportExecutionTimestamp(loadedImportIndex.getLastImportExecutionTimestamp());
    }
  }

  @Override
  public TimestampBasedImportPage getNextPage() {
    final TimestampBasedImportPage page = new TimestampBasedImportPage();
    page.setTimestampOfLastEntity(timestampOfLastEntity);
    return page;
  }

  @Override
  public void resetImportIndex() {
    updateLastImportExecutionTimestamp(BEGINNING_OF_TIME);
    updateLastPersistedEntityTimestamp(BEGINNING_OF_TIME);
    updatePendingLastEntityTimestamp(BEGINNING_OF_TIME);
  }

  public String getDataSourceAlias() {
    return EXTERNAL_DATA_SOURCE_ALIAS;
  }

  public OffsetDateTime getTimestampOfLastEntity() {
    return timestampOfLastEntity;
  }

  public void updateLastImportExecutionTimestamp() {
    updateLastImportExecutionTimestamp(LocalDateUtil.getCurrentDateTime());
  }

  public void updateTimestampOfLastEntity(final OffsetDateTime timestamp) {
    final OffsetDateTime backOffWindowStart =
        reduceByCurrentTimeBackoff(OffsetDateTime.ofInstant(Instant.now(), ZoneId.systemDefault()));
    if (timestamp.isAfter(backOffWindowStart)) {
      logger.info(
          "Timestamp is in the current time backoff window of {}ms, will save begin of backoff window as last timestamp",
          getTipOfTimeBackoffMilliseconds());
      updateLastPersistedEntityTimestamp(backOffWindowStart);
    } else {
      updateLastPersistedEntityTimestamp(timestamp);
    }
  }

  public void updatePendingTimestampOfLastEntity(final OffsetDateTime timestamp) {
    final OffsetDateTime backOffWindowStart =
        reduceByCurrentTimeBackoff(OffsetDateTime.ofInstant(Instant.now(), ZoneId.systemDefault()));
    if (timestamp.isAfter(backOffWindowStart)) {
      logger.info(
          "Timestamp is in the current time backoff window of {}ms, will save begin of backoff window as last timestamp",
          getTipOfTimeBackoffMilliseconds());
      updatePendingLastEntityTimestamp(backOffWindowStart);
    } else {
      updatePendingLastEntityTimestamp(timestamp);
    }
  }

  private String getDatabaseDocID() {
    return EXTERNAL_VARIABLE_UPDATE_IMPORT_INDEX_DOC_ID;
  }

  private IngestedDataSourceDto getDataSource() {
    return new IngestedDataSourceDto(getDataSourceAlias());
  }

  private void updateLastPersistedEntityTimestamp(final OffsetDateTime timestamp) {
    this.persistedTimestampOfLastEntity = timestamp;
  }

  private void updateLastImportExecutionTimestamp(final OffsetDateTime timestamp) {
    this.lastImportExecutionTimestamp = timestamp;
  }

  private void updatePendingLastEntityTimestamp(final OffsetDateTime timestamp) {
    timestampOfLastEntity = timestamp;
  }

  private OffsetDateTime reduceByCurrentTimeBackoff(final OffsetDateTime currentDateTime) {
    return currentDateTime.minus(getTipOfTimeBackoffMilliseconds(), ChronoUnit.MILLIS);
  }

  private int getTipOfTimeBackoffMilliseconds() {
    return configurationService.getCurrentTimeBackoffMilliseconds();
  }
}
