/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.operate.entities;

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
