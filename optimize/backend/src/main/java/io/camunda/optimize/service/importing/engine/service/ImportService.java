/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.importing.engine.service;

import io.camunda.optimize.service.importing.DatabaseImportJobExecutor;
import java.util.List;

public interface ImportService<T> {

  void executeImport(List<T> pageOfEngineEntities, Runnable importCompleteCallback);

  DatabaseImportJobExecutor getDatabaseImportJobExecutor();

  default void shutdown() {
    getDatabaseImportJobExecutor().shutdown();
  }

  default boolean hasPendingImportJobs() {
    return getDatabaseImportJobExecutor().isActive();
  }
}
