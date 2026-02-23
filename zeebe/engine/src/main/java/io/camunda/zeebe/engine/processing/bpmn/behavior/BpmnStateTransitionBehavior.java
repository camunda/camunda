/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.bpmn.behavior;

import io.camunda.zeebe.engine.metrics.ProcessEngineMetrics;
import io.camunda.zeebe.engine.processing.bpmn.BpmnElementContainerProcessor;
import io.camunda.zeebe.engine.processing.bpmn.BpmnElementContext;
import io.camunda.zeebe.engine.processing.bpmn.BpmnProcessingException;
import io.camunda.zeebe.engine.processing.bpmn.ProcessInstanceLifecycle;
import io.camunda.zeebe.engine.processing.common.ElementTreePathBuilder.ElementTreePathProperties;
import io.camunda.zeebe.engine.processing.common.Failure;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableCallActivity;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableFlowElement;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableFlowNode;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableIntermediateThrowEvent;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableSequenceFlow;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedCommandWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.deployment.DeployedProcess;
import io.camunda.zeebe.engine.state.immutable.AsyncRequestState.AsyncRequest;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceBatchRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.RuntimeInstructionRecord;
import io.camunda.zeebe.protocol.record.intent.AsyncRequestIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceBatchIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.RuntimeInstructionIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.BpmnEventType;
import io.camunda.zeebe.protocol.record.value.RuntimeInstructionType;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import io.camunda.zeebe.util.Either;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.Arrays;
import java.util.function.Function;

public final class BpmnStateTransitionBehavior {
  private static final String ALREADY_MIGRATED_ERROR_MSG =
      "The Processor for the element type %s is already migrated no need to call %s again this is already done in the BpmnStreamProcessor for you. Happy to help :) ";
  private static final String NO_PROCESS_FOUND_MESSAGE =
      "Expected to find a deployed process for process id '%s', but none found.";

  private final ProcessInstanceRecord childInstanceRecord = new ProcessInstanceRecord();
  private final ProcessInstanceRecord followUpInstanceRecord = new ProcessInstanceRecord();
  private final RuntimeInstructionRecord runtimeInstructionRecord = new RuntimeInstructionRecord();

  private final KeyGenerator keyGenerator;
  private final BpmnStateBehavior stateBehavior;
  private final BpmnJobBehavior jobBehavior;
  private final Function<BpmnElementType, BpmnElementContainerProcessor<ExecutableFlowElement>>
      processorLookUp;

  private final BpmnUserTaskBehavior userTaskBehavior;
  private final ProcessEngineMetrics metrics;
  private final StateWriter stateWriter;
  private final TypedCommandWriter commandWriter;
  private final BpmnEventSubscriptionBehavior eventSubscriptionBehavior;
  private final BpmnIncidentBehavior incidentBehavior;

  public BpmnStateTransitionBehavior(
      final KeyGenerator keyGenerator,
      final BpmnStateBehavior stateBehavior,
      final BpmnJobBehavior bpmnJobBehavior,
      final BpmnEventSubscriptionBehavior eventSubscriptionBehavior,
      final BpmnIncidentBehavior incidentBehavior,
      final BpmnUserTaskBehavior userTaskBehavior,
      final ProcessEngineMetrics metrics,
      final Function<BpmnElementType, BpmnElementContainerProcessor<ExecutableFlowElement>>
          processorLookUp,
      final Writers writers) {
    this.keyGenerator = keyGenerator;
    this.stateBehavior = stateBehavior;
    jobBehavior = bpmnJobBehavior;
    this.userTaskBehavior = userTaskBehavior;
    this.metrics = metrics;
    this.processorLookUp = processorLookUp;
    stateWriter = writers.state();
    commandWriter = writers.command();
    this.eventSubscriptionBehavior = eventSubscriptionBehavior;
    this.incidentBehavior = incidentBehavior;
  }

