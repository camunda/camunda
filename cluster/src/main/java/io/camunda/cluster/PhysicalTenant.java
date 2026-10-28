/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.cluster;

import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * One incarnation of a physical tenant on this node.
 *
 * @param id the physical tenant id, the only identifier the system routes and keys by
 * @param name a display name for user-facing surfaces; not unique and never used by the system
 * @param incarnationId generated on every add, so a remove followed by an add of the same id is
 *     distinguishable; kept across a replace of the configuration
 */
@NullMarked
public record PhysicalTenant(String id, @Nullable String name, UUID incarnationId) {

  public PhysicalTenant {
    Objects.requireNonNull(id, "id must not be null");
    Objects.requireNonNull(incarnationId, "incarnationId must not be null");
  }

  public static PhysicalTenant newIncarnation(final String id, final @Nullable String name) {
    return new PhysicalTenant(id, name, UUID.randomUUID());
  }

  public PhysicalTenant withName(final @Nullable String name) {
    return new PhysicalTenant(id, name, incarnationId);
  }
}
