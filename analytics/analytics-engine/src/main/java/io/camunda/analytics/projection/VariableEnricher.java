/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.projection;

import io.camunda.analytics.dataset.EnrichmentTiming;
import java.util.Map;

/**
 * Selects the variable snapshot to stamp on a fact for a given {@link EnrichmentTiming}. The pure
 * seam both the projector and the (later) dataset compiler share, so they agree on which snapshot a
 * timing means. The projector supplies whichever snapshots it holds (a {@code null} snapshot — e.g.
 * the completion snapshot before the instance has completed — resolves to empty).
 */
public final class VariableEnricher {

  private VariableEnricher() {}

  public static Map<String, String> select(
      final EnrichmentTiming timing,
      final Map<String, String> atCreate,
      final Map<String, String> atEvent,
      final Map<String, String> atComplete) {
    final Map<String, String> chosen =
        switch (timing) {
          case EVENT_TIME -> atEvent;
          case PI_CREATE -> atCreate;
          case PI_COMPLETE -> atComplete;
        };
    return chosen == null ? Map.of() : chosen;
  }
}
