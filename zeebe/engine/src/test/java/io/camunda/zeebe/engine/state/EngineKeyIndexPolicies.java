/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state;

import io.camunda.zeebe.db.writebehind.KeyIndexPolicy;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The write-behind key index of the engine tests, per column family, chosen with the environment
 * variable {@code ZEEBE_DB_KEY_INDEX}. zb-db ships no defaults for the engine, so the set lives
 * here.
 */
@NullMarked
public final class EngineKeyIndexPolicies {

  /**
   * Column families whose scans start at one end of the key space, so a window at that end serves
   * them although they hold more keys than the cap.
   *
   * <p>The {@link KeyIndexPolicy#KEEP_LOWEST} group is scanned from the lowest key (next due
   * entry). The {@link KeyIndexPolicy#KEEP_HIGHEST} group has keys that start with an element
   * instance, scope, job or process instance key, which grows with time, so the engine reads and
   * scans the newest keys. Most of their lookups and scans find nothing: the engine checks for
   * variables, event triggers, timers and subscriptions when an element ends. With one tenant the
   * same holds for {@code COMPENSATION_SUBSCRIPTION}, whose keys start with the tenant. {@code
   * PROCESS_INSTANCE_KEY_BY_DEFINITION_KEY} is left out, because the definition key comes first.
   */
  public static final Map<ZbColumnFamilies, KeyIndexPolicy> DEFAULT =
      Collections.unmodifiableMap(
          new EnumMap<>(
              Map.ofEntries(
                  Map.entry(ZbColumnFamilies.TIMER_DUE_DATES, KeyIndexPolicy.KEEP_LOWEST),
                  Map.entry(ZbColumnFamilies.JOB_DEADLINES, KeyIndexPolicy.KEEP_LOWEST),
                  Map.entry(ZbColumnFamilies.MESSAGE_DEADLINES, KeyIndexPolicy.KEEP_LOWEST),
                  Map.entry(ZbColumnFamilies.JOB_BACKOFF, KeyIndexPolicy.KEEP_LOWEST),
                  Map.entry(
                      ZbColumnFamilies.ELEMENT_INSTANCE_PARENT_CHILD, KeyIndexPolicy.KEEP_HIGHEST),
                  Map.entry(
                      ZbColumnFamilies.NUMBER_OF_TAKEN_SEQUENCE_FLOWS, KeyIndexPolicy.KEEP_HIGHEST),
                  Map.entry(ZbColumnFamilies.ELEMENT_INSTANCE_KEY, KeyIndexPolicy.KEEP_HIGHEST),
                  Map.entry(
                      ZbColumnFamilies.ELEMENT_INSTANCE_CHILD_PARENT, KeyIndexPolicy.KEEP_HIGHEST),
                  Map.entry(ZbColumnFamilies.EVENT_SCOPE, KeyIndexPolicy.KEEP_HIGHEST),
                  Map.entry(ZbColumnFamilies.EVENT_TRIGGER, KeyIndexPolicy.KEEP_HIGHEST),
                  Map.entry(ZbColumnFamilies.VARIABLES, KeyIndexPolicy.KEEP_HIGHEST),
                  Map.entry(ZbColumnFamilies.TIMERS, KeyIndexPolicy.KEEP_HIGHEST),
                  Map.entry(
                      ZbColumnFamilies.PROCESS_SUBSCRIPTION_BY_KEY, KeyIndexPolicy.KEEP_HIGHEST),
                  Map.entry(
                      ZbColumnFamilies.SIGNAL_SUBSCRIPTION_BY_KEY_AND_NAME,
                      KeyIndexPolicy.KEEP_HIGHEST),
                  Map.entry(
                      ZbColumnFamilies.CONDITIONAL_SUBSCRIPTION_BY_SCOPE_KEY,
                      KeyIndexPolicy.KEEP_HIGHEST),
                  Map.entry(ZbColumnFamilies.SECRET_REFERENCES_BY_JOB, KeyIndexPolicy.KEEP_HIGHEST),
                  Map.entry(ZbColumnFamilies.BANNED_INSTANCE, KeyIndexPolicy.KEEP_HIGHEST),
                  Map.entry(
                      ZbColumnFamilies.SUSPENDED_PROCESS_INSTANCES, KeyIndexPolicy.KEEP_HIGHEST),
                  Map.entry(ZbColumnFamilies.JOBS, KeyIndexPolicy.KEEP_HIGHEST),
                  Map.entry(ZbColumnFamilies.JOB_STATES, KeyIndexPolicy.KEEP_HIGHEST),
                  Map.entry(ZbColumnFamilies.RUNTIME_INSTRUCTIONS, KeyIndexPolicy.KEEP_HIGHEST),
                  Map.entry(ZbColumnFamilies.AWAIT_WORKLOW_RESULT, KeyIndexPolicy.KEEP_HIGHEST),
                  Map.entry(
                      ZbColumnFamilies.COMPENSATION_SUBSCRIPTION, KeyIndexPolicy.KEEP_HIGHEST))));

