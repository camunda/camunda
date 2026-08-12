/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.index;

import io.camunda.zeebe.protocol.record.value.StorageOrdinalKeyRelated;

public class TargetIndexLocator {

  public TargetIndex locateOrdinalIndex(
      final String indexName, final StorageOrdinalKeyRelated ordinalKeyRelated) {
    final var ordinal = ordinalKeyRelated.getStorageOrdinalKey();
    if (ordinal <= OrdinalIndex.DEFAULT_ORDINAL) {
      return TargetIndex.mainIndex(indexName);
    }
    return TargetIndex.ordinalIndex(indexName, ordinal);
  }

  public TargetIndex locateMainIndex(final String indexName) {
    return TargetIndex.mainIndex(indexName);
  }
}
