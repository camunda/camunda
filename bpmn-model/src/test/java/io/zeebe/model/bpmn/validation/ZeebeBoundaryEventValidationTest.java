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
import io.zeebe.model.bpmn.instance.SignalEventDefinition;
import java.util.Arrays;
import org.junit.runners.Parameterized.Parameters;

public class ZeebeBoundaryEventValidationTest extends AbstractZeebeValidationTest {

  @Parameters(name = "{index}: {1}")
  public static Object[][] parameters() {
    return new Object[][] {
      {
        Bpmn.createExecutableProcess("process")
            .startEvent("start")
            .serviceTask("task", b -> b.zeebeJobType("type"))
            .boundaryEvent("boundary")
            .cancelActivity(true)
            .signal("signal")
            .endEvent("end")
            .done(),
        Arrays.asList(
            expect(SignalEventDefinition.class, "Event definition of this type is not supported"),
            expect("boundary", "Boundary events must be one of: timer, message, error"))
      },
      {
        Bpmn.createExecutableProcess("process")
            .startEvent("start")
            .serviceTask("task", b -> b.zeebeJobType("type"))
            .boundaryEvent("boundary")
            .cancelActivity(true)
            .endEvent("end")
            .done(),
        singletonList(expect("boundary", "Must have exactly one event definition"))
      },
      {
        Bpmn.createExecutableProcess("process")
            .startEvent("start")
            .serviceTask("task", b -> b.zeebeJobType("type"))
            .boundaryEvent("boundary")
            .cancelActivity(true)
            .timerWithDuration("PT0.5S")
            .timerWithDuration("PT0.5S")
            .endEvent("end")
            .done(),
        singletonList(expect("boundary", "Must have exactly one event definition"))
      },
      {
        Bpmn.createExecutableProcess("process")
            .startEvent("start")
            .serviceTask("task", b -> b.zeebeJobType("type"))
            .boundaryEvent("boundary")
            .cancelActivity(true)
            .timerWithDuration("PT0.5S")
            .moveToActivity("task")
            .endEvent("end")
            .done(),
        singletonList(expect("boundary", "Must have at least one outgoing sequence flow"))
      },
      {
        Bpmn.createExecutableProcess("process")
            .startEvent("start")
            .serviceTask("task1", b -> b.zeebeJobType("type"))
            .boundaryEvent("boundary")
            .cancelActivity(true)
            .timerWithDuration("PT0.5S")
            .moveToActivity("task1")
            .serviceTask("task2", b -> b.zeebeJobType("type"))
            .sequenceFlowId("taskOut")
            .connectTo("boundary")
            .endEvent("end")
            .done(),
        singletonList(expect("boundary", "Cannot have incoming sequence flows"))
      }
    };
  }
}
