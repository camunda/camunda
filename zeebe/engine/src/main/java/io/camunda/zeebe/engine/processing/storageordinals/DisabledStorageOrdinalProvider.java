/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.storageordinals;

/**
 * Provider used while ordinal-based storage is disabled: every process instance is assigned ordinal
 * {@code 0}, routing all its records to the main index.
 */
public final class DisabledStorageOrdinalProvider implements StorageOrdinalProvider {

  @Override
  public int getStorageOrdinal() {
    return 0;
  }
}
