package org.camunda.operate.entities;

import io.zeebe.protocol.intent.IncidentIntent;

public enum IncidentState {

  ACTIVE,
  RESOLVED;

  public static IncidentState fromZeebeIncidentIntent(String zeebeIncidentIntent) {
    switch (zeebeIncidentIntent) {
    case "CREATED":
    case "RESOLVE_FAILED":
      return ACTIVE;
    case "RESOLVED":
      return RESOLVED;
    default:
      return ACTIVE;
    }
  }

}
