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
import io.camunda.zeebe.model.bpmn.builder.AdHocSubProcessBuilder;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeAdHoc;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

public class AdHocSubProcessValidatorTest {

  private static final String PROCESS_ID = "process";
  private static final String AD_HOC_SUB_PROCESS_ELEMENT_ID = "ad-hoc";

  private BpmnModelInstance process(final Consumer<AdHocSubProcessBuilder> modifier) {
    return Bpmn.createExecutableProcess(PROCESS_ID)
        .startEvent()
        .adHocSubProcess(AD_HOC_SUB_PROCESS_ELEMENT_ID, modifier)
        .endEvent()
        .done();
  }

  @Test
  void withNoActiveElementsCollectionOrCompletionConditionExpression() {
    // given
    final BpmnModelInstance process = process(adHocSubProcess -> adHocSubProcess.task("A"));

    // when/then
    ProcessValidationUtil.validateProcess(process);
  }

  @Test
  void withEmptyActiveElementsCollectionExpression() {
    // given
    final BpmnModelInstance process =
        process(
            adHocSubProcess -> {
              adHocSubProcess.zeebeActiveElementsCollectionExpression("");
              adHocSubProcess.task("A");
            });

    // when/then
    ProcessValidationUtil.validateProcess(process);
  }

  @Test
  void withActiveElementsCollectionExpression() {
    // given
    final BpmnModelInstance process =
        process(
            adHocSubProcess -> {
              adHocSubProcess.zeebeActiveElementsCollectionExpression("elements");
              adHocSubProcess.task("A");
            });

    // when/then
    ProcessValidationUtil.validateProcess(process);
  }

  @Test
  void withInvalidActiveElementsCollectionExpression() {
    // given
    final BpmnModelInstance process =
        process(
            adHocSubProcess -> {
              adHocSubProcess.zeebeActiveElementsCollectionExpression("???");
              adHocSubProcess.task("A");
            });

    // when/then
    ProcessValidationUtil.validateProcess(
        process,
        ExpectedValidationResult.expect(ZeebeAdHoc.class, "failed to parse expression '???'"));
  }

  @Test
  void withEmptyCompletionConditionExpression() {
    // given
    final BpmnModelInstance process =
        process(
            adHocSubProcess -> {
              adHocSubProcess.zeebeCompletionConditionExpression("");
              adHocSubProcess.task("A");
            });

    // when/then
    ProcessValidationUtil.validateProcess(process);
  }

  @Test
  void withCompletionConditionExpression() {
    // given
    final BpmnModelInstance process =
        process(
            adHocSubProcess -> {
              adHocSubProcess.zeebeCompletionConditionExpression("elements");
              adHocSubProcess.task("A");
            });

    // when/then
    ProcessValidationUtil.validateProcess(process);
  }

  @Test
  void withInvalidCompletionConditionExpression() {
    // given
    final BpmnModelInstance process =
        process(
            adHocSubProcess -> {
              adHocSubProcess.zeebeCompletionConditionExpression("???");
              adHocSubProcess.task("A");
            });

    // when/then
    ProcessValidationUtil.validateProcess(
        process,
        ExpectedValidationResult.expect(ZeebeAdHoc.class, "failed to parse expression '???'"));
  }
}
