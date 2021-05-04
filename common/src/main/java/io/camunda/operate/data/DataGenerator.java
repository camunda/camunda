/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.operate.data;

public interface DataGenerator {

  void createZeebeDataAsync(boolean manuallyCalled);

  DataGenerator DO_NOTHING = (boolean manuallyCalled) -> {
  };
}
