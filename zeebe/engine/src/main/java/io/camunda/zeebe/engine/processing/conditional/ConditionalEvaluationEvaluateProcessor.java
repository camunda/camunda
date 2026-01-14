/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.conditional;

import io.camunda.zeebe.el.ExpressionLanguage;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnStateBehavior;
import io.camunda.zeebe.engine.processing.common.EventHandle;
import io.camunda.zeebe.engine.processing.common.EventTriggerBehavior;
import io.camunda.zeebe.engine.processing.common.ExpressionProcessor;
import io.camunda.zeebe.engine.processing.expression.InMemoryVariableEvaluationContext;
import io.camunda.zeebe.engine.processing.identity.authorization.AuthorizationCheckBehavior;
import io.camunda.zeebe.engine.processing.identity.authorization.exception.ForbiddenException;
import io.camunda.zeebe.engine.processing.identity.authorization.request.AuthorizationRequest;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.ConditionalSubscriptionState;
import io.camunda.zeebe.engine.state.immutable.ProcessState;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.protocol.impl.record.value.conditional.ConditionalEvaluationRecord;
import io.camunda.zeebe.protocol.impl.record.value.conditional.ConditionalSubscriptionRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.ConditionalEvaluationIntent;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.agrona.DirectBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConditionalEvaluationEvaluateProcessor
    implements TypedRecordProcessor<ConditionalEvaluationRecord> {

  private static final Logger LOG =
      LoggerFactory.getLogger(ConditionalEvaluationEvaluateProcessor.class);

  private static final String NO_PROCESS_DEFINITION_FOUND_MESSAGE =
      "Expected to evaluate conditional with command key '%d' for process definition key '%d', but no such process was found.";
  private static final String USER_NOT_ASSIGNED_TO_TENANT_MESSAGE =
      "Expected to evaluate conditional for tenant '%s', but user is not assigned to this tenant.";

  private final StateWriter stateWriter;
  private final KeyGenerator keyGenerator;
  private final EventHandle eventHandle;
  private final TypedResponseWriter responseWriter;
  private final TypedRejectionWriter rejectionWriter;
  private final ProcessState processState;
  private final ConditionalSubscriptionState conditionalSubscriptionState;
  private final AuthorizationCheckBehavior authCheckBehavior;
  private final ExpressionProcessor expressionProcessor;
  private final ExpressionLanguage expressionLanguage;

  public ConditionalEvaluationEvaluateProcessor(
      final Writers writers,
      final KeyGenerator keyGenerator,
      final ProcessingState processingState,
      final BpmnStateBehavior stateBehavior,
      final EventTriggerBehavior eventTriggerBehavior,
      final AuthorizationCheckBehavior authCheckBehavior,
      final ExpressionProcessor expressionProcessor,
      final ExpressionLanguage expressionLanguage) {
    stateWriter = writers.state();
    responseWriter = writers.response();
    rejectionWriter = writers.rejection();
    processState = processingState.getProcessState();
    conditionalSubscriptionState = processingState.getConditionalSubscriptionState();
    this.keyGenerator = keyGenerator;
    this.authCheckBehavior = authCheckBehavior;
    this.expressionProcessor = expressionProcessor;
    this.expressionLanguage = expressionLanguage;
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

    if (!authCheckBehavior.isAssignedToTenant(command, record.getTenantId())) {
      final var failureMessage =
          USER_NOT_ASSIGNED_TO_TENANT_MESSAGE.formatted(record.getTenantId());
      rejectionWriter.appendRejection(command, RejectionType.FORBIDDEN, failureMessage);
      responseWriter.writeRejectionOnCommand(command, RejectionType.FORBIDDEN, failureMessage);
      return;
    }

    final var processDefinitionKey = record.getProcessDefinitionKey();

    if (processDefinitionKey > 0
        && processState.getProcessByKeyAndTenant(processDefinitionKey, record.getTenantId())
            == null) {
      final var failureMessage =
          NO_PROCESS_DEFINITION_FOUND_MESSAGE.formatted(command.getKey(), processDefinitionKey);
      rejectionWriter.appendRejection(command, RejectionType.NOT_FOUND, failureMessage);
      responseWriter.writeRejectionOnCommand(command, RejectionType.NOT_FOUND, failureMessage);
      return;
    }

    final List<MatchedStartEvent> matchedStartEvents = collectMatchingStartEvents(record);

    for (final var match : matchedStartEvents) {
      checkAuthorizationToStartProcessInstance(command, match.bpmnProcessId());
    }

    for (final var match : matchedStartEvents) {
      final long processInstanceKey = keyGenerator.nextKey();

      eventHandle.activateProcessInstanceForStartEvent(
          match.processDefinitionKey(),
          processInstanceKey,
          match.startEventId(),
          record.getVariablesBuffer(),
          record.getTenantId());

      record.addStartedProcessInstance(match.processDefinitionKey(), processInstanceKey);
    }

    final long eventKey = keyGenerator.nextKey();
    stateWriter.appendFollowUpEvent(eventKey, ConditionalEvaluationIntent.EVALUATED, record);

    responseWriter.writeEventOnCommand(
        eventKey, ConditionalEvaluationIntent.EVALUATED, record, command);
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
      final ConditionalEvaluationRecord record) {

    final List<MatchedStartEvent> matches = new ArrayList<>();

    final var tenantId = record.getTenantId();
    final var variables = record.getVariables();
    final var providedVariableNames = variables.keySet();
    final var context = new InMemoryVariableEvaluationContext(variables);

    if (record.getProcessDefinitionKey() > 0) {
      conditionalSubscriptionState.visitStartEventSubscriptionsByProcessDefinitionKey(
          record.getProcessDefinitionKey(),
          subscription -> {
            evaluateSubscription(subscription.getRecord(), providedVariableNames, context, matches);
            return true;
          });
    } else {
      conditionalSubscriptionState.visitStartEventSubscriptionsByTenantId(
          tenantId,
          subscription -> {
            evaluateSubscription(subscription.getRecord(), providedVariableNames, context, matches);
            return true;
          });
    }
    return matches;
  }

  private void evaluateSubscription(
      final ConditionalSubscriptionRecord subscription,
      final Set<String> providedVariableNames,
      final InMemoryVariableEvaluationContext context,
      final List<MatchedStartEvent> matches) {

    final var variableNames = subscription.getVariableNames();

    if (!variableNames.isEmpty()
        && variableNames.stream().noneMatch(providedVariableNames::contains)) {
      return;
    }

    final String conditionString = subscription.getCondition();
    final var conditionExpression = expressionLanguage.parseExpression(conditionString);

    if (!conditionExpression.isValid()) {
      LOG.warn(
          "Failed to parse condition expression for conditional start event '{}' in process '{}': {}",
          subscription.getCatchEventId(),
          subscription.getBpmnProcessId(),
          conditionExpression.getFailureMessage());
      return;
    }

    final var result = expressionProcessor.evaluateBooleanExpression(conditionExpression, context);

    if (result.isRight() && Boolean.TRUE.equals(result.get())) {
      matches.add(
          new MatchedStartEvent(
              subscription.getProcessDefinitionKey(),
              subscription.getBpmnProcessId(),
              subscription.getCatchEventIdBuffer()));
    } else if (result.isLeft()) {
      LOG.debug(
          "Failed to evaluate condition on conditional start event '{}' in process '{}': {}",
          subscription.getCatchEventId(),
          subscription.getBpmnProcessId(),
          result.getLeft().getMessage());
    }
  }

  private void checkAuthorizationToStartProcessInstance(
      final TypedRecord<ConditionalEvaluationRecord> command, final String bpmnProcessId) {
    final var authRequest =
        AuthorizationRequest.builder()
            .command(command)
            .resourceType(AuthorizationResourceType.PROCESS_DEFINITION)
            .permissionType(PermissionType.CREATE_PROCESS_INSTANCE)
            .tenantId(command.getValue().getTenantId())
            .addResourceId(bpmnProcessId)
            .build();

    final var isAuthorized = authCheckBehavior.isAuthorized(authRequest);
    if (isAuthorized.isLeft()) {
      throw new ForbiddenException(authRequest);
    }
  }

  private record MatchedStartEvent(
      long processDefinitionKey, String bpmnProcessId, DirectBuffer startEventId) {}
}
