/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.engine.importing.service;

import java.util.List;

public interface ImportService<T> {

  default void executeImport(List<T> pageOfEngineEntities) {
    executeImport(pageOfEngineEntities, () -> {
    });
  }

  void executeImport(List<T> pageOfEngineEntities, Runnable callback);
}
