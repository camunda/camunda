/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service.exception;

public class PhysicalTenantUnavailableException extends ServiceException {
  public static final String PHYSICAL_TENANT_DEGRADED_MESSAGE =
      "Physical tenant '%s' is degraded: its secondary storage is not ready. Requests are rejected until it recovers.";

  public PhysicalTenantUnavailableException(final String physicalTenantId) {
    super(PHYSICAL_TENANT_DEGRADED_MESSAGE.formatted(physicalTenantId), Status.UNAVAILABLE);
  }
}
