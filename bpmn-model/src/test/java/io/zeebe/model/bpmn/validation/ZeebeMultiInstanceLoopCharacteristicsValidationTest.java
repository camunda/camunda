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
import io.zeebe.model.bpmn.instance.MultiInstanceLoopCharacteristics;
import io.zeebe.model.bpmn.instance.zeebe.ZeebeLoopCharacteristics;
import org.junit.runners.Parameterized.Parameters;

public class ZeebeMultiInstanceLoopCharacteristicsValidationTest
    extends AbstractZeebeValidationTest {

  @Parameters(name = "{index}: {1}")
  public static Object[][] parameters() {
    return new Object[][] {
      {
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .serviceTask("task", t -> t.zeebeJobType("test").multiInstance())
            .done(),
        singletonList(
            expect(
                MultiInstanceLoopCharacteristics.class,
                "Must have exactly one 'zeebe:loopCharacteristics' extension element"))
      },
      {
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .serviceTask(
                "task",
                t ->
                    t.zeebeJobType("test")
                        .multiInstance(b -> b.zeebeInputCollectionExpression(null)))
            .done(),
        singletonList(
            expect(
                ZeebeLoopCharacteristics.class,
                "Attribute 'inputCollection' must be present and not empty"))
      },
      {
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .serviceTask(
                "task",
                t ->
                    t.zeebeJobType("test")
                        .multiInstance(
                            b ->
                                b.zeebeInputCollectionExpression("xs").zeebeOutputCollection("ys")))
            .done(),
        singletonList(
            expect(
                ZeebeLoopCharacteristics.class,
                "Attribute 'outputElement' must be present if the attribute 'outputCollection' is set"))
      },
      {
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .serviceTask(
                "task",
                t ->
                    t.zeebeJobType("test")
                        .multiInstance(
                            b ->
                                b.zeebeInputCollectionExpression("xs")
                                    .zeebeOutputElementExpression("y")))
            .done(),
        singletonList(
            expect(
                ZeebeLoopCharacteristics.class,
                "Attribute 'outputCollection' must be present if the attribute 'outputElement' is set"))
      },
    };
  }
}
