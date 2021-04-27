/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.engine.processing.bpmn;

import io.zeebe.engine.Loggers;
import io.zeebe.engine.processing.bpmn.behavior.BpmnBehaviorsImpl;
import io.zeebe.engine.processing.bpmn.behavior.BpmnIncidentBehavior;
import io.zeebe.engine.processing.bpmn.behavior.BpmnStateTransitionBehavior;
import io.zeebe.engine.processing.bpmn.behavior.TypedResponseWriterProxy;
import io.zeebe.engine.processing.bpmn.behavior.TypedStreamWriterProxy;
import io.zeebe.engine.processing.common.CatchEventBehavior;
import io.zeebe.engine.processing.common.EventTriggerBehavior;
import io.zeebe.engine.processing.common.ExpressionProcessor;
import io.zeebe.engine.processing.deployment.model.element.ExecutableFlowElement;
import io.zeebe.engine.processing.streamprocessor.MigratedStreamProcessors;
import io.zeebe.engine.processing.streamprocessor.ReadonlyProcessingContext;
import io.zeebe.engine.processing.streamprocessor.TypedRecord;
import io.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.zeebe.engine.processing.streamprocessor.sideeffect.SideEffectProducer;
import io.zeebe.engine.processing.streamprocessor.sideeffect.SideEffectQueue;
import io.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.zeebe.engine.processing.streamprocessor.writers.TypedStreamWriter;
import io.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.zeebe.engine.processing.variable.VariableBehavior;
import io.zeebe.engine.state.immutable.ProcessState;
import io.zeebe.engine.state.mutable.MutableElementInstanceState;
import io.zeebe.engine.state.mutable.MutableZeebeState;
import io.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.zeebe.protocol.record.RejectionType;
import io.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.zeebe.protocol.record.value.BpmnElementType;
import java.util.function.Consumer;
import org.slf4j.Logger;

public final class BpmnStreamProcessor implements TypedRecordProcessor<ProcessInstanceRecord> {

  private static final Logger LOGGER = Loggers.PROCESS_PROCESSOR_LOGGER;

  private final TypedStreamWriterProxy streamWriterProxy = new TypedStreamWriterProxy();
  private final TypedResponseWriterProxy responseWriterProxy = new TypedResponseWriterProxy();
  private final SideEffectQueue sideEffectQueue = new SideEffectQueue();
  private final BpmnElementContextImpl context = new BpmnElementContextImpl();

  private final ProcessState processState;
  private final BpmnElementProcessors processors;
  private final ProcessInstanceStateTransitionGuard stateTransitionGuard;
  private final BpmnStateTransitionBehavior stateTransitionBehavior;
  private final MutableElementInstanceState elementInstanceState;
  private final TypedRejectionWriter rejectionWriter;

  private boolean reprocessingMode = true;
  private final BpmnIncidentBehavior incidentBehavior;

  public BpmnStreamProcessor(
      final ExpressionProcessor expressionProcessor,
      final CatchEventBehavior catchEventBehavior,
      final VariableBehavior variableBehavior,
      final EventTriggerBehavior eventTriggerBehavior,
      final MutableZeebeState zeebeState,
      final Writers writers) {
    processState = zeebeState.getProcessState();
    elementInstanceState = zeebeState.getElementInstanceState();

    final var bpmnBehaviors =
        new BpmnBehaviorsImpl(
            expressionProcessor,
            streamWriterProxy,
            responseWriterProxy,
            sideEffectQueue,
            zeebeState,
            catchEventBehavior,
            variableBehavior,
            eventTriggerBehavior,
            this::getContainerProcessor,
            writers);
    rejectionWriter = writers.rejection();
    incidentBehavior = bpmnBehaviors.incidentBehavior();
    processors = new BpmnElementProcessors(bpmnBehaviors);

    stateTransitionGuard = bpmnBehaviors.stateTransitionGuard();
    stateTransitionBehavior = bpmnBehaviors.stateTransitionBehavior();
  }

  private BpmnElementContainerProcessor<ExecutableFlowElement> getContainerProcessor(
      final BpmnElementType elementType) {
    return processors.getContainerProcessor(elementType);
  }

  @Override
  public void onRecovered(final ReadonlyProcessingContext context) {
    reprocessingMode = false;
  }

