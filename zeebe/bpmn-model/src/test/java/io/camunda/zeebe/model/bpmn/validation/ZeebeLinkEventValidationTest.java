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
import io.camunda.zeebe.model.bpmn.builder.ProcessBuilder;
import io.camunda.zeebe.model.bpmn.instance.EventBasedGateway;
import io.camunda.zeebe.model.bpmn.instance.Process;
import org.camunda.bpm.model.xml.impl.util.ReflectUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class ZeebeLinkEventValidationTest {

  @Test
  @DisplayName("A link throw and a catch event appear in pairs are allowed")
  void testValidEventLink() {
    // given
    final BpmnModelInstance process =
        Bpmn.readModelFromStream(
            ReflectUtil.getResourceAsStream(
                "io/camunda/zeebe/model/bpmn/validation/LinkEventTest.testValidEventLink.bpmn"));

    // when/then
    ProcessValidationUtil.assertThatProcessIsValid(process);
  }

  @Test
  @DisplayName("Two link throw and one catch event appear in pairs are allowed")
  void testEventLinkMultipleSources() {
    // given
    final BpmnModelInstance process =
        Bpmn.readModelFromStream(
            ReflectUtil.getResourceAsStream(
                "io/camunda/zeebe/model/bpmn/validation/LinkEventTest.testEventLinkMultipleSources.bpmn"));

    // when/then
    ProcessValidationUtil.assertThatProcessIsValid(process);
  }

  @Test
  @DisplayName("Link Name must be present and not empty and must appear in pairs")
  void testInvalidEventLink() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .intermediateThrowEvent()
            .linkEventDefinition("linkEvent")
            .name("")
            .linkEventDefinitionDone()
            .done();

    // when/then
    ProcessValidationUtil.assertThatProcessHasViolations(
        process, expect("linkEvent", "Link name must be present and not empty."));
  }

  @Test
  @DisplayName(
      "If there is a source Link, there MUST be a matching target Link (they have the same name)")
  void testNotPairsEventLink() {
    // given
    final BpmnModelInstance process = getLinkEventProcess();

    // when/then
    ProcessValidationUtil.assertThatProcessHasViolations(
        process,
        expect(
            "process",
            "Can't find an catch link event for the throw link event with the name 'LinkA'."));
  }

  @Test
  @DisplayName("There MUST NOT be multiple target Links for a single source Link.")
  void testInvalidEventLinkMultipleTarget() {
    // given
    final BpmnModelInstance process =
        Bpmn.readModelFromStream(
            ReflectUtil.getResourceAsStream(
                "io/camunda/zeebe/model/bpmn/validation/LinkEventTest.testInvalidEventLinkMultipleTarget.bpmn"));

    // when/then
    ProcessValidationUtil.assertThatProcessHasViolations(
        process,
        expect(
            Process.class,
            "Multiple intermediate catch link event definitions with the same name 'LinkA' are not allowed."));
  }

  @Test
  @DisplayName("Intermediate catch link event after event-based gateway is not allowed")
  void testCatchLinkEventAfterEventBasedGatewayNotAllowed() {
    // given
    final BpmnModelInstance process =
        Bpmn.readModelFromStream(
            ReflectUtil.getResourceAsStream(
                "io/camunda/zeebe/model/bpmn/validation/LinkEventTest.testCatchLinkEventAfterEventBasedGatewayNotAllowed.bpmn"));

    // when/then
    ProcessValidationUtil.assertThatProcessHasViolations(
        process,
        expect(
            EventBasedGateway.class,
            "Event-based gateway must have at least 2 outgoing sequence flows."),
        expect(
            EventBasedGateway.class,
            "Event-based gateway must not have an outgoing sequence flow to other elements than message/timer/signal/conditional intermediate catch events."));
  }

  @Test
  @DisplayName("If there is only target intermediate catch link event, it's allowed")
  void testOnlyTargetEventLink() {
    // given
    final BpmnModelInstance process = getOnlyTargetLinkEventProcess();

    // when/then
    ProcessValidationUtil.assertThatProcessIsValid(process);
  }

  @Test
  @DisplayName("If there are only target intermediate catch link events, these are allowed")
  void testOnlyManyTargetEventLink() {
    // given
    final BpmnModelInstance process = getOnlyManyTargetLinkEventProcess();

    // when/then
    ProcessValidationUtil.assertThatProcessIsValid(process);
  }

  @Test
  @DisplayName(
      "Link intermediate events can also be used as generic “Go To” objects within the Process level.")
  void testGotoEventLink() {
    // given
    final BpmnModelInstance process = getGoToLinkEventProcess();

    // when/then
    ProcessValidationUtil.assertThatProcessIsValid(process);
  }

  @Test
  @DisplayName("A test for many link intermediate events")
  void testManyEventLink() {
    // given
    final BpmnModelInstance process = getManyLinkEventProcess();

    // when/then
    ProcessValidationUtil.assertThatProcessHasViolations(
        process,
        expect(
            Process.class,
            "Multiple intermediate catch link event definitions with the same name 'LinkA' are not allowed."),
        expect(
            Process.class,
            "Can't find an catch link event for the throw link event with the name 'LinkB'."));
  }

  public static BpmnModelInstance getLinkEventProcess() {
    final ProcessBuilder process = Bpmn.createExecutableProcess("process");
    process.startEvent().manualTask("manualTask1").intermediateThrowEvent().link("LinkA");
    return process.linkCatchEvent().link("LinkB").manualTask("manualTask2").endEvent().done();
  }

  public static BpmnModelInstance getOnlyTargetLinkEventProcess() {
    final ProcessBuilder process = Bpmn.createExecutableProcess("process");
    process.startEvent().endEvent();
    return process.linkCatchEvent().link("LinkB").endEvent().done();
  }

  public static BpmnModelInstance getOnlyManyTargetLinkEventProcess() {
    final ProcessBuilder process = Bpmn.createExecutableProcess("process");
    process.startEvent().endEvent();
    process.linkCatchEvent().link("LinkB").endEvent();

    return process.linkCatchEvent().link("LinkC").endEvent().done();
  }

  public static BpmnModelInstance getManyLinkEventProcess() {
    final ProcessBuilder process = Bpmn.createExecutableProcess("process");
    process
        .startEvent()
        .manualTask("manualTask1")
        .intermediateThrowEvent("linkThrow1")
        .link("LinkA");
    process
        .linkCatchEvent()
        .link("LinkA")
        .manualTask("manualTask2")
        .intermediateThrowEvent("linkThrow2")
        .link("LinkB");
    return process.linkCatchEvent().link("LinkA").manualTask("manualTask3").endEvent().done();
  }

  public static BpmnModelInstance getGoToLinkEventProcess() {
    final ProcessBuilder process = Bpmn.createExecutableProcess("process");
    process
        .startEvent()
        .exclusiveGateway("exclusive1")
        .manualTask("manualTask1")
        .manualTask("manualTask2")
        .exclusiveGateway("exclusive2")
        .defaultFlow()
        .manualTask("manualTask3")
        .endEvent()
        .moveToLastExclusiveGateway()
        .conditionExpression("condition_link")
        .intermediateThrowEvent("linkThrow")
        .link("LinkA");
    return process.linkCatchEvent().link("LinkA").connectTo("exclusive1").done();
  }
}
