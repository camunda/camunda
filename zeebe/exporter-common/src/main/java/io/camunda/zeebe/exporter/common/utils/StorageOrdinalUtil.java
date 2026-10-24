/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.common.utils;

import io.camunda.zeebe.protocol.record.value.StorageOrdinalRelated;
import java.util.Optional;

public final class StorageOrdinalUtil {
  private StorageOrdinalUtil() {}

  public static boolean isOrdinalControlled(final StorageOrdinalRelated storageOrdinalRelated) {
    final var ordinal = storageOrdinalRelated.getStorageOrdinal();
    if (ordinal < StorageOrdinalRelated.NOT_ORDINAL_CONTROLLED) {
      throw new IllegalArgumentException("Invalid storage ordinal: " + ordinal);
    }
    return ordinal != StorageOrdinalRelated.MAIN_INDEX
        && ordinal != StorageOrdinalRelated.NOT_ORDINAL_CONTROLLED;
  }

  public static Optional<Integer> ordinalIfOrdinalControlled(
      final StorageOrdinalRelated storageOrdinalRelated) {
    if (isOrdinalControlled(storageOrdinalRelated)) {
      return Optional.of(storageOrdinalRelated.getStorageOrdinal());
    }
    return Optional.empty();
  }
}
