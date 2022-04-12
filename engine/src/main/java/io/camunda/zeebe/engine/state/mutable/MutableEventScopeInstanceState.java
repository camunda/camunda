/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.state.mutable;

import io.camunda.zeebe.engine.state.immutable.EventScopeInstanceState;
import io.camunda.zeebe.engine.state.instance.EventTrigger;
import java.util.Collection;
import org.agrona.DirectBuffer;

public interface MutableEventScopeInstanceState extends EventScopeInstanceState {

  /**
   * Creates a new event scope instance in the state. The event scope is interrupted if one of the
   * interrupting elements is triggered. An interrupted event scope can not be triggered by other
   * interrupting or non-interrupting events, except boundary events. After a interrupting boundary
   * event is triggered, no other event, including boundary events, can be triggered for the event
   * scope.
   *
   * @param eventScopeKey the event scope key
   * @param interruptingElementIds element IDs that interrupt the event scope
   * @param boundaryElementIds element IDs of boundary events
   */
  void createInstance(
      long eventScopeKey,
      Collection<DirectBuffer> interruptingElementIds,
      Collection<DirectBuffer> boundaryElementIds);

  /**
   * Delete an event scope from the state. Does not fail in case the scope does not exist.
   *
   * @param eventScopeKey the key of the event scope to delete
   */
  void deleteInstance(long eventScopeKey);

  /**
   * Returns the next event trigger for the event scope or null if none exists. This will remove the
   * polled event trigger from the state if it exists.
   *
   * @param eventScopeKey the key of the event scope
   * @return the next event trigger or null if none exist
   */
  EventTrigger pollEventTrigger(long eventScopeKey);

  /**
   * Creates a new event trigger for the given event scope. Ignores the trigger if the event scope
   * doesn't exist or if the event can be triggered in the scope (e.g. the scope is interrupted).
   * Use {@link #canTriggerEvent(long, DirectBuffer)} to check if the event can be triggered before
   * calling this method.
   *
   * @param eventScopeKey the key of the event scope the event is triggered in
   * @param eventKey the key of the event record (used for ordering)
   * @param elementId the id of the element which should be triggered, e.g. boundary event
   * @param variables the variables of the occurred event, i.e. message variables
   */
  void triggerEvent(
      long eventScopeKey, long eventKey, DirectBuffer elementId, DirectBuffer variables);

  /**
   * Creates an event trigger for a process start event. Uses the process definition key as the
   * scope key of the trigger.
   *
   * @param processDefinitionKey the key of the process definition a new instance should be created
   *     of
   * @param eventKey the key of the event record (used for ordering)
   * @param elementId the id of the start event which should be triggered
   * @param variables the variables of the occurred event, i.e. message variables
   */
  void triggerStartEvent(
      long processDefinitionKey, long eventKey, DirectBuffer elementId, DirectBuffer variables);

  /**
   * Deletes an event trigger by key and scope key. Does not fail if the trigger does not exist.
   *
   * @param eventScopeKey the key of the event scope
   * @param eventKey the key of the event trigger
   */
  void deleteTrigger(long eventScopeKey, long eventKey);
}