  /**
   * @return context with updated intent
   */
  public BpmnElementContext transitionToActivating(final BpmnElementContext context) {

    final var elementInstance = stateBehavior.getElementInstance(context);
    if (elementInstance != null) {
      verifyIncidentResolving(context, "#transitionToActivating");
      // if the element already exists, then the Activate_Element command is processed as a result
      // of resolving an incident. We don't have to transition again. Just update the context
      return context.copy(
          context.getElementInstanceKey(),
          context.getRecordValue(),
          ProcessInstanceIntent.ELEMENT_ACTIVATING);
    }

    var transitionContext = context;

    // When the element instance key is not set (-1), then we process the ACTIVATE_ELEMENT
    // command. We need to generate a new key in order to transition to ELEMENT_ACTIVATING, such
    // that we can assign the new create element instance a correct key. It is expected that on
    // the command the key is not set. But some elements (such as multi instance), need to
    // generate the key before they write ACTIVATE command, to prepare the state (e.g. set
    // variables) for the upcoming element instance.
    if (context.getElementInstanceKey() == -1) {
      final var newElementInstanceKey = keyGenerator.nextKey();
      transitionContext =
          context.copy(newElementInstanceKey, context.getRecordValue(), context.getIntent());
    }

    // Create intra tree path - potential spanning over multiple process instances (via call
    // activities)
    //
    // Be aware that the ELEMENT INSTANCE doesn't exist yet. It is created in the applier.
    // This is the reason why we have to pass in the flow scope and record value directly to the
    // tree path builder.
    //
    // For the process instance in ACTIVATING then the flow scope is -1.
    final ElementTreePathProperties elementTreePath =
        stateBehavior.getElementTreePath(
            transitionContext.getElementInstanceKey(),
            transitionContext.getFlowScopeKey(),
            transitionContext.getRecordValue());
    // We only set the tree path properties on the ACTIVATING event
    transitionContext
        .getRecordValue()
        .setElementInstancePath(elementTreePath.elementInstancePath())
        .setProcessDefinitionPath(elementTreePath.processDefinitionPath())
        .setCallingElementPath(elementTreePath.callingElementPath());

    return transitionTo(transitionContext, ProcessInstanceIntent.ELEMENT_ACTIVATING);
  }

  /**
   * @return context with updated intent
   */
  public BpmnElementContext transitionToActivated(
      final BpmnElementContext context, final BpmnEventType eventType) {
    resetTreePathProperties(context);

    final BpmnElementContext transitionedContext =
        transitionTo(context, ProcessInstanceIntent.ELEMENT_ACTIVATED);
    metrics.elementInstanceActivated(context, eventType);
    return transitionedContext;
  }

  /**
   * @return context with updated intent
   */
  public BpmnElementContext transitionToCompleting(final BpmnElementContext context) {
    resetTreePathProperties(context);

    final var elementInstance = stateBehavior.getElementInstance(context);
    if (elementInstance.getState() == ProcessInstanceIntent.ELEMENT_COMPLETING) {
      verifyIncidentResolving(context, "#transitionToCompleting");
      // if the element is already completing, then the Complete_Element command is processed as a
      // result of resolving an incident. We don't have to transition again. Just update the
      // context
      return context.copy(
          context.getElementInstanceKey(),
          context.getRecordValue(),
          ProcessInstanceIntent.ELEMENT_COMPLETING);
    }

    return transitionTo(context, ProcessInstanceIntent.ELEMENT_COMPLETING);
  }

  /**
   * In several places we need to reset the treePath properties, this is especially useful when we
   * directly transition from ACTIVATING to COMPLETING or TERMINATING.
   *
   * @param context the current element context
   */
  private static void resetTreePathProperties(final BpmnElementContext context) {
    // reset the tree path, so we are not writing this always (optimization)
    context
        .getRecordValue()
        .resetCallingElementPath()
        .resetElementInstancePath()
        .resetProcessDefinitionPath();
  }

