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

import static io.zeebe.util.buffer.BufferUtil.wrapString;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.model.bpmn.impl.error.InvalidModelException;
import io.zeebe.model.bpmn.instance.*;
import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.util.List;
import org.assertj.core.util.Files;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class BpmnTransformTest {
  private static final String BPMN_FILE = "/process.bpmn";
  private static final String BPMN_OVERWRITE_PROCESS_FILE = "/overwrite_output_process.bpmn";
  private static final String BPMN_UPPER_CASE_OVERWRITE_PROCESS_FILE =
      "/upper_case_overwrite_output_process.bpmn";
  private static final String BPMN_NONE_PROCESS_FILE = "/none_output_process.bpmn";
  private static final String BPMN_MERGE_PROCESS_FILE = "/merge_output_process.bpmn";
  private static final String BPMN_XOR_GATEWAY_FILE = "/process-xor-gateway.bpmn";

  @Rule public ExpectedException expectedException = ExpectedException.none();

  @Test
  public void shouldReadFromFile() throws Exception {
    final URL resource = getClass().getResource(BPMN_FILE);
    final File bpmnFile = new File(resource.toURI());

    final WorkflowDefinition workflowDefinition = Bpmn.readFromXmlFile(bpmnFile);

    assertThat(workflowDefinition).isNotNull();
    assertThat(workflowDefinition.getWorkflows()).hasSize(1);
  }

  @Test
  public void shouldReadFromStream() {
    final InputStream stream = getClass().getResourceAsStream(BPMN_FILE);

    final WorkflowDefinition workflowDefinition = Bpmn.readFromXmlStream(stream);

    assertThat(workflowDefinition).isNotNull();
    assertThat(workflowDefinition.getWorkflows()).hasSize(1);
  }

  @Test
  public void shouldReadFromString() throws Exception {
    final URL resource = getClass().getResource(BPMN_FILE);
    final File bpmnFile = new File(resource.toURI());
    final String workflow = Files.contentOf(bpmnFile, UTF_8);

    final WorkflowDefinition workflowDefinition = Bpmn.readFromXmlString(workflow);

    assertThat(workflowDefinition).isNotNull();
    assertThat(workflowDefinition.getWorkflows()).hasSize(1);
  }

  @Test
  public void shouldTransformValidWorkflow() {
    final InputStream stream = getClass().getResourceAsStream(BPMN_FILE);
    final WorkflowDefinition workflowDefinition = Bpmn.readFromXmlStream(stream);

    assertThat(workflowDefinition).isNotNull();
  }

  @Test
  public void shouldTransformWorkflow() {
    final InputStream stream = getClass().getResourceAsStream(BPMN_FILE);
    final WorkflowDefinition workflowDefinition = Bpmn.readFromXmlStream(stream);

    assertThat(workflowDefinition.getWorkflows()).hasSize(1);

    final Workflow workflow = workflowDefinition.getWorkflow(wrapString("process"));
    assertThat(workflow).isNotNull();
    assertThat(workflow.getBpmnProcessId()).isEqualTo(wrapString("process"));
  }

  @Test
  public void shouldTransformStartEvent() {
    final InputStream stream = getClass().getResourceAsStream(BPMN_FILE);
    final WorkflowDefinition workflowDefinition = Bpmn.readFromXmlStream(stream);

    final Workflow workflow = workflowDefinition.getWorkflow(wrapString("process"));

    final StartEvent initialStartEvent = workflow.getInitialStartEvent();
    assertThat(initialStartEvent).isNotNull();
    assertThat(initialStartEvent.getIdAsBuffer()).isEqualTo(wrapString("start"));
  }

  @Test
  public void shouldTransformSequenceFlows() {
    final InputStream stream = getClass().getResourceAsStream(BPMN_FILE);
    final WorkflowDefinition workflowDefinition = Bpmn.readFromXmlStream(stream);

    final Workflow workflow = workflowDefinition.getWorkflow(wrapString("process"));
    final StartEvent initialStartEvent = workflow.getInitialStartEvent();

    assertThat(initialStartEvent.getOutgoingSequenceFlows()).hasSize(1);

    final FlowNode targetElement =
        initialStartEvent.getOutgoingSequenceFlows().get(0).getTargetNode();
    assertThat(targetElement).isNotNull();
    assertThat(targetElement.getIdAsBuffer()).isEqualTo(wrapString("task"));

    assertThat(targetElement.getIncomingSequenceFlows()).hasSize(1);
    assertThat(targetElement.getIncomingSequenceFlows().get(0).getSourceNode())
        .isEqualTo(initialStartEvent);

    assertThat(targetElement.getOutgoingSequenceFlows()).hasSize(1);

    final FlowNode nextElement = targetElement.getOutgoingSequenceFlows().get(0).getTargetNode();
    assertThat(nextElement).isNotNull();
    assertThat(nextElement.getIdAsBuffer()).isEqualTo(wrapString("end"));

    assertThat(nextElement.getIncomingSequenceFlows()).hasSize(1);
    assertThat(nextElement.getIncomingSequenceFlows().get(0).getSourceNode())
        .isEqualTo(targetElement);
    assertThat(nextElement.getOutgoingSequenceFlows()).isEmpty();
  }

  @Test
  public void shouldTransformServiceTask() {
    final InputStream stream = getClass().getResourceAsStream(BPMN_FILE);
    final WorkflowDefinition workflowDefinition = Bpmn.readFromXmlStream(stream);

    final Workflow workflow = workflowDefinition.getWorkflow(wrapString("process"));

    final ServiceTask serviceTask = workflow.findFlowElementById(wrapString("task"));
    final TaskDefinition taskDefinition = serviceTask.getTaskDefinition();
    assertThat(taskDefinition).isNotNull();
    assertThat(taskDefinition.getTypeAsBuffer()).isEqualTo(wrapString("task"));
    assertThat(taskDefinition.getRetries()).isEqualTo(3);

    final TaskHeaders taskHeaders = serviceTask.getTaskHeaders();
    assertThat(taskHeaders).isNotNull();
    assertThat(taskHeaders.asMap()).hasSize(2).containsEntry("foo", "f").containsEntry("bar", "b");

    final InputOutputMapping inputOutputMapping = serviceTask.getInputOutputMapping();
    assertThat(inputOutputMapping).isNotNull();

    assertThat(inputOutputMapping.getOutputBehavior()).isEqualTo(OutputBehavior.MERGE);

    assertThat(inputOutputMapping.getInputMappingsAsMap()).hasSize(1).containsEntry("$.a", "$.b");

    assertThat(inputOutputMapping.getOutputMappingsAsMap()).hasSize(1).containsEntry("$.c", "$.d");
  }

  @Test
  public void shouldTransformOverwriteOutputBehaviorOnServiceTask() {
    // given
    final InputStream stream = getClass().getResourceAsStream(BPMN_OVERWRITE_PROCESS_FILE);
    final WorkflowDefinition workflowDefinition = Bpmn.readFromXmlStream(stream);
    final Workflow workflow = workflowDefinition.getWorkflow(wrapString("process"));

    // when
    final ServiceTask serviceTask = workflow.findFlowElementById(wrapString("task"));

    // then
    final InputOutputMapping inputOutputMapping = serviceTask.getInputOutputMapping();
    assertThat(inputOutputMapping).isNotNull();
    assertThat(inputOutputMapping.getOutputBehavior()).isEqualTo(OutputBehavior.OVERWRITE);

    assertThat(inputOutputMapping.getInputMappingsAsMap()).hasSize(1).containsEntry("$.a", "$.b");

    assertThat(inputOutputMapping.getOutputMappingsAsMap()).hasSize(1).containsEntry("$.c", "$.d");
  }

  @Test
  public void shouldTransformUpperCaseOutputBehaviorOnServiceTask() {
    // given
    final InputStream stream =
        getClass().getResourceAsStream(BPMN_UPPER_CASE_OVERWRITE_PROCESS_FILE);
    final WorkflowDefinition workflowDefinition = Bpmn.readFromXmlStream(stream);
    final Workflow workflow = workflowDefinition.getWorkflow(wrapString("process"));

    // when
    final ServiceTask serviceTask = workflow.findFlowElementById(wrapString("task"));

    // then
    final InputOutputMapping inputOutputMapping = serviceTask.getInputOutputMapping();
    assertThat(inputOutputMapping).isNotNull();
    assertThat(inputOutputMapping.getOutputBehavior()).isEqualTo(OutputBehavior.OVERWRITE);

    assertThat(inputOutputMapping.getInputMappingsAsMap()).hasSize(1).containsEntry("$.a", "$.b");

    assertThat(inputOutputMapping.getOutputMappingsAsMap()).hasSize(1).containsEntry("$.c", "$.d");
  }

  @Test
  public void shouldTransformNoneOutputBehaviorOnServiceTask() {
    // given
    final InputStream stream = getClass().getResourceAsStream(BPMN_NONE_PROCESS_FILE);
    final WorkflowDefinition workflowDefinition = Bpmn.readFromXmlStream(stream);
    final Workflow workflow = workflowDefinition.getWorkflow(wrapString("process"));

    // when
    final ServiceTask serviceTask = workflow.findFlowElementById(wrapString("task"));

    // then
    final InputOutputMapping inputOutputMapping = serviceTask.getInputOutputMapping();
    assertThat(inputOutputMapping).isNotNull();
    assertThat(inputOutputMapping.getOutputBehavior()).isEqualTo(OutputBehavior.NONE);

    assertThat(inputOutputMapping.getInputMappingsAsMap()).hasSize(1).containsEntry("$.a", "$.b");
  }

  @Test
  public void shouldTransformMergeOutputBehaviorOnServiceTask() {
    // given
    final InputStream stream = getClass().getResourceAsStream(BPMN_MERGE_PROCESS_FILE);
    final WorkflowDefinition workflowDefinition = Bpmn.readFromXmlStream(stream);
    final Workflow workflow = workflowDefinition.getWorkflow(wrapString("process"));

    // when
    final ServiceTask serviceTask = workflow.findFlowElementById(wrapString("task"));

    // then
    final InputOutputMapping inputOutputMapping = serviceTask.getInputOutputMapping();
    assertThat(inputOutputMapping).isNotNull();
    assertThat(inputOutputMapping.getOutputBehavior()).isEqualTo(OutputBehavior.MERGE);

    assertThat(inputOutputMapping.getInputMappingsAsMap()).hasSize(1).containsEntry("$.a", "$.b");

    assertThat(inputOutputMapping.getOutputMappingsAsMap()).hasSize(1).containsEntry("$.c", "$.d");
  }

  @Test
  public void shouldTransformDefaultOutputBehaviorOnServiceTask() {
    // given
    final InputStream stream = getClass().getResourceAsStream(BPMN_FILE);
    final WorkflowDefinition workflowDefinition = Bpmn.readFromXmlStream(stream);
    final Workflow workflow = workflowDefinition.getWorkflow(wrapString("process"));

    // when
    final ServiceTask serviceTask = workflow.findFlowElementById(wrapString("task"));

    // then
    final InputOutputMapping inputOutputMapping = serviceTask.getInputOutputMapping();
    assertThat(inputOutputMapping).isNotNull();
    assertThat(inputOutputMapping.getOutputBehavior()).isEqualTo(OutputBehavior.MERGE);

    assertThat(inputOutputMapping.getInputMappingsAsMap()).hasSize(1).containsEntry("$.a", "$.b");

    assertThat(inputOutputMapping.getOutputMappingsAsMap()).hasSize(1).containsEntry("$.c", "$.d");
  }

  @Test
  public void shouldTransformExclusiveGateway() {
    final InputStream stream = getClass().getResourceAsStream(BPMN_XOR_GATEWAY_FILE);
    final WorkflowDefinition workflowDefinition = Bpmn.readFromXmlStream(stream);

    final Workflow workflow = workflowDefinition.getWorkflow(wrapString("workflow"));

    final ExclusiveGateway exclusiveGateway = workflow.findFlowElementById(wrapString("xor"));
    assertThat(exclusiveGateway).isNotNull();

    final List<SequenceFlow> outgoingSequenceFlows = exclusiveGateway.getOutgoingSequenceFlows();
    assertThat(outgoingSequenceFlows).hasSize(3);

    final SequenceFlow defaultOutgoingSequenceFlow = exclusiveGateway.getDefaultFlow();
    assertThat(defaultOutgoingSequenceFlow).isNotNull();
    assertThat(defaultOutgoingSequenceFlow.hasCondition()).isFalse();

    final List<SequenceFlow> outgoingSequenceFlowsWithConditions =
        exclusiveGateway.getOutgoingSequenceFlowsWithConditions();
    assertThat(outgoingSequenceFlowsWithConditions).hasSize(2);
    assertThat(outgoingSequenceFlowsWithConditions).allMatch(SequenceFlow::hasCondition);
    assertThat(outgoingSequenceFlowsWithConditions)
        .extracting(s -> s.getCondition().getExpression())
        .containsExactly("$.foo < 5", "$.foo >= 5 && $.foo < 10");
  }

  @Test
  public void shouldThrowTransformExceptionOnSyntacticallyInvalidMapping() {
    // expect
    expectedException.expect(InvalidModelException.class);
    expectedException.expectMessage("JSON path query 'foo' is not valid!");
    expectedException.expectMessage("JSON path query 'bar' is not valid!");

    // when
    Bpmn.createExecutableWorkflow("process")
        .startEvent()
        .serviceTask()
        .taskType("test")
        .input("foo", "$")
        .output("bar", "$")
        .done()
        .done();
  }

  @Test
  public void shouldThrowTransformExceptionOnSyntacticallyInvalidSequenceFlowCondition() {
    // expect
    expectedException.expect(InvalidModelException.class);
    expectedException.expectMessage("The condition 'foobar' is not valid");

    // when
    Bpmn.createExecutableWorkflow("workflow")
        .startEvent()
        .exclusiveGateway("xor")
        .sequenceFlow("s1", s -> s.condition("foobar"))
        .endEvent()
        .sequenceFlow("s2", s -> s.defaultFlow())
        .endEvent()
        .done();
  }
}
