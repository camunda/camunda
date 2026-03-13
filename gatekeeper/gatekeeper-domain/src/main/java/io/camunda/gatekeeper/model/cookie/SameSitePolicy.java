/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gatekeeper.model.cookie;

/** Cookie SameSite attribute values. */
public enum SameSitePolicy {
  STRICT("Strict"),
  LAX("Lax"),
  NONE("None");

  private final String headerValue;

  SameSitePolicy(final String headerValue) {
    this.headerValue = headerValue;
  }

  public String headerValue() {
    return headerValue;
  }
}