  /**
   * Verifies whether we have been called during incident resolving, which will call again the bpmn
   * processor#process method. This can cause that the transition activating, completing and
   * terminating are called multiple times. In other cases this should not happen, which is the
   * reason why we throw an exception.
   *
   * <p>Should be removed as soon as possible, e.g. as part of
   * https://github.com/camunda/camunda/issues/8005
   *
   * @param context the element instance context
   * @param methodName the method which is called
   * @throws IllegalStateException thrown if called not during incident resolving
   */
  private void verifyIncidentResolving(final BpmnElementContext context, final String methodName) {
    final var illegalStateException =
        new IllegalStateException(
            String.format(ALREADY_MIGRATED_ERROR_MSG, context.getBpmnElementType(), methodName));
    if (Arrays.stream(illegalStateException.getStackTrace())
        .noneMatch(ele -> ele.getMethodName().equals("attemptToContinueProcessProcessing"))) {
      throw illegalStateException;
    }
  }

  /**
   * @return context with updated intent
   */
  public <T extends ExecutableFlowNode> Either<Failure, BpmnElementContext> transitionToCompleted(
      final T element, final BpmnElementContext context) {
    resetTreePathProperties(context);

    final boolean endOfExecutionPath;
    if (context.getBpmnElementType() == BpmnElementType.PROCESS) {
      // a completing child process is not considered here.
      // the corresponding call activity is informed of the
      // child process completion explicitly by the process processor
      endOfExecutionPath = false;
    } else if (isLinkThrowEvent(element)) {
      endOfExecutionPath = false;
    } else {
      endOfExecutionPath = element.getOutgoing().isEmpty();
    }

    final Either<Failure, ?> satisfiesCompletionConditionOrFailure;
    if (endOfExecutionPath) {
      satisfiesCompletionConditionOrFailure = beforeExecutionPathCompleted(element, context);
      if (satisfiesCompletionConditionOrFailure.isLeft()) {
        return satisfiesCompletionConditionOrFailure.map(ok -> context);
      }
    } else {
      satisfiesCompletionConditionOrFailure = Either.right(null);
    }

    final var completed = transitionTo(context, ProcessInstanceIntent.ELEMENT_COMPLETED);

    metrics.elementInstanceCompleted(context, element.getEventType());

    if (endOfExecutionPath) {
      // afterExecutionPathCompleted is not allowed to fail (incident would be unresolvable)
      afterExecutionPathCompleted(
          element, completed, (Boolean) satisfiesCompletionConditionOrFailure.get());
    }
    return Either.right(completed);
  }

  /**
   * @return context with updated intent
   */
  public BpmnElementContext transitionToTerminating(final BpmnElementContext context) {
    resetTreePathProperties(context);

    if (context.getIntent() == ProcessInstanceIntent.ELEMENT_TERMINATING) {
      throw new IllegalStateException(
          String.format(
              ALREADY_MIGRATED_ERROR_MSG,
              context.getBpmnElementType(),
              "#transitionToTerminating"));
    }
    return transitionTo(context, ProcessInstanceIntent.ELEMENT_TERMINATING);
  }

  /**
   * @return context with updated intent
   */
  public BpmnElementContext transitionToTerminated(
      final BpmnElementContext context, final BpmnEventType eventType) {
    return transitionToTerminated(context, eventType, null);
  }

  public BpmnElementContext transitionToTerminated(
      final BpmnElementContext context,
      final BpmnEventType eventType,
      final AsyncRequest asyncRequest) {
    resetTreePathProperties(context);

    final var transitionedContext =
        transitionTo(context, ProcessInstanceIntent.ELEMENT_TERMINATED, asyncRequest);
    metrics.elementInstanceTerminated(context, eventType);
    return transitionedContext;
  }

  private BpmnElementContext transitionTo(
      final BpmnElementContext context, final ProcessInstanceIntent transition) {
    return transitionTo(context, transition, null);
  }

  private BpmnElementContext transitionTo(
      final BpmnElementContext context,
      final ProcessInstanceIntent transition,
      final AsyncRequest asyncRequest) {
    final var key = context.getElementInstanceKey();
    final var value = context.getRecordValue();

    if (asyncRequest != null) {
      stateWriter.appendFollowUpEvent(
          key, transition, value, m -> m.operationReference(asyncRequest.operationReference()));
      stateWriter.appendFollowUpEvent(
          asyncRequest.key(), AsyncRequestIntent.PROCESSED, asyncRequest.record());
    } else {
      stateWriter.appendFollowUpEvent(key, transition, value);
    }

    return context.copy(key, value, transition);
  }

