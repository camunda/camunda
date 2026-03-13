/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gatekeeper.model.session;

/** Result of validating a session token. */
public record SessionTokenValidation(
    boolean valid, String subject, String sessionId, String reason) {

  public static SessionTokenValidation success(final String subject, final String sessionId) {
    return new SessionTokenValidation(true, subject, sessionId, "");
  }

  public static SessionTokenValidation failure(final String reason) {
    return new SessionTokenValidation(false, null, null, reason);
  }
}
