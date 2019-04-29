/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.job;

import org.camunda.optimize.dto.optimize.OptimizeDto;
import org.camunda.optimize.service.util.BackoffCalculator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;

/**
 * Represents one page of entities that should be added
 * to elasticsearch.
 */
public abstract class ElasticsearchImportJob<OPT extends OptimizeDto> implements Runnable {
  private final Logger logger = LoggerFactory.getLogger(getClass());

  private final BackoffCalculator backoffCalculator = new BackoffCalculator(1L, 30L);
  private final Runnable callback;

  protected List<OPT> newOptimizeEntities = Collections.emptyList();

  protected ElasticsearchImportJob() {
    // @formatter:off
    this(() -> { });
    // @formatter:on
  }

  protected ElasticsearchImportJob(Runnable callback) {
    this.callback = callback;
  }

  /**
   * Run the import job
   */
  @Override
  public void run() {
    executeImport();
  }

  /**
   * Prepares the given page of entities to be imported.
   *
   * @param pageOfOptimizeEntities that are not already in
   *                               elasticsearch and need to be imported.
   */
  public void setEntitiesToImport(List<OPT> pageOfOptimizeEntities) {
    this.newOptimizeEntities = pageOfOptimizeEntities;
  }

  protected void executeImport() {
    boolean success = false;
    while (!success) {
      try {
        final long persistStart = System.currentTimeMillis();
        persistEntities(newOptimizeEntities);
        final long persistEnd = System.currentTimeMillis();
        logger.debug("Executing import to elasticsearch took [{}] ms", persistEnd - persistStart);
        success = true;
      } catch (Exception e) {
        logger.error("Error while executing import to elasticsearch", e);
        long sleepTime = backoffCalculator.calculateSleepTime();
        try {
          Thread.sleep(sleepTime);
        } catch (InterruptedException exception) {
          //
        }
      }
    }
    callback.run();
  }

  protected abstract void persistEntities(List<OPT> newOptimizeEntities) throws Exception;

}
