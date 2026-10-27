/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.shared.management;

import java.util.Collection;
import org.jspecify.annotations.NullMarked;

/**
 * Thrown when an actuator's {@code physicalTenant} query parameter names a tenant this cluster does
 * not have. The message lists the configured ids, since an operator who mistyped one cannot look
 * them up on the actuator surface.
 */
@NullMarked
public final class UnknownPhysicalTenantException extends IllegalArgumentException {

  public UnknownPhysicalTenantException(final String requested, final Collection<String> known) {
    super(
        "Unknown physical tenant '%s'. Configured physical tenants: %s."
            .formatted(requested, known.stream().sorted().toList()));
  }
}
