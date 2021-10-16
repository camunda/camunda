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
   * If the scope exists, sets its accepting property to false.
   *
   * @param eventScopeKey the event scope key
   */
  void shutdownInstance(long eventScopeKey);

  /**
   * Creates a new event scope instance in the state
   *
   * @param eventScopeKey the event scope key
   * @param interruptingIds list of element IDs which should set accepting to false
   * @return whether the scope was created or not
   */
  boolean createIfNotExists(long eventScopeKey, Collection<DirectBuffer> interruptingIds);

  /**
   * Creates a new event scope instance in the state
   *
   * @param eventScopeKey the event scope key
   * @param interruptingIds list of element IDs which should set accepting to false
   */
  void createInstance(long eventScopeKey, Collection<DirectBuffer> interruptingIds);

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
   * is not accepting anymore or doesn't exist.
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
