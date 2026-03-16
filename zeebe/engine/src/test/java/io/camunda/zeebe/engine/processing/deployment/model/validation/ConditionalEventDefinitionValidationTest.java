/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.deployment.model.validation;

import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.instance.Condition;
import io.camunda.zeebe.model.bpmn.instance.Process;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class ConditionalEventDefinitionValidationTest {

  // ---------------------------------------------------------------------------
  // Root level conditional start event tests
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("Condition is a valid expression for conditional start event")
  void validConditionForConditionalStartEvent() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .condition(c -> c.condition("= x > 1"))
            .done();

    // when/then
    ProcessValidationUtil.validateProcess(process);
  }

  @Test
  @DisplayName("Condition is mandatory for conditional start event")
  void emptyConditionForConditionalStartEvent() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess("process").startEvent().condition(c -> c.condition("")).done();

    // when/then
    ProcessValidationUtil.validateProcess(
        process,
        ExpectedValidationResult.expect(
            Condition.class,
            "Expected expression but found static value ''. An expression must start with '=' (e.g. '=')."));
  }

  @Test
  @DisplayName("Condition must be a valid expression for conditional start event")
  void invalidConditionExpressionForConditionalStartEvent() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .condition(c -> c.condition("= x >>> 1"))
            .done();

    // when/then
    ProcessValidationUtil.validateProcess(
        process,
        ExpectedValidationResult.expect(
            Condition.class,
            "failed to parse expression ' x >>> 1': Expected (binaryComparison | between"
                + " | instanceOf | in | \"and\" | \"or\" | end-of-input):1:4, found \">>> 1\""));
  }

  @Test
  @DisplayName("Valid conditional event XML for root level conditional start event")
  void validConditionalXMLForRootLevelConditionalStartEvent() {
    // given
    final BpmnModelInstance process =
        Bpmn.readModelFromStream(
            ConditionalEventDefinitionValidationTest.class.getResourceAsStream(
                "/processes/root-level-conditional-start-event.bpmn"));

    // when/then
    ProcessValidationUtil.validateProcess(process);
  }

  @Test
  @DisplayName("Duplicate conditions for conditional start events are not allowed")
  void duplicateConditionsForConditionalStartEventsNotAllowed() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess("process")
            .startEvent("startEvent1")
            .condition(c -> c.condition("= x > 1"))
            .endEvent()
            .moveToProcess("process")
            .startEvent("startEvent2")
            .condition(c -> c.condition("= x > 1"))
            .endEvent()
            .done();

    // when/then
    ProcessValidationUtil.validateProcess(
        process,
        ExpectedValidationResult.expect(
            Process.class,
            "Duplicate condition expression '= x > 1' found in conditional start events of process"
                + " 'process'. Condition expressions for conditional start events must be unique"
                + " within a process."));
  }

  @Test
  @DisplayName("Non-duplicate conditions for conditional start events are allowed")
  void nonDuplicateConditionsForConditionalStartEventsAllowed() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess("process")
            .startEvent("startEvent1")
            .condition(c -> c.condition("= x > 1"))
            .endEvent()
            .moveToProcess("process")
            .startEvent("startEvent2")
            .condition(c -> c.condition("= y < 5"))
            .endEvent()
            .done();

    // when/then
    ProcessValidationUtil.validateProcess(process);
  }

  // ---------------------------------------------------------------------------
  // Conditional boundary catch event tests
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("Condition is a valid expression for conditional boundary event")
  void validConditionForConditionalBoundaryEvent() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .serviceTask("task")
            .zeebeJobType("task")
            .boundaryEvent()
            .condition(c -> c.condition("= x > 1"))
            .endEvent()
            .moveToActivity("task")
            .endEvent()
            .done();

    // when/then
    ProcessValidationUtil.validateProcess(process);
  }

  @Test
  @DisplayName("Condition is mandatory for conditional boundary event")
  void emptyConditionForConditionalBoundaryEvent() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .serviceTask("task")
            .zeebeJobType("task")
            .boundaryEvent()
            .condition(c -> c.condition(""))
            .endEvent()
            .moveToActivity("task")
            .endEvent()
            .done();

    // when/then
    ProcessValidationUtil.validateProcess(
        process,
        ExpectedValidationResult.expect(
            Condition.class,
            "Expected expression but found static value ''. An expression must start with '=' (e.g. '=')."));
  }

  @Test
  @DisplayName("Condition must be a valid expression for conditional boundary event")
  void invalidConditionExpressionForConditionalBoundaryEvent() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .serviceTask("task")
            .zeebeJobType("task")
            .boundaryEvent()
            .condition(c -> c.condition("= x >>> 1"))
            .endEvent()
            .moveToActivity("task")
            .endEvent()
            .done();

    // when/then
    ProcessValidationUtil.validateProcess(
        process,
        ExpectedValidationResult.expect(
            Condition.class,
            "failed to parse expression ' x >>> 1': Expected (binaryComparison | between"
                + " | instanceOf | in | \"and\" | \"or\" | end-of-input):1:4, found \">>> 1\""));
  }

  @Test
  @DisplayName("Non-interrupting conditional boundary event is allowed")
  void nonInterruptingConditionalBoundaryEvent() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .serviceTask("task")
            .zeebeJobType("task")
            .boundaryEvent()
            .cancelActivity(false)
            .condition(c -> c.condition("= x > 1"))
            .endEvent()
            .moveToActivity("task")
            .endEvent()
            .done();

    // when/then
    ProcessValidationUtil.validateProcess(process);
  }

  @Test
  @DisplayName("Valid conditional event XML for conditional boundary event")
  void validConditionalXMLForConditionalBoundaryEvent() {
    // given
    final BpmnModelInstance process =
        Bpmn.readModelFromStream(
            ConditionalEventDefinitionValidationTest.class.getResourceAsStream(
                "/processes/conditional-boundary-event.bpmn"));

    // when/then
    ProcessValidationUtil.validateProcess(process);
  }

  // ---------------------------------------------------------------------------
  // Conditional intermediate catch event tests
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("Condition is a valid expression for conditional intermediate catch event")
  void validConditionForConditionalIntermediateCatchEvent() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .intermediateCatchEvent()
            .condition(c -> c.condition("= x > 1"))
            .endEvent()
            .done();

    // when/then
    ProcessValidationUtil.validateProcess(process);
  }

  @Test
  @DisplayName("Condition is mandatory for conditional intermediate catch event")
  void emptyConditionForConditionalIntermediateCatchEvent() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .intermediateCatchEvent()
            .condition(c -> c.condition(""))
            .endEvent()
            .done();

    // when/then
    ProcessValidationUtil.validateProcess(
        process,
        ExpectedValidationResult.expect(
            Condition.class,
            "Expected expression but found static value ''. An expression must start with '=' (e.g. '=')."));
  }

  @Test
  @DisplayName("Condition must be a valid expression for conditional intermediate catch event")
  void invalidConditionExpressionForConditionalIntermediateCatchEvent() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .intermediateCatchEvent()
            .condition(c -> c.condition("= x >>> 1"))
            .endEvent()
            .done();

    // when/then
    ProcessValidationUtil.validateProcess(
        process,
        ExpectedValidationResult.expect(
            Condition.class,
            "failed to parse expression ' x >>> 1': Expected (binaryComparison | between"
                + " | instanceOf | in | \"and\" | \"or\" | end-of-input):1:4, found \">>> 1\""));
  }

  @Test
  @DisplayName("Valid conditional event XML for conditional intermediate catch event")
  void validConditionalXMLForConditionalIntermediateCatchEvent() {
    // given
    final BpmnModelInstance process =
        Bpmn.readModelFromStream(
            ConditionalEventDefinitionValidationTest.class.getResourceAsStream(
                "/processes/conditional-intermediate-catch-event.bpmn"));

    // when/then
    ProcessValidationUtil.validateProcess(process);
  }

  // ---------------------------------------------------------------------------
  // Conditional event subprocess start event tests
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("Condition is a valid expression for conditional event subprocess start event")
  void validConditionForConditionalEventSubProcessStartEvent() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .endEvent()
            .moveToProcess("process")
            .eventSubProcess()
            .startEvent()
            .condition(c -> c.condition("= x > 1"))
            .endEvent()
            .subProcessDone()
            .done();

    // when/then
    ProcessValidationUtil.validateProcess(process);
  }

  @Test
  @DisplayName("Condition is mandatory for conditional event subprocess start event")
  void emptyConditionForConditionalEventSubProcessStartEvent() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .endEvent()
            .moveToProcess("process")
            .eventSubProcess()
            .startEvent()
            .condition(c -> c.condition(""))
            .endEvent()
            .subProcessDone()
            .done();

    // when/then
    ProcessValidationUtil.validateProcess(
        process,
        ExpectedValidationResult.expect(
            Condition.class,
            "Expected expression but found static value ''. An expression must start with '=' (e.g. '=')."));
  }

  @Test
  @DisplayName("Condition must be a valid expression for conditional event subprocess start event")
  void invalidConditionExpressionForConditionalEventSubProcessStartEvent() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .endEvent()
            .moveToProcess("process")
            .eventSubProcess()
            .startEvent()
            .condition(c -> c.condition("= x >>> 1"))
            .endEvent()
            .subProcessDone()
            .done();

    // when/then
    ProcessValidationUtil.validateProcess(
        process,
        ExpectedValidationResult.expect(
            Condition.class,
            "failed to parse expression ' x >>> 1': Expected (binaryComparison | between"
                + " | instanceOf | in | \"and\" | \"or\" | end-of-input):1:4, found \">>> 1\""));
  }

  @Test
  @DisplayName("Non-interrupting event subprocess conditional start event is allowed")
  void nonInterruptingConditionalEventSubProcessStartEvent() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .endEvent()
            .moveToProcess("process")
            .eventSubProcess()
            .startEvent()
            .interrupting(false)
            .condition(c -> c.condition("= x > 1"))
            .endEvent()
            .subProcessDone()
            .done();

    // when/then
    ProcessValidationUtil.validateProcess(process);
  }

  @Test
  @DisplayName("Valid conditional event XML for event subprocess conditional start event")
  void validConditionalXMLForEventSubprocessConditionalStartEvent() {
    // given
    final BpmnModelInstance process =
        Bpmn.readModelFromStream(
            ConditionalEventDefinitionValidationTest.class.getResourceAsStream(
                "/processes/event-subprocess-conditional-start-event.bpmn"));

    // when/then
    ProcessValidationUtil.validateProcess(process);
  }

  @Test
  @DisplayName("Duplicate conditions for conditional event subprocess start events are not allowed")
  void duplicateConditionsForConditionalEventSubProcessStartEventsNotAllowed() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .endEvent()
            .moveToProcess("process")
            .eventSubProcess()
            .startEvent("startEvent1")
            .condition(c -> c.condition("= x > 1"))
            .endEvent()
            .subProcessDone()
            .moveToProcess("process")
            .eventSubProcess()
            .startEvent("startEvent2")
            .condition(c -> c.condition("= x > 1"))
            .endEvent()
            .subProcessDone()
            .done();

    // when/then
    ProcessValidationUtil.validateProcess(
        process,
        ExpectedValidationResult.expect(
            Process.class,
            "Duplicate condition expression '= x > 1' found in conditional start events of event"
                + " subprocesses in process 'process'. Condition expressions for conditional start"
                + " events in event subprocesses must be unique within a process."));
  }

  @Test
  @DisplayName("Non-duplicate conditions for conditional event subprocess start events are allowed")
  void nonDuplicateConditionsForConditionalEventSubProcessStartEventsAllowed() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .endEvent()
            .moveToProcess("process")
            .eventSubProcess()
            .startEvent("startEvent1")
            .condition(c -> c.condition("= x > 1"))
            .endEvent()
            .subProcessDone()
            .moveToProcess("process")
            .eventSubProcess()
            .startEvent("startEvent2")
            .condition(c -> c.condition("= y < 5"))
            .endEvent()
            .subProcessDone()
            .done();

    // when/then
    ProcessValidationUtil.validateProcess(process);
  }
}
