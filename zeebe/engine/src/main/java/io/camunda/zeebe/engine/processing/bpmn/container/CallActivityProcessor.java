/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.bpmn.container;

import io.camunda.zeebe.el.EvaluationResult;
import io.camunda.zeebe.engine.processing.bpmn.BpmnElementContainerProcessor;
import io.camunda.zeebe.engine.processing.bpmn.BpmnElementContext;
import io.camunda.zeebe.engine.processing.bpmn.BpmnProcessingException;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnBehaviors;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnCompensationSubscriptionBehaviour;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnEventSubscriptionBehavior;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnIncidentBehavior;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnJobBehavior;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnStateBehavior;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnStateTransitionBehavior;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnVariableMappingBehavior;
import io.camunda.zeebe.engine.processing.common.ExpressionProcessor;
import io.camunda.zeebe.engine.processing.common.Failure;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableCallActivity;
import io.camunda.zeebe.engine.processing.processinstance.BusinessIdValidator;
import io.camunda.zeebe.engine.state.deployment.DeployedProcess;
import io.camunda.zeebe.engine.state.deployment.PersistedProcess.PersistedProcessState;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeBindingType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.ErrorType;
import io.camunda.zeebe.util.Either;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.stream.Collectors;
import org.agrona.DirectBuffer;

