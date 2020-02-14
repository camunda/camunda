/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing.engine.service;

import java.util.List;

public interface ImportService<T> {

  default void executeImport(List<T> pageOfEngineEntities) {
    // @formatter:off
    executeImport(pageOfEngineEntities, () -> {});
    // @formatter:on
  }

  void executeImport(List<T> pageOfEngineEntities, Runnable callback);
}
