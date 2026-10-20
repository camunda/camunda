/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.eventplane.service;

import io.camunda.zeebe.eventplane.store.InvalidObjectNameException;
import io.camunda.zeebe.eventplane.store.ObjectNames;
import io.camunda.zeebe.eventplane.store.ObjectStore;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reads a consumer group's workers and cursors from the store, and works out how far each cursor is
 * behind its log. The store is the only source: whatever a worker wrote is what the view shows. An
 * object that isn't what the contract says is skipped and logged, so that one broken object doesn't
 * hide the group.
 */
final class GroupView {

  private static final Logger LOG = LoggerFactory.getLogger(GroupView.class);
  private static final int PAGE_SIZE = 1000;
  private static final String JSON = ".json";

  private final ObjectStore store;
  private final NewestIndex newestIndex;

  GroupView(final ObjectStore store, final NoticeBoard notices) {
    this.store = store;
    newestIndex = new NewestIndex(store, notices);
  }

  /**
   * The group's workers and cursors, or empty if the store has no object of the group.
   *
   * @throws IOException if the store can't be read
   */
  Optional<GroupStatus> read(final String group) throws IOException {
    final var workerKeys = listJson(ObjectNames.workersPrefix(group));
    final var cursorKeys = listJson(ObjectNames.cursorsPrefix(group));
    if (workerKeys.isEmpty()
        && cursorKeys.isEmpty()
        && store.head(ObjectNames.group(group)).isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(new GroupStatus(group, workers(workerKeys), cursors(cursorKeys)));
  }

  private List<GroupStatus.Worker> workers(final List<String> keys) throws IOException {
    final var workers = new ArrayList<GroupStatus.Worker>();
    for (final var key : keys) {
      final var content = store.get(key);
      if (content.isEmpty()) {
        continue; // deleted since the listing
      }
      try {
        final var heartbeat = GroupObjects.heartbeat(key, content.get());
        workers.add(new GroupStatus.Worker(heartbeat.worker(), heartbeat.beat()));
      } catch (final InvalidGroupObjectException e) {
        LOG.warn("Skipped a worker: {}", e.getMessage());
      }
    }
    return workers;
  }

  private List<GroupStatus.Cursor> cursors(final List<String> keys) throws IOException {
    final var cursors = new ArrayList<GroupStatus.Cursor>();
    for (final var key : keys) {
      final var content = store.get(key);
      if (content.isEmpty()) {
        continue; // deleted since the listing
      }
      try {
        cursors.add(cursor(key, content.get()));
      } catch (final InvalidGroupObjectException | InvalidObjectNameException e) {
        LOG.warn("Skipped a cursor: {}", e.getMessage());
      }
    }
    return cursors;
  }

  private GroupStatus.Cursor cursor(final String key, final byte[] content) throws IOException {
    final var log = LogId.of(ObjectNames.parseCursor(key));
    final var cursor = GroupObjects.cursor(key, content);
    final var newest = newestIndex.of(log, cursor.history());
    final long lag = newest.isPresent() ? Math.max(0, newest.getAsLong() - cursor.index()) : 0;
    return new GroupStatus.Cursor(
        log.toString(),
        cursor.history(),
        cursor.index(),
        cursor.position(),
        cursor.worker(),
        newest.isPresent() ? newest.getAsLong() : null,
        lag);
  }

  /** The keys of the JSON objects below a prefix, in key order. */
  private List<String> listJson(final String prefix) throws IOException {
    final var keys = new ArrayList<String>();
    @Nullable String startAfter = null;
    while (true) {
      final var page = store.list(prefix, startAfter, PAGE_SIZE);
      for (final var object : page) {
        if (object.key().endsWith(JSON)) {
          keys.add(object.key());
        }
      }
      if (page.size() < PAGE_SIZE) {
        return keys;
      }
      startAfter = page.getLast().key();
    }
  }
}
