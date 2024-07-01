/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.importing;

import com.fasterxml.jackson.annotation.JsonValue;

public enum IdentityLinkLogType {
  CANDIDATE("candidate"),
  ASSIGNEE("assignee"),
  OWNER("owner"),
  ;

  private final String id;

  IdentityLinkLogType(final String id) {
    this.id = id;
  }

  @JsonValue
  public String getId() {
    return id;
  }

  @Override
  public String toString() {
    return getId();
  }

  public static IdentityLinkLogType fromString(final String id) {
    return valueOf(id.toUpperCase());
  }
}
