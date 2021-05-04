/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.operate.zeebeimport;

public interface ImportListener {

  void finished(ImportBatch importBatch);
  void failed(ImportBatch importBatch);

}
