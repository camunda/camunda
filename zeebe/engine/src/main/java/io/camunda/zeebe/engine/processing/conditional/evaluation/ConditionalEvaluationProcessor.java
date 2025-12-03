/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.conditional.evaluation;

import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnStateBehavior;
import io.camunda.zeebe.engine.processing.common.EventHandle;
import io.camunda.zeebe.engine.processing.common.EventTriggerBehavior;
import io.camunda.zeebe.engine.processing.common.ExpressionProcessor;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableStartEvent;
import io.camunda.zeebe.engine.processing.expression.InMemoryVariableEvaluationContext;
import io.camunda.zeebe.engine.processing.identity.AuthorizationCheckBehavior;
import io.camunda.zeebe.engine.processing.identity.AuthorizationCheckBehavior.AuthorizationRequest;
import io.camunda.zeebe.engine.processing.identity.AuthorizationCheckBehavior.ForbiddenException;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.deployment.DeployedProcess;
import io.camunda.zeebe.engine.state.immutable.ProcessState;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.protocol.impl.record.value.conditionalevaluation.ConditionalEvaluationRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.ConditionalEvaluationIntent;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.agrona.DirectBuffer;

public class ConditionalEvaluationProcessor
    implements TypedRecordProcessor<ConditionalEvaluationRecord> {

  private final StateWriter stateWriter;
  private final KeyGenerator keyGenerator;
  private final EventHandle eventHandle;
  private final TypedResponseWriter responseWriter;
  private final TypedRejectionWriter rejectionWriter;
  private final ProcessState processState;
  private final AuthorizationCheckBehavior authCheckBehavior;
  private final ExpressionProcessor expressionProcessor;

  public ConditionalEvaluationProcessor(
      final Writers writers,
      final KeyGenerator keyGenerator,
      final ProcessingState processingState,
      final BpmnStateBehavior stateBehavior,
      final EventTriggerBehavior eventTriggerBehavior,
      final AuthorizationCheckBehavior authCheckBehavior,
      final ExpressionProcessor expressionProcessor) {
    stateWriter = writers.state();
    responseWriter = writers.response();
    rejectionWriter = writers.rejection();
    processState = processingState.getProcessState();
    this.keyGenerator = keyGenerator;
    this.authCheckBehavior = authCheckBehavior;
    this.expressionProcessor = expressionProcessor;
    eventHandle =
        new EventHandle(
            keyGenerator,
            processingState.getEventScopeInstanceState(),
            writers,
            processState,
            eventTriggerBehavior,
            stateBehavior);
  }

  @Override
  public void processRecord(final TypedRecord<ConditionalEvaluationRecord> command) {
    final var record = command.getValue();

    final var isAuthNeeded =
        !authCheckBehavior.isInternalCommand(
            command.hasRequestMetadata(), command.getBatchOperationReference());

    if (isAuthNeeded) {
      if (!authCheckBehavior.isAssignedToTenant(command, record.getTenantId())) {
        final var message =
            "Expected to evaluate conditional for tenant '%s', but user is not assigned to this tenant."
                .formatted(record.getTenantId());
        rejectionWriter.appendRejection(command, RejectionType.FORBIDDEN, message);
        responseWriter.writeRejectionOnCommand(command, RejectionType.FORBIDDEN, message);
        return;
      }
    }

    final var processDefinitionKey = record.getProcessDefinitionKey();

    final List<MatchedStartEvent> matchedStartEvents;
    if (processDefinitionKey > 0) {
      final var process =
          processState.getProcessByKeyAndTenant(processDefinitionKey, record.getTenantId());
      if (process == null) {
        rejectionWriter.appendRejection(
            command,
            RejectionType.NOT_FOUND,
            String.format(
                "Expected to evaluate conditional with command key '%d' for process definition key '%d', but no such process was found",
                command.getKey(), processDefinitionKey));
        return;
      }
      matchedStartEvents = collectMatchingStartEvents(record, process);
    } else {
      matchedStartEvents = collectMatchingStartEvents(record);
    }

    if (isAuthNeeded) {
      for (final var match : matchedStartEvents) {
        checkAuthorizationToStartProcessInstance(command, match.process());
      }
    }

    final long eventKey = keyGenerator.nextKey();
    stateWriter.appendFollowUpEvent(eventKey, ConditionalEvaluationIntent.EVALUATED, record);

    for (final var match : matchedStartEvents) {
      eventHandle.activateProcessInstanceForStartEvent(
          match.process().getKey(),
          keyGenerator.nextKey(),
          match.startEventId(),
          record.getVariablesBuffer(),
          record.getTenantId());
    }

    if (command.hasRequestMetadata()) {
      responseWriter.writeEventOnCommand(
          eventKey, ConditionalEvaluationIntent.EVALUATED, record, command);
    }
  }

  @Override
  public ProcessingError tryHandleError(
      final TypedRecord<ConditionalEvaluationRecord> command, final Throwable error) {
    if (error instanceof final ForbiddenException exception) {
      rejectionWriter.appendRejection(
          command, exception.getRejectionType(), exception.getMessage());
      responseWriter.writeRejectionOnCommand(
          command, exception.getRejectionType(), exception.getMessage());
      return ProcessingError.EXPECTED_ERROR;
    }
    return ProcessingError.UNEXPECTED_ERROR;
  }

  private List<MatchedStartEvent> collectMatchingStartEvents(
      final ConditionalEvaluationRecord record, final DeployedProcess process) {
    final var variables = record.getVariables();
    return findMatchingConditionalStartEvents(process, variables.keySet(), variables);
  }

  private List<MatchedStartEvent> collectMatchingStartEvents(
      final ConditionalEvaluationRecord record) {

    final List<MatchedStartEvent> matches = new ArrayList<>();

    final var tenantId = record.getTenantId();
    final var variables = record.getVariables();

    processState.forEachProcessWithLatestVersion(
        persistedProcess -> {
          if (persistedProcess.getTenantId().equals(tenantId)) {
            final var process =
                processState.getProcessByKeyAndTenant(persistedProcess.getKey(), tenantId);
            if (process != null) {
              matches.addAll(
                  findMatchingConditionalStartEvents(process, variables.keySet(), variables));
            }
          }
          return true; // continue iteration
        });
    return matches;
  }

  private List<MatchedStartEvent> findMatchingConditionalStartEvents(
      final DeployedProcess process,
      final Set<String> providedVariableNames,
      final Map<String, Object> variables) {
    final var executableProcess = process.getProcess();

    final List<MatchedStartEvent> matches = new ArrayList<>();

    executableProcess.getStartEvents().stream()
        .filter(ExecutableStartEvent::isConditional)
        .filter(
            startEvent ->
                startEvent.getConditional() != null
                    && startEvent.getConditional().getConditionExpression() != null)
        .filter(
            startEvent -> {
              final var subscriptionVariableNames = startEvent.getConditional().getVariableNames();

              // If no variable names are specified, the subscription matches any variable change
              if (subscriptionVariableNames.isEmpty()) {
                return true;
              }

              return subscriptionVariableNames.stream().anyMatch(providedVariableNames::contains);
            })
        .forEach(
            startEvent -> {
              final var conditionExpression = startEvent.getConditional().getConditionExpression();

              final var result =
                  expressionProcessor.evaluateBooleanExpression(
                      conditionExpression, new InMemoryVariableEvaluationContext(variables));

              if (result.isRight() && result.get()) {
                matches.add(new MatchedStartEvent(process, startEvent.getId()));
              }
            });

    return matches;
  }

  private void checkAuthorizationToStartProcessInstance(
      final TypedRecord<ConditionalEvaluationRecord> command, final DeployedProcess process) {
    final var authRequest =
        new AuthorizationRequest(
                command,
                AuthorizationResourceType.PROCESS_DEFINITION,
                PermissionType.CREATE_PROCESS_INSTANCE,
                command.getValue().getTenantId())
            .addResourceId(BufferUtil.bufferAsString(process.getBpmnProcessId()));

    final var isAuthorized = authCheckBehavior.isAuthorized(authRequest.build());
    if (isAuthorized.isLeft()) {
      throw new ForbiddenException(authRequest);
    }
  }

  private record MatchedStartEvent(DeployedProcess process, DirectBuffer startEventId) {}
}
