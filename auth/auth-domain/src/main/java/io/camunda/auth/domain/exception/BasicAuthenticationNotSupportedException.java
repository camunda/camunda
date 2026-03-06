/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.auth.domain.exception;

/** Exception thrown when Basic Authentication is configured but secondary storage is disabled. */
public class BasicAuthenticationNotSupportedException extends RuntimeException {
  public BasicAuthenticationNotSupportedException() {
    super(
        "Basic Authentication is not supported when secondary storage is disabled. "
            + "Basic Authentication requires access to user data stored in secondary storage. "
            + "Please either enable secondary storage by configuring a supported database type, "
            + "or use another authentication method by updating the camunda.auth.method configuration.");
  }
}
