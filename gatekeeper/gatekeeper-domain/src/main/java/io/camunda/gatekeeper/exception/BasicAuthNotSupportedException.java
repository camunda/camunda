/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gatekeeper.exception;

/**
 * Exception thrown when Basic Authentication is configured but the required storage backend is not
 * available.
 */
public final class BasicAuthNotSupportedException extends RuntimeException {

  public BasicAuthNotSupportedException() {
    super(
        "Basic Authentication is not supported without a configured user storage backend. "
            + "Basic Authentication requires access to persisted user data. "
            + "Please configure a supported storage backend or use another authentication method.");
  }
}
