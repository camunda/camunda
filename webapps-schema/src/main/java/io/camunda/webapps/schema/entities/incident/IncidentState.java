/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.schema.entities.incident;

import java.util.HashMap;
import java.util.Map;

public enum IncidentState {
  ACTIVE("CREATED", "RESOLVE"),
  MIGRATED("MIGRATED"),
  RESOLVED("RESOLVED"),
  PENDING(null);

  private static final Map<String, IncidentState> INTENT_MAP = new HashMap<>();

  static {
    for (final IncidentState state : IncidentState.values()) {
      for (final String intent : state.zeebeIntents) {
        if (intent != null) {
          INTENT_MAP.put(intent, state);
        }
      }
    }
  }

  private final String[] zeebeIntents;

  IncidentState(final String... zeebeIntents) {
    this.zeebeIntents = zeebeIntents != null ? zeebeIntents : new String[0];
  }

  public static IncidentState createFrom(final String zeebeIntent) {
    return INTENT_MAP.get(zeebeIntent);
  }
}
