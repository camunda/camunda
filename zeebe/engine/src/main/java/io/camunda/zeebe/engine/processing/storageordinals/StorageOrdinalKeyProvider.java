/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.storageordinals;

import io.camunda.zeebe.engine.EngineConfiguration;

public interface StorageOrdinalKeyProvider {

  int getStorageOrdinalKey();

  static FixedStorageOrdinalKeyProvider getFixedProvider(final EngineConfiguration config) {
    return new FixedStorageOrdinalKeyProvider(config.getFixedStorageOrdinalKey());
  }

  static NoopStorageOrdinalKeyProvider getNoopProvider() {
    return new NoopStorageOrdinalKeyProvider();
  }
}
