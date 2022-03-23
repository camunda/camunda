/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.importing;

import org.camunda.optimize.service.importing.engine.mediator.MediatorRank;

import java.util.concurrent.CompletableFuture;

public interface ImportMediator {

  CompletableFuture<Void> runImport();

  long getBackoffTimeInMs();

  void resetBackoff();

  boolean canImport();

  boolean hasPendingImportJobs();

  void shutdown();

  MediatorRank getRank();

}
