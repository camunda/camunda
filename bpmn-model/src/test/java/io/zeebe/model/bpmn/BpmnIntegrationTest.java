package io.zeebe.model.bpmn;

import static io.zeebe.util.buffer.BufferUtil.wrapString;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.net.URL;

import io.zeebe.model.bpmn.instance.*;
import org.junit.Test;

public class BpmnIntegrationTest
{

    @Test
    public void shouldReadWorkflowFromFile() throws Exception
    {
        final URL resource = getClass().getResource("/process.bpmn");
        final File bpmnFile = new File(resource.toURI());

        final WorkflowDefinition workflowDefinition = Bpmn.readFromFile(bpmnFile);

        assertThat(workflowDefinition).isNotNull();
        assertThat(workflowDefinition.getWorkflows()).hasSize(1);

        final Workflow workflow = workflowDefinition.getWorklow(wrapString("process"));
        assertThat(workflow).isNotNull();
        assertThat(workflow.getBpmnProcessId()).isEqualTo(wrapString("process"));

        assertThat(workflow.getFlowElementMap()).isNotEmpty();

        final StartEvent initialStartEvent = workflow.getInitialStartEvent();
        assertThat(initialStartEvent).isNotNull();
        assertThat(initialStartEvent.getIdAsBuffer()).isEqualTo(wrapString("start"));
        assertThat(initialStartEvent.getOutgoingSequenceFlows()).isNotEmpty();

        final FlowNode targetElement = initialStartEvent.getOutgoingSequenceFlows().get(0).getTargetNode();
        assertThat(targetElement).isNotNull();
        assertThat(targetElement.getIdAsBuffer()).isEqualTo(wrapString("task"));

        assertThat(targetElement.getIncomingSequenceFlows()).hasSize(1);
        assertThat(targetElement.getIncomingSequenceFlows().get(0).getSourceNode()).isEqualTo(initialStartEvent);

    }

    @Test
    public void shouldBuildWorkflow()
    {
        final WorkflowDefinition workflowDefinition = Bpmn.createExecutableWorkflow("process")
            .startEvent("start")
            .sequenceFlow("sf1")
            .serviceTask("task")
            .sequenceFlow("sf2")
            .endEvent("end")
            .done();

        assertThat(workflowDefinition).isNotNull();
        assertThat(workflowDefinition.getWorkflows()).hasSize(1);

        final Workflow workflow = workflowDefinition.getWorklow(wrapString("process"));
        assertThat(workflow).isNotNull();
        assertThat(workflow.getBpmnProcessId()).isEqualTo(wrapString("process"));

        assertThat(workflow.getFlowElementMap()).isNotEmpty();

        final StartEvent initialStartEvent = workflow.getInitialStartEvent();
        assertThat(initialStartEvent).isNotNull();
        assertThat(initialStartEvent.getIdAsBuffer()).isEqualTo(wrapString("start"));
        assertThat(initialStartEvent.getOutgoingSequenceFlows()).isNotEmpty();

        final FlowNode targetElement = initialStartEvent.getOutgoingSequenceFlows().get(0).getTargetNode();
        assertThat(targetElement).isNotNull();
        assertThat(targetElement.getIdAsBuffer()).isEqualTo(wrapString("task"));

        assertThat(targetElement.getIncomingSequenceFlows()).hasSize(1);
        assertThat(targetElement.getIncomingSequenceFlows().get(0).getSourceNode()).isEqualTo(initialStartEvent);
    }

}
