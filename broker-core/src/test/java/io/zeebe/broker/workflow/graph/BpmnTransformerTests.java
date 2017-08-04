/*
 * Zeebe Broker Core
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.zeebe.broker.workflow.graph;

import static io.zeebe.broker.workflow.graph.transformer.ZeebeExtensions.wrap;
import static io.zeebe.test.util.BufferAssert.assertThatBuffer;
import static io.zeebe.util.StringUtil.getBytes;
import static io.zeebe.util.buffer.BufferUtil.wrapString;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.zeebe.broker.workflow.graph.model.*;
import io.zeebe.broker.workflow.graph.transformer.BpmnTransformer;
import io.zeebe.msgpack.mapping.Mapping;
import io.zeebe.msgpack.spec.MsgPackWriter;
import io.zeebe.util.buffer.BufferUtil;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class BpmnTransformerTests
{
    private BpmnTransformer bpmnTransformer = new BpmnTransformer();

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    @Test
    public void shouldTransformStartEvent()
    {
        // given
        final BpmnModelInstance bpmnModelInstance = Bpmn.createExecutableProcess()
            .startEvent("foo")
                .name("bar")
            .done();

        // when
        final ExecutableWorkflow process = transformSingleProcess(bpmnModelInstance);

        // then
        final ExecutableFlowElement element = process.findFlowElementById(wrapString("foo"));
        assertThat(element).isInstanceOf(ExecutableStartEvent.class);
        assertThat(element.getId()).isEqualTo(wrapString("foo"));
        assertThat(element.getName()).isEqualTo("bar");

        assertThat(process.getScopeStartEvent()).isEqualTo(element);
    }

    @Test
    public void shouldTransformSequenceFlow()
    {
        // given
        final BpmnModelInstance bpmnModelInstance = Bpmn.createExecutableProcess()
                .startEvent("a")
                .sequenceFlowId("to")
                .endEvent("b")
                .done();

        // when
        final ExecutableWorkflow process = transformSingleProcess(bpmnModelInstance);

        // then
        final ExecutableFlowElement element = process.findFlowElementById(wrapString("to"));
        assertThat(element).isInstanceOf(ExecutableSequenceFlow.class);

        final ExecutableSequenceFlow sequenceFlow = (ExecutableSequenceFlow) element;
        assertThat(sequenceFlow.getId()).isEqualTo(wrapString("to"));
        assertThat(sequenceFlow.getSourceNode().getId()).isEqualTo(wrapString("a"));
        assertThat(sequenceFlow.getTargetNode().getId()).isEqualTo(wrapString("b"));

        assertThat(sequenceFlow.getSourceNode().getOutgoingSequenceFlows()).hasSize(1).contains(sequenceFlow);
        assertThat(sequenceFlow.getTargetNode().getIncomingSequenceFlows()).hasSize(1).contains(sequenceFlow);
    }

    @Test
    public void shouldTransformEndEvent()
    {
        // given
        final BpmnModelInstance bpmnModelInstance = Bpmn.createExecutableProcess()
            .startEvent()
            .endEvent("foo")
                .name("bar")
            .done();

        // when
        final ExecutableWorkflow process = transformSingleProcess(bpmnModelInstance);

        // then
        final ExecutableFlowElement element = process.findFlowElementById(wrapString("foo"));
        assertThat(element).isInstanceOf(ExecutableEndEvent.class);
        assertThat(element.getId()).isEqualTo(wrapString("foo"));
        assertThat(element.getName()).isEqualTo("bar");
    }

    @Test
    public void shouldTransformServiceTask()
    {
        // given
        final Map<String, String> taskHeaders = new HashMap<>();
        taskHeaders.put("a", "b");
        taskHeaders.put("c", "d");

        final BpmnModelInstance bpmnModelInstance = wrap(Bpmn.createExecutableProcess()
            .startEvent()
            .serviceTask("foo")
                .name("bar")
            .done())
                .taskDefinition("foo", "test", 4)
                .taskHeaders("foo", taskHeaders);

        // when
        final ExecutableWorkflow process = transformSingleProcess(bpmnModelInstance);

        // then
        final ExecutableFlowElement element = process.findFlowElementById(wrapString("foo"));
        assertThat(element).isInstanceOf(ExecutableServiceTask.class);

        final ExecutableServiceTask serviceTask = (ExecutableServiceTask) element;
        assertThat(serviceTask.getId()).isEqualTo(wrapString("foo"));
        assertThat(serviceTask.getName()).isEqualTo("bar");

        assertThat(serviceTask.getTaskMetadata()).isNotNull();
        assertThatBuffer(serviceTask.getTaskMetadata().getTaskType()).hasBytes(getBytes("test"));
        assertThat(serviceTask.getTaskMetadata().getRetries()).isEqualTo(4);

        final UnsafeBuffer buffer = new UnsafeBuffer(new byte[256]);
        final MsgPackWriter msgPackWriter = new MsgPackWriter();
        msgPackWriter.wrap(buffer, 0);
        msgPackWriter.writeMapHeader(2);
        msgPackWriter.writeString(BufferUtil.wrapString("a"));
        msgPackWriter.writeString(BufferUtil.wrapString("b"));
        msgPackWriter.writeString(BufferUtil.wrapString("c"));
        msgPackWriter.writeString(BufferUtil.wrapString("d"));
        buffer.wrap(buffer.byteArray(), 0, msgPackWriter.getOffset());

        final DirectBuffer headers = serviceTask.getTaskMetadata()
                                                .getHeaders();
        assertThat(headers.capacity()).isEqualTo(msgPackWriter.getOffset());

        assertThatBuffer(headers).hasBytes(buffer);
    }

    @Test
    public void shouldNotCreateDefaultTaskMapping()
    {
        // given task without specified io mapping
        final BpmnModelInstance bpmnModelInstance = wrap(Bpmn.createExecutableProcess()
            .startEvent()
            .serviceTask("foo")
            .name("bar")
            .done())
            .taskDefinition("foo", "test", 4);

        // when
        final ExecutableWorkflow process = transformSingleProcess(bpmnModelInstance);

        // then
        final ExecutableFlowElement element = process.findFlowElementById(wrapString("foo"));
        final ExecutableServiceTask serviceTask = (ExecutableServiceTask) element;

        assertThat(serviceTask.getIoMapping()).isNotNull();
        final Mapping[] inputMappings = serviceTask.getIoMapping().getInputMappings();
        assertThat(inputMappings.length).isEqualTo(0);

        final Mapping[] outputMappings = serviceTask.getIoMapping().getOutputMappings();
        assertThat(outputMappings.length).isEqualTo(0);
    }

    @Test
    public void shouldDiscardTaskRootMapping()
    {
        // given
        final BpmnModelInstance bpmnModelInstance = wrap(Bpmn.createExecutableProcess()
            .startEvent()
            .serviceTask("foo")
            .name("bar")
            .done())
            .taskDefinition("foo", "test", 4)
            .ioMapping("foo")
                .input("$", "$")
                .output("$", "$")
            .done();

        // when
        final ExecutableWorkflow process = transformSingleProcess(bpmnModelInstance);

        // then
        final ExecutableFlowElement element = process.findFlowElementById(wrapString("foo"));
        final ExecutableServiceTask serviceTask = (ExecutableServiceTask) element;

        assertThat(serviceTask.getIoMapping()).isNotNull();
        final Mapping[] inputMappings = serviceTask.getIoMapping().getInputMappings();
        assertThat(inputMappings.length).isEqualTo(0);


        final Mapping[] outputMappings = serviceTask.getIoMapping().getOutputMappings();
        assertThat(outputMappings.length).isEqualTo(0);
    }

    @Test
    public void shouldTransformTaskMapping()
    {
        // given
        final BpmnModelInstance bpmnModelInstance = wrap(Bpmn.createExecutableProcess()
                                                             .startEvent()
                                                             .serviceTask("foo")
                                                             .name("bar")
                                                             .done())
            .taskDefinition("foo", "test", 4)
            .ioMapping("foo")
            .input("$.foo", "$.bar")
            .output("$.bar", "$.foo")
            .done();

        // when
        final ExecutableWorkflow process = transformSingleProcess(bpmnModelInstance);

        // then
        final ExecutableFlowElement element = process.findFlowElementById(wrapString("foo"));
        final ExecutableServiceTask serviceTask = (ExecutableServiceTask) element;

        assertThat(serviceTask.getIoMapping()).isNotNull();
        final Mapping[] inputMappings = serviceTask.getIoMapping().getInputMappings();
        assertThat(inputMappings.length).isEqualTo(1);
        assertThat(inputMappings[0].getSource().isValid()).isTrue();

        final Mapping[] outputMappings = serviceTask.getIoMapping().getOutputMappings();
        assertThat(outputMappings.length).isEqualTo(1);
        assertThat(outputMappings[0].getSource().isValid()).isTrue();

    }

    protected ExecutableWorkflow transformSingleProcess(BpmnModelInstance bpmnModelInstance)
    {
        final List<ExecutableWorkflow> processes = bpmnTransformer.transform(bpmnModelInstance);

        assertThat(processes.size()).isEqualTo(1);

        return processes.get(0);
    }

}
