/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.atomix.cluster.protocol;

import io.atomix.cluster.protocol.SwimMembershipProtocolMetricsDoc.SwimKeyNames;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import net.jcip.annotations.ThreadSafe;

@ThreadSafe
final class SwimMembershipProtocolMetrics {

  private final Map<String, AtomicLong> incarnationNumbers = new ConcurrentHashMap<>();

  private final MeterRegistry registry;

  public SwimMembershipProtocolMetrics(final MeterRegistry registry) {
    this.registry = registry;
  }

  public void updateMemberIncarnationNumber(final String member, final long incarnationNumber) {
    registerIncarnationNumberGauge(member).set(incarnationNumber);
  }

  private AtomicLong registerIncarnationNumberGauge(final String member) {
    // do a get first to see if we may have to allocate
    final var counter = incarnationNumbers.get(member);
    if (counter == null) {
      final AtomicLong finalCounter = new AtomicLong(0L);
      // try setting the counter to the one just allocated
      final var inside = incarnationNumbers.computeIfAbsent(member, unused -> finalCounter);
      // we won the race: use reference equality to check if it's the same instance
      if (inside == finalCounter) {
        Gauge.builder(
                SwimMembershipProtocolMetricsDoc.MEMBERS_INCARNATION_NUMBER.getName(), inside::get)
            .description(
                SwimMembershipProtocolMetricsDoc.MEMBERS_INCARNATION_NUMBER.getDescription())
            .tag(SwimKeyNames.MEMBER_ID.asString(), member)
            .register(registry);
      }
      // always return what is present in the map, not what was just allocated
      return inside;
    } else {
      return counter;
    }
  }
}