public final class CallActivityProcessor
    implements BpmnElementContainerProcessor<ExecutableCallActivity> {

  public static final String MAX_DEPTH_EXCEEDED_MESSAGE =
      """
      The call activity has reached the maximum depth of %d. \
      This is likely due to a recursive call. \
      Cancel the root process instance if this was unintentional. \
      Otherwise, consider increasing the maximum depth, \
      or use process instance modification to adjust the process instance.""";
  private static final String UNABLE_TO_COMPLETE_FROM_STATE_MESSAGE =
      "Expected to complete call activity after child completed, but call activity cannot be completed from state '%s'";
  private static final String UNABLE_TO_TERMINATE_FROM_STATE_MESSAGE =
      "Expected to terminate call activity after child terminated, but call activity cannot be terminated from state '%s'";

  private final ExpressionProcessor expressionProcessor;
  private final BpmnStateTransitionBehavior stateTransitionBehavior;
  private final BpmnStateBehavior stateBehavior;
  private final BpmnIncidentBehavior incidentBehavior;
  private final BpmnEventSubscriptionBehavior eventSubscriptionBehavior;
  private final BpmnVariableMappingBehavior variableMappingBehavior;
  private final BpmnCompensationSubscriptionBehaviour compensationSubscriptionBehaviour;
  private final BpmnJobBehavior jobBehavior;
  private final int maxProcessDepth;

  public CallActivityProcessor(
      final BpmnBehaviors bpmnBehaviors,
      final BpmnStateTransitionBehavior stateTransitionBehavior,
      final int maxProcessDepth) {
    expressionProcessor = bpmnBehaviors.expressionProcessor();
    this.stateTransitionBehavior = stateTransitionBehavior;
    stateBehavior = bpmnBehaviors.stateBehavior();
    incidentBehavior = bpmnBehaviors.incidentBehavior();
    eventSubscriptionBehavior = bpmnBehaviors.eventSubscriptionBehavior();
    variableMappingBehavior = bpmnBehaviors.variableMappingBehavior();
    compensationSubscriptionBehaviour = bpmnBehaviors.compensationSubscriptionBehaviour();
    jobBehavior = bpmnBehaviors.jobBehavior();
    this.maxProcessDepth = maxProcessDepth;
  }

  @Override
  public Class<ExecutableCallActivity> getType() {
    return ExecutableCallActivity.class;
  }

  @Override
  public Either<Failure, ?> onActivate(
      final ExecutableCallActivity element, final BpmnElementContext context) {
    return variableMappingBehavior
        .applyInputMappings(context, element)
        .flatMap(ok -> validateProcessDepth(context));
  }

  @Override
  public Either<Failure, ?> finalizeActivation(
      final ExecutableCallActivity element, final BpmnElementContext context) {
    return evaluateProcessId(context, element)
        .flatMap(
            processId ->
                getCalledProcess(
                    processId, element.getBindingType(), element.getVersionTag(), context))
        .flatMap(this::rejectIfDraining)
        .flatMap(this::checkProcessHasNoneStartEvent)
        .flatMap(p -> eventSubscriptionBehavior.subscribeToEvents(element, context).map(ok -> p))
        .flatMap(
            process ->
                resolveChildBusinessId(context, element)
                    .map(businessId -> new CalledProcess(process, businessId)))
        .thenDo(
            calledProcess -> {
              final var process = calledProcess.process();
              final var activated =
                  stateTransitionBehavior.transitionToActivated(context, element.getEventType());

              final var childProcessInstanceKey =
                  stateTransitionBehavior.createChildProcessInstance(
                      process, context, calledProcess.businessId());

              final var propagateAllParentVariablesEnabled =
                  element.isPropagateAllParentVariablesEnabled();
              final var inputMappings = element.getInputMappings();
              final var callActivityInstanceKey = activated.getElementInstanceKey();
              final var rootProcessInstanceKey = context.getRootProcessInstanceKey();

              if (propagateAllParentVariablesEnabled) {
                stateBehavior.copyAllVariablesToProcessInstance(
                    callActivityInstanceKey,
                    childProcessInstanceKey,
                    rootProcessInstanceKey,
                    process);
              } else if (inputMappings.isPresent()) {
                // when activating the call activity, the input mappings will be applied.
                // Resulting in local variables in the (local) call activity scope.
                // These local variables can simply be propagated to the called child
                // process instance.
                stateBehavior.copyLocalVariablesToProcessInstance(
                    callActivityInstanceKey,
                    childProcessInstanceKey,
                    rootProcessInstanceKey,
                    process);
              }
            });
  }

  @Override
  public Either<Failure, ?> onComplete(
      final ExecutableCallActivity element, final BpmnElementContext context) {
    return variableMappingBehavior
        .applyOutputMappings(context, element)
        .thenDo(ok -> eventSubscriptionBehavior.unsubscribeFromEvents(context));
  }

  @Override
  public Either<Failure, ?> finalizeCompletion(
      final ExecutableCallActivity element, final BpmnElementContext context) {
    compensationSubscriptionBehaviour.createCompensationSubscription(element, context);
    return stateTransitionBehavior
        .transitionToCompleted(element, context)
        .thenDo(
            completed -> {
              compensationSubscriptionBehaviour.completeCompensationHandler(completed);
              stateTransitionBehavior
                  .executeRuntimeInstructions(element, completed)
                  .ifRight(
                      notInterrupted ->
                          stateTransitionBehavior.takeOutgoingSequenceFlows(element, completed));
            });
  }

  @Override
  public TransitionOutcome onTerminate(
      final ExecutableCallActivity element, final BpmnElementContext context) {

    if (element.hasExecutionListeners()) {
      jobBehavior.cancelJob(context);
    }

    eventSubscriptionBehavior.unsubscribeFromEvents(context);
    incidentBehavior.resolveIncidents(context);
    stateTransitionBehavior.terminateChildProcessInstance(this, element, context);
    return TransitionOutcome.CONTINUE;
  }

  @Override
  public void finalizeTermination(
      final ExecutableCallActivity element, final BpmnElementContext context) {
    stateTransitionBehavior.executeRuntimeInstructions(element, context);
  }

  @Override
  public void afterExecutionPathCompleted(
      final ExecutableCallActivity element,
      final BpmnElementContext callActivityContext,
      final BpmnElementContext childContext,
      final Boolean satisfiesCompletionCondition) {
    final var currentState = callActivityContext.getIntent();

    if (currentState == ProcessInstanceIntent.ELEMENT_ACTIVATED) {
      stateTransitionBehavior.completeElement(callActivityContext);

    } else if (currentState == ProcessInstanceIntent.ELEMENT_TERMINATING) {
      // the call activity is interrupted concurrently (e.g. by a boundary event)
      transitionToTerminated(element, callActivityContext);

    } else {
      final var message = String.format(UNABLE_TO_COMPLETE_FROM_STATE_MESSAGE, currentState);
      throw new BpmnProcessingException(callActivityContext, message);
    }
  }

  @Override
  public void onChildTerminated(
      final ExecutableCallActivity element,
      final BpmnElementContext callActivityContext,
      final BpmnElementContext childContext) {
    final var currentState = callActivityContext.getIntent();
    if (currentState != ProcessInstanceIntent.ELEMENT_TERMINATING) {
      final var message = String.format(UNABLE_TO_TERMINATE_FROM_STATE_MESSAGE, currentState);
      throw new BpmnProcessingException(callActivityContext, message);
    }

    transitionToTerminated(element, callActivityContext);
  }

  /**
   * Returns a failure if the process depth of the called instance is about to exceed the maximum
   * allowed depth. Otherwise, returns a right.
   */
  private Either<Failure, Void> validateProcessDepth(final BpmnElementContext context) {
    final var processInstance = stateBehavior.getElementInstance(context.getProcessInstanceKey());
    final int processDepth = processInstance.getProcessDepth();
    final var isExceedingMaxDepth = (processDepth + 1) > maxProcessDepth;
    if (isExceedingMaxDepth) {
      final var message = MAX_DEPTH_EXCEEDED_MESSAGE.formatted(maxProcessDepth);
      return Either.left(new Failure(message, ErrorType.CALLED_ELEMENT_ERROR));
    }
    return Either.right(null);
  }

  private void transitionToTerminated(
      final ExecutableCallActivity element, final BpmnElementContext context) {
    final var flowScopeInstance = stateBehavior.getFlowScopeInstance(context);

    eventSubscriptionBehavior
        .findEventTrigger(context)
        .filter(eventTrigger -> flowScopeInstance.isActive())
        .filter(eventTrigger -> !flowScopeInstance.isInterrupted())
        .ifPresentOrElse(
            eventTrigger -> {
              final var terminated =
                  stateTransitionBehavior.transitionToTerminated(context, element.getEventType());
              eventSubscriptionBehavior.activateTriggeredEvent(
                  context.getElementInstanceKey(),
                  terminated.getFlowScopeKey(),
                  eventTrigger,
                  terminated);
            },
            () -> {
              final var terminated =
                  stateTransitionBehavior.transitionToTerminated(context, element.getEventType());
              stateTransitionBehavior.onElementTerminated(element, terminated);
            });
  }

  private Either<Failure, DirectBuffer> evaluateProcessId(
      final BpmnElementContext context, final ExecutableCallActivity element) {
    final var processIdExpression = element.getCalledElementProcessId();
    final var scopeKey = context.getElementInstanceKey();
    final var tenantId = context.getTenantId();
    return expressionProcessor.evaluateStringExpressionAsDirectBuffer(
        processIdExpression, scopeKey, tenantId);
  }

  /**
   * Resolves the Business ID to assign to the child process instance, per ADR 0003-810 (D2/D3/D4):
   *
   * <ul>
   *   <li><b>inherit</b> (no configuration) — keep the parent's Business ID, preserving the 8.9
   *       behavior;
   *   <li><b>literal/empty</b> (static expression) — use the configured value verbatim (subject to
   *       the length constraint), so a numeric or reserved-word literal is not coerced and an empty
   *       value yields no Business ID;
   *   <li><b>FEEL</b> (dynamic expression) — evaluate the raw result at the call-activity
   *       element-instance scope and interpret it: an explicit null or an empty string discards the
   *       Business ID (no incident), while an unresolvable variable, a non-string/non-null type, or
   *       an over-long value raises a resolvable incident.
   * </ul>
   */
  private Either<Failure, String> resolveChildBusinessId(
      final BpmnElementContext context, final ExecutableCallActivity element) {
    final var businessIdExpression = element.getCalledElementBusinessId();
    if (businessIdExpression == null) {
      // No explicit business id: inherit the parent's. Read it from the process-scope element
      // instance (not the call-activity element's record copy) so a Business ID assigned late to a
      // running instance is inherited by children started afterwards.
      return Either.right(getParentBusinessId(context));
    }
    if (businessIdExpression.isStatic()) {
      return validateBusinessId(businessIdExpression.getExpression(), context);
    }
    return expressionProcessor
        .evaluateAnyExpression(
            businessIdExpression, context.getElementInstanceKey(), context.getTenantId())
        .flatMap(result -> resolveBusinessIdFromResult(result, context));
  }

  private String getParentBusinessId(final BpmnElementContext context) {
    final var processInstance = stateBehavior.getElementInstance(context.getProcessInstanceKey());
    return processInstance != null ? processInstance.getValue().getBusinessId() : "";
  }

  private Either<Failure, String> resolveBusinessIdFromResult(
      final EvaluationResult result, final BpmnElementContext context) {
    return switch (result.getType()) {
      // An empty string is a valid discard, a non-empty one is used (subject to the length check).
      case STRING -> validateBusinessId(result.getString(), context);
      // A null result is either an intentional discard ('=null') or a coerced null from a failed
      // evaluation — only the latter is an incident.
      case NULL -> resolveNullBusinessId(result, context);
      default -> Either.left(nonStringBusinessIdFailure(result, context));
    };
  }

  private Either<Failure, String> resolveNullBusinessId(
      final EvaluationResult result, final BpmnElementContext context) {
    // A null accompanied by evaluation warnings was coerced from a failure (e.g. a missing
    // variable, function, or property), so it is an incident; an intentional '=null' reports no
    // warnings and discards the Business ID.
    if (!result.getWarnings().isEmpty()) {
      return Either.left(
          new Failure(
              "Expected to resolve the business id for the call activity from expression '%s', but it evaluated to null.%s"
                  .formatted(result.getExpression(), formatWarnings(result)),
              ErrorType.EXTRACT_VALUE_ERROR,
              context.getElementInstanceKey()));
    }
    // Explicit null (e.g. '=null'): intentionally discard the Business ID.
    return Either.right("");
  }

  private Either<Failure, String> validateBusinessId(
      final String businessId, final BpmnElementContext context) {
    return BusinessIdValidator.validate(businessId)
        .mapLeft(
            reason ->
                new Failure(
                    "Expected to resolve a valid business id for the call activity, but it %s."
                        .formatted(reason),
                    ErrorType.EXTRACT_VALUE_ERROR,
                    context.getElementInstanceKey()));
  }

  private Failure nonStringBusinessIdFailure(
      final EvaluationResult result, final BpmnElementContext context) {
    return new Failure(
        "Expected the business id for the call activity to resolve to a string, but expression '%s' evaluated to '%s'."
            .formatted(result.getExpression(), result.getType()),
        ErrorType.EXTRACT_VALUE_ERROR,
        context.getElementInstanceKey());
  }

  private static String formatWarnings(final EvaluationResult result) {
    final var warnings = result.getWarnings();
    if (warnings.isEmpty()) {
      return "";
    }
    return " The evaluation reported the following warnings:\n"
        + warnings.stream()
            .map(warning -> "[%s] %s".formatted(warning.getType(), warning.getMessage()))
            .collect(Collectors.joining("\n"));
  }

  private Either<Failure, DeployedProcess> getCalledProcess(
      final DirectBuffer processId,
      final ZeebeBindingType bindingType,
      final String versionTag,
      final BpmnElementContext context) {
    return switch (bindingType) {
      case deployment -> getProcessVersionInSameDeployment(processId, context);
      case latest -> getLatestProcessVersion(processId, context.getTenantId());
      case versionTag ->
          getLatestProcessVersionWithVersionTag(processId, versionTag, context.getTenantId());
    };
  }

  private Either<Failure, DeployedProcess> getProcessVersionInSameDeployment(
      final DirectBuffer processId, final BpmnElementContext context) {
    return stateBehavior
        .getDeploymentKey(context.getProcessDefinitionKey(), context.getTenantId())
        .flatMap(
            deploymentKey ->
                stateBehavior
                    .getProcessByProcessIdAndDeploymentKey(
                        processId, deploymentKey, context.getTenantId())
                    .<Either<Failure, DeployedProcess>>map(Either::right)
                    .orElseGet(
                        () ->
                            Either.left(
                                new Failure(
                                    String.format(
                                        """
                                        Expected to call process with BPMN process id '%s' with binding type 'deployment', \
                                        but no such process found in the deployment with key %s which contained the current process. \
                                        To resolve this incident, migrate the process instance to a process definition \
                                        that is deployed together with the intended process definition to call.\
                                        """,
                                        BufferUtil.bufferAsString(processId), deploymentKey),
                                    ErrorType.CALLED_ELEMENT_ERROR))));
  }

  private Either<Failure, DeployedProcess> getLatestProcessVersion(
      final DirectBuffer processId, final String tenantId) {
    final var process = stateBehavior.getLatestProcessVersion(processId, tenantId);
    return process
        .<Either<Failure, DeployedProcess>>map(Either::right)
        .orElseGet(
            () ->
                Either.left(
                    new Failure(
                        String.format(
                            "Expected process with BPMN process id '%s' to be deployed, but not found.",
                            BufferUtil.bufferAsString(processId)),
                        ErrorType.CALLED_ELEMENT_ERROR)));
  }

  private Either<Failure, DeployedProcess> getLatestProcessVersionWithVersionTag(
      final DirectBuffer processId, final String versionTag, final String tenantId) {
    final var process =
        stateBehavior.getProcessByProcessIdAndVersionTag(processId, versionTag, tenantId);
    return process
        .<Either<Failure, DeployedProcess>>map(Either::right)
        .orElseGet(
            () ->
                Either.left(
                    new Failure(
                        String.format(
                            """
                            Expected to call process with BPMN process id '%s' and version tag '%s', but no such process found. \
                            To resolve this incident, deploy a process with the given process id and version tag.\
                            """,
                            BufferUtil.bufferAsString(processId), versionTag),
                        ErrorType.CALLED_ELEMENT_ERROR)));
  }

  private Either<Failure, DeployedProcess> rejectIfDraining(final DeployedProcess process) {
    if (process.isDraining()) {
      return Either.left(
          new Failure(
              String.format(
                  "Expected to call process with BPMN process id '%s', but it is being deleted.",
                  BufferUtil.bufferAsString(process.getBpmnProcessId())),
              ErrorType.CALLED_ELEMENT_ERROR));
    }
    return Either.right(process);
  }

  private Either<Failure, DeployedProcess> checkProcessHasNoneStartEvent(
      final DeployedProcess process) {
    if (process.getProcess().getNoneStartEvent() == null) {
      return Either.left(
          new Failure(
              String.format(
                  "Expected process with BPMN process id '%s' to have a none start event, but not found.",
                  BufferUtil.bufferAsString(process.getBpmnProcessId())),
              ErrorType.CALLED_ELEMENT_ERROR));
    }
    return Either.right(process);
  }

  private record CalledProcess(DeployedProcess process, String businessId) {}
}
