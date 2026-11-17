/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.mutable;

import io.camunda.zeebe.engine.state.immutable.ProbeState;
import io.camunda.zeebe.protocol.impl.record.value.probe.ProbeRecord;
import org.agrona.DirectBuffer;
import org.jspecify.annotations.NullMarked;

@NullMarked
public interface MutableProbeState extends ProbeState {

  /** Adds the probe, activatable: a poll of its probe ID may collect it until its deadline. */
  void create(long probeKey, ProbeRecord probe);

  /**
   * Hands the activatable probe to the worker of the poll that collected it: no poll collects it
   * any more, and its answer names this worker.
   */
  void activate(long probeKey, DirectBuffer worker);

  /**
   * Makes the activated probe activatable again, without a worker: the next poll of its probe ID
   * may collect it.
   */
  void makeActivatable(long probeKey);

  /** Removes the probe and its index entries: the probe ended. */
  void remove(long probeKey);
}
