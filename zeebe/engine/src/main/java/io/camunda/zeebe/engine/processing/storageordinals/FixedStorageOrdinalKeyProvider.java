/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.storageordinals;

public class FixedStorageOrdinalKeyProvider implements StorageOrdinalKeyProvider {

  private final int storageOrdinalKey;

  public FixedStorageOrdinalKeyProvider(final int storageOrdinalKey) {
    this.storageOrdinalKey = storageOrdinalKey;
  }

  @Override
  public int getStorageOrdinalKey() {
    return storageOrdinalKey;
  }
}
