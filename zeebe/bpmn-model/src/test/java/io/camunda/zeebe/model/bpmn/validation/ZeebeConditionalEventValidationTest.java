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
import io.camunda.zeebe.model.bpmn.instance.Condition;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeConditionalFilter;
import org.junit.runners.Parameterized.Parameters;

public class ZeebeConditionalEventValidationTest extends AbstractZeebeValidationTest {

  @Parameters(name = "{index}: {1}")
  public static Object[][] parameters() {
    return new Object[][] {
      // -----------------------------------------------------------------------
      // Root level conditional start event tests
      // -----------------------------------------------------------------------
      {
        Bpmn.createExecutableProcess("process").startEvent().condition(c -> c.condition("")).done(),
        singletonList(
            expect(Condition.class, "Attribute 'condition' must be present and not empty"))
      },
      {
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .condition(c -> c.condition("x > 1"))
            .done(),
        valid()
      },
      {
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .condition(c -> c.condition("x > 1").zeebeVariableEvents(""))
            .done(),
        valid()
      },
      {
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .condition(c -> c.condition("x > 1").zeebeVariableEvents("create"))
            .done(),
        valid()
      },
      {
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .condition(c -> c.condition("x > 1").zeebeVariableEvents("update"))
            .done(),
        valid()
      },
      {
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .condition(c -> c.condition("x > 1").zeebeVariableEvents("create, update"))
            .done(),
        valid()
      },
      {
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .condition(c -> c.condition("x > 1").zeebeVariableEvents("create update"))
            .done(),
        singletonList(
            expect(
                ZeebeConditionalFilter.class,
                "Variable event 'create update' is not valid. Must be one of: create, update or a comma-separated list of both."))
      },
      {
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .condition(c -> c.condition("x > 1").zeebeVariableEvents("delete"))
            .done(),
        singletonList(
            expect(
                ZeebeConditionalFilter.class,
                "Variable event 'delete' is not valid. Must be one of: create, update or a comma-separated list of both."))
      },

      // -----------------------------------------------------------------------
      // Conditional boundary catch event tests
      // -----------------------------------------------------------------------
      {
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .serviceTask("task")
            .zeebeJobType("task")
            .boundaryEvent()
            .condition(c -> c.condition(""))
            .endEvent()
            .moveToActivity("task")
            .endEvent()
            .done(),
        singletonList(
            expect(Condition.class, "Attribute 'condition' must be present and not empty"))
      },
      {
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .serviceTask("task")
            .zeebeJobType("task")
            .boundaryEvent()
            .condition(c -> c.condition("x > 1"))
            .endEvent()
            .moveToActivity("task")
            .endEvent()
            .done(),
        valid()
      },
      {
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .serviceTask("task")
            .zeebeJobType("task")
            .boundaryEvent()
            .condition(c -> c.condition("x > 1").zeebeVariableEvents(""))
            .endEvent()
            .moveToActivity("task")
            .endEvent()
            .done(),
        valid()
      },
      {
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .serviceTask("task")
            .zeebeJobType("task")
            .boundaryEvent()
            .condition(c -> c.condition("x > 1").zeebeVariableEvents("create"))
            .endEvent()
            .moveToActivity("task")
            .endEvent()
            .done(),
        valid()
      },
      {
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .serviceTask("task")
            .zeebeJobType("task")
            .boundaryEvent()
            .condition(c -> c.condition("x > 1").zeebeVariableEvents("update"))
            .endEvent()
            .moveToActivity("task")
            .endEvent()
            .done(),
        valid()
      },
      {
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .serviceTask("task")
            .zeebeJobType("task")
            .boundaryEvent()
            .condition(c -> c.condition("x > 1").zeebeVariableEvents("create, update"))
            .endEvent()
            .moveToActivity("task")
            .endEvent()
            .done(),
        valid()
      },
      {
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .serviceTask("task")
            .zeebeJobType("task")
            .boundaryEvent()
            .condition(c -> c.condition("x > 1").zeebeVariableEvents("create update"))
            .endEvent()
            .moveToActivity("task")
            .endEvent()
            .done(),
        singletonList(
            expect(
                ZeebeConditionalFilter.class,
                "Variable event 'create update' is not valid. Must be one of: create, update or a comma-separated list of both."))
      },
      {
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .serviceTask("task")
            .zeebeJobType("task")
            .boundaryEvent()
            .condition(c -> c.condition("x > 1").zeebeVariableEvents("delete"))
            .endEvent()
            .moveToActivity("task")
            .endEvent()
            .done(),
        singletonList(
            expect(
                ZeebeConditionalFilter.class,
                "Variable event 'delete' is not valid. Must be one of: create, update or a comma-separated list of both."))
      },

      // -----------------------------------------------------------------------
      // Conditional intermediate catch event tests
      // -----------------------------------------------------------------------
      {
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .intermediateCatchEvent()
            .condition(c -> c.condition(""))
            .endEvent()
            .done(),
        singletonList(
            expect(Condition.class, "Attribute 'condition' must be present and not empty"))
      },
      {
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .intermediateCatchEvent()
            .condition(c -> c.condition("x > 1"))
            .endEvent()
            .done(),
        valid()
      },
      {
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .intermediateCatchEvent()
            .condition(c -> c.condition("x > 1").zeebeVariableEvents(""))
            .endEvent()
            .done(),
        valid()
      },
      {
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .intermediateCatchEvent()
            .condition(c -> c.condition("x > 1").zeebeVariableEvents("create"))
            .endEvent()
            .done(),
        valid()
      },
      {
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .intermediateCatchEvent()
            .condition(c -> c.condition("x > 1").zeebeVariableEvents("update"))
            .endEvent()
            .done(),
        valid()
      },
      {
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .intermediateCatchEvent()
            .condition(c -> c.condition("x > 1").zeebeVariableEvents("create, update"))
            .endEvent()
            .done(),
        valid()
      },
      {
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .intermediateCatchEvent()
            .condition(c -> c.condition("x > 1").zeebeVariableEvents("create update"))
            .endEvent()
            .done(),
        singletonList(
            expect(
                ZeebeConditionalFilter.class,
                "Variable event 'create update' is not valid. Must be one of: create, update or a comma-separated list of both."))
      },
      {
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .intermediateCatchEvent()
            .condition(c -> c.condition("x > 1").zeebeVariableEvents("delete"))
            .endEvent()
            .done(),
        singletonList(
            expect(
                ZeebeConditionalFilter.class,
                "Variable event 'delete' is not valid. Must be one of: create, update or a comma-separated list of both."))
      },

      // -----------------------------------------------------------------------
      // Conditional event subprocess start event tests
      // -----------------------------------------------------------------------
      {
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .endEvent()
            .moveToProcess("process")
            .eventSubProcess()
            .startEvent()
            .condition(c -> c.condition(""))
            .endEvent()
            .subProcessDone()
            .done(),
        singletonList(
            expect(Condition.class, "Attribute 'condition' must be present and not empty"))
      },
      {
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .endEvent()
            .moveToProcess("process")
            .eventSubProcess()
            .startEvent()
            .condition(c -> c.condition("x > 1"))
            .endEvent()
            .subProcessDone()
            .done(),
        valid()
      },
      {
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .endEvent()
            .moveToProcess("process")
            .eventSubProcess()
            .startEvent()
            .condition(c -> c.condition("x > 1").zeebeVariableEvents(""))
            .endEvent()
            .subProcessDone()
            .done(),
        valid()
      },
      {
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .endEvent()
            .moveToProcess("process")
            .eventSubProcess()
            .startEvent()
            .condition(c -> c.condition("x > 1").zeebeVariableEvents("create"))
            .endEvent()
            .subProcessDone()
            .done(),
        valid()
      },
      {
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .endEvent()
            .moveToProcess("process")
            .eventSubProcess()
            .startEvent()
            .condition(c -> c.condition("x > 1").zeebeVariableEvents("update"))
            .endEvent()
            .subProcessDone()
            .done(),
        valid()
      },
      {
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .endEvent()
            .moveToProcess("process")
            .eventSubProcess()
            .startEvent()
            .condition(c -> c.condition("x > 1").zeebeVariableEvents("create, update"))
            .endEvent()
            .subProcessDone()
            .done(),
        valid()
      },
      {
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .endEvent()
            .moveToProcess("process")
            .eventSubProcess()
            .startEvent()
            .condition(c -> c.condition("x > 1").zeebeVariableEvents("create update"))
            .endEvent()
            .subProcessDone()
            .done(),
        singletonList(
            expect(
                ZeebeConditionalFilter.class,
                "Variable event 'create update' is not valid. Must be one of: create, update or a comma-separated list of both."))
      },
      {
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .endEvent()
            .moveToProcess("process")
            .eventSubProcess()
            .startEvent()
            .condition(c -> c.condition("x > 1").zeebeVariableEvents("delete"))
            .endEvent()
            .subProcessDone()
            .done(),
        singletonList(
            expect(
                ZeebeConditionalFilter.class,
                "Variable event 'delete' is not valid. Must be one of: create, update or a comma-separated list of both."))
      },
      {
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .subProcess("subProcess")
            .embeddedSubProcess()
            .startEvent()
            .condition(c -> c.condition("= x > 1"))
            .endEvent()
            .subProcessDone()
            .endEvent()
            .done(),
        singletonList(expect("subProcess", "Start events in subprocesses must be of type none"))
      }
    };
  }
}
