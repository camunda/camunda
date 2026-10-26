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

public interface MutableProbeState extends ProbeState {

  void create(long probeKey, ProbeRecord record);

  /** Records the worker the probe was pushed to and marks it activated. */
  void activate(long probeKey, String worker);

  /** Counts the yield and marks the probe as created again, to be dispatched once more. */
  void yield(long probeKey);

  void park(long probeKey);

  /** Removes the probe and its deadline; the probe ended. */
  void remove(long probeKey);
}
