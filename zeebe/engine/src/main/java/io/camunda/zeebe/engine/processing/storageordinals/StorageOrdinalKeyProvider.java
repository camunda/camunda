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

  /**
   * Returns the storage ordinal key to assign to a process instance that is being created. The
   * value may change between calls once ordinal rollover is introduced, so it must be read once at
   * instance creation and inherited by all follow-up records, never re-resolved later.
   */
  int getStorageOrdinalKey();

  static StorageOrdinalKeyProvider getFixedProvider(final EngineConfiguration config) {
    return new FixedStorageOrdinalKeyProvider(config.getFixedStorageOrdinalKey());
  }

  static StorageOrdinalKeyProvider getDisabledProvider() {
    return new DisabledStorageOrdinalKeyProvider();
  }
}
