/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.eventplane.maintenance;

import io.camunda.zeebe.eventplane.segment.LogId;
import io.camunda.zeebe.eventplane.store.InvalidObjectNameException;
import io.camunda.zeebe.eventplane.store.ObjectNames;
import io.camunda.zeebe.eventplane.store.ObjectStore;
import io.camunda.zeebe.eventplane.store.StoredObject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Lists the objects below a prefix, all pages of them. */
final class Listing {

  static final String SEGMENT_SUFFIX = ".seg";

  private static final Logger LOG = LoggerFactory.getLogger(Listing.class);
  private static final int PAGE_SIZE = 1000;

  private Listing() {}

  /** The objects below the prefix, in key order. */
  static List<StoredObject> objects(final ObjectStore store, final String prefix)
      throws IOException {
    final var objects = new ArrayList<StoredObject>();
    String startAfter = null;
    while (true) {
      final var page = store.list(prefix, startAfter, PAGE_SIZE);
      objects.addAll(page);
      if (page.size() < PAGE_SIZE) {
        return objects;
      }
      startAfter = page.getLast().key();
    }
  }

  /** The segments in a history's directory of a log, in key order; keys that aren't skipped. */
  static List<StoredSegment> segments(final ObjectStore store, final LogId log, final UUID history)
      throws IOException {
    final var segments = new ArrayList<StoredSegment>();
    for (final var object : objects(store, ObjectNames.segmentsPrefix(log, history))) {
      if (object.key().endsWith(SEGMENT_SUFFIX)) {
        try {
          segments.add(
              new StoredSegment(
                  object.key(), ObjectNames.parseSegment(object.key()), object.size()));
        } catch (final InvalidObjectNameException e) {
          LOG.warn("Skipping the object '{}', which is not named like a segment", object.key(), e);
        }
      }
    }
    return segments;
  }
}
