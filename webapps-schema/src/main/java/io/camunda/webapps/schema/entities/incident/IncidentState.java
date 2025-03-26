/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.schema.entities.incident;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public enum IncidentState {
  ACTIVE("CREATED"),
  MIGRATED("MIGRATED"),
  RESOLVED("RESOLVED"),
  PENDING(null);

  private static final Map<String, IncidentState> intentMap = new HashMap<>();

  static {
    Arrays.stream(IncidentState.values()).forEach(is -> intentMap.put(is.getZeebeIntent(), is));
  }

  private final String zeebeIntent;

  IncidentState(final String zeebeIntent) {
    this.zeebeIntent = zeebeIntent;
  }

  public static IncidentState createFrom(final String zeebeIntent) {
    return intentMap.get(zeebeIntent);
  }

  public String getZeebeIntent() {
    return zeebeIntent;
  }
}
