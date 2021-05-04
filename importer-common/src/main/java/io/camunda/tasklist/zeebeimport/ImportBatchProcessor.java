/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.tasklist.zeebeimport;

import io.camunda.tasklist.exceptions.PersistenceException;

public interface ImportBatchProcessor {

  void performImport(ImportBatch importBatch) throws PersistenceException;

  String getZeebeVersion();
}
