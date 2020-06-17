/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing.engine.service;

import org.camunda.optimize.service.es.ElasticsearchImportJobExecutor;

import java.util.List;

public interface ImportService<T> {
  void executeImport(List<T> pageOfEngineEntities, Runnable importCompleteCallback);

  ElasticsearchImportJobExecutor getElasticsearchImportJobExecutor();

  default void shutdown() {
    getElasticsearchImportJobExecutor().shutdown();
  }

  default boolean hasPendingImportJobs() {
    return getElasticsearchImportJobExecutor().isActive();
  }
}
