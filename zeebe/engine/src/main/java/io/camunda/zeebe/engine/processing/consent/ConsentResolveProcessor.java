/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.consent;

import io.camunda.zeebe.engine.metrics.IncidentMetrics;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnEventPublicationBehavior;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnJobActivationBehavior;
import io.camunda.zeebe.engine.processing.common.ElementTreePathBuilder;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedCommandWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.analyzers.CatchEventAnalyzer;
import io.camunda.zeebe.engine.state.immutable.ElementInstanceState;
import io.camunda.zeebe.engine.state.immutable.EventScopeInstanceState;
import io.camunda.zeebe.engine.state.immutable.JobState;
import io.camunda.zeebe.engine.state.immutable.JobState.State;
import io.camunda.zeebe.engine.state.immutable.ProcessState;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.protocol.impl.record.value.consent.ConsentRecord;
import io.camunda.zeebe.protocol.impl.record.value.incident.IncidentRecord;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.ConsentIntent;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.value.ConsentDecision;
import io.camunda.zeebe.protocol.record.value.ErrorType;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.Optional;

/**
 * Answers the consent request of a parked job, by the job's key. Only the subject may answer: the
 * caller's username must equal the subject's, and no permission overrides that, unlike a user task,
 * where a permission on the process lets anyone complete the task of anyone else.
 *
 * <ul>
 *   <li>{@code APPROVE_ONCE} records an approval for the process instance and the value shown, and
 *       releases the job.
 *   <li>{@code GRANT} records a standing grant, distributed to every partition, and also approves
 *       this request, which the subject has seen together with who asks.
 *   <li>{@code DECLINE} throws {@code CONSENT_DECLINED} to the job's catch event, without a worker
 *       and without a worker's permission check, or raises an incident when nothing catches it.
 * </ul>
 */
public final class ConsentResolveProcessor implements TypedRecordProcessor<ConsentRecord> {

  public static final String CONSENT_DECLINED = "CONSENT_DECLINED";

  private final JobState jobState;
  private final ElementInstanceState elementInstanceState;
  private final EventScopeInstanceState eventScopeInstanceState;
  private final ProcessState processState;
  private final CatchEventAnalyzer catchEventAnalyzer;
  private final BpmnEventPublicationBehavior eventPublicationBehavior;
  private final BpmnJobActivationBehavior jobActivationBehavior;
  private final ConsentGate consentGate;
  private final KeyGenerator keyGenerator;
  private final StateWriter stateWriter;
  private final TypedCommandWriter commandWriter;
  private final TypedRejectionWriter rejectionWriter;
  private final TypedResponseWriter responseWriter;
  private final IncidentMetrics incidentMetrics;

  public ConsentResolveProcessor(
      final ProcessingState state,
      final BpmnEventPublicationBehavior eventPublicationBehavior,
      final BpmnJobActivationBehavior jobActivationBehavior,
      final KeyGenerator keyGenerator,
      final Writers writers,
      final IncidentMetrics incidentMetrics) {
    jobState = state.getJobState();
    elementInstanceState = state.getElementInstanceState();
    eventScopeInstanceState = state.getEventScopeInstanceState();
    processState = state.getProcessState();
    catchEventAnalyzer = new CatchEventAnalyzer(processState, elementInstanceState);
    this.eventPublicationBehavior = eventPublicationBehavior;
    this.jobActivationBehavior = jobActivationBehavior;
    consentGate = jobActivationBehavior.consentGate();
    this.keyGenerator = keyGenerator;
    stateWriter = writers.state();
    commandWriter = writers.command();
    rejectionWriter = writers.rejection();
    responseWriter = writers.response();
    this.incidentMetrics = incidentMetrics;
  }

  @Override
  public void processRecord(final TypedRecord<ConsentRecord> command) {
    final long jobKey = command.getKey() > 0 ? command.getKey() : command.getValue().getJobKey();
    final var job = jobState.getJob(jobKey);
    if (job == null || !job.getConsent().isGated()) {
      reject(
          command,
          RejectionType.NOT_FOUND,
          "Expected to answer the consent request of the job with key '%d', but no such request exists"
              .formatted(jobKey));
      return;
    }
    if (!jobState.isInState(jobKey, State.WAITING_FOR_CONSENT)) {
      reject(
          command,
          RejectionType.INVALID_STATE,
          "Expected to answer the consent request of the job with key '%d', but it is answered already"
              .formatted(jobKey));
      return;
    }

    final String subject = job.getConsent().getSubject();
    final String caller = ConsentGrantProcessor.callerOf(command);
    if (!subject.equals(caller)) {
      reject(
          command,
          RejectionType.FORBIDDEN,
          "Only '%s' can answer this consent request; '%s' cannot answer it for them, and no permission overrides that"
              .formatted(subject, caller));
      return;
    }

    final var operation =
        consentGate
            .connections()
            .findOperation(job.getConsent().getConnection(), job.getConsent().getOperation());
    if (operation.isLeft()) {
      reject(command, RejectionType.NOT_FOUND, operation.getLeft());
      return;
    }

    final ConsentDecision decision = command.getValue().getDecision();
    final ConsentRecord answer = consentGate.requestOf(jobKey, job).setDecision(decision);
    switch (decision) {
      case APPROVE_ONCE -> {
        if (!operation.get().scope().allowsApproval()) {
          reject(command, RejectionType.INVALID_ARGUMENT, scopeMessage(job, "a grant"));
          return;
        }
        approve(command, jobKey, answer);
      }
      case GRANT -> {
        if (!operation.get().scope().allowsGrant()) {
          reject(command, RejectionType.INVALID_ARGUMENT, scopeMessage(job, "a one-time approval"));
          return;
        }
        commandWriter.appendFollowUpCommand(
            keyGenerator.nextKey(),
            ConsentIntent.GRANT,
            new ConsentRecord()
                .setSubject(subject)
                .setConnection(answer.getConnection())
                .setOperation(answer.getOperation())
                .setTenantId(answer.getTenantId()));
        approve(command, jobKey, answer);
      }
      case DECLINE -> decline(command, jobKey, job, answer);
      default ->
          reject(
              command,
              RejectionType.INVALID_ARGUMENT,
              "Expected a decision of APPROVE_ONCE, GRANT or DECLINE, but got '%s'"
                  .formatted(decision));
    }
  }

