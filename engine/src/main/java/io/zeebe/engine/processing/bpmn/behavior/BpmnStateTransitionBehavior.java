/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.engine.processing.bpmn.behavior;

import io.zeebe.engine.metrics.ProcessEngineMetrics;
import io.zeebe.engine.processing.bpmn.BpmnElementContainerProcessor;
import io.zeebe.engine.processing.bpmn.BpmnElementContext;
import io.zeebe.engine.processing.bpmn.BpmnProcessingException;
import io.zeebe.engine.processing.bpmn.ProcessInstanceLifecycle;
import io.zeebe.engine.processing.common.Failure;
import io.zeebe.engine.processing.deployment.model.element.ExecutableCallActivity;
import io.zeebe.engine.processing.deployment.model.element.ExecutableFlowElement;
import io.zeebe.engine.processing.deployment.model.element.ExecutableFlowNode;
import io.zeebe.engine.processing.deployment.model.element.ExecutableSequenceFlow;
import io.zeebe.engine.processing.streamprocessor.MigratedStreamProcessors;
import io.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.zeebe.engine.processing.streamprocessor.writers.TypedCommandWriter;
import io.zeebe.engine.processing.streamprocessor.writers.TypedStreamWriter;
import io.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.zeebe.engine.state.KeyGenerator;
import io.zeebe.engine.state.deployment.DeployedProcess;
import io.zeebe.engine.state.mutable.MutableElementInstanceState;
import io.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.zeebe.protocol.record.value.BpmnElementType;
import io.zeebe.util.Either;
import java.util.Arrays;
import java.util.function.Function;

public final class BpmnStateTransitionBehavior {

  private static final String ALREADY_MIGRATED_ERROR_MSG =
      "The Processor for the element type %s is already migrated no need to call %s again this is already done in the BpmnStreamProcessor for you. Happy to help :) ";
  private static final String NO_PROCESS_FOUND_MESSAGE =
      "Expected to find a deployed process for process id '%s', but none found.";

  private final ProcessInstanceRecord childInstanceRecord = new ProcessInstanceRecord();
  private final ProcessInstanceRecord followUpInstanceRecord = new ProcessInstanceRecord();

  private final TypedStreamWriter streamWriter;
  private final KeyGenerator keyGenerator;
  private final BpmnStateBehavior stateBehavior;
  private final Function<BpmnElementType, BpmnElementContainerProcessor<ExecutableFlowElement>>
      processorLookUp;

  private final ProcessEngineMetrics metrics;
  private final StateWriter stateWriter;
  private final TypedCommandWriter commandWriter;
  private final MutableElementInstanceState elementInstanceState;

  public BpmnStateTransitionBehavior(
      final TypedStreamWriter streamWriter,
      final KeyGenerator keyGenerator,
      final BpmnStateBehavior stateBehavior,
      final ProcessEngineMetrics metrics,
      final Function<BpmnElementType, BpmnElementContainerProcessor<ExecutableFlowElement>>
          processorLookUp,
      final Writers writers,
      final MutableElementInstanceState elementInstanceState) {
    // todo (@korthout): replace streamWriter by writers
    this.streamWriter = streamWriter;
    this.keyGenerator = keyGenerator;
    this.stateBehavior = stateBehavior;
    this.metrics = metrics;
    this.processorLookUp = processorLookUp;
    stateWriter = writers.state();
    commandWriter = writers.command();
    this.elementInstanceState = elementInstanceState;
  }

  /** @return context with updated intent */
  public BpmnElementContext transitionToActivating(final BpmnElementContext context) {
    if (MigratedStreamProcessors.isMigrated(context.getBpmnElementType())) {
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

      // When the element instance key is not set (-1), then we process the ACTIVATE_ELEMENT
      // command. We need to generate a new key in order to transition to ELEMENT_ACTIVATING, such
      // that we can assign the new create element instance a correct key. It is expected that on
      // the command the key is not set. But some elements (such as multi instance), need to
      // generate the key before they write ACTIVATE command, to prepare the state (e.g. set
      // variables) for the upcoming element instance.
      if (context.getElementInstanceKey() == -1) {
        final var newElementInstanceKey = keyGenerator.nextKey();
        final var newContext =
            context.copy(
                newElementInstanceKey,
                context.getRecordValue(),
                ProcessInstanceIntent.ELEMENT_ACTIVATING);
        return transitionTo(newContext, ProcessInstanceIntent.ELEMENT_ACTIVATING);
      }
    }
    return transitionTo(context, ProcessInstanceIntent.ELEMENT_ACTIVATING);
  }

