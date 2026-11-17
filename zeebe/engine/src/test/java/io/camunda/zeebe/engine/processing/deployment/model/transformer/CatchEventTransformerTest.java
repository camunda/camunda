/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.deployment.model.transformer;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.el.ExpressionLanguageFactory;
import io.camunda.zeebe.engine.processing.bpmn.clock.ZeebeFeelEngineClock;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableBoundaryEvent;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableMultiInstanceBody;
import io.camunda.zeebe.engine.processing.deployment.model.transformation.BpmnTransformer;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.InstantSource;
import org.junit.jupiter.api.Test;

class CatchEventTransformerTest {

  private static final String HANDLER_ASSOCIATION =
      """
      <bpmn:association id="to-handler" associationDirection="One"
          sourceRef="compensation-boundary" targetRef="undo" />
      """;
  private static final String ANNOTATION_ASSOCIATION =
      """
      <bpmn:association id="to-annotation" sourceRef="compensation-boundary" targetRef="note" />
      """;
  private static final String MULTI_INSTANCE =
      """
      <bpmn:multiInstanceLoopCharacteristics>
        <bpmn:extensionElements>
          <zeebe:loopCharacteristics inputCollection="=[1, 2]" />
        </bpmn:extensionElements>
      </bpmn:multiInstanceLoopCharacteristics>
      """;

  private final BpmnTransformer transformer =
      new BpmnTransformer(
          ExpressionLanguageFactory.createExpressionLanguage(
              new ZeebeFeelEngineClock(InstantSource.system())));

  @Test
  void shouldResolveCompensationHandlerWhenHandlerAssociationComesFirst() {
    // given
    final var model = processWithCompensation("", HANDLER_ASSOCIATION + ANNOTATION_ASSOCIATION);

    // when
    final var boundaryEvent = transformCompensationBoundaryEvent(model);

    // then
    assertThat(boundaryEvent.getCompensation().getCompensationHandler())
        .extracting(handler -> BufferUtil.bufferAsString(handler.getId()))
        .isEqualTo("undo");
  }

  @Test
  void shouldResolveCompensationHandlerWhenTextAnnotationAssociationComesFirst() {
    // given
    final var model = processWithCompensation("", ANNOTATION_ASSOCIATION + HANDLER_ASSOCIATION);

    // when
    final var boundaryEvent = transformCompensationBoundaryEvent(model);

    // then
    assertThat(boundaryEvent.getCompensation().getCompensationHandler())
        .extracting(handler -> BufferUtil.bufferAsString(handler.getId()))
        .isEqualTo("undo");
  }

  @Test
  void shouldResolveMultiInstanceCompensationHandlerWhenTextAnnotationAssociationComesFirst() {
    // given
    final var model =
        processWithCompensation(MULTI_INSTANCE, ANNOTATION_ASSOCIATION + HANDLER_ASSOCIATION);

    // when
    final var boundaryEvent = transformCompensationBoundaryEvent(model);

    // then
    assertThat(boundaryEvent.getCompensation().getCompensationHandler())
        .isInstanceOf(ExecutableMultiInstanceBody.class)
        .extracting(handler -> BufferUtil.bufferAsString(handler.getId()))
        .isEqualTo("undo");
  }

  private ExecutableBoundaryEvent transformCompensationBoundaryEvent(
      final BpmnModelInstance model) {
    return transformer
        .transformDefinitions(model)
        .getFirst()
        .getElementById("compensation-boundary", ExecutableBoundaryEvent.class);
  }

  /**
   * Creates a process with a compensation boundary event that is associated with a text annotation
   * and a compensation handler, using the given associations in that document order.
   */
  private static BpmnModelInstance processWithCompensation(
      final String handlerLoopCharacteristics, final String associations) {
    final String xml =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
            xmlns:zeebe="http://camunda.org/schema/zeebe/1.0"
            id="definitions" targetNamespace="http://bpmn.io/schema/bpmn">
          <bpmn:process id="process" isExecutable="true">
            <bpmn:startEvent id="start"><bpmn:outgoing>to-task</bpmn:outgoing></bpmn:startEvent>
            <bpmn:sequenceFlow id="to-task" sourceRef="start" targetRef="task" />
            <bpmn:task id="task">
              <bpmn:incoming>to-task</bpmn:incoming>
              <bpmn:outgoing>to-end</bpmn:outgoing>
            </bpmn:task>
            <bpmn:sequenceFlow id="to-end" sourceRef="task" targetRef="end" />
            <bpmn:endEvent id="end"><bpmn:incoming>to-end</bpmn:incoming></bpmn:endEvent>
            <bpmn:boundaryEvent id="compensation-boundary" attachedToRef="task">
              <bpmn:compensateEventDefinition id="compensation-definition" />
            </bpmn:boundaryEvent>
            <bpmn:task id="undo" isForCompensation="true">%s</bpmn:task>
            <bpmn:textAnnotation id="note"><bpmn:text>undo the task</bpmn:text></bpmn:textAnnotation>
            %s
          </bpmn:process>
        </bpmn:definitions>
        """
            .formatted(handlerLoopCharacteristics, associations);
    return Bpmn.readModelFromStream(
        new ByteArrayInputStream(xml.strip().getBytes(StandardCharsets.UTF_8)));
  }
}