  @Override
  public void processRecord(
      final TypedRecord<ProcessInstanceRecord> record,
      final TypedResponseWriter responseWriter,
      final TypedStreamWriter streamWriter,
      final Consumer<SideEffectProducer> sideEffect) {

    // todo (#6202): replace writer proxies by Writers
    // initialize
    streamWriterProxy.wrap(streamWriter);
    responseWriterProxy.wrap(responseWriter, writer -> sideEffectQueue.add(writer::flush));
    sideEffectQueue.clear();
    sideEffect.accept(sideEffectQueue);

    final var intent = (ProcessInstanceIntent) record.getIntent();
    final var recordValue = record.getValue();

    context.init(record.getKey(), recordValue, intent);
    context.setReprocessingMode(reprocessingMode);

    final var bpmnElementType = recordValue.getBpmnElementType();
    final var processor = processors.getProcessor(bpmnElementType);
    final ExecutableFlowElement element = getElement(recordValue, processor);

    if (!MigratedStreamProcessors.isMigrated(context.getBpmnElementType())) {
      relieveReprocessingStateProblems();
    }

    stateTransitionGuard
        .isValidStateTransition(context)
        .ifRightOrLeft(
            ok -> {
              LOGGER.trace("Process process instance event [context: {}]", context);
              processEvent(intent, processor, element);
            },
            violation ->
                rejectionWriter.appendRejection(
                    record, RejectionType.INVALID_STATE, violation.getMessage()));
  }

  /**
   * On migrating processors we saw issues on replay, where element instances are not existing on
   * replay/reprocessing. You need to know that migrated processors are only replayed, which means
   * the events are applied to the state and non migrated are reprocessed, so the processor is
   * called.
   *
   * <p>If we have a non migrated type and reprocess that, we expect an existing element instance.
   * On normal processing this element instance was created by using the state writer of the
   * previous command/event most likely by an already migrated type. On replay this state writer is
   * not called which causes issues, like non existing element instance.
   *
   * <p>To cover the gap between migrated and non migrated processors we need to re-create an
   * element instance here, such that we can continue in migrate the processors individually and
   * still are able to run the replay tests.
   */
  private void relieveReprocessingStateProblems() {
    final var instance = elementInstanceState.getInstance(context.getElementInstanceKey());
    if (instance == null) {
      if (context.getIntent() != ProcessInstanceIntent.ELEMENT_ACTIVATING) {
        // only create new instance for elements that are activating
        return;
      }

      final var flowScopeInstance = elementInstanceState.getInstance(context.getFlowScopeKey());
      final var elementInstance =
          elementInstanceState.newInstance(
              flowScopeInstance,
              context.getElementInstanceKey(),
              context.getRecordValue(),
              ProcessInstanceIntent.ELEMENT_ACTIVATING);

      final var parentElementInstance =
          elementInstanceState.getInstance(context.getRecordValue().getParentElementInstanceKey());
      if (parentElementInstance == null
          || context.getBpmnElementType() != BpmnElementType.PROCESS) {
        // only connect to call activity for child processes
        return;
      }

      parentElementInstance.setCalledChildInstanceKey(elementInstance.getKey());
      elementInstanceState.updateInstance(parentElementInstance);
      return;
    }

    if (context.getIntent() == ProcessInstanceIntent.ELEMENT_TERMINATING
        && context.getBpmnElementType() == BpmnElementType.PROCESS) {
      instance.setState(ProcessInstanceIntent.ELEMENT_TERMINATING);
      elementInstanceState.updateInstance(instance);
    }
  }

  private void processEvent(
      final ProcessInstanceIntent intent,
      final BpmnElementProcessor<ExecutableFlowElement> processor,
      final ExecutableFlowElement element) {

    switch (intent) {
      case ACTIVATE_ELEMENT:
        final var activatingContext = stateTransitionBehavior.transitionToActivating(context);
        stateTransitionBehavior
            .onElementActivating(element, activatingContext)
            .ifRightOrLeft(
                ok -> processor.onActivate(element, activatingContext),
                failure -> incidentBehavior.createIncident(failure, activatingContext));
        break;
      case COMPLETE_ELEMENT:
        final var completingContext = stateTransitionBehavior.transitionToCompleting(context);
        processor.onComplete(element, completingContext);
        break;
      case TERMINATE_ELEMENT:
        final var terminatingContext = stateTransitionBehavior.transitionToTerminating(context);
        processor.onTerminate(element, terminatingContext);
        break;
      default:
        throw new BpmnProcessingException(
            context,
            String.format(
                "Expected the processor '%s' to handle the event but the intent '%s' is not supported",
                processor.getClass(), intent));
    }
  }

  private ExecutableFlowElement getElement(
      final ProcessInstanceRecord recordValue,
      final BpmnElementProcessor<ExecutableFlowElement> processor) {

    return processState.getFlowElement(
        recordValue.getProcessDefinitionKey(),
        recordValue.getElementIdBuffer(),
        processor.getType());
  }
}
