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
import io.camunda.zeebe.model.bpmn.builder.zeebe.PublishMessageBuilder;
import io.camunda.zeebe.model.bpmn.instance.Message;
import io.camunda.zeebe.model.bpmn.instance.SendTask;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebePublishMessage;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeTaskDefinition;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

class ZeebeSendTaskValidatorTest {

  @Test
  void noMessageRef() {
    // when
    final BpmnModelInstance process = process(b -> b.zeebeCorrelationKey("corrleationKey"));

    // then
    ProcessValidationUtil.assertThatProcessHasViolations(
        process, expect(SendTask.class, "Must reference a message"));
  }

  @Test
  void emptyMessageName() {
    // when
    final BpmnModelInstance process =
        process(b -> b.name("").zeebeCorrelationKey("corrleationKey"));

    // then
    ProcessValidationUtil.assertThatProcessHasViolations(
        process, expect(Message.class, "Name must be present and not empty"));
  }

  @Test
  void emptyCorrleationKey() {
    // when
    final BpmnModelInstance process = process(b -> b.name("message-name").zeebeCorrelationKey(""));

    // then
    ProcessValidationUtil.assertThatProcessHasViolations(
        process,
        expect(
            ZeebePublishMessage.class, "Attribute 'correlationKey' must be present and not empty"));
  }

  @Test
  void noPublishMessageAndTaskDefinitionExtension() {
    // when
    final BpmnModelInstance process = process(b -> {});

    // then
    ProcessValidationUtil.assertThatProcessHasViolations(
        process,
        ExpectedValidationResult.expect(
            SendTask.class,
            "Must have either one 'zeebe:publishMessage' or one 'zeebe:taskDefinition' extension element"));
  }

  @Test
  void emptyJobType() {
    // when
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .sendTask("task", t -> t.zeebeJobType("jobType"))
            .zeebeJobType("")
            .done();

    // then
    ProcessValidationUtil.assertThatProcessHasViolations(
        process,
        expect(ZeebeTaskDefinition.class, "Attribute 'type' must be present and not empty"));
  }

  @Test
  void bothPublishMessageAndTaskDefinitionExtension() {
    // when
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .sendTask("task", t -> t.zeebeJobType("jobType"))
            .message(b -> b.name("message-name").zeebeCorrelationKey("corrleation-key"))
            .done();

    // then
    ProcessValidationUtil.assertThatProcessHasViolations(
        process,
        ExpectedValidationResult.expect(
            SendTask.class,
            "Must have either one 'zeebe:publishMessage' or one 'zeebe:taskDefinition' extension element"));
  }

  private BpmnModelInstance process(final Consumer<PublishMessageBuilder> consumer) {
    return Bpmn.createExecutableProcess("process")
        .startEvent()
        .sendTask("task")
        .message(consumer)
        .done();
  }
}
