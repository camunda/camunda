/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.engine.state.immutable;

import io.zeebe.engine.state.instance.EventScopeInstance;
import io.zeebe.engine.state.instance.EventTrigger;

public interface EventScopeInstanceState {

  /**
   * Returns a event scope instance by key or null if none exists with this key.
   *
   * @param eventScopeKey the key of the event scope
   * @return the event scope instance or null
   */
  EventScopeInstance getInstance(long eventScopeKey);

  /**
   * Returns the next event trigger for the event scope or null if none exists. This will not remove
   * the event trigger from the state.
   *
   * @param eventScopeKey the key of the event scope
   * @return the next event trigger or null if none exist
   */
  EventTrigger peekEventTrigger(long eventScopeKey);

  /**
   * @param eventScopeKey the key of the event scope the event is triggered in
   * @return true if the event can be accepted
   */
  boolean isAcceptingEvent(long eventScopeKey);
}
