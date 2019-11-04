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
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.model.bpmn.builder.ProcessBuilder;
import io.zeebe.model.bpmn.instance.Process;
import io.zeebe.model.bpmn.instance.SignalEventDefinition;
import io.zeebe.model.bpmn.instance.StartEvent;
import io.zeebe.model.bpmn.instance.SubProcess;
import java.util.Arrays;
import java.util.Collections;
import org.junit.runners.Parameterized.Parameters;

public class ZeebeStartEventValidationTest extends AbstractZeebeValidationTest {

  @Parameters(name = "{index}: {1}")
  public static Object[][] parameters() {
    return new Object[][] {
      {
        "no-start-event-sub-process.bpmn",
        singletonList(expect("subProcess", "Must have exactly one start event"))
      },
      {
        Bpmn.createExecutableProcess().startEvent().signal("signal").endEvent().done(),
        singletonList(
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
            expect(SubProcess.class, "Must have exactly one start event"))
      },
      {
        getProcessWithMultipleNoneStartEvents(),
        singletonList(
            expect(
                Process.class,
                "Must be either one none start event or multiple message/timer start events"))
      },
      {
        cycleTimerStartEventSubprocess(false), emptyList(),
      },
      {
        cycleTimerStartEventSubprocess(true),
        Collections.singletonList(
            expect(
                SubProcess.class,
                "Interrupting timer start events in event subprocesses can't have time cycles")),
      }
    };
  }

  private static BpmnModelInstance getProcessWithMultipleNoneStartEvents() {
    final ProcessBuilder process = Bpmn.createExecutableProcess();
    process.startEvent().endEvent();
    return process.startEvent().endEvent().done();
  }

  private static BpmnModelInstance cycleTimerStartEventSubprocess(boolean interrupting) {
    final ProcessBuilder processBuilder = Bpmn.createExecutableProcess();
    processBuilder.startEvent().serviceTask("task", b -> b.zeebeTaskType("type")).endEvent();
    return processBuilder
        .eventSubProcess()
        .startEvent()
        .interrupting(interrupting)
        .timerWithCycle("R/PT60S")
        .endEvent()
        .done();
  }
}
