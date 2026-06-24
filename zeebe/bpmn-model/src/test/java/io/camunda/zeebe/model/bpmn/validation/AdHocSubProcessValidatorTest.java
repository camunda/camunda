/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.zeebe.model.bpmn.validation;

import static io.camunda.zeebe.model.bpmn.validation.ExpectedValidationResult.expect;

import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.builder.AdHocSubProcessBuilder;
import io.camunda.zeebe.model.bpmn.instance.AdHocSubProcess;
import io.camunda.zeebe.model.bpmn.instance.StartEvent;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeTaskDefinition;
import java.util.function.Consumer;
import org.camunda.bpm.model.xml.instance.ModelElementInstance;
import org.junit.jupiter.api.Test;

class AdHocSubProcessValidatorTest {

  private static final String AD_HOC_SUB_PROCESS_ELEMENT_ID = "ad-hoc";

  @Test
  void withOneActivity() {
    // given
    final BpmnModelInstance process = process(adHocSubProcess -> adHocSubProcess.task("A"));

    // when/then
    ProcessValidationUtil.assertThatProcessIsValid(process);
  }

  @Test
  void withMultipleActivities() {
    // given
    final BpmnModelInstance process =
        process(
            adHocSubProcess -> {
              adHocSubProcess.task("A");
              adHocSubProcess.task("B");
            });

    // when/then
    ProcessValidationUtil.assertThatProcessIsValid(process);
  }

  @Test
  void withNoActivity() {
    // given
    final BpmnModelInstance process = process(adHocSubProcess -> {});

    // when/then
    ProcessValidationUtil.assertThatProcessHasViolations(
        process, expect(AdHocSubProcess.class, "Must have at least one activity."));
  }

  @Test
  void withStartEvent() {
    // given
    final BpmnModelInstance process = process(adHocSubProcess -> {});

    final ModelElementInstance adHocSubProcess =
        process.getModelElementById(AD_HOC_SUB_PROCESS_ELEMENT_ID);
    adHocSubProcess.addChildElement(process.newInstance(StartEvent.class));

    // when/then
    ProcessValidationUtil.assertThatProcessHasViolations(
        process, expect(AdHocSubProcess.class, "Must not contain a start event"));
  }

  @Test
  void withEndEvent() {
    // given
    final BpmnModelInstance process =
        process(adHocSubProcess -> adHocSubProcess.endEvent("invalid"));

    // when/then
    ProcessValidationUtil.assertThatProcessHasViolations(
        process, expect(AdHocSubProcess.class, "Must not contain an end event"));
  }

  @Test
  void withIntermediateCatchEventAndOutgoingSequenceFlow() {
    // given
    final BpmnModelInstance process =
        process(
            adHocSubProcess -> adHocSubProcess.intermediateCatchEvent().signal("signal").task("A"));

    // when/then
    ProcessValidationUtil.assertThatProcessIsValid(process);
  }

  @Test
  void withIntermediateCatchEventAndNoOutgoingSequenceFlow() {
    // given
    final BpmnModelInstance process =
        process(adHocSubProcess -> adHocSubProcess.intermediateCatchEvent().signal("signal"));

    // when/then
    ProcessValidationUtil.assertThatProcessIsValid(process);
  }

  @Test
  void withIntermediateThrowEventAndOutgoingSequenceFlow() {
    // given
    final BpmnModelInstance process =
        process(
            adHocSubProcess -> adHocSubProcess.intermediateThrowEvent().signal("signal").task("A"));

    // when/then
    ProcessValidationUtil.assertThatProcessIsValid(process);
  }

  @Test
  void withIntermediateThrowEventAndNoOutgoingSequenceFlow() {
    // given
    final BpmnModelInstance process =
        process(adHocSubProcess -> adHocSubProcess.intermediateThrowEvent().signal("signal"));

    // when/then
    ProcessValidationUtil.assertThatProcessIsValid(process);
  }

  @Test
  void withBpmnImplementationProperties() {
    // given
    final BpmnModelInstance process =
        process(
            adHocSubProcess ->
                adHocSubProcess
                    .completionCondition("=true")
                    .cancelRemainingInstances(false)
                    .zeebeActiveElementsCollectionExpression("=activeElements")
                    .task("A"));

    // when/then
    ProcessValidationUtil.assertThatProcessIsValid(process);
  }

