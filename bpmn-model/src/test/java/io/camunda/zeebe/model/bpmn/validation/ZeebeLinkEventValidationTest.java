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
        process,
        expect("linkEvent", "Link name must be present and not empty."),
        expect(
            "process",
            "Intermediate throw and catch link event definitions must appear in pairs."));
  }

  @Test
  @DisplayName("Intermediate throw and catch link event definitions must appear in pairs")
  void testNotPairsEventLink() {
    // given
    final BpmnModelInstance process = getLinkEventProcess();

    // when/then
    ProcessValidationUtil.assertThatProcessHasViolations(
        process,
        expect(
            "process",
            "Intermediate throw and catch link event definitions must appear in pairs."));
  }

  @Test
  @DisplayName(
      "Intermediate throw and catch link event definitions with the same link name are not allowed")
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
            "Event-based gateway must not have an outgoing sequence flow to other elements than message/timer intermediate catch events."),
        expect(
            Process.class,
            "Intermediate throw and catch link event definitions must appear in pairs."));
  }

  public static BpmnModelInstance getLinkEventProcess() {
    final ProcessBuilder process = Bpmn.createExecutableProcess("process");
    process
        .startEvent()
        .manualTask("manualTask1")
        .intermediateThrowEvent()
        .linkEventDefinition()
        .name("LinkA")
        .linkEventDefinitionDone();
    return process
        .linkCatchEvent()
        .linkEventDefinition()
        .name("LinkB")
        .linkEventDefinitionDone()
        .manualTask("manualTask2")
        .endEvent()
        .done();
  }
}
