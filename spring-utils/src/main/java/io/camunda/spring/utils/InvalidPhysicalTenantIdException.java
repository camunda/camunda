/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.spring.utils;

/**
 * Thrown when a physical-tenant id fails format or length validation. A plain {@link
 * RuntimeException}: {@code spring-utils} has no dependency on the {@code configuration} module's
 * exception hierarchy, so callers that need a module-specific exception type catch this one and
 * wrap it.
 */
public final class InvalidPhysicalTenantIdException extends RuntimeException {

  public InvalidPhysicalTenantIdException(final String message) {
    super(message);
  }
}
