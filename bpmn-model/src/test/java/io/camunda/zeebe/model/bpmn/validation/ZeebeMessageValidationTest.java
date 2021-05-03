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
import static java.util.Collections.singletonList;

import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.builder.ProcessBuilder;
import io.camunda.zeebe.model.bpmn.instance.EndEvent;
import io.camunda.zeebe.model.bpmn.instance.IntermediateThrowEvent;
import io.camunda.zeebe.model.bpmn.instance.Message;
import io.camunda.zeebe.model.bpmn.instance.MessageEventDefinition;
import io.camunda.zeebe.model.bpmn.instance.ReceiveTask;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeSubscription;
import java.util.Arrays;
import org.junit.runners.Parameterized.Parameters;

public class ZeebeMessageValidationTest extends AbstractZeebeValidationTest {

  @Parameters(name = "{index}: {1}")
  public static Object[][] parameters() {
    return new Object[][] {
      // validate message catch events
      {
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .intermediateCatchEvent("foo")
            .message("")
            .done(),
        Arrays.asList(
            expect(Message.class, "Name must be present and not empty"),
            expect(Message.class, "Must have exactly one zeebe:subscription extension element"))
      },
      {
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .intermediateCatchEvent("foo")
            .message("foo")
            .done(),
        singletonList(
            expect(Message.class, "Must have exactly one zeebe:subscription extension element"))
      },
      {
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .intermediateCatchEvent("foo")
            .message(m -> m.name("foo").zeebeCorrelationKeyExpression(""))
            .done(),
        singletonList(
            expect(ZeebeSubscription.class, "zeebe:correlationKey must be present and not empty"))
      },
      {
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .intermediateCatchEvent("foo")
            .messageEventDefinition()
            .done(),
        singletonList(expect(MessageEventDefinition.class, "Must reference a message"))
      },
      // validate receive tasks
      {
        Bpmn.createExecutableProcess("process").startEvent().receiveTask("foo").done(),
        singletonList(expect(ReceiveTask.class, "Must reference a message"))
      },
      {
        Bpmn.createExecutableProcess("process").startEvent().receiveTask("foo").message("").done(),
        Arrays.asList(
            expect(Message.class, "Name must be present and not empty"),
            expect(Message.class, "Must have exactly one zeebe:subscription extension element"))
      },
      {
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .receiveTask("foo")
            .message(m -> m.name("foo").zeebeCorrelationKeyExpression(""))
            .done(),
        singletonList(
            expect(ZeebeSubscription.class, "zeebe:correlationKey must be present and not empty"))
      },
      {
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .receiveTask("foo")
            .message(m -> m.name("foo"))
            .done(),
        singletonList(
            expect(Message.class, "Must have exactly one zeebe:subscription extension element"))
      },
      {
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .intermediateThrowEvent()
            .message("foo")
            .done(),
        singletonList(
            expect(IntermediateThrowEvent.class, "Elements of this type are not supported"))
      },
      {
        Bpmn.createExecutableProcess("process").startEvent().endEvent().message("foo").done(),
        singletonList(expect(EndEvent.class, "End events must be one of: none or error"))
      },
      {
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .subProcess("subProcess")
            .embeddedSubProcess()
            .startEvent("subProcessStart")
            .message(b -> b.name("message").zeebeCorrelationKeyExpression("correlationKey"))
            .endEvent()
            .subProcessDone()
            .endEvent()
            .done(),
        Arrays.asList(expect("subProcess", "Start events in subprocesses must be of type none"))
      },
      {
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .receiveTask("task")
            .message(m -> m.name("message").zeebeCorrelationKeyExpression("correlationKey"))
            .boundaryEvent("boundary")
            .message(m -> m.name("message").zeebeCorrelationKeyExpression("correlationKey"))
            .endEvent()
            .done(),
        singletonList(expect("task", "Cannot reference the same message name as a boundary event"))
      },
      {
        getProcessWithMultipleStartEventsWithSameMessage(),
        singletonList(
            expect("start-message", "A message cannot be referred by more than one start event"))
      },
      {
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .serviceTask("task", t -> t.zeebeJobType("test"))
            .boundaryEvent(
                "boundary-1",
                b -> b.message(m -> m.name(null).zeebeCorrelationKeyExpression("correlationKey")))
            .endEvent()
            .moveToActivity("task")
            .boundaryEvent(
                "boundary-2",
                b -> b.message(m -> m.name(null).zeebeCorrelationKeyExpression("correlationKey")))
            .endEvent()
            .done(),
        Arrays.asList(
            expect(Message.class, "Name must be present and not empty"),
            expect(Message.class, "Name must be present and not empty"))
      },
      {
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .receiveTask("task")
            .message(m -> m.name("message").zeebeCorrelationKeyExpression("correlationKey"))
            .boundaryEvent("boundary")
            .message(m -> m.name(null).zeebeCorrelationKeyExpression("correlationKey"))
            .endEvent()
            .done(),
        singletonList(expect(Message.class, "Name must be present and not empty"))
      },
      {
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .message(m -> m.name("message"))
            .endEvent()
            .done(),
        valid()
      },
      {
        getMessageEventSubProcessWithNoCorrelationKey(),
        singletonList(
            expect(Message.class, "Must have exactly one zeebe:subscription extension element"))
      },
    };
  }

  private static BpmnModelInstance getProcessWithMultipleStartEventsWithSameMessage() {
    final ProcessBuilder process = Bpmn.createExecutableProcess();
    final String messageName = "messageName";
    process.startEvent("start1").message(m -> m.id("start-message").name(messageName)).endEvent();
    process.startEvent("start2").message(messageName).endEvent();
    return process.done();
  }

  private static BpmnModelInstance getMessageEventSubProcessWithNoCorrelationKey() {
    final ProcessBuilder builder = Bpmn.createExecutableProcess("process");
    builder
        .eventSubProcess("subprocess")
        .startEvent("substart")
        .message(m -> m.name("message"))
        .endEvent("subend")
        .done();

    return builder.startEvent("start").endEvent("end").done();
  }
}
