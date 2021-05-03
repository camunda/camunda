/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.engine.processing.streamprocessor.sideeffect;

import java.util.ArrayList;
import java.util.List;

public final class SideEffectQueue implements SideEffectProducer, SideEffects {
  private final List<SideEffectProducer> sideEffects = new ArrayList<>();

  public void clear() {
    sideEffects.clear();
  }

  @Override
  public boolean flush() {
    if (sideEffects.isEmpty()) {
      return true;
    }

    boolean flushed = true;

    // iterates once over everything, setting the side effect to null to avoid reprocessing if we
    // couldn't flush and this is retried. considered lesser evil than removing from the list and
    // having to shuffle elements around in the list.
    for (int i = 0; i < sideEffects.size(); i++) {
      final SideEffectProducer sideEffect = sideEffects.get(i);

      if (sideEffect != null) {
        if (sideEffect.flush()) {
          sideEffects.set(i, null);
        } else {
          flushed = false;
        }
      }
    }

    // reset list size to 0 if everything was flushed
    if (flushed) {
      sideEffects.clear();
    }

    return flushed;
  }

  @Override
  public void add(final SideEffectProducer sideEffectProducer) {
    sideEffects.add(sideEffectProducer);
  }
}
