/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.security.sso;

// Wrap auth0 exceptions for testing and specific handling
public class Auth0ServiceException extends RuntimeException {

  public Auth0ServiceException(String message) {
    super(message);
  }

  public Auth0ServiceException(final Exception e) {
    super(e);
  }
}
