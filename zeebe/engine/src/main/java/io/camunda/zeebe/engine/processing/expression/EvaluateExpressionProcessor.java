/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.expression;

import io.camunda.zeebe.engine.processing.common.ExpressionProcessor;
import io.camunda.zeebe.engine.processing.identity.AuthorizationCheckBehavior;
import io.camunda.zeebe.engine.processing.identity.AuthorizationCheckBehavior.AuthorizationRequest;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.ElementInstanceState;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.impl.record.value.expression.ExpressionRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.ExpressionIntent;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.ExpressionScopeType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.util.Either;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.Map;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

/**
 * Processor for evaluating FEEL expressions using the engine's expression evaluator.
 *
 * <p>This processor handles expression evaluation requests without causing side effects. It does
 * not write to the event log - evaluation results are returned directly to the requester via the
 * response writer.
 *
 * <p>Supports three evaluation scopes:
 *
 * <ul>
 *   <li><b>NONE</b>: Only uses provided context variables
 *   <li><b>CLUSTER</b>: Uses provided context + cluster variables
 *   <li><b>PROCESS_INSTANCE</b>: Uses provided context + cluster + process instance variables
 * </ul>
 */
public final class EvaluateExpressionProcessor implements TypedRecordProcessor<ExpressionRecord> {

  private static final int MAX_EXPRESSION_LENGTH = 10_000; // characters
  private static final int MAX_CONTEXT_SIZE = 100_000; // bytes

  private final ExpressionProcessor baseExpressionProcessor;
  private final ElementInstanceState elementInstanceState;
  private final TypedResponseWriter responseWriter;
  private final AuthorizationCheckBehavior authCheckBehavior;

  public EvaluateExpressionProcessor(
      final ExpressionProcessor baseExpressionProcessor,
      final ProcessingState processingState,
      final Writers writers,
      final AuthorizationCheckBehavior authCheckBehavior) {
    this.baseExpressionProcessor = baseExpressionProcessor;
    elementInstanceState = processingState.getElementInstanceState();
    responseWriter = writers.response();
    this.authCheckBehavior = authCheckBehavior;
  }

  @Override
  public void processRecord(final TypedRecord<ExpressionRecord> command) {
    final var expressionRecord = command.getValue();

    // Validate request size limits
    final var validationFailure = validateRequestLimits(expressionRecord);
    if (validationFailure != null) {
      rejectCommand(command, RejectionType.INVALID_ARGUMENT, validationFailure);
      return;
    }

    // Check tenant authorization
    if (!authCheckBehavior.isAssignedToTenant(command, expressionRecord.getTenantId())) {
      final var message =
          "Expected to evaluate expression for tenant '%s', but user is not assigned to this tenant."
              .formatted(expressionRecord.getTenantId());
      rejectCommand(command, RejectionType.UNAUTHORIZED, message);
      return;
    }

    // Authorize based on scope
    final var authorizationFailure = authorizeEvaluationScope(command, expressionRecord);
    if (authorizationFailure != null) {
      rejectCommand(command, RejectionType.UNAUTHORIZED, authorizationFailure);
      return;
    }

    // Parse expression first
    final var expression =
        baseExpressionProcessor.parseExpression(expressionRecord.getExpression());
    if (!expression.isValid()) {
      final var message =
          "Failed to parse expression '%s': %s"
              .formatted(expressionRecord.getExpression(), expression.getFailureMessage());
      rejectCommand(command, RejectionType.INVALID_ARGUMENT, message);
      return;
    }

    // Build evaluation context from provided variables
    final var providedContext = createScopedContextFromMap(expressionRecord.getContext());

    // Build scoped expression processor by prepending the provided context
    final var scopedProcessor =
        buildScopedProcessor(
            expressionRecord.getScopeType(),
            expressionRecord.getProcessInstanceKey(),
            providedContext,
            expressionRecord.getTenantId());

    // Evaluate expression
    // For NONE scope: use negative scopeKey (empty context from processor)
    // For CLUSTER scope: use negative scopeKey (cluster vars already in processor)
    // For PROCESS_INSTANCE scope: use processInstanceKey to activate process scoping
    final long scopeKey =
        expressionRecord.getScopeType() == ExpressionScopeType.PROCESS_INSTANCE
            ? expressionRecord.getProcessInstanceKey()
            : -1;

    final var evaluationResult =
        scopedProcessor.evaluateAnyExpression(expression, scopeKey, expressionRecord.getTenantId());

    // Build response record
    final var responseRecord = new ExpressionRecord();
    responseRecord.wrap(expressionRecord);

    if (evaluationResult.isLeft()) {
      final var failure = evaluationResult.getLeft();
      responseRecord.setRejectionReason(failure.getMessage());
      rejectCommand(command, RejectionType.INVALID_STATE, failure.getMessage());
      return;
    }

    // Get result buffer
    final var resultBuffer = evaluationResult.get();

    // TODO: Extract result type and warnings
    // evaluateAnyExpression returns DirectBuffer but not the type or warnings
    responseRecord.setResultType("UNKNOWN");
    responseRecord.setResult(resultBuffer);

    // Write response (ephemeral - not written to log)
    responseWriter.writeResponse(
        command.getKey(),
        ExpressionIntent.EVALUATED,
        responseRecord,
        command.getValueType(),
        command.getRequestId(),
        command.getRequestStreamId());
  }

