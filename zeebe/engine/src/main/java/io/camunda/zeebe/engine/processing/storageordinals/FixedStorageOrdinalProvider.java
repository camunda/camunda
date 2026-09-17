/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.storageordinals;

public final class FixedStorageOrdinalProvider implements StorageOrdinalProvider {

  private final int storageOrdinal;

  public FixedStorageOrdinalProvider(final int storageOrdinal) {
    this.storageOrdinal = storageOrdinal;
  }

  @Override
  public int getStorageOrdinal() {
    return storageOrdinal;
  }
}