  private void approve(
      final TypedRecord<ConsentRecord> command, final long jobKey, final ConsentRecord answer) {
    final long key = keyGenerator.nextKey();
    stateWriter.appendFollowUpEvent(key, ConsentIntent.APPROVED, answer);
    responseWriter.writeAcceptedResponseOnCommand(key, ConsentIntent.APPROVED, answer, command);
    // the applier made the job activatable again; push it to a waiting worker, or notify one
    jobActivationBehavior.publishWork(jobKey, jobState.getJob(jobKey));
  }

  private void decline(
      final TypedRecord<ConsentRecord> command,
      final long jobKey,
      final JobRecord job,
      final ConsentRecord answer) {
    final long key = keyGenerator.nextKey();
    stateWriter.appendFollowUpEvent(key, ConsentIntent.DECLINED, answer);
    responseWriter.writeAcceptedResponseOnCommand(key, ConsentIntent.DECLINED, answer, command);

    job.setErrorCode(BufferUtil.wrapString(CONSENT_DECLINED));
    job.setErrorMessage("%s declined the consent request".formatted(answer.getSubject()));
    final var elementInstance = elementInstanceState.getInstance(job.getElementInstanceKey());
    final var catchEvent =
        catchEventAnalyzer.findErrorCatchEvent(
            job.getErrorCodeBuffer(), elementInstance, Optional.of(job.getErrorMessageBuffer()));
    stateWriter.appendFollowUpEvent(jobKey, JobIntent.ERROR_THROWN, job);
    if (catchEvent.isLeft()) {
      raiseIncident(jobKey, job, catchEvent.getLeft().getMessage());
    } else if (eventScopeInstanceState.canTriggerEvent(
        catchEvent.get().getElementInstance().getKey(), catchEvent.get().getCatchEvent().getId())) {
      eventPublicationBehavior.throwErrorEvent(catchEvent.get());
    }
  }

  private void raiseIncident(final long jobKey, final JobRecord job, final String message) {
    final var treePath =
        new ElementTreePathBuilder()
            .withElementInstanceProvider(elementInstanceState::getInstance)
            .withCallActivityIndexProvider(processState::getFlowElement)
            .withElementInstanceKey(job.getElementInstanceKey())
            .build();
    final var incident =
        new IncidentRecord()
            .setErrorType(ErrorType.UNHANDLED_ERROR_EVENT)
            .setErrorMessage(message)
            .setBpmnProcessId(job.getBpmnProcessIdBuffer())
            .setProcessDefinitionKey(job.getProcessDefinitionKey())
            .setProcessInstanceKey(job.getProcessInstanceKey())
            .setElementId(job.getElementIdBuffer())
            .setElementInstanceKey(job.getElementInstanceKey())
            .setTenantId(job.getTenantId())
            .setJobKey(jobKey)
            .setVariableScopeKey(job.getElementInstanceKey())
            .setElementInstancePath(treePath.elementInstancePath())
            .setProcessDefinitionPath(treePath.processDefinitionPath())
            .setCallingElementPath(treePath.callingElementPath());
    stateWriter.appendFollowUpEvent(keyGenerator.nextKey(), IncidentIntent.CREATED, incident);
    incidentMetrics.incidentCreated();
  }

  private static String scopeMessage(final JobRecord job, final String accepted) {
    return "Expected an answer that the operation '%s' of the connection '%s' accepts, but it accepts only %s"
        .formatted(job.getConsent().getOperation(), job.getConsent().getConnection(), accepted);
  }

  private void reject(
      final TypedRecord<ConsentRecord> command, final RejectionType type, final String reason) {
    rejectionWriter.appendRejection(command, type, reason);
    if (command.hasRequestMetadata()) {
      responseWriter.writeRejectedResponseOnCommand(command, type, reason);
    }
  }
}