  private void verifyTransition(
      final BpmnElementContext context, final ProcessInstanceIntent transition) {

    if (!ProcessInstanceLifecycle.canTransition(context.getIntent(), transition)) {
      throw new BpmnProcessingException(
          context,
          String.format(
              "Expected to take transition to '%s' but element instance is in state '%s'.",
              transition, context.getIntent()));
    }
  }

  private <T extends ExecutableFlowNode> boolean isLinkThrowEvent(final T element) {
    return element instanceof ExecutableIntermediateThrowEvent
        && ((ExecutableIntermediateThrowEvent) element).isLinkThrowEvent();
  }

  public void takeSequenceFlow(
      final BpmnElementContext context, final ExecutableSequenceFlow sequenceFlow) {
    verifyTransition(context, ProcessInstanceIntent.SEQUENCE_FLOW_TAKEN);
    final var target = sequenceFlow.getTarget();

    followUpInstanceRecord.wrap(context.getRecordValue());
    followUpInstanceRecord
        .setElementId(sequenceFlow.getId())
        .setBpmnElementType(sequenceFlow.getElementType())
        .setBpmnEventType(sequenceFlow.getEventType());

    // take the sequence flow
    final var sequenceFlowKey = keyGenerator.nextKey();
    stateWriter.appendFollowUpEvent(
        sequenceFlowKey, ProcessInstanceIntent.SEQUENCE_FLOW_TAKEN, followUpInstanceRecord);
    final BpmnElementContext sequenceFlowTaken =
        context.copy(
            sequenceFlowKey, followUpInstanceRecord, ProcessInstanceIntent.SEQUENCE_FLOW_TAKEN);

    activateElementInstanceInFlowScope(sequenceFlowTaken, target);
  }

  public void completeElement(final BpmnElementContext context) {
    commandWriter.appendFollowUpCommand(
        context.getElementInstanceKey(),
        ProcessInstanceIntent.COMPLETE_ELEMENT,
        context.getRecordValue());
  }

  public void terminateElement(final BpmnElementContext context) {
    commandWriter.appendFollowUpCommand(
        context.getElementInstanceKey(),
        ProcessInstanceIntent.TERMINATE_ELEMENT,
        context.getRecordValue());
  }

  public void activateChildInstance(
      final BpmnElementContext context, final ExecutableFlowElement childElement) {

    childInstanceRecord.wrap(context.getRecordValue());
    childInstanceRecord
        .setFlowScopeKey(context.getElementInstanceKey())
        .setElementId(childElement.getId())
        .setBpmnElementType(childElement.getElementType())
        .setBpmnEventType(childElement.getEventType());

    commandWriter.appendNewCommand(ProcessInstanceIntent.ACTIVATE_ELEMENT, childInstanceRecord);
  }

  public long activateChildInstanceWithKey(
      final BpmnElementContext context, final ExecutableFlowElement childElement) {

    childInstanceRecord.wrap(context.getRecordValue());
    childInstanceRecord
        .setFlowScopeKey(context.getElementInstanceKey())
        .setElementId(childElement.getId())
        .setBpmnElementType(childElement.getElementType())
        .setBpmnEventType(childElement.getEventType());

    final long childInstanceKey = keyGenerator.nextKey();
    commandWriter.appendFollowUpCommand(
        childInstanceKey, ProcessInstanceIntent.ACTIVATE_ELEMENT, childInstanceRecord);

    return childInstanceKey;
  }

  /**
   * Activate a given amount of children of a multi-instance element.
   *
   * @param context the context of the multi-instance element
   * @param amount the amount of children for which we will write an activate command
   */
  public void activateChildInstancesInBatches(final BpmnElementContext context, final int amount) {
    final var record =
        new ProcessInstanceBatchRecord()
            .setProcessInstanceKey(context.getProcessInstanceKey())
            .setProcessDefinitionKey(context.getProcessDefinitionKey())
            .setBatchElementInstanceKey(context.getElementInstanceKey())
            .setIndex(amount);

    final var key = keyGenerator.nextKey();
    commandWriter.appendFollowUpCommand(key, ProcessInstanceBatchIntent.ACTIVATE, record);
  }

