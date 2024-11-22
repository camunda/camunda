/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.zeebeimport.post;

import static java.time.temporal.ChronoUnit.MILLIS;

import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.util.BackoffIdleStrategy;
import io.camunda.operate.zeebe.ImportValueType;
import io.camunda.operate.zeebeimport.ImportPositionHolder;
import io.camunda.webapps.schema.entities.operate.ImportPositionEntity;
import io.camunda.webapps.schema.entities.operate.IncidentEntity;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
public abstract class AbstractIncidentPostImportAction implements PostImportAction {
  public static final long BACKOFF = 5000L;
  public static final long DELAY_BETWEEN_TWO_RUNS = 3000L;
  private static final Logger LOGGER =
      LoggerFactory.getLogger(AbstractIncidentPostImportAction.class);
  protected int partitionId;

  @Autowired
  @Qualifier("postImportThreadPoolScheduler")
  protected ThreadPoolTaskScheduler postImportScheduler;

  @Autowired protected OperateProperties operateProperties;

  @Autowired protected ImportPositionHolder importPositionHolder;
  protected ImportPositionEntity lastProcessedPosition;
  private final BackoffIdleStrategy errorStrategy;

  public AbstractIncidentPostImportAction(final int partitionId) {
    this.partitionId = partitionId;
    errorStrategy = new BackoffIdleStrategy(BACKOFF, 1.2f, 10_000);
  }

  @Override
  public boolean performOneRound() throws IOException {
    final List<IncidentEntity> pendingIncidents = processPendingIncidents();
    errorStrategy.reset();
    final boolean smthWasProcessed = pendingIncidents.size() > 0;
    return smthWasProcessed;
  }

  @Override
  public void clearCache() {
    lastProcessedPosition = null;
  }

  @Override
  public void run() {
    if (operateProperties.getImporter().isPostImportEnabled()) {
      try {
        if (performOneRound()) {
          postImportScheduler.schedule(this, Instant.now().plus(DELAY_BETWEEN_TWO_RUNS, MILLIS));
        } else {
          postImportScheduler.schedule(this, Instant.now().plus(BACKOFF, MILLIS));
        }
      } catch (final Exception ex) {
        LOGGER.error(
            String.format(
                "Exception occurred when performing post import for partition %d: %s. Will be retried...",
                partitionId, ex.getMessage()),
            ex);
        errorStrategy.idle();
        postImportScheduler.schedule(this, Instant.now().plus(errorStrategy.idleTime(), MILLIS));
      }
    }
  }

  protected abstract PendingIncidentsBatch getPendingIncidents(
      final AdditionalData data, final Long lastProcessedPosition);

  protected abstract void searchForInstances(
      final List<IncidentEntity> incidents, final AdditionalData data) throws IOException;

  protected abstract boolean processIncidents(AdditionalData data, PendingIncidentsBatch batch)
      throws PersistenceException;

  protected List<IncidentEntity> processPendingIncidents() throws IOException {
    if (lastProcessedPosition == null) {
      lastProcessedPosition =
          importPositionHolder.getLatestLoadedPosition(
              ImportValueType.INCIDENT.getAliasTemplate(), partitionId);
    }

    final AdditionalData data = new AdditionalData();

    final PendingIncidentsBatch batch =
        getPendingIncidents(data, lastProcessedPosition.getPostImporterPosition());

    if (batch.getIncidents().isEmpty()) {
      return new ArrayList<>();
    }

    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug("Processing pending incidents: " + batch.getIncidents());
    }

    try {

      searchForInstances(batch.getIncidents(), data);

      final boolean done = processIncidents(data, batch);

      if (batch.getIncidents().size() > 0 && done) {
        lastProcessedPosition.setPostImporterPosition(batch.getLastProcessedPosition());
        importPositionHolder.recordLatestPostImportedPosition(lastProcessedPosition);
      }

      if (LOGGER.isDebugEnabled()) {
        LOGGER.debug("Finished processing");
      }

    } catch (final IOException | PersistenceException e) {
      final String message =
          String.format(
              "Exception occurred, while processing pending incidents: %s", e.getMessage());
      throw new OperateRuntimeException(message, e);
    }
    return batch.getIncidents();
  }
}
