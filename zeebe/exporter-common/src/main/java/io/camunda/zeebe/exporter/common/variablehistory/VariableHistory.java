/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.common.variablehistory;

import io.camunda.search.entities.AuditLogEntity.AuditLogActorType;
import io.camunda.zeebe.exporter.common.auditlog.AuditLogConfiguration;
import io.camunda.zeebe.exporter.common.auditlog.AuditLogInfo;
import io.camunda.zeebe.exporter.common.auditlog.AuditLogInfo.AuditLogActor;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.protocol.record.intent.VariableIntent;
import io.camunda.zeebe.protocol.record.value.VariableRecordValue;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Decides whether the variable history records a variable event, and who made the change. Both
 * history handlers use it, so they record the same changes with the same actor.
 */
@NullMarked
public final class VariableHistory {

  private VariableHistory() {}

  /**
   * Returns whether the history records the event. Only {@code CREATED} and {@code UPDATED} events
   * count, and only when the audit log configuration selects the change of its {@link
   * #actorOf(Record) actor}.
   */
  public static boolean isRecorded(
      final Record<VariableRecordValue> record, final AuditLogConfiguration configuration) {
    final Intent intent = record.getIntent();
    if (intent != VariableIntent.CREATED && intent != VariableIntent.UPDATED) {
      return false;
    }

    final var actor = actorOf(record);
    return configuration.isEnabled(
        AuditLogInfo.of(record, new AuditLogActor(actor.type(), actor.id())));
  }

  /**
   * Returns who made the change. An engine type gives the engine, and a type without a reliable
   * identity gives the unknown actor, whatever identity the record carries. Any other type gives
   * the identity on the record, with the agent element ID when a user or client acts for an AI
   * agent.
   */
  public static Actor actorOf(final Record<VariableRecordValue> record) {
    // No default branch: a new operation type must be classified here.
    return switch (record.getValue().getSource().getType()) {
      case INPUT_MAPPING,
          OUTPUT_MAPPING,
          SCRIPT_RESULT,
          DECISION_RESULT,
          CALL_ACTIVITY_INPUT,
          CALL_ACTIVITY_OUTPUT,
          MULTI_INSTANCE_INPUT_ELEMENT,
          MULTI_INSTANCE_LOOP_COUNTER,
          MULTI_INSTANCE_OUTPUT_ELEMENT,
          MULTI_INSTANCE_OUTPUT_COLLECTION,
          AD_HOC_SUB_PROCESS_METADATA,
          AD_HOC_SUB_PROCESS_OUTPUT_ELEMENT,
          AD_HOC_SUB_PROCESS_OUTPUT_COLLECTION ->
          Actor.of(AuditLogActor.ENGINE, null);
      // A buffered message can be correlated under the command of another client, so the identity
      // on its payload record is not the publisher's.
      case UNKNOWN, MESSAGE_CORRELATION -> Actor.of(AuditLogActor.UNKNOWN, null);
      case API,
          USER_TASK_COMPLETION,
          PROCESS_INSTANCE_CREATION,
          PROCESS_INSTANCE_MODIFICATION,
          JOB_COMPLETION,
          JOB_FAILURE,
          JOB_ERROR,
          EXECUTION_LISTENER_COMPLETION,
          SIGNAL_BROADCAST,
          CONDITIONAL_EVALUATION,
          AD_HOC_SUB_PROCESS_ACTIVATION ->
          requesterOf(record);
    };
  }

  private static Actor requesterOf(final Record<VariableRecordValue> record) {
    final var actor = AuditLogActor.of(record);
    final var agent = record.getAgent();
    final var isUserOrClient =
        actor.actorType() == AuditLogActorType.USER
            || actor.actorType() == AuditLogActorType.CLIENT;
    final var agentElementId = isUserOrClient && agent != null ? agent.getElementId() : null;
    return Actor.of(actor, agentElementId);
  }

  /**
   * The actor of a variable change.
   *
   * @param type the type of the actor, {@code ANONYMOUS} only for a change that {@link
   *     #isRecorded(Record, AuditLogConfiguration)} rejects
   * @param id the user name or client ID, or null for an actor without an identity
   * @param agentElementId the BPMN element ID of the AI agent, or null unless a user or client
   *     acted for an agent
   */
  public record Actor(
      AuditLogActorType type, @Nullable String id, @Nullable String agentElementId) {

    private static Actor of(final AuditLogActor actor, final @Nullable String agentElementId) {
      return new Actor(actor.actorType(), actor.actorId(), agentElementId);
    }
  }
}
