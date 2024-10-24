/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.importing;

import io.camunda.optimize.dto.optimize.OptimizeDto;
import io.camunda.optimize.service.db.DatabaseClient;
import io.camunda.optimize.service.util.BackoffCalculator;
import java.util.Collections;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Represents one page of entities that should be added to database. */
public abstract class DatabaseImportJob<OPT extends OptimizeDto> implements Runnable {

  protected final DatabaseClient databaseClient;
  protected List<OPT> newOptimizeEntities = Collections.emptyList();
  private final Logger logger = LoggerFactory.getLogger(getClass());
  private final BackoffCalculator backoffCalculator = new BackoffCalculator(1L, 30L);
  private final Runnable importCompleteCallback;

  protected DatabaseImportJob(
      final Runnable importCompleteCallback, final DatabaseClient databaseClient) {
    this.importCompleteCallback = importCompleteCallback;
    this.databaseClient = databaseClient;
  }

  /** Run the import job */
  @Override
  public void run() {
    executeImport();
  }

  /**
   * Prepares the given page of entities to be imported.
   *
   * @param pageOfOptimizeEntities that are not already in database and need to be imported.
   */
  public void setEntitiesToImport(final List<OPT> pageOfOptimizeEntities) {
    newOptimizeEntities = pageOfOptimizeEntities;
  }

  protected void executeImport() {
    if (!newOptimizeEntities.isEmpty()) {
      boolean success = false;
      do {
        try {
          final long persistStart = System.currentTimeMillis();
          persistEntities(newOptimizeEntities);
          final long persistEnd = System.currentTimeMillis();
          logger.debug("Executing import to database took [{}] ms", persistEnd - persistStart);
          success = true;
        } catch (final Exception e) {
          logger.error("Error while executing import to database", e);
          final long sleepTime = backoffCalculator.calculateSleepTime();
          try {
            Thread.sleep(sleepTime);
          } catch (final InterruptedException exception) {
            Thread.currentThread().interrupt();
          }
        }
      } while (!success);
    } else {
      logger.debug("Import job with no new entities, import bulk execution is skipped.");
    }
    importCompleteCallback.run();
  }

  protected abstract void persistEntities(List<OPT> newOptimizeEntities) throws Exception;
}