  public void activateElementInstanceInFlowScope(
      final BpmnElementContext context, final ExecutableFlowElement element) {

    followUpInstanceRecord.wrap(context.getRecordValue());
    followUpInstanceRecord
        .setFlowScopeKey(context.getFlowScopeKey())
        .setElementId(element.getId())
        .setBpmnElementType(element.getElementType())
        .setBpmnEventType(element.getEventType());

    final var elementInstanceKey = keyGenerator.nextKey();
    commandWriter.appendFollowUpCommand(
        elementInstanceKey, ProcessInstanceIntent.ACTIVATE_ELEMENT, followUpInstanceRecord);
  }

  /**
   * Terminate all child instances of the given scope. Terminating is done in batches. It is
   * triggered by writing the ProcessInstanceBatch TERMINATE command.
   *
   * @param context the scope to terminate the child instances of
   * @return {@code true} if the scope has no active child instances
   */
  public boolean terminateChildInstances(final BpmnElementContext context) {
    final var elementInstance = stateBehavior.getElementInstance(context);
    final var activeChildInstances = elementInstance.getNumberOfActiveElementInstances();

    if (activeChildInstances == 0) {
      return true;
    } else {
      final var batchRecord =
          new ProcessInstanceBatchRecord()
              .setProcessInstanceKey(context.getProcessInstanceKey())
              .setProcessDefinitionKey(context.getProcessDefinitionKey())
              .setBatchElementInstanceKey(context.getElementInstanceKey());
      final var key = keyGenerator.nextKey();
      commandWriter.appendFollowUpCommand(key, ProcessInstanceBatchIntent.TERMINATE, batchRecord);
      return false;
    }
  }

  public <T extends ExecutableFlowNode> void takeOutgoingSequenceFlows(
      final T element, final BpmnElementContext context) {
    element.getOutgoing().forEach(sequenceFlow -> takeSequenceFlow(context, sequenceFlow));
  }

  /**
   * Executes runtime instructions for the given flow node element if there are any. This method may
   * cause the process instance to be interrupted, depending on the runtime instructions present.
   *
   * @param element the flow node element that is being processed
   * @param context the context of the process instance
   * @return an Either with:
   *     <ul>
   *       <li><b>Right:</b> the context if the process instance is not interrupted (e.g. there are
   *           no runtime instructions to execute), or
   *       <li><b>Left:</b> a {@link ProcessInstanceInterrupted} singleton marker if the process
   *           instance is interrupted. In this case, the caller should not append any further
   *           commands/events.
   *     </ul>
   */
  public <T extends ExecutableFlowNode>
      Either<ProcessInstanceInterrupted, BpmnElementContext> executeRuntimeInstructions(
          final T element, final BpmnElementContext context) {

    final var runtimeInstructions =
        stateBehavior.getRuntimeInstructionsForElementId(
            context.getProcessInstanceKey(), BufferUtil.bufferAsString(element.getId()));

    if (runtimeInstructions.isEmpty()) {
      return Either.right(context);
    }

    // if an element is part of a multi-instance, we skip runtime instructions as they are applied
    // to the multi-instance container element
    final var isMultiInstanceActivity =
        element.getFlowScope() != null
            && element.getFlowScope().getElementType() == BpmnElementType.MULTI_INSTANCE_BODY;
    if (isMultiInstanceActivity) {
      return Either.right(context);
    }

    // We only have one runtime instruction type for now (termination), so we can directly execute
    // its logic (interrupt process instance and request termination).
    // Due to this, we also assume that only one non-duplicate instruction can be present for each
    // element, and always take the first one.
    // In the future, we might have branches for different instruction types

    final var runtimeInstruction = runtimeInstructions.getFirst();
    if (runtimeInstruction.getType() != RuntimeInstructionType.TERMINATE_PROCESS_INSTANCE) {
      // other runtime instruction types are not supported yet
      return Either.right(context);
    }

    final var processInstance = stateBehavior.getElementInstance(context.getProcessInstanceKey());
    if (processInstance.isTerminating()) {
      // if the process instance is already terminating, we don't need to interrupt and terminate it
      // again, the instruction has already been processed.
      return Either.right(context);
    }

    runtimeInstructionRecord.reset();
    runtimeInstructionRecord.setProcessInstanceKey(context.getProcessInstanceKey());
    runtimeInstructionRecord.setProcessDefinitionKey(context.getProcessDefinitionKey());
    runtimeInstructionRecord.setTenantId(context.getTenantId());
    runtimeInstructionRecord.setElementId(runtimeInstruction.getAfterElementId());

    stateWriter.appendFollowUpEvent(
        processInstance.getKey(), RuntimeInstructionIntent.INTERRUPTED, runtimeInstructionRecord);
    commandWriter.appendFollowUpCommand(
        processInstance.getKey(),
        ProcessInstanceIntent.TERMINATE_ELEMENT,
        processInstance.getValue());

    return Either.left(ProcessInstanceInterrupted.INSTANCE);
  }

