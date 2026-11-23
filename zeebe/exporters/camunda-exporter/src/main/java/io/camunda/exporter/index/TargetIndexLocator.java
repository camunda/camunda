/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.index;

import io.camunda.webapps.schema.descriptors.IndexTemplateDescriptor;
import io.camunda.zeebe.protocol.record.value.StorageOrdinalRelated;
import org.jspecify.annotations.NonNull;

public class TargetIndexLocator {

  public TargetIndex locateOrdinalIndex(
      final IndexTemplateDescriptor template, final int storageOrdinal) {
    return locateOrdinalIndex(template.getFullQualifiedName(), storageOrdinal);
  }

  public TargetIndex locateOrdinalIndex(
      final String indexName, final StorageOrdinalRelated ordinalRelated) {
    final var ordinal = ordinalRelated.getStorageOrdinal();
    return locateOrdinalIndex(indexName, ordinal);
  }

  private static @NonNull TargetIndex locateOrdinalIndex(
      final String indexName, final int ordinal) {
    if (ordinal <= OrdinalIndex.DEFAULT_ORDINAL) {
      return TargetIndex.mainIndex(indexName);
    }
    return TargetIndex.ordinalIndex(indexName, ordinal);
  }

  public TargetIndex locateMainIndex(final String indexName) {
    return TargetIndex.mainIndex(indexName);
  }
}
