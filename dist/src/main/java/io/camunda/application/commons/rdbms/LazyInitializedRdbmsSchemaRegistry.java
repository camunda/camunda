/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.rdbms;

import io.camunda.db.rdbms.RdbmsSchemaManagerRegistry;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The {@link RdbmsSchemaManagerRegistry} every consumer is wired with, reporting no tenant as
 * initialized until {@link RdbmsSchemaInitializer} is bound to it.
 *
 * <p>It exists to break a dependency cycle. The initializer must be created after the broker has
 * started, so that its recovery check reads the broker's own cluster configuration rather than an
 * empty one; but the broker needs the RDBMS exporter, which needs a registry. Handing the exporter
 * this holder instead of the initializer lets the broker be created first. A lazy Spring proxy
 * would not do: the exporter's first lookup happens on a broker thread while the context refresh is
 * still creating the initializer, and would block that thread on the bean creation for as long as
 * the initializer holds startup.
 */
@NullMarked
final class LazyInitializedRdbmsSchemaRegistry implements RdbmsSchemaManagerRegistry {

  private volatile @Nullable RdbmsSchemaInitializer initializer;

  void bind(final RdbmsSchemaInitializer initializer) {
    this.initializer = initializer;
  }

  @Override
  public boolean isInitialized(final String physicalTenantId) {
    final var bound = initializer;
    return bound != null && bound.isInitialized(physicalTenantId);
  }
}