  private static final String DEFAULT_NAME = "default";
  private static final String NONE_NAME = "none";

  private EngineKeyIndexPolicies() {}

  /** Parses {@code ZEEBE_DB_KEY_INDEX}; see {@link #parse}. */
  public static Setting fromEnvironment() {
    return parse(System.getenv("ZEEBE_DB_KEY_INDEX"));
  }

  /**
   * {@code default}, blank or {@code null} for {@link #DEFAULT}, {@code none} for no key index, or
   * a comma-separated list {@code NAME[:POLICY]} of {@link ZbColumnFamilies} names, where the
   * policy defaults to {@link KeyIndexPolicy#DISABLE}, e.g. {@code VARIABLES:KEEP_HIGHEST,JOBS}.
   *
   * @throws IllegalArgumentException on an unknown column family or policy, or a duplicate
   */
  public static Setting parse(final @Nullable String value) {
    if (value == null || value.isBlank() || value.trim().equalsIgnoreCase(DEFAULT_NAME)) {
      return new Setting(DEFAULT_NAME, DEFAULT);
    }
    if (value.trim().equalsIgnoreCase(NONE_NAME)) {
      return new Setting(NONE_NAME, Map.of());
    }
    final var policies = new EnumMap<ZbColumnFamilies, KeyIndexPolicy>(ZbColumnFamilies.class);
    for (final var entry : value.split(",")) {
      if (entry.isBlank()) {
        continue;
      }
      final var parts = entry.trim().split(":", 2);
      final var columnFamily = columnFamily(parts[0].trim(), value);
      final var policy = parts.length > 1 ? policy(parts[1].trim(), value) : KeyIndexPolicy.DISABLE;
      if (policies.put(columnFamily, policy) != null) {
        throw new IllegalArgumentException(
            "Column family %s appears twice in ZEEBE_DB_KEY_INDEX='%s'"
                .formatted(columnFamily, value));
      }
    }
    return new Setting("custom", Collections.unmodifiableMap(policies));
  }

  private static ZbColumnFamilies columnFamily(final String name, final String value) {
    try {
      return ZbColumnFamilies.valueOf(name);
    } catch (final IllegalArgumentException e) {
      throw new IllegalArgumentException(
          "Unknown column family '%s' in ZEEBE_DB_KEY_INDEX='%s'; expected 'default', 'none' or a list of NAME[:POLICY] with ZbColumnFamilies names"
              .formatted(name, value),
          e);
    }
  }

  private static KeyIndexPolicy policy(final String name, final String value) {
    try {
      return KeyIndexPolicy.valueOf(name);
    } catch (final IllegalArgumentException e) {
      throw new IllegalArgumentException(
          "Unknown key index policy '%s' in ZEEBE_DB_KEY_INDEX='%s'; expected one of %s"
              .formatted(name, value, Arrays.toString(KeyIndexPolicy.values())),
          e);
    }
  }

  /**
   * @param name {@code default}, {@code none} or {@code custom}
   * @param policies the column families with a key index, and the policy of each
   */
  public record Setting(String name, Map<ZbColumnFamilies, KeyIndexPolicy> policies) {

    /** The policies by column family ID, as {@code WriteBehindConfiguration} takes them. */
    public Map<Long, KeyIndexPolicy> byId() {
      final var byId = new HashMap<Long, KeyIndexPolicy>();
      policies.forEach((columnFamily, policy) -> byId.put((long) columnFamily.getValue(), policy));
      return byId;
    }

    /** {@code NAME:POLICY,...} in column family order. */
    public String describePolicies() {
      return policies.entrySet().stream()
          .map(e -> e.getKey() + ":" + e.getValue())
          .collect(Collectors.joining(","));
    }
  }
}
