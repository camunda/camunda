/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.exception;

import static io.camunda.spring.utils.DatabaseTypeUtils.CAMUNDA_DATABASE_TYPE_NONE;
import static io.camunda.spring.utils.DatabaseTypeUtils.PROPERTY_CAMUNDA_DATABASE_TYPE;

/** Exception thrown when Basic Authentication is configured but secondary storage is disabled. */
public class BasicAuthenticationNotSupportedException extends RuntimeException {
  public BasicAuthenticationNotSupportedException() {
    super(
        """
          Basic Authentication is not supported when secondary storage is disabled (
          %s=%s). Basic Authentication requires access to user data stored in secondary storage.
          Please either enable secondary storage by configuring %s to a supported database type,
          or use another authentication method by updating the camunda.security.authentication.method configuration."""
            .formatted(
                PROPERTY_CAMUNDA_DATABASE_TYPE,
                CAMUNDA_DATABASE_TYPE_NONE,
                PROPERTY_CAMUNDA_DATABASE_TYPE));
  }
}
