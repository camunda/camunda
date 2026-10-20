/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.eventplane.service;

import io.camunda.zeebe.eventplane.notification.UploadNotice;
import io.camunda.zeebe.eventplane.store.CursorName;

/**
 * A log, a partition of a physical tenant of a cluster, as the service's endpoints name it: {@code
 * {cluster}/{tenant}/{partition}}, with the plain values, not percent-encoded as in object keys.
 */
record LogId(String cluster, String tenant, int partition) {

  /**
   * Reads {@code {cluster}/{tenant}/{partition}}.
   *
   * @throws InvalidRequestException if the text isn't that
   */
  static LogId parse(final String text) {
    final var parts = text.split("/", -1);
    if (parts.length == 3 && !parts[0].isEmpty() && !parts[1].isEmpty()) {
      try {
        final int partition = Integer.parseInt(parts[2]);
        if (partition >= 1) {
          return new LogId(parts[0], parts[1], partition);
        }
      } catch (final NumberFormatException e) {
        // reported below
      }
    }
    throw new InvalidRequestException(
        "a log as {cluster}/{tenant}/{partition}, with a partition of 1 or more", "'" + text + "'");
  }

  static LogId of(final UploadNotice notice) {
    return new LogId(notice.clusterId(), notice.tenantId(), notice.partition());
  }

  static LogId of(final CursorName cursor) {
    return new LogId(cursor.cluster(), cursor.tenant(), cursor.partition());
  }

  @Override
  public String toString() {
    return cluster + "/" + tenant + "/" + partition;
  }
}
