/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.engine.importing.service.mediator;

import org.camunda.optimize.service.util.ImportJobExecutor;

public interface EngineImportMediator {

  void importNextPage();

  long getBackoffTimeInMs();

  void resetBackoff();

  boolean canImport();

  ImportJobExecutor getImportJobExecutor();
}