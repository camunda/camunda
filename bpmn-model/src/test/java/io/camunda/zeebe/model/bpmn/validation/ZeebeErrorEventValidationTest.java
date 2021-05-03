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
import io.camunda.zeebe.model.bpmn.instance.BoundaryEvent;
import io.camunda.zeebe.model.bpmn.instance.ErrorEventDefinition;
import io.camunda.zeebe.model.bpmn.instance.Process;
import io.camunda.zeebe.model.bpmn.instance.ServiceTask;
import io.camunda.zeebe.model.bpmn.instance.SubProcess;
import org.junit.runners.Parameterized.Parameters;

public class ZeebeErrorEventValidationTest extends AbstractZeebeValidationTest {

  @Parameters(name = "{index}: {1}")
  public static Object[][] parameters() {
    return new Object[][] {
      {
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .serviceTask("task", t -> t.zeebeJobType("type"))
            .boundaryEvent("catch", b -> b.error(""))
            .endEvent()
            .done(),
        singletonList(expect(ErrorEventDefinition.class, "ErrorCode must be present and not empty"))
      },
      {
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .serviceTask("task", t -> t.zeebeJobType("type"))
            .boundaryEvent("catch", b -> b.errorEventDefinition())
            .endEvent()
            .done(),
        singletonList(expect(ErrorEventDefinition.class, "Must reference an error"))
      },
      {
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .serviceTask("task", t -> t.zeebeJobType("type"))
            .boundaryEvent("catch", b -> b.error("ERROR").cancelActivity(false))
            .endEvent()
            .done(),
        singletonList(
            expect(BoundaryEvent.class, "Non-Interrupting event of this type is not allowed"))
      },
      {
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .serviceTask("task", t -> t.zeebeJobType("type"))
            .boundaryEvent("catch-1", b -> b.error("ERROR").endEvent())
            .moveToActivity("task")
            .boundaryEvent("catch-2", b -> b.error("ERROR").endEvent())
            .done(),
        singletonList(
            expect(
                ServiceTask.class,
                "Multiple error event definitions with the same errorCode 'ERROR' are not allowed."))
      },
      {
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .subProcess("sub", s -> s.embeddedSubProcess().startEvent().endEvent())
            .boundaryEvent("catch-1", b -> b.error("ERROR").endEvent())
            .moveToActivity("sub")
            .boundaryEvent("catch-2", b -> b.error("ERROR").endEvent())
            .done(),
        singletonList(
            expect(
                SubProcess.class,
                "Multiple error event definitions with the same errorCode 'ERROR' are not allowed."))
      },
      {
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .subProcess(
                "sub",
                s ->
                    s.embeddedSubProcess()
                        .startEvent()
                        .serviceTask("task", t -> t.zeebeJobType("type"))
                        .boundaryEvent("catch", b -> b.error(""))
                        .endEvent())
            .done(),
        singletonList(expect(ErrorEventDefinition.class, "ErrorCode must be present and not empty"))
      },
      {
        Bpmn.createExecutableProcess("process")
            .eventSubProcess("sub", s -> s.startEvent().error("").endEvent())
            .startEvent()
            .endEvent()
            .done(),
        singletonList(expect(ErrorEventDefinition.class, "ErrorCode must be present and not empty"))
      },
      {
        Bpmn.createExecutableProcess("process")
            .eventSubProcess(
                "sub", s -> s.startEvent().interrupting(false).error("ERROR").endEvent())
            .startEvent()
            .endEvent()
            .done(),
        singletonList(
            expect(SubProcess.class, "Non-Interrupting event of this type is not allowed"))
      },
      {
        Bpmn.createExecutableProcess("process")
            .eventSubProcess(
                "sub-1", s -> s.startEvent().interrupting(true).error("ERROR").endEvent())
            .eventSubProcess(
                "sub-2", s -> s.startEvent().interrupting(true).error("ERROR").endEvent())
            .startEvent()
            .endEvent()
            .done(),
        singletonList(
            expect(
                Process.class,
                "Multiple error event definitions with the same errorCode 'ERROR' are not allowed."))
      },
      {
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .subProcess(
                "sub",
                s ->
                    s.embeddedSubProcess()
                        .eventSubProcess(
                            "sub-1",
                            e -> e.startEvent().interrupting(true).error("ERROR").endEvent())
                        .eventSubProcess(
                            "sub-2",
                            e -> e.startEvent().interrupting(true).error("ERROR").endEvent())
                        .startEvent()
                        .endEvent())
            .endEvent()
            .done(),
        singletonList(
            expect(
                SubProcess.class,
                "Multiple error event definitions with the same errorCode 'ERROR' are not allowed."))
      },
      {
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .endEvent("error", e -> e.error(""))
            .done(),
        singletonList(expect(ErrorEventDefinition.class, "ErrorCode must be present and not empty"))
      },
      {
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .endEvent("error", e -> e.errorEventDefinition())
            .done(),
        singletonList(expect(ErrorEventDefinition.class, "Must reference an error"))
      },
    };
  }
}
