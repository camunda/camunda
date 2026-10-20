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
import java.util.OptionalLong;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Finds the last index of the newest segment of a log's history: from the newest notice if it is of
 * that history, and otherwise by listing the history's directory in the store.
 */
final class NewestIndex {

  private static final Logger LOG = LoggerFactory.getLogger(NewestIndex.class);
  private static final int PAGE_SIZE = 1000;

  private final ObjectStore store;
  private final NoticeBoard notices;

  NewestIndex(final ObjectStore store, final NoticeBoard notices) {
    this.store = store;
    this.notices = notices;
  }

  /** The newest index in the history of the log, or empty if the service knows of no segment. */
  OptionalLong of(final LogId log, final UUID history) throws IOException {
    final var notice = notices.newest(log);
    if (notice.isPresent() && notice.get().history().equals(history)) {
      return OptionalLong.of(notice.get().lastIndex());
    }
    return listed(log, history);
  }

  /**
   * The highest last index of the segments in the history's directory. Keys sort by first index,
   * but a rollup can end after a segment that starts later, so every key counts.
   */
  private OptionalLong listed(final LogId log, final UUID history) throws IOException {
    final var prefix =
        ObjectNames.segmentsPrefix(log.cluster(), log.tenant(), log.partition(), history);
    var newest = OptionalLong.empty();
    @Nullable String startAfter = null;
    while (true) {
      final var page = store.list(prefix, startAfter, PAGE_SIZE);
      for (final var object : page) {
        try {
          final long lastIndex = ObjectNames.parseSegment(object.key()).lastIndex();
          if (newest.isEmpty() || lastIndex > newest.getAsLong()) {
            newest = OptionalLong.of(lastIndex);
          }
        } catch (final InvalidObjectNameException e) {
          LOG.debug("Skipped {}, which is no segment: {}", object.key(), e.getMessage());
        }
      }
      if (page.size() < PAGE_SIZE) {
        return newest;
      }
      startAfter = page.getLast().key();
    }
  }
}
