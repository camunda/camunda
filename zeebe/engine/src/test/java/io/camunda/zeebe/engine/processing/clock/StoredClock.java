/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.clock;

import io.camunda.zeebe.engine.util.client.ClockClient;
import io.camunda.zeebe.protocol.record.intent.ClockIntent;
import io.camunda.zeebe.stream.api.StreamClock.ControllableStreamClock.Modification;
import java.time.Duration;
import java.time.Instant;

/**
 * Reads the clock the engine has stored, rather than the running one. Adding to the clock builds on
 * the stored clock, so adding nothing shows it without moving it.
 */
final class StoredClock {

  private StoredClock() {}

  static Modification read(final ClockClient clock) {
    final var added = clock.add(Duration.ZERO);
    final long time = added.getValue().getTime();
    return switch ((ClockIntent) added.getIntent()) {
      case PINNED -> Modification.pinAt(Instant.ofEpochMilli(time));
      case OFFSETTED ->
          time == 0 ? Modification.none() : Modification.offsetBy(Duration.ofMillis(time));
      default -> throw new IllegalStateException("Unexpected clock event " + added);
    };
  }
}