  public Either<Failure, ?> beforeExecutionPathCompleted(
      final ExecutableFlowElement element, final BpmnElementContext childContext) {
    return invokeElementContainerIfPresent(
        element,
        childContext,
        (containerProcessor, containerScope, containerContext) ->
            containerProcessor.beforeExecutionPathCompleted(
                containerScope, containerContext, childContext));
  }

  // CALL ACTIVITY SPECIFIC
  public void onCalledProcessCompleted(
      final BpmnElementContext childContext, final BpmnElementContext parentInstanceContext) {
    final var containerScope = getParentProcessScope(parentInstanceContext, childContext);
    final var containerProcessor = processorLookUp.apply(containerScope.getElementType());
    final Either<Failure, ?> satisfiesCompletionConditionOrFailure =
        containerProcessor.beforeExecutionPathCompleted(
            containerScope, parentInstanceContext, childContext);
    // todo(@korthout): deal with the left case of satisfiesCompletionConditionOrFailure
    containerProcessor.afterExecutionPathCompleted(
        containerScope,
        parentInstanceContext,
        childContext,
        (Boolean) satisfiesCompletionConditionOrFailure.get());
  }

  public void onCalledProcessTerminated(
      final BpmnElementContext childContext, final BpmnElementContext parentInstanceContext) {
    final var containerScope = getParentProcessScope(parentInstanceContext, childContext);
    final var containerProcessor = processorLookUp.apply(containerScope.getElementType());
    containerProcessor.onChildTerminated(containerScope, parentInstanceContext, childContext);
  }

  public void afterExecutionPathCompleted(
      final ExecutableFlowElement element,
      final BpmnElementContext childContext,
      final Boolean satisfiesCompletionCondition) {

    invokeElementContainerIfPresent(
        element,
        childContext,
        (containerProcessor, containerScope, containerContext) -> {
          containerProcessor.afterExecutionPathCompleted(
              containerScope, containerContext, childContext, satisfiesCompletionCondition);
          return Either.right(null);
        });
  }

  public void onElementTerminated(
      final ExecutableFlowElement element, final BpmnElementContext childContext) {

    invokeElementContainerIfPresent(
        element,
        childContext,
        (containerProcessor, containerScope, containerContext) -> {
          try {
            containerProcessor.onChildTerminated(containerScope, containerContext, childContext);
          } catch (final StackOverflowError stackOverFlow) {
            // This is a dirty quick "fix" for https://github.com/camunda/camunda/issues/8955
            // It's done so a cluster doesn't die when a user encounters this.
            final var message =
                String.format(
                    """
                    Process instance `%d` has too many nested child instances and could not be terminated. \
                    The deepest nested child instance has been banned as a result.""",
                    containerContext.getProcessInstanceKey());
            throw new ChildTerminationStackOverflowException(message);
          }
          return Either.right(null);
        });
  }

  public Either<Failure, ?> onElementActivating(
      final ExecutableFlowElement element, final BpmnElementContext childContext) {

    return invokeElementContainerIfPresent(
        element,
        childContext,
        (containerProcessor, containerScope, containerContext) ->
            containerProcessor.onChildActivating(containerScope, containerContext, childContext));
  }

