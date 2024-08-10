/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.stream.impl;

import io.camunda.zeebe.logstreams.log.LoggedEvent;
import io.camunda.zeebe.stream.api.EventFilter;
import java.util.Set;
import org.agrona.collections.LongHashSet;

/** A filter that skips events with positions in a given set of positions. */
public final class SkipPositionsFilter implements EventFilter {

  private final LongHashSet positionsToSkip;

  private SkipPositionsFilter(final LongHashSet positionsToSkip) {

    this.positionsToSkip = positionsToSkip;
  }

  public static SkipPositionsFilter of(final Set<Long> positionsToSkip) {
    final LongHashSet longHashSet = new LongHashSet(positionsToSkip.size());
    longHashSet.addAll(positionsToSkip);
    return new SkipPositionsFilter(longHashSet);
  }

  /**
   * @return true if the event position is not in the set of positions to skip
   */
  @Override
  public boolean applies(final LoggedEvent event) {
    return positionsToSkip.isEmpty() || !positionsToSkip.contains(event.getPosition());
  }
}
