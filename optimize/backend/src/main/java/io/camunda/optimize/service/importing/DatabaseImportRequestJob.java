/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.importing;

import io.camunda.optimize.dto.optimize.ImportRequestDto;
import io.camunda.optimize.service.db.DatabaseClient;
import io.camunda.optimize.service.util.BackoffCalculator;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DatabaseImportRequestJob implements Runnable {

  private final Logger logger = LoggerFactory.getLogger(getClass());
  private final DatabaseClient client;
  private final ConfigurationService configurationService;
  private final List<ImportRequestDto> requests;
  private final Runnable importCompleteCallback;
  private final BackoffCalculator backoffCalculator = new BackoffCalculator(1L, 30L);

  public DatabaseImportRequestJob(
      final DatabaseClient client,
      final ConfigurationService configurationService,
      final Runnable importCompleteCallback,
      final List<ImportRequestDto> requests) {
    this.client = client;
    this.configurationService = configurationService;
    this.importCompleteCallback = importCompleteCallback;
    this.requests = requests;
  }

  @Override
  public void run() {
    executeImport();
  }

  protected void executeImport() {
    if (!requests.isEmpty()) {
      boolean success = false;
      do {
        try {
          final long persistStart = System.currentTimeMillis();
          persistEntities();
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

  protected void persistEntities() throws Exception {
    client.executeImportRequestsAsBulk(
        "batch-combined", requests, configurationService.getSkipDataAfterNestedDocLimitReached());
  }
}
