/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.nwe;

import io.zeebe.engine.Loggers;
import io.zeebe.engine.nwe.behavior.BpmnBehaviorsImpl;
import io.zeebe.engine.nwe.behavior.TypesStreamWriterProxy;
import io.zeebe.engine.processor.SideEffectProducer;
import io.zeebe.engine.processor.TypedRecord;
import io.zeebe.engine.processor.TypedRecordProcessor;
import io.zeebe.engine.processor.TypedResponseWriter;
import io.zeebe.engine.processor.TypedStreamWriter;
import io.zeebe.engine.processor.workflow.BpmnStepContext;
import io.zeebe.engine.processor.workflow.CatchEventBehavior;
import io.zeebe.engine.processor.workflow.ExpressionProcessor;
import io.zeebe.engine.processor.workflow.deployment.model.element.ExecutableFlowElement;
import io.zeebe.engine.processor.workflow.deployment.model.element.ExecutableMultiInstanceBody;
import io.zeebe.engine.state.ZeebeState;
import io.zeebe.engine.state.deployment.WorkflowState;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceRecord;
import io.zeebe.protocol.record.intent.WorkflowInstanceIntent;
import io.zeebe.protocol.record.value.BpmnElementType;
import java.util.function.Consumer;
import org.slf4j.Logger;

public final class BpmnStreamProcessor implements TypedRecordProcessor<WorkflowInstanceRecord> {

  private static final Logger LOGGER = Loggers.WORKFLOW_PROCESSOR_LOGGER;

  private final TypesStreamWriterProxy streamWriterProxy = new TypesStreamWriterProxy();

  private final BpmnElementContextImpl context;
  private final WorkflowState workflowState;
  private final BpmnElementProcessors processors;
  private final WorkflowInstanceStateTransitionGuard stateTransitionGuard;

  private final Consumer<BpmnStepContext<?>> fallback;

  public BpmnStreamProcessor(
      final ExpressionProcessor expressionProcessor,
      final CatchEventBehavior catchEventBehavior,
      final ZeebeState zeebeState,
      final Consumer<BpmnStepContext<?>> fallback) {
    workflowState = zeebeState.getWorkflowState();
    context = new BpmnElementContextImpl(zeebeState);

    final var bpmnBehaviors =
        new BpmnBehaviorsImpl(
            expressionProcessor,
            streamWriterProxy,
            zeebeState,
            catchEventBehavior,
            this::getContainerProcessor);
    processors = new BpmnElementProcessors(bpmnBehaviors);

    this.fallback = fallback;
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

    final var intent = (WorkflowInstanceIntent) record.getIntent();
    final var recordValue = record.getValue();
    final var bpmnElementType = recordValue.getBpmnElementType();
    final var processor = processors.getProcessor(bpmnElementType);

    if (processor == null) {
      // TODO (saig0): remove multi-instance fallback when the processors of all multi-instance
      // elements are migrated
      LOGGER.debug("[NEW] No processor found for BPMN element type '{}'", bpmnElementType);

      final var multiInstanceBody =
          workflowState.getFlowElement(
              recordValue.getWorkflowKey(),
              recordValue.getElementIdBuffer(),
              ExecutableMultiInstanceBody.class);
      final var element = multiInstanceBody.getInnerActivity();

      streamWriterProxy.wrap(streamWriter);
      context.init(record, intent, element, streamWriterProxy, sideEffect);

      fallback.accept(context.toStepContext());
      return;
    }

    LOGGER.debug(
        "[NEW] process workflow instance event [BPMN element type: {}, intent: {}]",
        bpmnElementType,
        intent);

    final ExecutableFlowElement element = getElement(recordValue, processor);

    // initialize the stuff
    streamWriterProxy.wrap(streamWriter);
    context.init(record, intent, element, streamWriterProxy, sideEffect);

    // process the event
    if (stateTransitionGuard.isValidStateTransition(context)) {
      processEvent(intent, processor, element);
    }
  }

  private void processEvent(
      final WorkflowInstanceIntent intent,
      final BpmnElementProcessor<ExecutableFlowElement> processor,
      final ExecutableFlowElement element) {

    switch (intent) {
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
      default:
        throw new UnsupportedOperationException(
            String.format(
                "processor '%s' can not handle intent '%s'", processor.getClass(), intent));
    }
  }

  private ExecutableFlowElement getElement(
      final WorkflowInstanceRecord recordValue,
      final BpmnElementProcessor<ExecutableFlowElement> processor) {

    return workflowState.getFlowElement(
        recordValue.getWorkflowKey(), recordValue.getElementIdBuffer(), processor.getType());
  }
}
