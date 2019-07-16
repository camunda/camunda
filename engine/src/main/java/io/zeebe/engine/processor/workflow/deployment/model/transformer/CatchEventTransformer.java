/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processor.workflow.deployment.model.transformer;

import io.zeebe.engine.processor.workflow.deployment.model.BpmnStep;
import io.zeebe.engine.processor.workflow.deployment.model.element.ExecutableCatchEventElement;
import io.zeebe.engine.processor.workflow.deployment.model.element.ExecutableMessage;
import io.zeebe.engine.processor.workflow.deployment.model.element.ExecutableWorkflow;
import io.zeebe.engine.processor.workflow.deployment.model.transformation.ModelElementTransformer;
import io.zeebe.engine.processor.workflow.deployment.model.transformation.TransformContext;
import io.zeebe.model.bpmn.instance.CatchEvent;
import io.zeebe.model.bpmn.instance.EventDefinition;
import io.zeebe.model.bpmn.instance.Message;
import io.zeebe.model.bpmn.instance.MessageEventDefinition;
import io.zeebe.model.bpmn.instance.TimerEventDefinition;
import io.zeebe.model.bpmn.util.time.Interval;
import io.zeebe.model.bpmn.util.time.RepeatingInterval;
import io.zeebe.model.bpmn.util.time.TimeDateTimer;
import io.zeebe.model.bpmn.util.time.Timer;
import io.zeebe.protocol.record.intent.WorkflowInstanceIntent;

public class CatchEventTransformer implements ModelElementTransformer<CatchEvent> {
  @Override
  public Class<CatchEvent> getType() {
    return CatchEvent.class;
  }

  @Override
  public void transform(CatchEvent element, TransformContext context) {
    final ExecutableWorkflow workflow = context.getCurrentWorkflow();
    final ExecutableCatchEventElement executableElement =
        workflow.getElementById(element.getId(), ExecutableCatchEventElement.class);

    executableElement.bindLifecycleState(
        WorkflowInstanceIntent.ELEMENT_COMPLETED, BpmnStep.FLOWOUT_ELEMENT_COMPLETED);

    if (!element.getEventDefinitions().isEmpty()) {
      transformEventDefinition(element, context, executableElement);
    }
  }

  private void transformEventDefinition(
      CatchEvent element, TransformContext context, ExecutableCatchEventElement executableElement) {
    final EventDefinition eventDefinition = element.getEventDefinitions().iterator().next();
    if (eventDefinition instanceof MessageEventDefinition) {
      transformMessageEventDefinition(
          context, executableElement, (MessageEventDefinition) eventDefinition);
    } else if (eventDefinition instanceof TimerEventDefinition) {
      transformTimerEventDefinition(executableElement, (TimerEventDefinition) eventDefinition);
    }
  }

  private void transformMessageEventDefinition(
      TransformContext context,
      final ExecutableCatchEventElement executableElement,
      final MessageEventDefinition messageEventDefinition) {

    final Message message = messageEventDefinition.getMessage();
    final ExecutableMessage executableMessage = context.getMessage(message.getId());
    executableElement.setMessage(executableMessage);
  }

  private void transformTimerEventDefinition(
      final ExecutableCatchEventElement executableElement,
      final TimerEventDefinition timerEventDefinition) {
    final Timer timer;

    if (timerEventDefinition.getTimeDuration() != null) {
      final String duration = timerEventDefinition.getTimeDuration().getTextContent();
      timer = new RepeatingInterval(1, Interval.parse(duration));
    } else if (timerEventDefinition.getTimeCycle() != null) {
      final String cycle = timerEventDefinition.getTimeCycle().getTextContent();
      timer = RepeatingInterval.parse(cycle);
    } else {
      final String timeDate = timerEventDefinition.getTimeDate().getTextContent();
      timer = TimeDateTimer.parse(timeDate);
    }

    executableElement.setTimer(timer);
  }
}
