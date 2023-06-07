/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.entities;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public enum IncidentState {

  ACTIVE("CREATED"), RESOLVED("RESOLVED"), PENDING(null);

  private static Map<String, IncidentState> intentMap = new HashMap<>();

  static {
    Arrays.stream(IncidentState.values()).forEach(is -> intentMap.put(is.getZeebeIntent(), is));
  }

  private String zeebeIntent;

  IncidentState(String zeebeIntent) {
    this.zeebeIntent = zeebeIntent;
  }

  public String getZeebeIntent() {
    return zeebeIntent;
  }

  public static IncidentState createFrom(String zeebeIntent) {
    return intentMap.get(zeebeIntent);
  }
}
