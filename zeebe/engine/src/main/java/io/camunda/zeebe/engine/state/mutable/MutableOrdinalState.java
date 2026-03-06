/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.mutable;

import io.camunda.zeebe.engine.state.immutable.OrdinalState;

public interface MutableOrdinalState extends OrdinalState {

  /**
   * Advances the ordinal by one and records the current wall-clock time for the new ordinal value.
   *
   * @param dateTimeMillis the epoch-millisecond timestamp to associate with the new ordinal
   * @return the new ordinal value
   */
  int incrementOrdinal(long dateTimeMillis);
}
