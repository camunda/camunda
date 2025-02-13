/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.util.micrometer;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import java.util.List;

public class WrappedCompositeMeterRegistry extends CompositeMeterRegistry {
  private Tags tags = Tags.empty();

  public WrappedCompositeMeterRegistry(final MeterRegistry registry) {
    this(registry, Tags.empty());
  }

  public WrappedCompositeMeterRegistry(final MeterRegistry registry, final Tags tags) {
    this(Clock.SYSTEM, registry != null ? List.of(registry) : List.of());
    addTags(tags);
  }

  public WrappedCompositeMeterRegistry(
      final Clock clock, final Iterable<MeterRegistry> registries) {
    super(clock, registries);
    for (final MeterRegistry meterRegistry : registries) {
      if (meterRegistry instanceof final WrappedCompositeMeterRegistry wrapped) {
        addTags(wrapped.tags);
      }
    }
  }

  public WrappedCompositeMeterRegistry addTags(final String... tags) {
    return addTags(Tags.of(tags));
  }

  public WrappedCompositeMeterRegistry addTags(final Tags tags) {
    this.tags = this.tags.and(tags);
    config().commonTags(tags);
    return this;
  }
}
