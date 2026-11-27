/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.monitoring.history;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * One entry of the health history.
 *
 * @param sequence increases by one per entry on this broker, across restarts
 * @param timestamp when the broker recorded the entry
 * @param component the health tree path of the component, for example {@code
 *     Broker-0/Partition-default-1/StreamProcessor-1}
 * @param type {@link HealthTreeRecorder#HEALTH_CHANGED} for a status change, otherwise the type of
 *     the event, for example {@code NODE_STARTED}
 * @param message a sentence for humans, or null
 * @param details machine-readable facts; a status change has {@code from} and {@code to}
 */
public record HealthHistoryEntry(
    long sequence,
    Instant timestamp,
    String component,
    String type,
    @Nullable String message,
    Map<String, String> details) {

  public HealthHistoryEntry {
    Objects.requireNonNull(timestamp, "timestamp");
    Objects.requireNonNull(component, "component");
    Objects.requireNonNull(type, "type");
    details = Map.copyOf(details);
  }
}