  private String validateRequestLimits(final ExpressionRecord record) {
    final var expression = record.getExpression();
    if (expression == null || expression.isEmpty()) {
      return "Expression must not be null or empty.";
    }

    if (expression.length() > MAX_EXPRESSION_LENGTH) {
      return "Expression exceeds maximum length of %d characters.".formatted(MAX_EXPRESSION_LENGTH);
    }

    final var contextBuffer = record.getContextBuffer();
    if (contextBuffer != null && contextBuffer.capacity() > MAX_CONTEXT_SIZE) {
      return "Context exceeds maximum size of %d bytes.".formatted(MAX_CONTEXT_SIZE);
    }

    return null;
  }

  private String authorizeEvaluationScope(
      final TypedRecord<ExpressionRecord> command, final ExpressionRecord record) {

    final var scopeType = record.getScopeType();

    if (scopeType == ExpressionScopeType.PROCESS_INSTANCE) {
      final var processInstanceKey = record.getProcessInstanceKey();

      if (processInstanceKey <= 0) {
        return "Process instance key must be positive for PROCESS_INSTANCE scope.";
      }

      // Check if process instance exists
      final var elementInstance = elementInstanceState.getInstance(processInstanceKey);
      if (elementInstance == null) {
        return "Process instance with key %d not found.".formatted(processInstanceKey);
      }

      // Authorize read access to process instance
      final var authRequest =
          new AuthorizationRequest(
                  command,
                  AuthorizationResourceType.PROCESS_DEFINITION,
                  PermissionType.READ_PROCESS_INSTANCE,
                  record.getTenantId())
              .addResourceId(
                  BufferUtil.bufferAsString(elementInstance.getValue().getBpmnProcessIdBuffer()));

      final var isAuthorized = authCheckBehavior.isAuthorized(authRequest);
      if (isAuthorized.isLeft()) {
        return "User is not authorized to read process instance %d.".formatted(processInstanceKey);
      }
    }

    // CLUSTER and NONE scopes don't require special authorization
    // User's tenant membership was already verified
    return null;
  }

  /**
   * Builds an ExpressionProcessor with the appropriate scope for evaluation.
   *
   * <p>Strategy: prepend the provided context to give it priority over cluster/process variables.
   */
  private ExpressionProcessor buildScopedProcessor(
      final ExpressionScopeType scopeType,
      final long processInstanceKey,
      final ScopedEvaluationContext providedContext,
      final String tenantId) {

    // For all scopes, prepend the provided context
    // The base processor already contains cluster variables and process variable context
    // When we call evaluateAnyExpression with a scopeKey:
    // - negative scopeKey = no process scoping (for NONE and CLUSTER)
    // - positive scopeKey = process scoping activated (for PROCESS_INSTANCE)
    return baseExpressionProcessor.prependContext(providedContext);
  }

  /**
   * Creates a ScopedEvaluationContext from a Map of context variables.
   *
   * <p>Variables are converted to MessagePack buffers for evaluation.
   */
  private ScopedEvaluationContext createScopedContextFromMap(final Map<String, Object> context) {
    if (context == null || context.isEmpty()) {
      return ScopedEvaluationContext.NONE_INSTANCE;
    }

    return new ScopedEvaluationContext() {
      @Override
      public Either<DirectBuffer, io.camunda.zeebe.el.EvaluationContext> getVariable(
          final String variableName) {
        final var value = context.get(variableName);
        if (value == null) {
          return Either.left(null);
        }

        // Convert the value to MessagePack buffer
        final var msgPackBytes = MsgPackConverter.convertToMsgPack(value);
        // Wrap byte array in DirectBuffer
        final var buffer = new UnsafeBuffer(msgPackBytes);
        return Either.left(buffer);
      }
    };
  }

  private void rejectCommand(
      final TypedRecord<ExpressionRecord> command,
      final RejectionType rejectionType,
      final String reason) {
    responseWriter.writeRejectionOnCommand(command, rejectionType, reason);
  }
}
