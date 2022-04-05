/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.zeebeimport;

public interface ImportListener {

  default void scheduled(ImportBatch importBatch) {}
  void finished(ImportBatch importBatch);
  default void failed(ImportBatch importBatch) {}

}
