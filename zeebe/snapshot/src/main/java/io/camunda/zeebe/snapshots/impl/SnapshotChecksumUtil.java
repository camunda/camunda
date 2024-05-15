/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.snapshots.impl;

import io.camunda.zeebe.snapshots.ImmutableChecksumsSFV;
import java.util.SortedMap;

final class SnapshotChecksumUtil {
  static boolean checksumsEqual(
      final ImmutableChecksumsSFV checksum, final ImmutableChecksumsSFV otherChecksum) {
    if (checksum == null || otherChecksum == null) {
      return false;
    }
    return checksumMapsEqual(checksum.getChecksums(), otherChecksum.getChecksums());
  }

  static boolean checksumMapsEqual(
      final SortedMap<String, Long> checksumMap, final SortedMap<String, Long> otherChecksumMap) {
    if (checksumMap.size() != otherChecksumMap.size()) {
      return false;
    }
    return checksumMap.equals(otherChecksumMap);
  }
}
