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
import io.camunda.zeebe.model.bpmn.instance.Process;
import io.camunda.zeebe.model.bpmn.instance.ServiceTask;
import io.camunda.zeebe.model.bpmn.instance.Signal;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeTaskDefinition;
import java.util.Arrays;
import org.junit.runners.Parameterized.Parameters;

public class ZeebeSignalValidationTest extends AbstractZeebeValidationTest {

  @Parameters(name = "{index}: {1}")
  public static Object[][] parameters() {
    return new Object[][] {
      {
        Bpmn.createExecutableProcess("process").startEvent().signal("").done(),
        Arrays.asList(expect(Signal.class, "Name must be present and not empty"))
      },
      {
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .signal(s -> s.nameExpression("signal_val"))
            .done(),
        valid()
      },
      {Bpmn.createExecutableProcess("process").startEvent().signal("signalName").done(), valid()},
      {
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .signal(s -> s.id("signalId").name("signalName"))
            .endEvent()
            .done(),
        valid()
      },
      {
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .endEvent()
            .addExtensionElement(ZeebeTaskDefinition.class, e -> e.setType("type"))
            .signalEventDefinition()
            .done(),
        valid()
      },
      {
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .intermediateThrowEvent()
            .addExtensionElement(ZeebeTaskDefinition.class, e -> e.setType("type"))
            .signalEventDefinition()
            .throwEventDefinitionDone()
            .endEvent()
            .done(),
        valid()
      },
      {
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .intermediateCatchEvent("foo")
            .signal("")
            .done(),
        Arrays.asList(expect(Signal.class, "Name must be present and not empty"))
      },
      {
        getProcessWithMultipleStartEventsWithSameSignal(),
        singletonList(
            expect(
                Process.class,
                "Multiple signal event definitions with the same name 'signalName' are not allowed."))
      },
      {getProcessWithMultipleSignalStartEvents(), valid()},
      {
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .intermediateCatchEvent("foo")
            .signalEventDefinition()
            .id("signal")
            .done(),
        singletonList(expect("signal", "Must reference a signal"))
      },
      {
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .subProcess("subProcess")
            .embeddedSubProcess()
            .startEvent("subProcessStart")
            .signal(s -> s.name("signal"))
            .endEvent()
            .subProcessDone()
            .endEvent()
            .done(),
        singletonList(expect("subProcess", "Start events in subprocesses must be of type none"))
      },
      {
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .serviceTask("task", t -> t.zeebeJobType("test"))
            .boundaryEvent("boundary-1", b -> b.signal(s -> s.name(null)))
            .endEvent()
            .moveToActivity("task")
            .boundaryEvent("boundary-2", b -> b.signal(s -> s.name(null)))
            .endEvent()
            .done(),
        Arrays.asList(
            expect(Signal.class, "Name must be present and not empty"),
            expect(Signal.class, "Name must be present and not empty"))
      },
      {
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .serviceTask("task", t -> t.zeebeJobType("test"))
            .boundaryEvent("boundary-1", b -> b.signal(s -> s.name("signalName")))
            .endEvent()
            .moveToActivity("task")
            .boundaryEvent("boundary-2", b -> b.signal(s -> s.name(null)))
            .endEvent()
            .done(),
        singletonList(expect(Signal.class, "Name must be present and not empty"))
      },
      {
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .serviceTask("task", t -> t.zeebeJobType("test"))
            .boundaryEvent("boundary-1", b -> b.signal(s -> s.name("signalName")))
            .endEvent()
            .moveToActivity("task")
            .boundaryEvent("boundary-2", b -> b.signal(s -> s.name("signalName")))
            .endEvent()
            .done(),
        singletonList(
            expect(
                ServiceTask.class,
                "Multiple signal event definitions with the same name 'signalName' are not allowed."))
      },
      {
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .serviceTask("task", t -> t.zeebeJobType("test"))
            .boundaryEvent("boundary-1", b -> b.signal(s -> s.name("signalName1")))
            .endEvent()
            .moveToActivity("task")
            .boundaryEvent("boundary-2", b -> b.signal(s -> s.name("signalName2")))
            .endEvent()
            .done(),
        valid()
      },
      {
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .signal(m -> m.id("start-signal").name("signalName"))
            .serviceTask("task", t -> t.zeebeJobType("test"))
            .boundaryEvent(
                "boundary-1", b -> b.signal(s -> s.id("boundary-signal").name("signalName")))
            .endEvent()
            .done(),
        valid()
      },
      {
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .signal(s -> s.id("start-signal").name("signalName"))
            .intermediateCatchEvent("foo")
            .signal(s -> s.id("foo-signal").name("signalName"))
            .done(),
        valid()
      },
      {getEventSubProcessWithEmbeddedSubProcessWithBoundarySignalEvent(), valid()}
    };
  }

  private static BpmnModelInstance getProcessWithMultipleStartEventsWithSameSignal() {
    final ProcessBuilder process = Bpmn.createExecutableProcess();
    final String signalName = "signalName";
    process.startEvent("start1").signal(s -> s.id("start-signal").name(signalName)).endEvent();
    process.startEvent("start2").signal(signalName).endEvent();
    return process.done();
  }

  private static BpmnModelInstance getProcessWithMultipleSignalStartEvents() {
    final ProcessBuilder process = Bpmn.createExecutableProcess();
    process.startEvent().signal("s1").endEvent();
    process.startEvent().signal("s2").endEvent();
    return process.startEvent().signal("s3").endEvent().done();
  }

  private static BpmnModelInstance
      getEventSubProcessWithEmbeddedSubProcessWithBoundarySignalEvent() {
    final ProcessBuilder builder = Bpmn.createExecutableProcess("process");
    builder
        .eventSubProcess("event_sub_proc")
        .startEvent(
            "event_sub_start",
            a -> a.signal(s -> s.id("event_sub_start_signal").name("signalName")))
        .subProcess(
            "embedded",
            sub ->
                sub.boundaryEvent(
                    "boundary-msg", s -> s.signal("signalName").endEvent("boundary-end")))
        .embeddedSubProcess()
        .startEvent("embedded_sub_start")
        .endEvent("embedded_sub_end")
        .moveToNode("embedded")
        .endEvent("event_sub_end");
    return builder.startEvent("start").endEvent("end").done();
  }
}
