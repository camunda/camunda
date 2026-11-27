/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.mutable;

import io.camunda.zeebe.engine.state.immutable.HubEventState;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public interface MutableHubEventState extends HubEventState {

  /**
   * Sets how far the Hub event is handled. Only partition 1 records it; on the other partitions
   * this does nothing. An event at or below the last finished Hub sequence is ignored, so the
   * sequence never goes back. Only one event is in progress at a time, so the event set last
   * replaces one in progress, whatever their sequences.
   *
   * @param seqNo the Hub sequence of the event
   * @param type what kind of event it is
   * @param status how far the event is handled
   * @param reason why the event was rejected, ran into an error or was canceled; {@code null}
   *     otherwise
   */
  void setStatus(long seqNo, HubEventType type, HubEventStatus status, @Nullable String reason);
}
