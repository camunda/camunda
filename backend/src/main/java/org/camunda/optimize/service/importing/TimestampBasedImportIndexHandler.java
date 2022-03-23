/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.importing;

import org.camunda.optimize.service.importing.page.TimestampBasedImportPage;
import org.camunda.optimize.service.security.util.LocalDateUtil;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

public abstract class TimestampBasedImportIndexHandler<INDEX_DTO>
  implements EngineImportIndexHandler<TimestampBasedImportPage, INDEX_DTO> {

  public static final OffsetDateTime BEGINNING_OF_TIME = OffsetDateTime.ofInstant(
    Instant.EPOCH,
    ZoneId.systemDefault()
  );

  protected Logger logger = LoggerFactory.getLogger(getClass());

  @Autowired
  protected ConfigurationService configurationService;

  protected OffsetDateTime timestampOfLastEntity = BEGINNING_OF_TIME;

  public void updateTimestampOfLastEntity(final OffsetDateTime timestamp) {
    final OffsetDateTime backOffWindowStart = reduceByCurrentTimeBackoff(
      OffsetDateTime.ofInstant(Instant.now(), ZoneId.systemDefault())
    );
    if (timestamp.isAfter(backOffWindowStart)) {
      logger.info(
        "Timestamp is in the current time backoff window of {}ms, will save begin of backoff window as last timestamp",
        getTipOfTimeBackoffMilliseconds()
      );
      updateLastPersistedEntityTimestamp(backOffWindowStart);
    } else {
      updateLastPersistedEntityTimestamp(timestamp);
    }
  }

  public void updatePendingTimestampOfLastEntity(final OffsetDateTime timestamp) {
    final OffsetDateTime backOffWindowStart = reduceByCurrentTimeBackoff(
      OffsetDateTime.ofInstant(Instant.now(), ZoneId.systemDefault())
    );
    if (timestamp.isAfter(backOffWindowStart)) {
      logger.info(
        "Timestamp is in the current time backoff window of {}ms, will save begin of backoff window as last timestamp",
        getTipOfTimeBackoffMilliseconds()
      );
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
    TimestampBasedImportPage page = new TimestampBasedImportPage();
    page.setTimestampOfLastEntity(getTimestampOfLastEntity());
    return page;
  }

  @Override
  public void resetImportIndex() {
    updateLastImportExecutionTimestamp(BEGINNING_OF_TIME);
    updateLastPersistedEntityTimestamp(BEGINNING_OF_TIME);
    updatePendingLastEntityTimestamp(BEGINNING_OF_TIME);
  }

  public OffsetDateTime getTimestampOfLastEntity() {
    return timestampOfLastEntity;
  }

  abstract protected void updateLastPersistedEntityTimestamp(OffsetDateTime timestamp);

  abstract protected void updateLastImportExecutionTimestamp(OffsetDateTime timestamp);

  protected void updatePendingLastEntityTimestamp(final OffsetDateTime timestamp) {
    this.timestampOfLastEntity = timestamp;
  }

  protected OffsetDateTime reduceByCurrentTimeBackoff(OffsetDateTime currentDateTime) {
    return currentDateTime.minus(getTipOfTimeBackoffMilliseconds(), ChronoUnit.MILLIS);
  }

  protected int getTipOfTimeBackoffMilliseconds() {
    return configurationService.getCurrentTimeBackoffMilliseconds();
  }
}
