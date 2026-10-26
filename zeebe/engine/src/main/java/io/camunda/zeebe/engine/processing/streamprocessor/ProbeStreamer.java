/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.streamprocessor;

import io.camunda.zeebe.protocol.impl.stream.probe.ActivatedProbe;
import io.camunda.zeebe.protocol.impl.stream.probe.ProbeActivationProperties;
import java.util.Optional;
import java.util.function.Predicate;
import org.agrona.DirectBuffer;

/**
 * Finds the probe stream to push a probe to. A probe stream is the subscription of a probe worker
 * for one capability, i.e. one operation and one template; its type is {@code
 * <operation>:<template>} and is matched exactly. Mirrors {@link JobStreamer}, without the
 * notification for long polling, which probes do not have.
 */
public interface ProbeStreamer {
  static ProbeStreamer noop() {
    return (streamType, filter) -> Optional.empty();
  }

  /**
   * Returns a probe stream of the given type whose properties the filter accepts, or {@link
   * Optional#empty()} if there is none. When several match, one is picked at random.
   */
  Optional<ProbeStream> streamFor(
      DirectBuffer streamType, Predicate<ProbeActivationProperties> filter);

  /** A {@link ProbeStream} allows the engine to push out activated probes. */
  interface ProbeStream {

    /** Returns the properties of the subscription, e.g. the worker name and tenants. */
    ProbeActivationProperties properties();

    /**
     * Pushes the probe to the stream. Asynchronous; a push that fails on every connection of the
     * subscription is reported by the stream's error handler, which yields the probe.
     */
    void push(ActivatedProbe payload);
  }
}
