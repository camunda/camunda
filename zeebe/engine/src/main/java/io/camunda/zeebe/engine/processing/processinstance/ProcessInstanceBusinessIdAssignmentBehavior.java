/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.processinstance;

import io.camunda.zeebe.engine.processing.Rejection;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.state.instance.ElementInstance;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceBusinessIdRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceBusinessIdIntent;
import io.camunda.zeebe.util.Either;

/**
 * Shared logic for the late assignment of a Business ID to a running root process instance (see ADR
 * 0006). It centralises the precondition checks (D2–D6) and the writing of the {@code ASSIGNED}
 * event so both the standalone {@code ASSIGN} command and the {@code CompleteJob} convenience carry
 * out an identical, single-sourced assignment rather than duplicating the rules.
 *
 * <p>Authorization (D9) is intentionally not handled here: each caller enforces it via its own
 * authorization mechanism before invoking {@link #validate(ElementInstance, String)}.
 */
public final class ProcessInstanceBusinessIdAssignmentBehavior {

  private static final String ERROR_CHILD_INSTANCE =
      "Expected to assign a business id to process instance with key '%d', but it is a child process instance; a business id can only be assigned to root process instances";
  private static final String ERROR_NOT_ACTIVE =
      "Expected to assign a business id to process instance with key '%d', but it is not active; a business id can only be assigned to active process instances";
  private static final String ERROR_UNIQUENESS_ENABLED =
      "Expected to assign a business id to process instance with key '%d', but business id assignment is not allowed while business id uniqueness is enabled";
  private static final String ERROR_EMPTY =
      "Expected to assign a business id to process instance with key '%d', but the provided business id is empty";
  private static final String ERROR_INVALID =
      "Expected to assign a business id to process instance with key '%d', but the business id %s";
  private static final String ERROR_ALREADY_ASSIGNED =
      "Expected to assign a business id to process instance with key '%d', but it already has a business id assigned";

  private final StateWriter stateWriter;
  private final boolean businessIdUniquenessEnabled;

  public ProcessInstanceBusinessIdAssignmentBehavior(
      final StateWriter stateWriter, final boolean businessIdUniquenessEnabled) {
    this.stateWriter = stateWriter;
    this.businessIdUniquenessEnabled = businessIdUniquenessEnabled;
  }

  /**
   * Validates that the given Business ID may be assigned to an already-resolved, authorized process
   * instance. The instance must be a root instance (D4), active, uniqueness must be disabled (D5),
   * and the value must pass {@link BusinessIdValidator} (D6). Re-sending the identical value is an
   * idempotent no-op (D3); a different value on an instance that already has one is rejected (D2).
   *
   * @return a decision describing whether the {@code ASSIGNED} event should be written, or a
   *     rejection if a precondition fails
   */
  public Either<Rejection, AssignmentDecision> validate(
      final ElementInstance processInstance, final String businessId) {
    final ProcessInstanceRecord processInstanceRecord = processInstance.getValue();
    final long processInstanceKey = processInstanceRecord.getProcessInstanceKey();

    if (processInstanceRecord.hasParentProcessInstance()) {
      return Either.left(
          new Rejection(
              RejectionType.INVALID_STATE, ERROR_CHILD_INSTANCE.formatted(processInstanceKey)));
    }

    if (!processInstance.isActive()) {
      return Either.left(
          new Rejection(
              RejectionType.INVALID_STATE, ERROR_NOT_ACTIVE.formatted(processInstanceKey)));
    }

    if (businessIdUniquenessEnabled) {
      return Either.left(
          new Rejection(
              RejectionType.INVALID_STATE, ERROR_UNIQUENESS_ENABLED.formatted(processInstanceKey)));
    }

    if (businessId.isEmpty()) {
      return Either.left(
          new Rejection(RejectionType.INVALID_ARGUMENT, ERROR_EMPTY.formatted(processInstanceKey)));
    }

    final var validation = BusinessIdValidator.validate(businessId);
    if (validation.isLeft()) {
      return Either.left(
          new Rejection(
              RejectionType.INVALID_ARGUMENT,
              ERROR_INVALID.formatted(processInstanceKey, validation.getLeft())));
    }

    final String existingBusinessId = processInstanceRecord.getBusinessId();
    if (!existingBusinessId.isEmpty()) {
      if (existingBusinessId.equals(businessId)) {
        return Either.right(new AssignmentDecision(processInstanceRecord, true));
      }
      return Either.left(
          new Rejection(
              RejectionType.INVALID_STATE, ERROR_ALREADY_ASSIGNED.formatted(processInstanceKey)));
    }

    return Either.right(new AssignmentDecision(processInstanceRecord, false));
  }

  /**
   * Enriches the assignment value with the process instance context so the {@code ASSIGNED} event
   * and its exporters carry the full process information.
   */
  public void enrich(
      final ProcessInstanceBusinessIdRecord value,
      final ProcessInstanceRecord processInstanceRecord) {
    value
        .setTenantId(processInstanceRecord.getTenantId())
        .setProcessDefinitionKey(processInstanceRecord.getProcessDefinitionKey())
        .setBpmnProcessId(processInstanceRecord.getBpmnProcessId())
        .setRootProcessInstanceKey(processInstanceRecord.getRootProcessInstanceKey());
  }

  /** Appends the {@code ASSIGNED} follow-up event for the given, already enriched value. */
  public void appendAssignedEvent(
      final long processInstanceKey, final ProcessInstanceBusinessIdRecord value) {
    stateWriter.appendFollowUpEvent(
        processInstanceKey, ProcessInstanceBusinessIdIntent.ASSIGNED, value);
  }

  /**
   * The outcome of a successful validation.
   *
   * @param processInstanceRecord the resolved process-scope record to enrich the assignment with
   * @param idempotent whether the instance already carries exactly the requested Business ID, in
   *     which case no {@code ASSIGNED} event must be written (D3)
   */
  public record AssignmentDecision(
      ProcessInstanceRecord processInstanceRecord, boolean idempotent) {}
}
