/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.secrets;

import io.camunda.zeebe.util.micrometer.PartitionKeyNames;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.InstantSource;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Publishes which configuration secret refreshes are failing, and when each node last applied a
 * change to each reference, as metrics and on {@code /actuator/health}.
 */
public final class ConfigurationSecretRefreshReport {

  static final String FAILING = "camunda.secret.config.refresh.failing";
  static final String LAST_APPLIED = "camunda.secret.config.refresh.last.applied";

  private final MeterRegistry meterRegistry;
  private final InstantSource clock;
  private final Map<String, Map<Failure, Gauge>> failing = new ConcurrentHashMap<>();
  private final Map<String, AtomicLong> lastApplied = new ConcurrentHashMap<>();

  public ConfigurationSecretRefreshReport(
      final MeterRegistry meterRegistry, final InstantSource clock) {
    this.meterRegistry = meterRegistry;
    this.clock = clock;
  }

  /** Replaces the failing refreshes of the given physical tenant. */
  void failing(final String physicalTenantId, final Set<Failure> failures) {
    final var gauges = failing.computeIfAbsent(physicalTenantId, id -> new ConcurrentHashMap<>());
    gauges
        .entrySet()
        .removeIf(
            entry -> {
              if (failures.contains(entry.getKey())) {
                return false;
              }
              meterRegistry.remove(entry.getValue());
              return true;
            });
    failures.forEach(
        failure ->
            gauges.computeIfAbsent(
                failure,
                f ->
                    Gauge.builder(FAILING, () -> 1)
                        .description(
                            "Set while a refresh of a configuration secret is failing, with the reason")
                        .tag(PartitionKeyNames.PHYSICAL_TENANT.asString(), physicalTenantId)
                        .tag("reference", f.reference())
                        .tag("client", f.client())
                        .tag("reason", f.reason().tag())
                        .register(meterRegistry)));
  }

  /** Records that a change to the given reference was applied on this node. */
  void applied(final String physicalTenantId, final String reference) {
    lastApplied
        .computeIfAbsent(
            physicalTenantId + "/" + reference,
            key -> {
              final var time = new AtomicLong();
              Gauge.builder(LAST_APPLIED, time, AtomicLong::get)
                  .description("When this node last applied a change to a configuration secret")
                  .baseUnit("seconds")
                  .tag(PartitionKeyNames.PHYSICAL_TENANT.asString(), physicalTenantId)
                  .tag("reference", reference)
                  .register(meterRegistry);
              return time;
            })
        .set(clock.instant().getEpochSecond());
  }

  /** The failing refreshes of each physical tenant that has any. */
  public Map<String, Set<Failure>> failing() {
    final Map<String, Set<Failure>> failures = new TreeMap<>();
    failing.forEach(
        (tenantId, gauges) -> {
          if (!gauges.isEmpty()) {
            failures.put(tenantId, Set.copyOf(new HashMap<>(gauges).keySet()));
          }
        });
    return failures;
  }

  /** Why a refresh failed. */
  public enum Reason {
    STORE_UNREACHABLE,
    ACCESS_DENIED,
    NOT_FOUND,
    UNREADABLE,
    REJECTED_BY_CHECK;

    String tag() {
      return name().toLowerCase(Locale.ROOT);
    }
  }

  /**
   * A failing refresh of a reference for a client.
   *
   * @param reference the reference, or the file, whose new value couldn't be applied
   * @param client the client that uses it, or {@code startup} for one that is only read on startup
   */
  public record Failure(String reference, String client, Reason reason) {}
}
