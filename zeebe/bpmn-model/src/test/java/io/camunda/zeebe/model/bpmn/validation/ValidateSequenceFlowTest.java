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
import io.camunda.zeebe.model.bpmn.instance.SequenceFlow;
import java.util.Arrays;
import org.junit.runners.Parameterized.Parameters;

public class ValidateSequenceFlowTest extends AbstractZeebeValidationTest {

  @Parameters(name = "{index}: {1}")
  public static Object[][] parameters() {
    return new Object[][] {
      {
        processWithUnresolvableTargetRef(),
        singletonList(
            expect(SequenceFlow.class, "Attribute 'targetRef' must reference a valid flow node"))
      },
      {
        processWithUnresolvableSourceRef(),
        singletonList(
            expect(SequenceFlow.class, "Attribute 'sourceRef' must reference a valid flow node"))
      },
      {
        processWithUnresolvableSourceAndTargetRef(),
        Arrays.asList(
            expect(SequenceFlow.class, "Attribute 'sourceRef' must reference a valid flow node"),
            expect(SequenceFlow.class, "Attribute 'targetRef' must reference a valid flow node"))
      },
    };
  }

  private static BpmnModelInstance processWithUnresolvableTargetRef() {
    final BpmnModelInstance model =
        Bpmn.createExecutableProcess("process")
            .startEvent("start")
            .sequenceFlowId("flow1")
            .endEvent("end")
            .done();

    final SequenceFlow flow = model.getModelElementById("flow1");
    flow.setAttributeValue("targetRef", "nonExistent", false);

    return model;
  }

  private static BpmnModelInstance processWithUnresolvableSourceRef() {
    final BpmnModelInstance model =
        Bpmn.createExecutableProcess("process")
            .startEvent("start")
            .sequenceFlowId("flow1")
            .endEvent("end")
            .done();

    final SequenceFlow flow = model.getModelElementById("flow1");
    flow.setAttributeValue("sourceRef", "nonExistent", false);

    return model;
  }

  private static BpmnModelInstance processWithUnresolvableSourceAndTargetRef() {
    final BpmnModelInstance model =
        Bpmn.createExecutableProcess("process")
            .startEvent("start")
            .sequenceFlowId("flow1")
            .endEvent("end")
            .done();

    final SequenceFlow flow = model.getModelElementById("flow1");
    flow.setAttributeValue("sourceRef", "nonExistent", false);
    flow.setAttributeValue("targetRef", "alsoNonExistent", false);

    return model;
  }
}
