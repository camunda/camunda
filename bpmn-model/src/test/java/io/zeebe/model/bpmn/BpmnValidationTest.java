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
package io.zeebe.model.bpmn;

import io.zeebe.model.bpmn.impl.error.InvalidModelException;
import io.zeebe.model.bpmn.instance.OutputBehavior;
import java.io.File;
import java.net.URL;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class BpmnValidationTest {
  @Rule public ExpectedException expectedException = ExpectedException.none();

  @Test
  public void shouldValidateBpmnFile() throws Exception {
    // given
    final URL resource = getClass().getResource("/invalid_process-activity-id.bpmn");
    final File bpmnFile = new File(resource.toURI());

    // expect
    expectedException.expect(InvalidModelException.class);
    expectedException.expectMessage("[line:4] (bpmn:startEvent) Activity id is required.");

    // when
    Bpmn.readFromXmlFile(bpmnFile);
  }

  @Test
  public void testMissingStartEvent() {
    // expect
    expectedException.expect(InvalidModelException.class);
    expectedException.expectMessage("The process must contain at least one none start event.");

    // when
    Bpmn.createExecutableWorkflow("process").done();
  }

  @Test
  public void testMissingActivityId() {
    // expect
    expectedException.expect(InvalidModelException.class);
    expectedException.expectMessage("Activity id is required.");

    // when
    Bpmn.createExecutableWorkflow("process").startEvent("").done();
  }

  @Test
  public void testMissingTaskDefinition() {
    // expect
    expectedException.expect(InvalidModelException.class);
    expectedException.expectMessage(
        "A service task must contain a 'taskDefinition' extension element.");

    // when
    Bpmn.createExecutableWorkflow("process").startEvent().serviceTask().done().endEvent().done();
  }

  @Test
  public void testMissingTaskType() {
    // expect
    expectedException.expect(InvalidModelException.class);
    expectedException.expectMessage(
        "A task definition must contain a 'type' attribute which specifies the type of the task.");

    // when
    Bpmn.createExecutableWorkflow("process")
        .startEvent()
        .serviceTask()
        .taskRetries(3)
        .done()
        .endEvent()
        .done();
  }

  @Test
  public void testInvalidElement() throws Exception {
    // given
    final URL resource = getClass().getResource("/invalid_process-task-type.bpmn");
    final File bpmnFile = new File(resource.toURI());

    // expect
    expectedException.expect(InvalidModelException.class);
    expectedException.expectMessage("Cannot find task as target of sequence flow.");
    expectedException.expectMessage("Cannot find task as source of sequence flow.");

    // when
    Bpmn.readFromXmlFile(bpmnFile);
  }

  @Test
  public void testProhibitedInputOutputMapping() {
    // expect
    expectedException.expect(InvalidModelException.class);
    expectedException.expectMessage(
        "Source mapping: JSON path '$.*' contains prohibited expression");

    // when
    Bpmn.createExecutableWorkflow("process")
        .startEvent()
        .serviceTask()
        .taskType("test")
        .input("$.*", "$.foo")
        .output("$.bar", "$.a[0,1]")
        .done()
        .done();
  }

  @Test
  public void testInvalidInputRootAndSpecificMapping() {
    // expect
    expectedException.expect(InvalidModelException.class);
    expectedException.expectMessage(
        "Target mapping: root mapping is not allowed because it would override other mapping.");

    // when
    Bpmn.createExecutableWorkflow("process")
        .startEvent()
        .serviceTask()
        .taskType("test")
        .input("$", "$")
        .input("$.bar", "$.bar")
        .done()
        .done();
  }

  @Test
  public void testInvalidOutputRootAndSpecificMapping() {
    // expect
    expectedException.expect(InvalidModelException.class);
    expectedException.expectMessage(
        "Target mapping: root mapping is not allowed because it would override other mapping.");

    // when
    Bpmn.createExecutableWorkflow("process")
        .startEvent()
        .serviceTask()
        .taskType("test")
        .input("$", "$")
        .output("$", "$")
        .output("$.bar", "$.bar")
        .done()
        .done();
  }

  @Test
  public void testInvalidOutputBehaviorWithOutputMapping() {
    // expect
    expectedException.expect(InvalidModelException.class);
    expectedException.expectMessage(
        "Output behavior 'NONE' is not supported in combination with output mappings.");

    // when
    Bpmn.createExecutableWorkflow("process")
        .startEvent()
        .serviceTask()
        .taskType("test")
        .outputBehavior(OutputBehavior.NONE)
        .output("$", "$")
        .done()
        .done();
  }

  @Test
  public void testInvalidOutputBehavior() throws Exception {
    // given
    final URL resource = getClass().getResource("/invalid_output_behavior.bpmn");
    final File bpmnFile = new File(resource.toURI());

    // expect
    expectedException.expect(InvalidModelException.class);
    expectedException.expectMessage(
        "Output behavior 'asdf' is not supported. Valid values are [MERGE, OVERWRITE, NONE].");

    // when
    Bpmn.readFromXmlFile(bpmnFile);
  }

  @Test
  public void testMissingConditionOnSequenceFlow() {
    // expect
    expectedException.expect(InvalidModelException.class);
    expectedException.expectMessage(
        "A sequence flow on an exclusive gateway must have a condition, if it is not the default flow.");

    // when
    Bpmn.createExecutableWorkflow("workflow")
        .startEvent()
        .exclusiveGateway("xor")
        .sequenceFlow("s1")
        .endEvent()
        .sequenceFlow("s2")
        .endEvent()
        .done();
  }

  @Test
  public void testDefaultSequenceFlowWithCondtion() {
    // expect
    expectedException.expect(InvalidModelException.class);
    expectedException.expectMessage("A default sequence flow must not have a condition.");

    // when
    Bpmn.createExecutableWorkflow("workflow")
        .startEvent()
        .exclusiveGateway("xor")
        .sequenceFlow("s1", s -> s.condition("$.foo < 5"))
        .endEvent()
        .sequenceFlow("s2", s -> s.defaultFlow().condition("$.foo >= 5"))
        .endEvent()
        .done();
  }

  @Test
  public void testInvalidDefaultSequenceFlow() throws Exception {
    // given
    final URL resource = getClass().getResource("/invalid_xor_default_flow.bpmn");
    final File bpmnFile = new File(resource.toURI());

    // expect
    expectedException.expect(InvalidModelException.class);
    expectedException.expectMessage(
        "The default sequence flow must be an outgoing sequence flow of the exclusive gateway.");

    // when
    Bpmn.readFromXmlFile(bpmnFile);
  }
}
