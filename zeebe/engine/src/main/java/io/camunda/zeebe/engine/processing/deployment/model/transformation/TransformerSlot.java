/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.deployment.model.transformation;

/**
 * Stable identifier for each logical BPMN sub-transformer. IDs are explicit and MUST never change
 * or be reused — they are persisted in {@code PersistedProcess} and in the event log via {@code
 * ProcessRecord}. Add new slots with the next free id; never renumber existing ones.
 */
public enum TransformerSlot {
  ERROR(1),
  ESCALATION(2),
  FLOW_ELEMENT_INSTANTIATION(3),
  MESSAGE(4),
  SIGNAL(5),
  CONDITIONAL(6),
  PROCESS(7),
  BOUNDARY_EVENT(8),
  BUSINESS_RULE_TASK(9),
  CALL_ACTIVITY(10),
  CATCH_EVENT(11),
  // CONTEXT_PROCESS is registered in steps 2, 3, 4 AND 5 — it is ONE logical transformer and is
  // versioned as a unit across all four registrations (bumping it moves all four together, which is
  // the intended/safe semantics since they are the same class).
  CONTEXT_PROCESS(12),
  END_EVENT(13),
  FLOW_NODE(14),
  SERVICE_TASK_JOB_WORKER(15),
  SEND_TASK_JOB_WORKER(16),
  RECEIVE_TASK(17),
  SCRIPT_TASK(18),
  SEQUENCE_FLOW(19),
  START_EVENT(20),
  USER_TASK(21),
  EVENT_BASED_GATEWAY(22),
  EXCLUSIVE_GATEWAY(23),
  INCLUSIVE_GATEWAY(24),
  INTERMEDIATE_CATCH_EVENT(25),
  SUB_PROCESS(26),
  INTERMEDIATE_THROW_EVENT(27),
  AD_HOC_SUB_PROCESS(28),
  MULTI_INSTANCE_ACTIVITY(29);

  /** Default version of every slot. Slots at this version are stored sparsely (i.e. not at all). */
  public static final int DEFAULT_VERSION = 1;

  private final int id;

  TransformerSlot(final int id) {
    this.id = id;
  }

  public int id() {
    return id;
  }

  public static TransformerSlot fromId(final int id) {
    for (final TransformerSlot slot : values()) {
      if (slot.id == id) {
        return slot;
      }
    }
    throw new IllegalArgumentException("Unknown TransformerSlot id: " + id);
  }
}
