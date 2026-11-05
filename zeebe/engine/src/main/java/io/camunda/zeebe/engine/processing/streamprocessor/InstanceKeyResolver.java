/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.streamprocessor;

import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.protocol.record.intent.AgentInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.AdHocSubProcessInstructionRecordValue;
import io.camunda.zeebe.protocol.record.value.AgentInstanceRecordValue;
import io.camunda.zeebe.protocol.record.value.IncidentRecordValue;
import io.camunda.zeebe.protocol.record.value.JobRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRelated;
import io.camunda.zeebe.protocol.record.value.UserTaskRecordValue;
import io.camunda.zeebe.protocol.record.value.VariableDocumentRecordValue;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Resolves the process instance a command targets: from the command value when it carries the
 * process instance key, otherwise from the entity state for {@code JOB}, {@code INCIDENT}, {@code
 * USER_TASK}, {@code AD_HOC_SUB_PROCESS_INSTRUCTION}, {@code VARIABLE_DOCUMENT} and {@code
 * AGENT_INSTANCE} commands, which don't carry it on the wire.
 */
@NullMarked
public final class InstanceKeyResolver {

  public static final InstanceKeys UNRESOLVED = new InstanceKeys(-1, -1);

  private final ProcessingState processingState;

  public InstanceKeyResolver(final ProcessingState processingState) {
    this.processingState = processingState;
  }

  /** Resolves the instance keys from the command value, falling back to state; -1 if unknown. */
  public InstanceKeys resolve(final TypedRecord<?> command) {
    final var value = command.getValue();
    if (value instanceof final ProcessInstanceRelated processInstanceRelated
        && processInstanceRelated.getProcessInstanceKey() > 0) {
      return instanceKeysOf(command, processInstanceRelated);
    }

    final long key = command.getKey();
    return switch (command.getValueType()) {
      case JOB -> instanceKeysOf(command, processingState.getJobState().getJob(key));
      case INCIDENT ->
          instanceKeysOf(command, processingState.getIncidentState().getIncidentRecord(key));
      case USER_TASK ->
          instanceKeysOf(command, processingState.getUserTaskState().getUserTask(key));
      case AD_HOC_SUB_PROCESS_INSTRUCTION -> {
        final var adHocValue = (AdHocSubProcessInstructionRecordValue) value;
        final var elementInstance =
            processingState
                .getElementInstanceState()
                .getInstance(adHocValue.getAdHocSubProcessInstanceKey());
        if (elementInstance == null) {
          yield UNRESOLVED;
        }
        yield processInstanceOnly(elementInstance.getValue().getProcessInstanceKey());
      }
      case VARIABLE_DOCUMENT -> {
        final var scopeKey = ((VariableDocumentRecordValue) value).getScopeKey();
        final var scope = processingState.getElementInstanceState().getInstance(scopeKey);
        if (scope == null) {
          yield UNRESOLVED;
        }
        yield processInstanceOnly(scope.getValue().getProcessInstanceKey());
      }
      case AGENT_INSTANCE -> processInstanceOnly(resolveAgentInstanceProcessInstanceKey(command));
      default -> UNRESOLVED;
    };
  }

  private static InstanceKeys instanceKeysOf(
      final TypedRecord<?> command, final @Nullable ProcessInstanceRelated entity) {
    if (entity == null) {
      return UNRESOLVED;
    }
    final long elementInstanceKey =
        switch (entity) {
          case final JobRecordValue job -> job.getElementInstanceKey();
          case final UserTaskRecordValue userTask -> userTask.getElementInstanceKey();
          case final IncidentRecordValue incident -> incident.getElementInstanceKey();
          // keyed by the element instance whose listener completed, e.g. a cancel listener
          case final ProcessInstanceRecordValue ignored
              when command.getIntent() == ProcessInstanceIntent.COMPLETE_EXECUTION_LISTENER ->
              command.getKey();
          default -> -1;
        };
    return new InstanceKeys(entity.getProcessInstanceKey(), elementInstanceKey);
  }

  private static InstanceKeys processInstanceOnly(final long processInstanceKey) {
    return new InstanceKeys(processInstanceKey, -1);
  }

  /**
   * CREATE carries {@code elementInstanceKey} on the value; the target element instance's process
   * instance key is looked up. Every other AgentInstance command (currently only UPDATE) targets an
   * existing agent instance identified by the command's own key.
   */
  private long resolveAgentInstanceProcessInstanceKey(final TypedRecord<?> command) {
    if (command.getIntent() == AgentInstanceIntent.CREATE) {
      final var value = (AgentInstanceRecordValue) command.getValue();
      final var elementInstance =
          processingState.getElementInstanceState().getInstance(value.getElementInstanceKey());
      return elementInstance != null ? elementInstance.getValue().getProcessInstanceKey() : -1;
    }

    final var agentInstance = processingState.getAgentInstanceState().getRecord(command.getKey());
    return agentInstance != null ? agentInstance.getProcessInstanceKey() : -1;
  }

  /**
   * The target process instance of a command and, when known, the element instance it addresses.
   */
  public record InstanceKeys(long processInstanceKey, long elementInstanceKey) {
    public boolean isResolved() {
      return processInstanceKey > 0;
    }
  }
}
