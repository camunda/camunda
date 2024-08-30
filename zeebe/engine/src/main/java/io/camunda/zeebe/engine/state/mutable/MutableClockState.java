/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.mutable;

import io.camunda.zeebe.engine.state.immutable.ClockState;

public interface MutableClockState extends ClockState {
  MutableClockState pinAt(final long epochMillis);

  MutableClockState offsetBy(final long offsetMillis);

  MutableClockState reset();
}