  /** @return context with updated intent */
  public BpmnElementContext transitionToActivated(final BpmnElementContext context) {
    final BpmnElementContext transitionedContext =
        transitionTo(context, ProcessInstanceIntent.ELEMENT_ACTIVATED);
    metrics.elementInstanceActivated(context);
    return transitionedContext;
  }

  /** @return context with updated intent */
  public BpmnElementContext transitionToCompleting(final BpmnElementContext context) {
    if (MigratedStreamProcessors.isMigrated(context.getBpmnElementType())) {
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
    }
    return transitionTo(context, ProcessInstanceIntent.ELEMENT_COMPLETING);
  }

  /**
   * Verifies wether we have been called during incident resolving, which will call again the bpmn
   * processor#process method. This can cause that the transition activating, completing and
   * terminating are called multiple times. In other cases this should not happen, which is the
   * reason why we throw an exception.
   *
   * <p>Should be removed as soon as possible, e.g. as part of
   * https://github.com/camunda-cloud/zeebe/issues/6202
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

  public <T extends ExecutableFlowNode>
      BpmnElementContext transitionToCompletedWithParentNotification(
          final T element, final BpmnElementContext context) {
    final boolean endOfExecutionPath = element.getOutgoing().isEmpty();

    if (endOfExecutionPath) {
      beforeExecutionPathCompleted(element, context);
    }
    final var completed = transitionToCompleted(context);
    if (endOfExecutionPath) {
      if (MigratedStreamProcessors.isMigrated(context.getBpmnElementType())) {
        afterExecutionPathCompleted(element, completed);
      }
    }
    return completed;
  }

  /** @return context with updated intent */
  public BpmnElementContext transitionToCompleted(final BpmnElementContext context) {
    final var transitionedContext = transitionTo(context, ProcessInstanceIntent.ELEMENT_COMPLETED);
    metrics.elementInstanceCompleted(context);
    return transitionedContext;
  }

  /** @return context with updated intent */
  public BpmnElementContext transitionToTerminating(final BpmnElementContext context) {
    final var isMigrated = MigratedStreamProcessors.isMigrated(context.getBpmnElementType());
    if (isMigrated && context.getIntent() == ProcessInstanceIntent.ELEMENT_TERMINATING) {
      throw new IllegalStateException(
          String.format(
              ALREADY_MIGRATED_ERROR_MSG,
              context.getBpmnElementType(),
              "#transitionToTerminating"));
    }
    return transitionTo(context, ProcessInstanceIntent.ELEMENT_TERMINATING);
  }

  /** @return context with updated intent */
  public BpmnElementContext transitionToTerminated(final BpmnElementContext context) {
    final var transitionedContext = transitionTo(context, ProcessInstanceIntent.ELEMENT_TERMINATED);
    metrics.elementInstanceTerminated(context);
    return transitionedContext;
  }

