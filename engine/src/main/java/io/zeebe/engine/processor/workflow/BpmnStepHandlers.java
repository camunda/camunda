/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processor.workflow;

import io.zeebe.engine.nwe.BpmnStreamProcessor;
import io.zeebe.engine.processor.workflow.deployment.model.BpmnStep;
import io.zeebe.engine.processor.workflow.deployment.model.element.ExecutableFlowElement;
import io.zeebe.engine.processor.workflow.handlers.CatchEventSubscriber;
import io.zeebe.engine.processor.workflow.handlers.IncidentResolver;
import io.zeebe.engine.processor.workflow.handlers.activity.ActivityElementActivatingHandler;
import io.zeebe.engine.processor.workflow.handlers.activity.ActivityElementCompletingHandler;
import io.zeebe.engine.processor.workflow.handlers.activity.ActivityElementTerminatedHandler;
import io.zeebe.engine.processor.workflow.handlers.activity.ActivityElementTerminatingHandler;
import io.zeebe.engine.processor.workflow.handlers.activity.ActivityEventOccurredHandler;
import io.zeebe.engine.processor.workflow.handlers.callactivity.CallActivityActivatingHandler;
import io.zeebe.engine.processor.workflow.handlers.callactivity.CallActivityTerminatingHandler;
import io.zeebe.engine.processor.workflow.handlers.container.ContainerElementActivatedHandler;
import io.zeebe.engine.processor.workflow.handlers.container.ContainerElementTerminatingHandler;
import io.zeebe.engine.processor.workflow.handlers.container.ProcessCompletedHandler;
import io.zeebe.engine.processor.workflow.handlers.container.ProcessTerminatedHandler;
import io.zeebe.engine.processor.workflow.handlers.container.WorkflowResultSender;
import io.zeebe.engine.processor.workflow.handlers.element.ElementActivatedHandler;
import io.zeebe.engine.processor.workflow.handlers.element.ElementActivatingHandler;
import io.zeebe.engine.processor.workflow.handlers.element.ElementCompletedHandler;
import io.zeebe.engine.processor.workflow.handlers.element.ElementCompletingHandler;
import io.zeebe.engine.processor.workflow.handlers.element.ElementTerminatedHandler;
import io.zeebe.engine.processor.workflow.handlers.element.ElementTerminatingHandler;
import io.zeebe.engine.processor.workflow.handlers.element.EventOccurredHandler;
import io.zeebe.engine.processor.workflow.handlers.eventsubproc.EventSubProcessEventOccurredHandler;
import io.zeebe.engine.processor.workflow.handlers.gateway.EventBasedGatewayElementActivatingHandler;
import io.zeebe.engine.processor.workflow.handlers.gateway.EventBasedGatewayElementCompletedHandler;
import io.zeebe.engine.processor.workflow.handlers.gateway.EventBasedGatewayElementCompletingHandler;
import io.zeebe.engine.processor.workflow.handlers.gateway.EventBasedGatewayElementTerminatingHandler;
import io.zeebe.engine.processor.workflow.handlers.gateway.EventBasedGatewayEventOccurredHandler;
import io.zeebe.engine.processor.workflow.handlers.seqflow.FlowOutElementCompletedHandler;
import io.zeebe.engine.processor.workflow.message.BufferedMessageToStartEventCorrelator;
import io.zeebe.engine.state.ZeebeState;
import io.zeebe.protocol.record.intent.WorkflowInstanceIntent;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class BpmnStepHandlers {
  private final Map<BpmnStep, BpmnStepHandler<?>> stepHandlers = new EnumMap<>(BpmnStep.class);
  private final BpmnStreamProcessor bpmnStreamProcessor;

  BpmnStepHandlers(
      final ZeebeState state,
      final ExpressionProcessor expressionProcessor,
      final CatchEventBehavior catchEventBehavior) {
    final IncidentResolver incidentResolver = new IncidentResolver(state.getIncidentState());
    final CatchEventSubscriber catchEventSubscriber = new CatchEventSubscriber(catchEventBehavior);
    final BufferedMessageToStartEventCorrelator messageStartEventCorrelator =
        new BufferedMessageToStartEventCorrelator(
            state.getKeyGenerator(),
            state.getMessageState(),
            state.getWorkflowState().getEventScopeInstanceState());

    stepHandlers.put(
        BpmnStep.ELEMENT_ACTIVATING, new ElementActivatingHandler<>(expressionProcessor));
    stepHandlers.put(BpmnStep.ELEMENT_ACTIVATED, new ElementActivatedHandler<>());
    stepHandlers.put(BpmnStep.EVENT_OCCURRED, new EventOccurredHandler<>());
    stepHandlers.put(
        BpmnStep.ELEMENT_COMPLETING, new ElementCompletingHandler<>(expressionProcessor));
    stepHandlers.put(BpmnStep.ELEMENT_COMPLETED, new ElementCompletedHandler<>());
    stepHandlers.put(BpmnStep.ELEMENT_TERMINATING, new ElementTerminatingHandler<>());
    stepHandlers.put(BpmnStep.ELEMENT_TERMINATED, new ElementTerminatedHandler<>(incidentResolver));
    stepHandlers.put(BpmnStep.FLOWOUT_ELEMENT_COMPLETED, new FlowOutElementCompletedHandler<>());

    stepHandlers.put(
        BpmnStep.ACTIVITY_ELEMENT_ACTIVATING,
        new ActivityElementActivatingHandler<>(catchEventSubscriber, expressionProcessor));
    stepHandlers.put(BpmnStep.ACTIVITY_ELEMENT_ACTIVATED, new ElementActivatedHandler<>(null));
    stepHandlers.put(BpmnStep.ACTIVITY_EVENT_OCCURRED, new ActivityEventOccurredHandler<>());
    stepHandlers.put(
        BpmnStep.ACTIVITY_ELEMENT_COMPLETING,
        new ActivityElementCompletingHandler<>(catchEventSubscriber, expressionProcessor));
    stepHandlers.put(
        BpmnStep.ACTIVITY_ELEMENT_TERMINATING,
        new ActivityElementTerminatingHandler<>(catchEventSubscriber));
    stepHandlers.put(
        BpmnStep.ACTIVITY_ELEMENT_TERMINATED,
        new ActivityElementTerminatedHandler<>(incidentResolver));

    stepHandlers.put(
        BpmnStep.CONTAINER_ELEMENT_ACTIVATED,
        new ContainerElementActivatedHandler<>(state.getWorkflowState()));
    stepHandlers.put(
        BpmnStep.CONTAINER_ELEMENT_TERMINATING,
        new ContainerElementTerminatingHandler<>(catchEventSubscriber));
    stepHandlers.put(
        BpmnStep.PROCESS_COMPLETED,
        new ProcessCompletedHandler(
            List.of(new WorkflowResultSender(), messageStartEventCorrelator)));
    stepHandlers.put(
        BpmnStep.PROCESS_TERMINATED,
        new ProcessTerminatedHandler(incidentResolver, messageStartEventCorrelator));

    stepHandlers.put(
        BpmnStep.EVENT_BASED_GATEWAY_ELEMENT_ACTIVATING,
        new EventBasedGatewayElementActivatingHandler<>(catchEventSubscriber, expressionProcessor));
    stepHandlers.put(
        BpmnStep.EVENT_BASED_GATEWAY_ELEMENT_ACTIVATED, new ElementActivatedHandler<>(null));
    stepHandlers.put(
        BpmnStep.EVENT_BASED_GATEWAY_EVENT_OCCURRED, new EventBasedGatewayEventOccurredHandler<>());
    stepHandlers.put(
        BpmnStep.EVENT_BASED_GATEWAY_ELEMENT_COMPLETING,
        new EventBasedGatewayElementCompletingHandler<>(catchEventSubscriber, expressionProcessor));
    stepHandlers.put(
        BpmnStep.EVENT_BASED_GATEWAY_ELEMENT_TERMINATING,
        new EventBasedGatewayElementTerminatingHandler<>(catchEventSubscriber));
    stepHandlers.put(
        BpmnStep.EVENT_BASED_GATEWAY_ELEMENT_COMPLETED,
        new EventBasedGatewayElementCompletedHandler<>());

    stepHandlers.put(
        BpmnStep.EVENT_SUBPROC_EVENT_OCCURRED,
        new EventSubProcessEventOccurredHandler<>(catchEventBehavior));

    stepHandlers.put(
        BpmnStep.CALL_ACTIVITY_ACTIVATING,
        new CallActivityActivatingHandler(
            catchEventSubscriber, state.getKeyGenerator(), expressionProcessor));
    stepHandlers.put(
        BpmnStep.CALL_ACTIVITY_TERMINATING,
        new CallActivityTerminatingHandler(catchEventSubscriber));

    // ---------- new -----------------
    bpmnStreamProcessor =
        new BpmnStreamProcessor(expressionProcessor, catchEventBehavior, state, this::handle);
  }

  public void handle(final BpmnStepContext context) {
    final ExecutableFlowElement flowElement = context.getElement();
    final WorkflowInstanceIntent state = context.getState();
    final BpmnStep step = flowElement.getStep(state);

    if (step == BpmnStep.BPMN_ELEMENT_PROCESSOR) {
      bpmnStreamProcessor.processRecord(
          context.getRecord(),
          context.getOutput().getResponseWriter(),
          context.getOutput().getStreamWriter(),
          context.getSideEffectConsumer());
      return;
    }

    if (step != null) {
      final BpmnStepHandler stepHandler = stepHandlers.get(step);

      if (stepHandler == null) {
        throw new IllegalStateException(
            String.format("Expected BPMN handler for step '%s' but not found.", step));
      }
      stepHandler.handle(context);
    }
  }
}
