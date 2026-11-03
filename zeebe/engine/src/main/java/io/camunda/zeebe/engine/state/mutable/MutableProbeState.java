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

  /** Adds the probe, activatable: a poll of its probe ID may collect it. */
  void create(long probeKey, ProbeRecord record);

  /** Records the worker whose poll collected the probe and marks it activated. */
  void activate(long probeKey, String worker);

  /**
   * Makes the probe activatable again: after a yield, or once the secrets it waited for resolved.
   * Releases the secret references it was parked on.
   */
  void makeActivatable(long probeKey);

  /**
   * Parks the probe on the secret reference until it is resolved. A probe can be parked on several
   * references; activating or removing it releases all of them.
   */
  void park(long probeKey, String storeId, String secretReference);

  /** Removes the probe and its deadline; the probe ended. */
  void remove(long probeKey);
}