  private BpmnElementContext transitionTo(
      final BpmnElementContext context, final ProcessInstanceIntent transition) {
    final var key = context.getElementInstanceKey();
    final var value = context.getRecordValue();
    if (!MigratedStreamProcessors.isMigrated(context.getBpmnElementType())) {
      verifyTransition(context, transition);
      streamWriter.appendFollowUpEvent(key, transition, value);
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

  public void takeSequenceFlow(
      final BpmnElementContext context, final ExecutableSequenceFlow sequenceFlow) {
    verifyTransition(context, ProcessInstanceIntent.SEQUENCE_FLOW_TAKEN);
    final var target = sequenceFlow.getTarget();

    followUpInstanceRecord.wrap(context.getRecordValue());
    followUpInstanceRecord
        .setElementId(sequenceFlow.getId())
        .setBpmnElementType(sequenceFlow.getElementType());

    // take the sequence flow
    final var sequenceFlowKey = keyGenerator.nextKey();
    stateWriter.appendFollowUpEvent(
        sequenceFlowKey, ProcessInstanceIntent.SEQUENCE_FLOW_TAKEN, followUpInstanceRecord);
    final BpmnElementContext sequenceFlowTaken =
        context.copy(
            sequenceFlowKey, followUpInstanceRecord, ProcessInstanceIntent.SEQUENCE_FLOW_TAKEN);

    if (canActivateTargetElement(context, target)) {
      activateElementInstanceInFlowScope(sequenceFlowTaken, target);
    }
  }

  private boolean canActivateTargetElement(
      final BpmnElementContext context, final ExecutableFlowNode targetElement) {

    final int numberOfIncomingSequenceFlows = targetElement.getIncoming().size();

    if (targetElement.getElementType() == BpmnElementType.PARALLEL_GATEWAY) {
      // activate the parallel gateway only if all incoming sequence flows are taken at least once
      final int numberOfTakenSequenceFlows =
          elementInstanceState.getNumberOfTakenSequenceFlows(
              context.getFlowScopeKey(), targetElement.getId());
      return numberOfTakenSequenceFlows == numberOfIncomingSequenceFlows;

    } else {
      return true;
    }
  }

  public void completeElement(final BpmnElementContext context) {

    if (MigratedStreamProcessors.isMigrated(context.getBpmnElementType())) {
      commandWriter.appendFollowUpCommand(
          context.getElementInstanceKey(),
          ProcessInstanceIntent.COMPLETE_ELEMENT,
          context.getRecordValue());

    } else {
      transitionToCompleting(context);
    }
  }

  public void terminateElement(final BpmnElementContext context) {

    if (MigratedStreamProcessors.isMigrated(context.getBpmnElementType())) {
      commandWriter.appendFollowUpCommand(
          context.getElementInstanceKey(),
          ProcessInstanceIntent.TERMINATE_ELEMENT,
          context.getRecordValue());

    } else {
      transitionToTerminating(context);
    }
  }

  public void activateChildInstance(
      final BpmnElementContext context, final ExecutableFlowElement childElement) {

    childInstanceRecord.wrap(context.getRecordValue());
    childInstanceRecord
        .setFlowScopeKey(context.getElementInstanceKey())
        .setElementId(childElement.getId())
        .setBpmnElementType(childElement.getElementType());

    if (MigratedStreamProcessors.isMigrated(childElement.getElementType())) {
      commandWriter.appendNewCommand(ProcessInstanceIntent.ACTIVATE_ELEMENT, childInstanceRecord);
    } else {
      stateWriter.appendFollowUpEvent(
          keyGenerator.nextKey(), ProcessInstanceIntent.ELEMENT_ACTIVATING, childInstanceRecord);
    }
  }

  public long activateChildInstanceWithKey(
      final BpmnElementContext context, final ExecutableFlowElement childElement) {

    childInstanceRecord.wrap(context.getRecordValue());
    childInstanceRecord
        .setFlowScopeKey(context.getElementInstanceKey())
        .setElementId(childElement.getId())
        .setBpmnElementType(childElement.getElementType());

    final long childInstanceKey = keyGenerator.nextKey();
    if (MigratedStreamProcessors.isMigrated(childElement.getElementType())) {
      commandWriter.appendFollowUpCommand(
          childInstanceKey, ProcessInstanceIntent.ACTIVATE_ELEMENT, childInstanceRecord);
    } else {
      stateWriter.appendFollowUpEvent(
          childInstanceKey, ProcessInstanceIntent.ELEMENT_ACTIVATING, childInstanceRecord);
    }

    return childInstanceKey;
  }

  public void activateElementInstanceInFlowScope(
      final BpmnElementContext context, final ExecutableFlowElement element) {

    followUpInstanceRecord.wrap(context.getRecordValue());
    followUpInstanceRecord
        .setFlowScopeKey(context.getFlowScopeKey())
        .setElementId(element.getId())
        .setBpmnElementType(element.getElementType());

    final var elementInstanceKey = keyGenerator.nextKey();

    if (MigratedStreamProcessors.isMigrated(element.getElementType())) {
      commandWriter.appendFollowUpCommand(
          elementInstanceKey, ProcessInstanceIntent.ACTIVATE_ELEMENT, followUpInstanceRecord);
    } else {
      // For migrated processors the active sequence flow count is decremented in the
      // *ActivatingApplier. For non migrated we do it here, otherwise we can't complete the process
      // instance in the end. Counting the active sequence flows is necessary to not complete the
      // process instance to early.
      final var flowScopeInstance = elementInstanceState.getInstance(context.getFlowScopeKey());
      flowScopeInstance.decrementActiveSequenceFlows();
      elementInstanceState.updateInstance(flowScopeInstance);

      streamWriter.appendFollowUpEvent(
          elementInstanceKey, ProcessInstanceIntent.ELEMENT_ACTIVATING, followUpInstanceRecord);

      stateBehavior.createElementInstanceInFlowScope(
          context, elementInstanceKey, followUpInstanceRecord);
    }
  }

  /**
   * Terminate all child instances of the given scope.
   *
   * @param context the scope to terminate the child instances of
   * @return {@code true} if the scope has no active child instances
   */
  public boolean terminateChildInstances(final BpmnElementContext context) {

    final var childInstances = stateBehavior.getChildInstances(context);

    for (final BpmnElementContext childInstanceContext : childInstances) {

      if (ProcessInstanceLifecycle.canTerminate(childInstanceContext.getIntent())) {
        if (!MigratedStreamProcessors.isMigrated(childInstanceContext.getBpmnElementType())) {
          transitionToTerminating(childInstanceContext);
        } else {
          commandWriter.appendFollowUpCommand(
              childInstanceContext.getElementInstanceKey(),
              ProcessInstanceIntent.TERMINATE_ELEMENT,
              childInstanceContext.getRecordValue());
        }

      } else if (childInstanceContext.getIntent() == ProcessInstanceIntent.ELEMENT_COMPLETED
          && !MigratedStreamProcessors.isMigrated(childInstanceContext.getBpmnElementType())) {
        // clean up the state because the completed event will not be processed
        stateBehavior.removeElementInstance(childInstanceContext);
      }
    }

    final var elementInstance = stateBehavior.getElementInstance(context);
    final var activeChildInstances = elementInstance.getNumberOfActiveElementInstances();

    return activeChildInstances == 0;
  }

  public <T extends ExecutableFlowNode> void takeOutgoingSequenceFlows(
      final T element, final BpmnElementContext context) {

    element.getOutgoing().forEach(sequenceFlow -> takeSequenceFlow(context, sequenceFlow));
  }

  public void beforeExecutionPathCompleted(
      final ExecutableFlowElement element, final BpmnElementContext childContext) {

    invokeElementContainerIfPresent(
        element,
        childContext,
        (containerProcessor, containerScope, containerContext) -> {
          containerProcessor.beforeExecutionPathCompleted(
              containerScope, containerContext, childContext);
          return Either.right(null);
        });
  }

  // CALL ACTIVITY SPECIFIC
  public void onCalledProcessCompleted(
      final BpmnElementContext childContext, final BpmnElementContext parentInstanceContext) {
    final var containerScope = getParentProcessScope(parentInstanceContext, childContext);
    final var containerProcessor = processorLookUp.apply(containerScope.getElementType());
    containerProcessor.beforeExecutionPathCompleted(
        containerScope, parentInstanceContext, childContext);
    containerProcessor.afterExecutionPathCompleted(
        containerScope, parentInstanceContext, childContext);
  }

  public void onCalledProcessTerminated(
      final BpmnElementContext childContext, final BpmnElementContext parentInstanceContext) {
    final var containerScope = getParentProcessScope(parentInstanceContext, childContext);
    final var containerProcessor = processorLookUp.apply(containerScope.getElementType());
    containerProcessor.onChildTerminated(containerScope, parentInstanceContext, childContext);
  }

  public void afterExecutionPathCompleted(
      final ExecutableFlowElement element, final BpmnElementContext childContext) {

    invokeElementContainerIfPresent(
        element,
        childContext,
        (containerProcessor, containerScope, containerContext) -> {
          containerProcessor.afterExecutionPathCompleted(
              containerScope, containerContext, childContext);
          return Either.right(null);
        });
  }

  public void onElementTerminated(
      final ExecutableFlowElement element, final BpmnElementContext childContext) {

    invokeElementContainerIfPresent(
        element,
        childContext,
        (containerProcessor, containerScope, containerContext) -> {
          containerProcessor.onChildTerminated(containerScope, containerContext, childContext);
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
        .getProcess(processDefinitionKey)
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
      final DeployedProcess process, final BpmnElementContext context) {

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
        .setBpmnElementType(process.getProcess().getElementType());

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
            child -> {
              if (!MigratedStreamProcessors.isMigrated(child.getValue().getBpmnElementType())) {
                // can't use transitionToTerminating because it would register the state transition
                streamWriter.appendFollowUpEvent(
                    child.getKey(), ProcessInstanceIntent.ELEMENT_TERMINATING, child.getValue());
              } else {
                terminateElement(context.copy(child.getKey(), child.getValue(), child.getState()));
              }
            },
            () -> containerProcessor.onChildTerminated(element, context, null));
  }

  @FunctionalInterface
  private interface ElementContainerProcessorFunction {
    Either<Failure, ?> apply(
        BpmnElementContainerProcessor<ExecutableFlowElement> containerProcessor,
        ExecutableFlowElement containerScope,
        BpmnElementContext containerContext);
  }
}
