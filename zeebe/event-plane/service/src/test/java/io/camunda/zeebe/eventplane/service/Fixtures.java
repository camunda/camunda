/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.eventplane.service;

import io.camunda.zeebe.eventplane.notification.UploadNotice;
import io.camunda.zeebe.eventplane.store.ObjectNames;
import io.camunda.zeebe.eventplane.store.ObjectStore;
import io.camunda.zeebe.eventplane.store.Ulid;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** The notices and the store objects that the tests make up. */
final class Fixtures {

  static final UUID HISTORY = UUID.fromString("11111111-1111-1111-1111-111111111111");
  static final UUID OTHER_HISTORY = UUID.fromString("22222222-2222-2222-2222-222222222222");

  private Fixtures() {}

  /** The notice of a segment of the log that holds the indexes. */
  static UploadNotice notice(
      final String cluster,
      final String tenant,
      final int partition,
      final UUID history,
      final long firstIndex,
      final long lastIndex) {
    return new UploadNotice(
        cluster,
        tenant,
        partition,
        history,
        ObjectNames.segment(
            cluster, tenant, partition, history, firstIndex, lastIndex, 0, Ulid.next()),
        firstIndex,
        lastIndex,
        firstIndex,
        lastIndex,
        1024);
  }

  /** A worker's heartbeat, as the contract shows it, with a field that the service doesn't know. */
  static void heartbeat(
      final ObjectStore store, final String group, final String worker, final long beat)
      throws IOException {
    put(
        store,
        ObjectNames.worker(group, worker),
        """
        {"version":1,"worker":"%s","beat":%d,"unknown":"skipped"}"""
            .formatted(worker, beat));
  }

  /** A group's cursor in a log, as the contract shows it. */
  static void cursor(
      final ObjectStore store,
      final String group,
      final String cluster,
      final String tenant,
      final int partition,
      final UUID history,
      final long index,
      final long position,
      final String worker)
      throws IOException {
    put(
        store,
        ObjectNames.cursor(group, cluster, tenant, partition),
        """
        {"version":1,"history":"%s","index":%d,"position":%d,"worker":"%s"}"""
            .formatted(history, index, position, worker));
  }

  /** An empty segment object with the indexes in its name. */
  static void segment(
      final ObjectStore store,
      final String cluster,
      final String tenant,
      final int partition,
      final UUID history,
      final long firstIndex,
      final long lastIndex,
      final int level)
      throws IOException {
    store.put(
        ObjectNames.segment(
            cluster, tenant, partition, history, firstIndex, lastIndex, level, Ulid.next()),
        new byte[0]);
  }

  static void put(final ObjectStore store, final String key, final String content)
      throws IOException {
    store.put(key, content.getBytes(StandardCharsets.UTF_8));
  }
}
