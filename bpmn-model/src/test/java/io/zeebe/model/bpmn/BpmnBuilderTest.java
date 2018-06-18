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
import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.model.bpmn.instance.*;
import java.util.List;
import org.junit.Test;

public class BpmnBuilderTest {

  @Test
  public void shouldBuildSimpleWorkflow() {
    final WorkflowDefinition workflowDefinition =
        Bpmn.createExecutableWorkflow("process").startEvent("start").endEvent("end").done();

    assertThat(workflowDefinition).isNotNull();
    assertThat(workflowDefinition.getWorkflows()).hasSize(1);

    final Workflow workflow = workflowDefinition.getWorkflow(wrapString("process"));
    assertThat(workflow).isNotNull();
    assertThat(workflow.getBpmnProcessId()).isEqualTo(wrapString("process"));

    final StartEvent initialStartEvent = workflow.getInitialStartEvent();
    assertThat(initialStartEvent).isNotNull();
    assertThat(initialStartEvent.getIdAsBuffer()).isEqualTo(wrapString("start"));
    assertThat(initialStartEvent.getOutgoingSequenceFlows()).hasSize(1);

    final FlowNode targetElement =
        initialStartEvent.getOutgoingSequenceFlows().get(0).getTargetNode();
    assertThat(targetElement).isNotNull();
    assertThat(targetElement.getIdAsBuffer()).isEqualTo(wrapString("end"));
  }

  @Test
  public void shouldBuildWorkflowWithServiceTask() {
    final WorkflowDefinition workflowDefinition =
        Bpmn.createExecutableWorkflow("process")
            .startEvent()
            .sequenceFlow()
            .serviceTask("task")
            .taskType("foo")
            .taskRetries(3)
            .taskHeader("foo", "f")
            .taskHeader("bar", "b")
            .outputBehavior(OutputBehavior.OVERWRITE)
            .input("$.a", "$.b")
            .output("$.c", "$.d")
            .done()
            .sequenceFlow()
            .endEvent()
            .done();
    final Workflow workflow = workflowDefinition.getWorkflow(wrapString("process"));

    final ServiceTask serviceTask = workflow.findFlowElementById(wrapString("task"));
    final TaskDefinition taskDefinition = serviceTask.getTaskDefinition();
    assertThat(taskDefinition).isNotNull();
    assertThat(taskDefinition.getTypeAsBuffer()).isEqualTo(wrapString("foo"));
    assertThat(taskDefinition.getRetries()).isEqualTo(3);

    final TaskHeaders taskHeaders = serviceTask.getTaskHeaders();
    assertThat(taskHeaders).isNotNull();
    assertThat(taskHeaders.asMap()).hasSize(2).containsEntry("foo", "f").containsEntry("bar", "b");

    final InputOutputMapping inputOutputMapping = serviceTask.getInputOutputMapping();
    assertThat(inputOutputMapping).isNotNull();

    assertThat(inputOutputMapping.getOutputBehavior()).isEqualTo(OutputBehavior.OVERWRITE);
    assertThat(inputOutputMapping.getInputMappingsAsMap()).hasSize(1).containsEntry("$.a", "$.b");

    assertThat(inputOutputMapping.getOutputMappingsAsMap()).hasSize(1).containsEntry("$.c", "$.d");
  }

  @Test
  public void shouldBuildWorkflowWithServiceTaskClosureStyle() {
    final WorkflowDefinition workflowDefinition =
        Bpmn.createExecutableWorkflow("process")
            .startEvent()
            .serviceTask("task", s -> s.taskType("foo").taskRetries(3))
            .endEvent()
            .done();

    final Workflow workflow = workflowDefinition.getWorkflow(wrapString("process"));

    final ServiceTask serviceTask = workflow.findFlowElementById(wrapString("task"));
    final TaskDefinition taskDefinition = serviceTask.getTaskDefinition();
    assertThat(taskDefinition).isNotNull();
    assertThat(taskDefinition.getTypeAsBuffer()).isEqualTo(wrapString("foo"));
    assertThat(taskDefinition.getRetries()).isEqualTo(3);
  }

