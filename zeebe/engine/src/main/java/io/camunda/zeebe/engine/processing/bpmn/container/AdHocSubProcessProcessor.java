/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.bpmn.container;

import io.camunda.zeebe.el.Expression;
import io.camunda.zeebe.engine.processing.adhocsubprocess.AdHocSubProcessUtils;
import io.camunda.zeebe.engine.processing.bpmn.BpmnElementContainerProcessor;
import io.camunda.zeebe.engine.processing.bpmn.BpmnElementContext;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnAdHocSubProcessBehavior;
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
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableAdHocSubProcess;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeAdHocImplementationType;
import io.camunda.zeebe.msgpack.spec.MsgPackHelper;
import io.camunda.zeebe.msgpack.spec.MsgPackReader;
import io.camunda.zeebe.msgpack.spec.MsgPackType;
import io.camunda.zeebe.msgpack.spec.MsgPackWriter;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.ErrorType;
import io.camunda.zeebe.util.Either;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class AdHocSubProcessProcessor
    implements BpmnElementContainerProcessor<ExecutableAdHocSubProcess> {

  private static final DirectBuffer AD_HOC_SUB_PROCESS_ELEMENTS_VARIABLE_NAME =
      BufferUtil.wrapString("adHocSubProcessElements");

  private final BpmnStateBehavior stateBehavior;
  private final BpmnStateTransitionBehavior stateTransitionBehavior;
  private final BpmnVariableMappingBehavior variableMappingBehavior;
  private final BpmnEventSubscriptionBehavior eventSubscriptionBehavior;
  private final BpmnJobBehavior jobBehavior;
  private final BpmnIncidentBehavior incidentBehavior;
  private final ExpressionProcessor expressionProcessor;
  private final BpmnCompensationSubscriptionBehaviour compensationSubscriptionBehaviour;
  private final BpmnAdHocSubProcessBehavior adHocSubProcessBehavior;

  private final EnumMap<ZeebeAdHocImplementationType, AdHocSubProcessBehavior>
      adHocSubProcessBehaviors =
          new EnumMap<>(
              Map.ofEntries(
                  Map.entry(ZeebeAdHocImplementationType.BPMN, new BpmnBehavior()),
                  Map.entry(ZeebeAdHocImplementationType.JOB_WORKER, new JobWorkerBehavior())));
  private final OutputCollectionUpdater outputCollectionUpdater = new OutputCollectionUpdater();

  public AdHocSubProcessProcessor(
      final BpmnBehaviors bpmnBehaviors,
      final BpmnStateTransitionBehavior stateTransitionBehavior) {
    stateBehavior = bpmnBehaviors.stateBehavior();
    variableMappingBehavior = bpmnBehaviors.variableMappingBehavior();
    eventSubscriptionBehavior = bpmnBehaviors.eventSubscriptionBehavior();
    jobBehavior = bpmnBehaviors.jobBehavior();
    incidentBehavior = bpmnBehaviors.incidentBehavior();
    expressionProcessor = bpmnBehaviors.expressionBehavior();
    compensationSubscriptionBehaviour = bpmnBehaviors.compensationSubscriptionBehaviour();
    this.stateTransitionBehavior = stateTransitionBehavior;
    adHocSubProcessBehavior = bpmnBehaviors.adHocSubProcessBehavior();
  }

  @Override
  public Class<ExecutableAdHocSubProcess> getType() {
    return ExecutableAdHocSubProcess.class;
  }

  @Override
  public Either<Failure, ?> onActivate(
      final ExecutableAdHocSubProcess element, final BpmnElementContext context) {
    stateBehavior.setLocalVariable(
        context, AD_HOC_SUB_PROCESS_ELEMENTS_VARIABLE_NAME, element.getAdHocActivitiesMetadata());

    element
        .getOutputCollection()
        .ifPresent(
            outputCollectionVariableName ->
                stateBehavior.setLocalVariable(
                    context,
                    outputCollectionVariableName,
                    BufferUtil.wrapArray(MsgPackHelper.EMPTY_ARRAY)));
    return variableMappingBehavior.applyInputMappings(context, element);
  }

  @Override
  public Either<Failure, ?> finalizeActivation(
      final ExecutableAdHocSubProcess element, final BpmnElementContext context) {
    return behaviorFor(element).finalizeActivation(element, context);
  }

  @Override
  public Either<Failure, ?> onComplete(
      final ExecutableAdHocSubProcess element, final BpmnElementContext context) {
    element
        .getOutputCollection()
        .ifPresent(variableName -> stateBehavior.propagateVariable(context, variableName));

    return variableMappingBehavior
        .applyOutputMappings(context, element)
        .thenDo(ok -> eventSubscriptionBehavior.unsubscribeFromEvents(context));
  }

  @Override
  public Either<Failure, ?> finalizeCompletion(
      final ExecutableAdHocSubProcess element, final BpmnElementContext context) {
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
      final ExecutableAdHocSubProcess element, final BpmnElementContext terminating) {

    if (element.hasExecutionListeners()
        || element.getImplementationType() == ZeebeAdHocImplementationType.JOB_WORKER) {
      jobBehavior.cancelJob(terminating);
    }
    eventSubscriptionBehavior.unsubscribeFromEvents(terminating);
    incidentBehavior.resolveIncidents(terminating);
    compensationSubscriptionBehaviour.deleteSubscriptionsOfSubprocess(terminating);

    final boolean noActiveChildInstances =
        stateTransitionBehavior.terminateChildInstances(terminating);
    if (noActiveChildInstances) {
      terminate(element, terminating);
    }
    return TransitionOutcome.CONTINUE;
  }

  @Override
  public void finalizeTermination(
      final ExecutableAdHocSubProcess element, final BpmnElementContext terminated) {
    stateTransitionBehavior.executeRuntimeInstructions(element, terminated);
  }

  private AdHocSubProcessBehavior behaviorFor(final ExecutableAdHocSubProcess adHocSubProcess) {
    return adHocSubProcessBehaviors.get(adHocSubProcess.getImplementationType());
  }

  private void terminate(
      final ExecutableAdHocSubProcess element, final BpmnElementContext context) {

    final var flowScopeInstance = stateBehavior.getFlowScopeInstance(context);

    eventSubscriptionBehavior
        .findEventTrigger(context)
        .filter(eventTrigger -> flowScopeInstance.isActive() && !flowScopeInstance.isInterrupted())
        .ifPresentOrElse(
            eventTrigger -> {
              final var terminated =
                  stateTransitionBehavior.transitionToTerminated(context, element.getEventType());

              eventSubscriptionBehavior.activateTriggeredEvent(
                  terminated.getElementInstanceKey(),
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

  private Either<Failure, List<String>> readActivateElementsCollection(
      final ExecutableAdHocSubProcess adHocSubProcess, final BpmnElementContext context) {
    final Expression activeElementsCollection = adHocSubProcess.getActiveElementsCollection();
    if (activeElementsCollection == null) {
      // The expression is not defined. No elements to activate.
      return Either.right(Collections.emptyList());

    } else {
      return expressionProcessor
          .evaluateArrayOfStringsExpression(
              activeElementsCollection, context.getElementInstanceKey())
          .mapLeft(
              failure ->
                  new Failure(
                      "Failed to activate ad-hoc elements. " + failure.getMessage(),
                      ErrorType.EXTRACT_VALUE_ERROR))
          .flatMap(
              elements ->
                  AdHocSubProcessUtils.validateActivateElementsExistInAdHocSubProcess(
                          context.getElementInstanceKey(), adHocSubProcess, elements)
                      .mapLeft(
                          rejection ->
                              new Failure(
                                  rejection.reason(),
                                  ErrorType.EXTRACT_VALUE_ERROR,
                                  context.getElementInstanceKey())));
    }
  }

  private void activateElements(
      final ExecutableAdHocSubProcess element,
      final BpmnElementContext context,
      final List<String> elementsToActivate) {

    elementsToActivate.forEach(
        elementToActivate ->
            adHocSubProcessBehavior.activateElement(context, element, elementToActivate));
  }

  @Override
  public Either<Failure, ?> beforeExecutionPathCompleted(
      final ExecutableAdHocSubProcess adHocSubProcess,
      final BpmnElementContext adHocSubProcessContext,
      final BpmnElementContext childContext) {
    return adHocSubProcess
        .getOutputCollection()
        .map(
            outputCollectionVariableName ->
                updateOutputCollection(
                    adHocSubProcess,
                    outputCollectionVariableName,
                    adHocSubProcessContext,
                    childContext))
        .orElse(Either.right(null))
        .flatMap(
            ok ->
                behaviorFor(adHocSubProcess)
                    .beforeExecutionPathCompleted(
                        adHocSubProcess, adHocSubProcessContext, childContext));
  }

  @Override
  public void afterExecutionPathCompleted(
      final ExecutableAdHocSubProcess adHocSubProcess,
      final BpmnElementContext adHocSubProcessContext,
      final BpmnElementContext childContext,
      final Boolean satisfiesCompletionCondition) {
    behaviorFor(adHocSubProcess)
        .afterExecutionPathCompleted(
            adHocSubProcess, adHocSubProcessContext, childContext, satisfiesCompletionCondition);
  }

  @Override
  public void onChildTerminated(
      final ExecutableAdHocSubProcess adHocSubProcess,
      final BpmnElementContext adHocSubProcessContext,
      final BpmnElementContext childContext) {
    if (adHocSubProcessContext.getIntent() == ProcessInstanceIntent.ELEMENT_TERMINATING) {
      // child termination is initiated in onTerminate
      // terminate ad-hoc sub-process as soon as all child instances have been terminated
      if (stateBehavior.canBeTerminated(childContext)) {
        terminate(adHocSubProcess, adHocSubProcessContext);
      }
    } else if (stateBehavior.canBeCompleted(childContext)) {
      // complete the ad-hoc sub-process because its completion condition was met previously and
      // all remaining child instances were terminated.
      stateTransitionBehavior.completeElement(adHocSubProcessContext);
    }
  }

  private Either<Failure, ?> updateOutputCollection(
      final ExecutableAdHocSubProcess adHocSubProcess,
      final DirectBuffer outputCollectionVariableName,
      final BpmnElementContext adHocSubProcessContext,
      final BpmnElementContext childContext) {

    final Expression outputElementExpression = adHocSubProcess.getOutputElement().orElseThrow();
    return expressionProcessor
        .evaluateAnyExpression(outputElementExpression, childContext.getElementInstanceKey())
        .flatMap(
            outputElementValue -> {
              final DirectBuffer outputCollectionValue =
                  stateBehavior.getLocalVariable(
                      adHocSubProcessContext, outputCollectionVariableName);

              return outputCollectionUpdater.appendToOutputCollection(
                  outputCollectionValue, outputElementValue);
            })
        .thenDo(
            updatedCollection ->
                stateBehavior.setLocalVariable(
                    adHocSubProcessContext, outputCollectionVariableName, updatedCollection));
  }

  private static final class OutputCollectionUpdater {

    private final MsgPackReader outputCollectionReader = new MsgPackReader();
    private final MsgPackWriter outputCollectionWriter = new MsgPackWriter();
    private final ExpandableArrayBuffer outputCollectionBuffer = new ExpandableArrayBuffer();
    private final DirectBuffer updatedOutputCollectionBuffer = new UnsafeBuffer(0, 0);

    public Either<Failure, DirectBuffer> appendToOutputCollection(
        final DirectBuffer outputCollection, final DirectBuffer newValue) {

      // read output collection
      outputCollectionReader.wrap(outputCollection, 0, outputCollection.capacity());
      final var token = outputCollectionReader.readToken();
      if (token.getType() != MsgPackType.ARRAY) {
        return Either.left(
            new Failure(
                "The output collection has the wrong type. Expected %s but was %s."
                    .formatted(MsgPackType.ARRAY, token.getType())));
      }
      final int currentSize = token.getSize();
      final int valuesOffset = outputCollectionReader.getOffset();

      // write updated output collection
      outputCollectionWriter.wrap(outputCollectionBuffer, 0);
      outputCollectionWriter.writeArrayHeader(currentSize + 1);
      // add current values
      outputCollectionWriter.writeRaw(
          outputCollection, valuesOffset, outputCollection.capacity() - valuesOffset);
      // add new value
      outputCollectionWriter.writeRaw(newValue);

      final var length = outputCollectionWriter.getOffset();
      updatedOutputCollectionBuffer.wrap(outputCollectionBuffer, 0, length);

      return Either.right(updatedOutputCollectionBuffer);
    }
  }

  private interface AdHocSubProcessBehavior {

    Either<Failure, ?> finalizeActivation(
        final ExecutableAdHocSubProcess element, final BpmnElementContext context);

    Either<Failure, ?> beforeExecutionPathCompleted(
        final ExecutableAdHocSubProcess adHocSubProcess,
        final BpmnElementContext adHocSubProcessContext,
        final BpmnElementContext childContext);

    void afterExecutionPathCompleted(
        final ExecutableAdHocSubProcess adHocSubProcess,
        final BpmnElementContext adHocSubProcessContext,
        final BpmnElementContext childContext,
        final Boolean satisfiesCompletionCondition);
  }

  private final class BpmnBehavior implements AdHocSubProcessBehavior {

    @Override
    public Either<Failure, ?> finalizeActivation(
        final ExecutableAdHocSubProcess element, final BpmnElementContext context) {
      return readActivateElementsCollection(element, context)
          .flatMap(
              elementsToActivate ->
                  eventSubscriptionBehavior
                      .subscribeToEvents(element, context)
                      .map(ok -> elementsToActivate))
          .thenDo(
              elementsToActivate -> {
                final var activated =
                    stateTransitionBehavior.transitionToActivated(context, element.getEventType());

                activateElements(element, activated, elementsToActivate);
              });
    }

    @Override
    public Either<Failure, ?> beforeExecutionPathCompleted(
        final ExecutableAdHocSubProcess adHocSubProcess,
        final BpmnElementContext adHocSubProcessContext,
        final BpmnElementContext childContext) {
      final Expression completionConditionExpression = adHocSubProcess.getCompletionCondition();
      if (completionConditionExpression == null) {
        return Either.right(null);
      }

      return expressionProcessor
          .evaluateBooleanExpression(
              completionConditionExpression, adHocSubProcessContext.getElementInstanceKey())
          .mapLeft(
              failure ->
                  new Failure(
                      "Failed to evaluate completion condition. " + failure.getMessage(),
                      ErrorType.EXTRACT_VALUE_ERROR));
    }

    @Override
    public void afterExecutionPathCompleted(
        final ExecutableAdHocSubProcess adHocSubProcess,
        final BpmnElementContext adHocSubProcessContext,
        final BpmnElementContext childContext,
        final Boolean satisfiesCompletionCondition) {
      if (satisfiesCompletionCondition == null) {
        // completion condition is not set - complete the ad-hoc sub-process if possible (no other
        // activity is active), otherwise skip completion as the same block will be evaluated when
        // the next activity is completed
        if (stateBehavior.canBeCompleted(childContext)) {
          stateTransitionBehavior.completeElement(adHocSubProcessContext);
        }

        return;
      }

      if (satisfiesCompletionCondition) {
        adHocSubProcessBehavior.completionConditionFulfilled(
            adHocSubProcessContext, adHocSubProcess.isCancelRemainingInstances());
      }
    }
  }

  private final class JobWorkerBehavior implements AdHocSubProcessBehavior {

    @Override
    public Either<Failure, ?> finalizeActivation(
        final ExecutableAdHocSubProcess element, final BpmnElementContext context) {
      return jobBehavior
          .evaluateJobExpressions(element.getJobWorkerProperties(), context)
          .flatMap(j -> eventSubscriptionBehavior.subscribeToEvents(element, context).map(ok -> j))
          .thenDo(
              jobProperties -> {
                jobBehavior.createNewAdHocSubProcessJob(context, element, jobProperties);
                stateTransitionBehavior.transitionToActivated(context, element.getEventType());
              });
    }

    @Override
    public Either<Failure, ?> beforeExecutionPathCompleted(
        final ExecutableAdHocSubProcess adHocSubProcess,
        final BpmnElementContext adHocSubProcessContext,
        final BpmnElementContext childContext) {
      return SUCCESS;
    }

    @Override
    public void afterExecutionPathCompleted(
        final ExecutableAdHocSubProcess adHocSubProcess,
        final BpmnElementContext adHocSubProcessContext,
        final BpmnElementContext childContext,
        final Boolean satisfiesCompletionCondition) {
      // There should only be 1 active Job for the ad-hoc sub-process. We should cancel any active
      // job before creating the new one.
      jobBehavior.cancelJob(adHocSubProcessContext);

      final var adHocSubProcessInstance =
          stateBehavior.getElementInstance(adHocSubProcessContext.getElementInstanceKey());
      if (adHocSubProcessInstance.isCompletionConditionFulfilled()) {
        adHocSubProcessBehavior.completionConditionFulfilled(
            adHocSubProcessContext,
            adHocSubProcess.isCancelRemainingInstances(),
            adHocSubProcessInstance);
      } else {
        // If the completion is fulfilled we should not create a new job. The ad-hoc sub-process
        // will be completed soon.
        jobBehavior
            .evaluateJobExpressions(
                adHocSubProcess.getJobWorkerProperties(), adHocSubProcessContext)
            .thenDo(
                jobProperties ->
                    jobBehavior.createNewAdHocSubProcessJob(
                        adHocSubProcessContext, adHocSubProcess, jobProperties));
      }
    }
  }
}
