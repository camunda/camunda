/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processing.bpmn;

import io.zeebe.engine.Loggers;
import io.zeebe.engine.processing.bpmn.behavior.BpmnBehaviorsImpl;
import io.zeebe.engine.processing.bpmn.behavior.TypedResponseWriterProxy;
import io.zeebe.engine.processing.bpmn.behavior.TypedStreamWriterProxy;
import io.zeebe.engine.processing.common.CatchEventBehavior;
import io.zeebe.engine.processing.common.ExpressionProcessor;
import io.zeebe.engine.processing.deployment.model.element.ExecutableFlowElement;
import io.zeebe.engine.processing.streamprocessor.MigratedStreamProcessors;
import io.zeebe.engine.processing.streamprocessor.TypedRecord;
import io.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.zeebe.engine.processing.streamprocessor.sideeffect.SideEffectProducer;
import io.zeebe.engine.processing.streamprocessor.sideeffect.SideEffectQueue;
import io.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.zeebe.engine.processing.streamprocessor.writers.TypedStreamWriter;
import io.zeebe.engine.state.ZeebeState;
import io.zeebe.engine.state.immutable.WorkflowState;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceRecord;
import io.zeebe.protocol.record.intent.WorkflowInstanceIntent;
import io.zeebe.protocol.record.value.BpmnElementType;
import java.util.function.Consumer;
import org.slf4j.Logger;

public final class BpmnStreamProcessor implements TypedRecordProcessor<WorkflowInstanceRecord> {

  private static final Logger LOGGER = Loggers.WORKFLOW_PROCESSOR_LOGGER;

  private final TypedStreamWriterProxy streamWriterProxy = new TypedStreamWriterProxy();
  private final TypedResponseWriterProxy responseWriterProxy = new TypedResponseWriterProxy();
  private final SideEffectQueue sideEffectQueue = new SideEffectQueue();
  private final BpmnElementContextImpl context = new BpmnElementContextImpl();

  private final WorkflowState workflowState;
  private final BpmnElementProcessors processors;
  private final WorkflowInstanceStateTransitionGuard stateTransitionGuard;

  public BpmnStreamProcessor(
      final ExpressionProcessor expressionProcessor,
      final CatchEventBehavior catchEventBehavior,
      final ZeebeState zeebeState) {
    workflowState = zeebeState.getWorkflowState();

    final var bpmnBehaviors =
        new BpmnBehaviorsImpl(
            expressionProcessor,
            streamWriterProxy,
            responseWriterProxy,
            sideEffectQueue,
            zeebeState,
            catchEventBehavior,
            this::getContainerProcessor);
    processors = new BpmnElementProcessors(bpmnBehaviors);

    stateTransitionGuard = bpmnBehaviors.stateTransitionGuard();
  }

  private BpmnElementContainerProcessor<ExecutableFlowElement> getContainerProcessor(
      final BpmnElementType elementType) {
    return processors.getContainerProcessor(elementType);
  }

  @Override
  public void processRecord(
      final TypedRecord<WorkflowInstanceRecord> record,
      final TypedResponseWriter responseWriter,
      final TypedStreamWriter streamWriter,
      final Consumer<SideEffectProducer> sideEffect) {

    // todo (#6202): replace writer proxies by Writers
    // initialize
    streamWriterProxy.wrap(streamWriter);
    responseWriterProxy.wrap(responseWriter, writer -> sideEffectQueue.add(writer::flush));
    sideEffectQueue.clear();
    sideEffect.accept(sideEffectQueue);

    final var intent = (WorkflowInstanceIntent) record.getIntent();
    final var recordValue = record.getValue();

    context.init(record.getKey(), recordValue, intent);

    final var bpmnElementType = recordValue.getBpmnElementType();
    final var processor = processors.getProcessor(bpmnElementType);
    final ExecutableFlowElement element = getElement(recordValue, processor);

    // process the event
    if (stateTransitionGuard.isValidStateTransition(context)) {
      LOGGER.trace("Process workflow instance event [context: {}]", context);

      processEvent(intent, processor, element);
    }
  }

  private void processEvent(
      final WorkflowInstanceIntent intent,
      final BpmnElementProcessor<ExecutableFlowElement> processor,
      final ExecutableFlowElement element) {

    switch (intent) {
      case ACTIVATE_ELEMENT:
        if (MigratedStreamProcessors.isMigrated(context.getBpmnElementType())) {
          processor.onActivate(element, context);
        } else {
          processor.onActivating(element, context);
        }
        break;
      case COMPLETE_ELEMENT:
        if (MigratedStreamProcessors.isMigrated(context.getBpmnElementType())) {
          processor.onComplete(element, context);
        } else {
          processor.onCompleting(element, context);
        }
        break;
      case TERMINATE_ELEMENT:
        if (MigratedStreamProcessors.isMigrated(context.getBpmnElementType())) {
          processor.onTerminate(element, context);
        } else {
          processor.onTerminating(element, context);
        }
        break;
        // legacy behavior for not migrated processors
      case ELEMENT_ACTIVATING:
        processor.onActivating(element, context);
        break;
      case ELEMENT_ACTIVATED:
        processor.onActivated(element, context);
        break;
      case EVENT_OCCURRED:
        processor.onEventOccurred(element, context);
        break;
      case ELEMENT_COMPLETING:
        processor.onCompleting(element, context);
        break;
      case ELEMENT_COMPLETED:
        processor.onCompleted(element, context);
        break;
      case ELEMENT_TERMINATING:
        processor.onTerminating(element, context);
        break;
      case ELEMENT_TERMINATED:
        processor.onTerminated(element, context);
        break;
      case SEQUENCE_FLOW_TAKEN:
        // in order to keep the implementation simple, a sequence flow acts as an element that can
        // process `activating`
        processor.onActivating(element, context);
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
      final WorkflowInstanceRecord recordValue,
      final BpmnElementProcessor<ExecutableFlowElement> processor) {

    return workflowState.getFlowElement(
        recordValue.getWorkflowKey(), recordValue.getElementIdBuffer(), processor.getType());
  }
}