  @Test
  public void shouldBuildWorkflowWithMultipleServiceTasks() {
    final WorkflowDefinition workflowDefinition =
        Bpmn.createExecutableWorkflow("process")
            .startEvent()
            .serviceTask("task1", s -> s.taskType("a"))
            .serviceTask("task2", s -> s.taskType("b"))
            .serviceTask("task3", s -> s.taskType("c"))
            .endEvent()
            .done();

    final Workflow workflow = workflowDefinition.getWorkflow(wrapString("process"));

    assertThat(workflow.getFlowElementMap())
        .containsKey(wrapString("task1"))
        .containsKey(wrapString("task2"))
        .containsKey(wrapString("task3"));
  }

  @Test
  public void shouldBuildWorkflowWithExclusiveGateway() {
    final WorkflowDefinition workflowDefinition =
        Bpmn.createExecutableWorkflow("process")
            .startEvent()
            .exclusiveGateway("xor")
            .sequenceFlow("s1", s -> s.condition("$.foo < 5"))
            .endEvent("a")
            .sequenceFlow("s2", s -> s.condition("$.foo >= 5 && $.foo < 10"))
            .endEvent("b")
            .sequenceFlow("s3", s -> s.defaultFlow())
            .endEvent("c")
            .done();

    final Workflow workflow = workflowDefinition.getWorkflow(wrapString("process"));

    final ExclusiveGateway exclusiveGateway = workflow.findFlowElementById(wrapString("xor"));
    assertThat(exclusiveGateway).isNotNull();

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
  public void shouldBuildWorkflowWithJoiningExclusiveGateway() {
    final WorkflowDefinition workflowDefinition =
        Bpmn.createExecutableWorkflow("process")
            .startEvent()
            .exclusiveGateway("split")
            .sequenceFlow("s1", s -> s.condition("$.foo < 5"))
            .exclusiveGateway("join")
            .continueAt("split")
            .sequenceFlow("s2", s -> s.defaultFlow())
            .joinWith("join")
            .endEvent("end")
            .done();

    final Workflow workflow = workflowDefinition.getWorkflow(wrapString("process"));

    final ExclusiveGateway exclusiveGateway = workflow.findFlowElementById(wrapString("join"));
    assertThat(exclusiveGateway).isNotNull();

    final List<SequenceFlow> incomingSequenceFlows = exclusiveGateway.getIncomingSequenceFlows();
    assertThat(incomingSequenceFlows).hasSize(2);
    assertThat(incomingSequenceFlows.get(0).getIdAsBuffer()).isEqualTo(wrapString("s1"));
    assertThat(incomingSequenceFlows.get(1).getIdAsBuffer()).isEqualTo(wrapString("s2"));
  }

  @Test
  public void shouldConvertWorkflowToString() {
    final WorkflowDefinition workflowDefinition =
        Bpmn.createExecutableWorkflow("process")
            .startEvent("start")
            .sequenceFlow("s1")
            .serviceTask("task")
            .taskType("foo")
            .taskRetries(3)
            .taskHeader("foo", "f")
            .taskHeader("bar", "b")
            .input("$.a", "$.b")
            .output("$.c", "$.d")
            .done()
            .sequenceFlow("s2")
            .endEvent("end2")
            .done();

    final String workflowAsString = Bpmn.convertToString(workflowDefinition);

    Bpmn.readFromXmlString(workflowAsString);

    final Workflow workflow = workflowDefinition.getWorkflow(wrapString("process"));
    final ServiceTask serviceTask = workflow.findFlowElementById(wrapString("task"));
    final TaskDefinition taskDefinition = serviceTask.getTaskDefinition();
    assertThat(taskDefinition).isNotNull();
    assertThat(taskDefinition.getTypeAsBuffer()).isEqualTo(wrapString("foo"));
  }

  @Test
  public void shouldUseDefaultOutputBehavior() {
    // given

    // when
    final WorkflowDefinition workflowDefinition =
        Bpmn.createExecutableWorkflow("process")
            .startEvent("start")
            .serviceTask("task")
            .taskType("foo")
            .done()
            .endEvent("end2")
            .done();

    // then
    final Workflow workflow = workflowDefinition.getWorkflow(wrapString("process"));

    final ServiceTask serviceTask = workflow.findFlowElementById(wrapString("task"));
    final InputOutputMapping inputOutputMapping = serviceTask.getInputOutputMapping();
    assertThat(inputOutputMapping).isNotNull();
    assertThat(inputOutputMapping.getOutputBehavior()).isEqualTo(OutputBehavior.MERGE);
  }
}
