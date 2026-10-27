/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.analytics;

import java.util.Locale;
import java.util.Set;

/**
 * Categories of analytics events. Used to control which kinds of metrics are exported.
 *
 * <ul>
 *   <li>{@link #CONTRACTUAL} — commercial/licence metrics (process instances, decision instances,
 *       task users, tenant created/deleted)
 *   <li>{@link #OPTIONAL} — non-commercial product usage metrics
 * </ul>
 */
public enum AnalyticsCategory {
  CONTRACTUAL,
  OPTIONAL;

  private static final Set<AnalyticsCategory> ALL = Set.of(values());

  /** Returns an immutable set containing all categories. */
  public static Set<AnalyticsCategory> all() {
    return ALL;
  }

  /** Case-insensitive parse from string. */
  static AnalyticsCategory parse(final String value) {
    return valueOf(value.toUpperCase(Locale.ROOT));
  }
}
