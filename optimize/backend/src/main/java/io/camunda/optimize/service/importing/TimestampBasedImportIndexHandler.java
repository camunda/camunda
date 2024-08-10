/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.importing;

import io.camunda.optimize.service.importing.page.TimestampBasedImportPage;
import io.camunda.optimize.service.security.util.LocalDateUtil;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

public abstract class TimestampBasedImportIndexHandler<INDEX_DTO>
    implements EngineImportIndexHandler<TimestampBasedImportPage, INDEX_DTO> {

  public static final OffsetDateTime BEGINNING_OF_TIME =
      OffsetDateTime.ofInstant(Instant.EPOCH, ZoneId.systemDefault());

  protected Logger logger = LoggerFactory.getLogger(getClass());

  @Autowired protected ConfigurationService configurationService;

  @Getter protected OffsetDateTime timestampOfLastEntity = BEGINNING_OF_TIME;

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

  public void updateLastImportExecutionTimestamp() {
    updateLastImportExecutionTimestamp(LocalDateUtil.getCurrentDateTime());
  }

  @Override
  public TimestampBasedImportPage getNextPage() {
    final TimestampBasedImportPage page = new TimestampBasedImportPage();
    page.setTimestampOfLastEntity(getTimestampOfLastEntity());
    return page;
  }

  @Override
  public void resetImportIndex() {
    updateLastImportExecutionTimestamp(BEGINNING_OF_TIME);
    updateLastPersistedEntityTimestamp(BEGINNING_OF_TIME);
    updatePendingLastEntityTimestamp(BEGINNING_OF_TIME);
  }

  protected abstract void updateLastPersistedEntityTimestamp(OffsetDateTime timestamp);

  protected abstract void updateLastImportExecutionTimestamp(OffsetDateTime timestamp);

  protected void updatePendingLastEntityTimestamp(final OffsetDateTime timestamp) {
    timestampOfLastEntity = timestamp;
  }

  protected OffsetDateTime reduceByCurrentTimeBackoff(final OffsetDateTime currentDateTime) {
    return currentDateTime.minus(getTipOfTimeBackoffMilliseconds(), ChronoUnit.MILLIS);
  }

  protected int getTipOfTimeBackoffMilliseconds() {
    return configurationService.getCurrentTimeBackoffMilliseconds();
  }
}
