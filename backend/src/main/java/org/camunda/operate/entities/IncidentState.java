package org.camunda.operate.entities;

public enum IncidentState {

  ACTIVE,
  RESOLVED,
  DELETED;

  public static IncidentState fromZeebeIncidentState(io.zeebe.client.api.events.IncidentState zeebeIncidentState) {
    switch (zeebeIncidentState) {
    case CREATED:
    case RESOLVE_FAILED:
      return ACTIVE;
    case RESOLVED:
      return RESOLVED;
    case DELETED:
      return DELETED;
    default:
      return ACTIVE;
    }
  }

}
