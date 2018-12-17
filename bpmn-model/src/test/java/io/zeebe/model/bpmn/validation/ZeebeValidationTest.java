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
import io.zeebe.model.bpmn.builder.AbstractCatchEventBuilder;
import io.zeebe.model.bpmn.instance.CompensateEventDefinition;
import io.zeebe.model.bpmn.instance.IntermediateCatchEvent;
import io.zeebe.model.bpmn.instance.SignalEventDefinition;
import io.zeebe.model.bpmn.instance.StartEvent;
import io.zeebe.model.bpmn.instance.SubProcess;
import io.zeebe.model.bpmn.instance.zeebe.ZeebeIoMapping;
import io.zeebe.model.bpmn.instance.zeebe.ZeebeOutputBehavior;
import java.util.Arrays;
import org.junit.runners.Parameterized.Parameters;

public class ZeebeValidationTest extends AbstractZeebeValidationTest {

  @Parameters(name = "{index}: {1}")
  public static Object[][] parameters() {
    return new Object[][] {
      {
        Bpmn.createExecutableProcess("process").done(),
        singletonList(expect("process", "Must have exactly one start event"))
      },
      {
        Bpmn.createExecutableProcess("process").startEvent().userTask("task").endEvent().done(),
        singletonList(expect("task", "Elements of this type are not supported"))
      },
      {
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .endEvent("end")
            .signalEventDefinition("foo")
            .id("eventDefinition")
            .done(),
        Arrays.asList(
            expect("end", "Must be a none end event"),
            expect("eventDefinition", "Event definition of this type is not supported"))
      },
      {
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .intermediateCatchEvent()
            .endEvent()
            .done(),
        singletonList(
            expect(IntermediateCatchEvent.class, "Must have exactly one event definition"))
      },
      {
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .intermediateCatchEvent("catch", AbstractCatchEventBuilder::compensateEventDefinition)
            .endEvent()
            .done(),
        Arrays.asList(
            expect(
                CompensateEventDefinition.class, "Event definition of this type is not supported"),
            expect(IntermediateCatchEvent.class, "Event definition must be one of: message, timer"))
      },
      {
        "no-start-event-sub-process.bpmn",
        singletonList(expect("subProcess", "Must have exactly one start event"))
      },
      {
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .serviceTask("task")
            .zeebeTaskType("foo")
            .zeebeOutputBehavior(ZeebeOutputBehavior.none)
            .zeebeOutput("source", "target")
            .endEvent()
            .done(),
        singletonList(
            expect(
                ZeebeIoMapping.class,
                "Output behavior 'none' cannot be used in combination without zeebe:output elements"))
      },
      {
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .serviceTask("task", b -> b.zeebeTaskType("type"))
            .boundaryEvent("msg1")
            .message(m -> m.name("message").zeebeCorrelationKey("$.id"))
            .endEvent()
            .moveToActivity("task")
            .boundaryEvent("msg2")
            .message(m -> m.name("message").zeebeCorrelationKey("$.orderId"))
            .endEvent()
            .done(),
        singletonList(
            expect(
                "task",
                "Cannot have two message catch boundary events with the same name: message"))
      },
      {
        Bpmn.createExecutableProcess().startEvent().signal("signal").endEvent().done(),
        Arrays.asList(
            expect(StartEvent.class, "Start event must be one of the following types: none, timer"),
            expect(SignalEventDefinition.class, "Event definition of this type is not supported")),
      },
      {
        Bpmn.createExecutableProcess()
            .startEvent()
            .timerWithCycle("R1/PT2H")
            .signal("signal")
            .endEvent()
            .done(),
        Arrays.asList(
            expect(StartEvent.class, "Start event can't have more than one type"),
            expect(SignalEventDefinition.class, "Event definition of this type is not supported")),
      },
      {
        "multiple-timer-start-event-sub-process.bpmn",
        Arrays.asList(
            expect(SubProcess.class, "Start events in subprocesses must be of type none"),
            expect(SubProcess.class, "Start events in subprocesses must be of type none"),
            expect(SubProcess.class, "Must have exactly one start event"))
      },
    };
  }
}
