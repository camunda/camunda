/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.api;

/**
 * A marker (file or object) indicating the start or end of a backup range, or a deletion within a
 * backup range.
 */
public sealed interface BackupRangeMarker {
  long checkpointId();

  static String toName(final BackupRangeMarker marker) {
    final var extension =
        switch (marker) {
          case final Start ignored -> ".start";
          case final End ignored -> ".end";
          case final Deletion ignored -> ".deletion";
        };
    return marker.checkpointId() + extension;
  }

  static BackupRangeMarker fromName(final String name) {
    final var dotIndex = name.indexOf('.');
    if (dotIndex < 0) {
      return null;
    }

    final var checkpointId = Long.parseLong(name.substring(0, dotIndex));
    return switch (name.substring(dotIndex + 1)) {
      case "start" -> new Start(checkpointId);
      case "end" -> new End(checkpointId);
      case "deletion" -> new Deletion(checkpointId);
      default -> null;
    };
  }

  record Start(long checkpointId) implements BackupRangeMarker {}

  record End(long checkpointId) implements BackupRangeMarker {}

  record Deletion(long checkpointId) implements BackupRangeMarker {}
}
