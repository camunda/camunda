/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.test.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;

public class SynchronizationElasticsearchImportJob implements Runnable {

  private static final Logger logger = LoggerFactory.getLogger(SynchronizationElasticsearchImportJob.class);

  private final CompletableFuture<Void> toComplete;

  public SynchronizationElasticsearchImportJob(CompletableFuture<Void> toComplete) {
    this.toComplete = toComplete;
  }

  @Override
  public void run() {
    logger.debug("Synchronization job was successfully executed.");
    toComplete.complete(null);
  }
}