  private Either<Failure, ?> invokeElementContainerIfPresent(
      final ExecutableFlowElement childElement,
      final BpmnElementContext childContext,
      final ElementContainerProcessorFunction containerFunction) {

    final ExecutableFlowElement containerScope;
    final BpmnElementContext containerContext;
    final var flowScope = childElement.getFlowScope();

    if (flowScope != null) {
      containerContext = stateBehavior.getFlowScopeContext(childContext);
      containerScope = flowScope;

    } else if (childContext.getParentElementInstanceKey() > 0) {
      // no flow scope, it is called from a parent process
      containerContext = stateBehavior.getParentElementInstanceContext(childContext);
      containerScope = getParentProcessScope(containerContext, childContext);

    } else {
      // no flow scope or no parent process
      return Either.right(null);
    }
    final var containerProcessor = processorLookUp.apply(containerScope.getElementType());

    return containerFunction.apply(containerProcessor, containerScope, containerContext);
  }

  private ExecutableCallActivity getParentProcessScope(
      final BpmnElementContext callActivityContext, final BpmnElementContext childContext) {
    final var processDefinitionKey = callActivityContext.getProcessDefinitionKey();
    final var elementId = callActivityContext.getElementId();

    return stateBehavior
        .getProcess(processDefinitionKey, callActivityContext.getTenantId())
        .map(DeployedProcess::getProcess)
        .map(
            process ->
                process.getElementById(
                    elementId, BpmnElementType.CALL_ACTIVITY, ExecutableCallActivity.class))
        .orElseThrow(
            () ->
                new BpmnProcessingException(
                    childContext, String.format(NO_PROCESS_FOUND_MESSAGE, processDefinitionKey)));
  }

  public long createChildProcessInstance(
      final DeployedProcess process, final BpmnElementContext context, final String businessId) {

    final var processInstanceKey = keyGenerator.nextKey();

    childInstanceRecord.reset();
    childInstanceRecord
        .setBpmnProcessId(process.getBpmnProcessId())
        .setVersion(process.getVersion())
        .setProcessDefinitionKey(process.getKey())
        .setProcessInstanceKey(processInstanceKey)
        .setParentProcessInstanceKey(context.getProcessInstanceKey())
        .setParentElementInstanceKey(context.getElementInstanceKey())
        .setElementId(process.getProcess().getId())
        .setBpmnElementType(process.getProcess().getElementType())
        .setTenantId(context.getTenantId())
        .setRootProcessInstanceKey(context.getRootProcessInstanceKey())
        .setBusinessId(businessId);

    commandWriter.appendFollowUpCommand(
        processInstanceKey, ProcessInstanceIntent.ACTIVATE_ELEMENT, childInstanceRecord);

    return processInstanceKey;
  }

  public <T extends ExecutableFlowElement> void terminateChildProcessInstance(
      final BpmnElementContainerProcessor<T> containerProcessor,
      final T element,
      final BpmnElementContext context) {
    stateBehavior
        .getCalledChildInstance(context)
        .ifPresentOrElse(
            child ->
                terminateElement(context.copy(child.getKey(), child.getValue(), child.getState())),
            () -> containerProcessor.onChildTerminated(element, context, null));
  }

  /** A singleton marker class to indicate that the process instance has been interrupted. */
  public static final class ProcessInstanceInterrupted {
    /**
     * A singleton instance of {@link ProcessInstanceInterrupted} to be used as a marker. It can be
     * returned as a result of a method call to indicate that process instance is interrupted.
     */
    private static final ProcessInstanceInterrupted INSTANCE = new ProcessInstanceInterrupted();

    private ProcessInstanceInterrupted() {}
  }

  private static final class ChildTerminationStackOverflowException extends RuntimeException {

    public ChildTerminationStackOverflowException(final String message) {
      super(message);
    }
  }

  @FunctionalInterface
  private interface ElementContainerProcessorFunction {
    Either<Failure, ?> apply(
        BpmnElementContainerProcessor<ExecutableFlowElement> containerProcessor,
        ExecutableFlowElement containerScope,
        BpmnElementContext containerContext);
  }
}