  @Test
  void withTaskDefinitionProperties() {
    // given
    final BpmnModelInstance process =
        process(
            adHocSubProcess ->
                adHocSubProcess
                    .zeebeJobType("jobType")
                    .zeebeJobRetries("1")
                    .zeebeTaskHeader("header", "value")
                    .task("A"));

    // when/then
    ProcessValidationUtil.assertThatProcessIsValid(process);
  }

  @Test
  void withTaskDefinitionPropertiesAndCancelRemainingInstancesFalse() {
    // given
    final BpmnModelInstance process =
        process(
            adHocSubProcess ->
                adHocSubProcess.zeebeJobType("jobType").cancelRemainingInstances(false).task("A"));

    // when/then
    ProcessValidationUtil.assertThatProcessHasViolations(
        process,
        expect(
            AdHocSubProcess.class,
            "Must not define cancelRemainingInstances in combination with zeebe:taskDefinition."));
  }

  @Test
  void withTaskDefinitionPropertiesAndCompletionCondition() {
    // given
    final BpmnModelInstance process =
        process(
            adHocSubProcess ->
                adHocSubProcess.zeebeJobType("jobType").completionCondition("=true").task("A"));

    // when/then
    ProcessValidationUtil.assertThatProcessHasViolations(
        process,
        expect(
            AdHocSubProcess.class,
            "Must not define completionCondition in combination with zeebe:taskDefinition."));
  }

  @Test
  void withTaskDefinitionPropertiesAndActiveElementsCollection() {
    // given
    final BpmnModelInstance process =
        process(
            adHocSubProcess ->
                adHocSubProcess
                    .zeebeJobType("jobType")
                    .zeebeActiveElementsCollectionExpression("=activeElements")
                    .task("A"));

    // when/then
    ProcessValidationUtil.assertThatProcessHasViolations(
        process,
        expect(
            AdHocSubProcess.class,
            "Must not define activeElementsCollection in combination with zeebe:taskDefinition."));
  }

  @Test
  void withTaskDefinitionPropertiesAndEmptyJobType() {
    // given
    final BpmnModelInstance process =
        process(adHocSubProcess -> adHocSubProcess.zeebeJobType("").task("A"));

    // when/then
    ProcessValidationUtil.assertThatProcessHasViolations(
        process,
        expect(ZeebeTaskDefinition.class, "Attribute 'type' must be present and not empty"));
  }

  private BpmnModelInstance process(final Consumer<AdHocSubProcessBuilder> modifier) {
    return Bpmn.createExecutableProcess("process")
        .startEvent()
        .adHocSubProcess(AD_HOC_SUB_PROCESS_ELEMENT_ID, modifier)
        .endEvent()
        .done();
  }

  @Test
  void withMissingOutputElement() {
    // when
    final BpmnModelInstance outputElementDoesntExist =
        process(adHocSubProcess -> adHocSubProcess.zeebeOutputCollection("collection").task("A"));

    // then
    ProcessValidationUtil.assertThatProcessHasViolations(
        outputElementDoesntExist,
        expect(
            AdHocSubProcess.class,
            "OutputElement and OutputCollection must both be set, or neither of them set. outputElement:null and outputCollection:collection."));
  }

  @Test
  void withMissingOutputCollection() {
    // when
    final BpmnModelInstance outputCollectionDoesntExist =
        process(
            adHocSubProcess -> adHocSubProcess.zeebeOutputElementExpression("element").task("A"));

    // then
    ProcessValidationUtil.assertThatProcessHasViolations(
        outputCollectionDoesntExist,
        expect(
            AdHocSubProcess.class,
            "OutputElement and OutputCollection must both be set, or neither of them set. outputElement:=element and outputCollection:null."));
  }

  @Test
  void withOutputElementAndCollection() {
    // when
    final BpmnModelInstance bothOutputElementOrOutputCollectionExist =
        process(
            adHocSubProcess ->
                adHocSubProcess
                    .zeebeOutputElementExpression("element")
                    .zeebeOutputCollection("collection")
                    .task("A"));

    // then
    ProcessValidationUtil.assertThatProcessIsValid(bothOutputElementOrOutputCollectionExist);
  }

  @Test
  void withNeitherOutputElementOrCollection() {
    // when
    final BpmnModelInstance neitherOutputElementOrOutputCollectionExist =
        process(adHocSubProcess -> adHocSubProcess.task("A"));

    // then
    ProcessValidationUtil.assertThatProcessIsValid(neitherOutputElementOrOutputCollectionExist);
  }
}
