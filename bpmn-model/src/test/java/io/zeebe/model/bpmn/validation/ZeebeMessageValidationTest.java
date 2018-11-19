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
package io.zeebe.model.bpmn.validation;

import static io.zeebe.model.bpmn.validation.ExpectedValidationResult.expect;
import static java.util.Collections.singletonList;

import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.instance.EndEvent;
import io.zeebe.model.bpmn.instance.IntermediateThrowEvent;
import io.zeebe.model.bpmn.instance.Message;
import io.zeebe.model.bpmn.instance.ReceiveTask;
import io.zeebe.model.bpmn.instance.StartEvent;
import io.zeebe.model.bpmn.instance.zeebe.ZeebeSubscription;
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
            .message(m -> m.name("foo").zeebeCorrelationKey(""))
            .done(),
        singletonList(
            expect(ZeebeSubscription.class, "zeebe:correlationKey must be present and not empty"))
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
            .message(m -> m.name("foo").zeebeCorrelationKey(""))
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
      // not supported message events
      {
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .message(b -> b.name("foo").zeebeCorrelationKey("correlationKey"))
            .done(),
        singletonList(expect(StartEvent.class, "Must be a none start event"))
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
        singletonList(expect(EndEvent.class, "Must be a none end event"))
      },
      {
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .subProcess("subProcess")
            .embeddedSubProcess()
            .startEvent("subProcessStart")
            .message(b -> b.name("message").zeebeCorrelationKey("correlationKey"))
            .endEvent()
            .subProcessDone()
            .endEvent()
            .done(),
        singletonList(expect("subProcessStart", "Must be a none start event"))
      },
      {
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .receiveTask("task")
            .message(m -> m.name("message").zeebeCorrelationKey("correlationKey"))
            .boundaryEvent("boundary")
            .message(m -> m.name("message").zeebeCorrelationKey("correlationKey"))
            .endEvent()
            .done(),
        singletonList(expect("task", "Cannot reference the same message name as a boundary event"))
      },
    };
  }
}
