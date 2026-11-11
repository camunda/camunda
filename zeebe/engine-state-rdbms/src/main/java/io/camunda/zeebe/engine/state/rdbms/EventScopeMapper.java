/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.rdbms;

import java.util.List;
import org.apache.ibatis.annotations.Param;

/**
 * MyBatis mapper of the event scope tables, see {@code
 * db/mybatis/engine-state/EventScopeMapper.xml}. The {@link EntitySession} reads and writes the
 * rows of the {@link EntityType}s by id; the mapper only declares the queries. Every statement is
 * scoped to one partition.
 *
 * <p>The rows are immutable snapshots, as the {@link EntitySession} requires: payloads and
 * variables are serialized msgpack and must not be modified.
 */
interface EventScopeMapper {

  /** The committed triggers of a scope, by event key. */
  List<EventTriggerRow> selectEventTriggersOfScope(
      @Param("partitionId") int partitionId, @Param("scopeKey") long scopeKey);

  /** The event keys of the committed triggers of a scope. */
  List<Long> selectEventTriggerKeysOfScope(
      @Param("partitionId") int partitionId, @Param("scopeKey") long scopeKey);

  /**
   * An event scope: its msgpack {@code EventScopeInstance}, with its flags as columns, plus the
   * process instance of its element instance.
   */
  record EventScopeRow(
      long scopeKey,
      boolean accepting,
      boolean interrupted,
      long processInstanceKey,
      long rootProcessInstanceKey,
      String tenantId,
      byte[] payload) {

    EventScopeRow withInstance(
        final boolean newAccepting, final boolean newInterrupted, final byte[] newPayload) {
      return new EventScopeRow(
          scopeKey,
          newAccepting,
          newInterrupted,
          processInstanceKey,
          rootProcessInstanceKey,
          tenantId,
          newPayload);
    }
  }

  /** A trigger is identified by its scope and event key. */
  record EventTriggerKey(long scopeKey, long eventKey) {}

  /** A trigger: the element to activate and the msgpack variables of the event, empty if none. */
  record EventTriggerRow(
      long scopeKey,
      long eventKey,
      String elementId,
      byte[] variables,
      long processInstanceKey,
      long rootProcessInstanceKey,
      String tenantId) {

    /** MyBatis reads a NULL variables column as null. */
    public EventTriggerRow {
      variables = variables == null ? new byte[0] : variables;
    }

    EventTriggerKey key() {
      return new EventTriggerKey(scopeKey, eventKey);
    }
  }
}
