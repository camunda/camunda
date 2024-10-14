/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.importing;

import static io.camunda.optimize.service.importing.ingested.handler.ExternalVariableUpdateImportIndexHandler.BEGINNING_OF_TIME;

import io.camunda.optimize.dto.optimize.datasource.ZeebeDataSourceDto;
import io.camunda.optimize.dto.optimize.index.PositionBasedImportIndexDto;
import io.camunda.optimize.service.db.reader.importindex.PositionBasedImportIndexReader;
import io.camunda.optimize.service.importing.page.PositionBasedImportPage;
import jakarta.annotation.PostConstruct;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;

@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public abstract class PositionBasedImportIndexHandler
    implements ZeebeImportIndexHandler<PositionBasedImportPage, PositionBasedImportIndexDto> {

  private static final Logger log =
      org.slf4j.LoggerFactory.getLogger(PositionBasedImportIndexHandler.class);
  protected ZeebeDataSourceDto dataSource;
  private OffsetDateTime lastImportExecutionTimestamp = BEGINNING_OF_TIME;
  private OffsetDateTime timestampOfLastPersistedEntity = BEGINNING_OF_TIME;
  private long persistedPositionOfLastEntity = 0;
  private long persistedSequenceOfLastEntity = 0;
  private long pendingPositionOfLastEntity = 0;
  private long pendingSequenceOfLastEntity = 0;
  private boolean hasSeenSequenceField = false;
  @Autowired private PositionBasedImportIndexReader positionBasedImportIndexReader;

  public PositionBasedImportIndexHandler() {}

  @PostConstruct
  protected void init() {
    final Optional<PositionBasedImportIndexDto> dto =
        positionBasedImportIndexReader.getImportIndex(getDatabaseDocID(), dataSource);
    if (dto.isPresent()) {
      final PositionBasedImportIndexDto loadedImportIndex = dto.get();
      updateLastPersistedEntityPositionAndSequence(
          loadedImportIndex.getPositionOfLastEntity(), loadedImportIndex.getSequenceOfLastEntity());
      updatePendingLastEntityPositionAndSequence(
          loadedImportIndex.getPositionOfLastEntity(), loadedImportIndex.getSequenceOfLastEntity());
      updateLastImportExecutionTimestamp(loadedImportIndex.getLastImportExecutionTimestamp());
      updateTimestampOfLastPersistedEntity(loadedImportIndex.getTimestampOfLastEntity());
      hasSeenSequenceField = loadedImportIndex.isHasSeenSequenceField();
    }
  }

  @Override
  public ZeebeDataSourceDto getDataSource() {
    return dataSource;
  }

  @Override
  public PositionBasedImportPage getNextPage() {
    final PositionBasedImportPage page = new PositionBasedImportPage();
    page.setPosition(pendingPositionOfLastEntity);
    page.setSequence(pendingSequenceOfLastEntity);
    page.setHasSeenSequenceField(hasSeenSequenceField);
    return page;
  }

  @Override
  public PositionBasedImportIndexDto getIndexStateDto() {
    final PositionBasedImportIndexDto indexToStore = new PositionBasedImportIndexDto();
    indexToStore.setDataSource(dataSource);
    indexToStore.setLastImportExecutionTimestamp(lastImportExecutionTimestamp);
    indexToStore.setPositionOfLastEntity(persistedPositionOfLastEntity);
    indexToStore.setSequenceOfLastEntity(persistedSequenceOfLastEntity);
    indexToStore.setTimestampOfLastEntity(timestampOfLastPersistedEntity);
    indexToStore.setHasSeenSequenceField(hasSeenSequenceField);
    indexToStore.setEsTypeIndexRefersTo(getDatabaseDocID());
    return indexToStore;
  }

  @Override
  public void resetImportIndex() {
    lastImportExecutionTimestamp = BEGINNING_OF_TIME;
    timestampOfLastPersistedEntity = BEGINNING_OF_TIME;
    persistedPositionOfLastEntity = 0;
    persistedSequenceOfLastEntity = 0;
    pendingPositionOfLastEntity = 0;
    pendingSequenceOfLastEntity = 0;
    hasSeenSequenceField = false;
  }

  /** States the database document name where the index information should be stored. */
  protected abstract String getDatabaseDocID();

  public void updateLastPersistedEntityPositionAndSequence(
      final long position, final long sequence) {
    persistedPositionOfLastEntity = position;
    persistedSequenceOfLastEntity = sequence;
    if (!hasSeenSequenceField && persistedSequenceOfLastEntity > 0) {
      hasSeenSequenceField = true;
    }
  }

  public void updatePendingLastEntityPositionAndSequence(final long position, final long sequence) {
    pendingPositionOfLastEntity = position;
    pendingSequenceOfLastEntity = sequence;
    if (!hasSeenSequenceField && pendingSequenceOfLastEntity > 0) {
      log.info(
          "First Zeebe record with sequence field for import type {} has been imported."
              + " Zeebe records will now be fetched based on sequence.",
          getDatabaseDocID());
      hasSeenSequenceField = true;
    }
  }

  public void updateLastImportExecutionTimestamp(final OffsetDateTime timestamp) {
    lastImportExecutionTimestamp = timestamp;
  }

  public void updateTimestampOfLastPersistedEntity(final OffsetDateTime timestamp) {
    timestampOfLastPersistedEntity = timestamp;
  }

  public OffsetDateTime getLastImportExecutionTimestamp() {
    return lastImportExecutionTimestamp;
  }

  public OffsetDateTime getTimestampOfLastPersistedEntity() {
    return timestampOfLastPersistedEntity;
  }

  public long getPersistedPositionOfLastEntity() {
    return persistedPositionOfLastEntity;
  }

  public long getPersistedSequenceOfLastEntity() {
    return persistedSequenceOfLastEntity;
  }

  public long getPendingPositionOfLastEntity() {
    return pendingPositionOfLastEntity;
  }

  public long getPendingSequenceOfLastEntity() {
    return pendingSequenceOfLastEntity;
  }

  public boolean isHasSeenSequenceField() {
    return hasSeenSequenceField;
  }

  public PositionBasedImportIndexReader getPositionBasedImportIndexReader() {
    return positionBasedImportIndexReader;
  }
}
