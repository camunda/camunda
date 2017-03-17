package org.camunda.tngp.broker.workflow.graph;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.tngp.broker.workflow.graph.model.ExecutableEndEvent;
import org.camunda.tngp.broker.workflow.graph.model.ExecutableFlowElement;
import org.camunda.tngp.broker.workflow.graph.model.ExecutableProcess;
import org.camunda.tngp.broker.workflow.graph.model.ExecutableServiceTask;
import org.camunda.tngp.broker.workflow.graph.model.ExecutableStartEvent;
import org.camunda.tngp.broker.workflow.graph.transformer.BpmnTransformer;
import org.junit.Test;

public class BpmnTransformerTests
{
    private BpmnTransformer bpmnTransformer = new BpmnTransformer();

    @Test
    public void shouldTransformStartEvnet()
    {
        // given
        final BpmnModelInstance bpmnModelInstance = Bpmn.createExecutableProcess()
            .startEvent("foo")
                .name("bar")
            .done();

        // when
        final ExecutableProcess process = transformSingleProcess(bpmnModelInstance);

        // then
        final ExecutableFlowElement element = process.findFlowElementById("foo");
        assertThat(element).isInstanceOf(ExecutableStartEvent.class);
        assertThat(element.getId()).isEqualTo("foo");
        assertThat(element.getName()).isEqualTo("bar");
    }

    @Test
    public void shouldTransformEndEvnet()
    {
        // given
        final BpmnModelInstance bpmnModelInstance = Bpmn.createExecutableProcess()
            .startEvent()
            .endEvent("foo")
                .name("bar")
            .done();

        // when
        final ExecutableProcess process = transformSingleProcess(bpmnModelInstance);

        // then
        final ExecutableFlowElement element = process.findFlowElementById("foo");
        assertThat(element).isInstanceOf(ExecutableEndEvent.class);
        assertThat(element.getId()).isEqualTo("foo");
        assertThat(element.getName()).isEqualTo("bar");
    }

    @Test
    public void shouldTransformServiceTask()
    {
        // g
        final BpmnModelInstance bpmnModelInstance = Bpmn.createExecutableProcess()
            .startEvent()
            .serviceTask("foo")
                .name("bar")
            .done();

        // w
        final ExecutableProcess process = transformSingleProcess(bpmnModelInstance);

        // t
        final ExecutableFlowElement element = process.findFlowElementById("foo");
        assertThat(element).isInstanceOf(ExecutableServiceTask.class);
        assertThat(element.getId()).isEqualTo("foo");
        assertThat(element.getName()).isEqualTo("bar");
    }

    protected ExecutableProcess transformSingleProcess(BpmnModelInstance bpmnModelInstance)
    {
        final List<ExecutableProcess> processes = bpmnTransformer.transform(bpmnModelInstance);

        assertThat(processes.size()).isEqualTo(1);

        return processes.get(0);
    }

}
