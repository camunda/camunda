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
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeTaskDefinition;
import org.junit.runners.Parameterized.Parameters;

public class ZeebeJobWorkerTaskValidationTest extends AbstractZeebeValidationTest {

  @Parameters(name = "{index}: {1}")
  public static Object[][] parameters() {
    return new Object[][] {
      {
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .serviceTask("task")
            .zeebeJobType("service")
            .zeebeJobRetries("5")
            .endEvent()
            .done(),
        valid()
      },
      {
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .serviceTask("task")
            .zeebeJobTypeExpression("serviceType")
            .zeebeJobRetriesExpression("jobRetries")
            .endEvent()
            .done(),
        valid()
      },
      {
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .serviceTask("task")
            .zeebeJobType("service")
            .zeebeTaskHeader("priority", "high")
            .endEvent()
            .done(),
        valid()
      },
      {
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .businessRuleTask("task")
            .zeebeJobType("DMN")
            .zeebeJobRetries("1")
            .endEvent()
            .done(),
        valid()
      },
      {
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .businessRuleTask("task")
            .zeebeJobTypeExpression("dmnType")
            .zeebeJobRetriesExpression("jobRetries")
            .endEvent()
            .done(),
        valid()
      },
      {
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .businessRuleTask("task")
            .zeebeJobType("DMN")
            .zeebeTaskHeader("decisionRef", "approveInvoice")
            .endEvent()
            .done(),
        valid()
      },
      {
        Bpmn.createExecutableProcess("process").startEvent().serviceTask("task").endEvent().done(),
        singletonList(
            expect("task", "Must have exactly one 'zeebe:taskDefinition' extension element"))
      },
      {
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .serviceTask("task")
            .zeebeJobType("")
            .endEvent()
            .done(),
        singletonList(expect(ZeebeTaskDefinition.class, "Task type must be present and not empty"))
      },
      {
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .businessRuleTask("task")
            .endEvent()
            .done(),
        singletonList(
            expect("task", "Must have exactly one 'zeebe:taskDefinition' extension element"))
      },
      {
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .businessRuleTask("task")
            .zeebeJobType("")
            .endEvent()
            .done(),
        singletonList(expect(ZeebeTaskDefinition.class, "Task type must be present and not empty"))
      }
    };
  }
}
