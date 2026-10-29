/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.pt;

import java.io.Serial;
import org.jspecify.annotations.NullMarked;

/** A physical tenant's resource could not be created, so the tenant cannot be served for now. */
@NullMarked
public final class PhysicalTenantResourceUnavailableException extends RuntimeException {

  @Serial private static final long serialVersionUID = 1L;

  private final String physicalTenantId;

  PhysicalTenantResourceUnavailableException(
      final String physicalTenantId, final String resourceName, final Throwable cause) {
    super(
        "The %s of physical tenant '%s' are unavailable: %s"
            .formatted(resourceName, physicalTenantId, cause.getMessage()),
        cause);
    this.physicalTenantId = physicalTenantId;
  }

  public String physicalTenantId() {
    return physicalTenantId;
  }
}
