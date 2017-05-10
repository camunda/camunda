/* Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.camunda.tngp.broker.workflow.processor;

import static org.assertj.core.api.Assertions.*;
import static org.camunda.tngp.test.util.BufferAssert.*;
import static org.camunda.tngp.util.StringUtil.*;
import static org.camunda.tngp.util.buffer.BufferUtil.*;
import static org.mockito.Mockito.*;

import java.io.ByteArrayOutputStream;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.tngp.broker.Constants;
import org.camunda.tngp.broker.test.MockStreamProcessorController;
import org.camunda.tngp.broker.transport.clientapi.CommandResponseWriter;
import org.camunda.tngp.broker.util.msgpack.value.ArrayValueIterator;
import org.camunda.tngp.broker.workflow.data.DeployedWorkflow;
import org.camunda.tngp.broker.workflow.data.WorkflowDeploymentEvent;
import org.camunda.tngp.broker.workflow.data.WorkflowDeploymentEventType;
import org.camunda.tngp.hashindex.store.IndexStore;
import org.camunda.tngp.logstreams.log.LogStream;
import org.camunda.tngp.logstreams.processor.StreamProcessorContext;
import org.camunda.tngp.protocol.clientapi.EventType;
import org.camunda.tngp.test.util.FluentMock;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class DeploymentStreamProcessorTest
{

    private DeploymentStreamProcessor streamProcessor;

    @FluentMock
    private CommandResponseWriter mockResponseWriter;

    @Rule
    public MockStreamProcessorController<WorkflowDeploymentEvent> mockController = new MockStreamProcessorController<>(WorkflowDeploymentEvent.class, EventType.DEPLOYMENT_EVENT);

    @Mock
    private LogStream mockLogStream;

    @Mock
    private IndexStore mockIndexStore;

    @Before
    public void init()
    {
        MockitoAnnotations.initMocks(this);


        streamProcessor = new DeploymentStreamProcessor(mockResponseWriter, mockIndexStore);

        final StreamProcessorContext context = new StreamProcessorContext();
        context.setSourceStream(mockLogStream);
        context.setTargetStream(mockLogStream);

        mockController.initStreamProcessor(streamProcessor, context);
    }

    @Test
    public void shouldCreateDeployment()
    {
        // given
        final BpmnModelInstance bpmnModelInstance = Bpmn.createExecutableProcess("process")
            .startEvent()
            .endEvent()
            .done();

        // when
        mockController.processEvent(1L, event -> event
                .setEventType(WorkflowDeploymentEventType.CREATE_DEPLOYMENT)
                .setBpmnXml(asBuffer(bpmnModelInstance)));

        // then
        assertThat(mockController.getLastWrittenEventValue().getEventType()).isEqualTo(WorkflowDeploymentEventType.DEPLOYMENT_CREATED);
        assertThat(mockController.getLastWrittenEventMetadata().getProtocolVersion()).isEqualTo(Constants.PROTOCOL_VERSION);

        verify(mockResponseWriter).key(1L);
        verify(mockResponseWriter).tryWriteResponse();
    }

    @Test
    public void shouldSetInitialVersionForNewWorkflow()
    {
        // given
        final BpmnModelInstance bpmnModelInstance = Bpmn.createExecutableProcess("process")
            .startEvent()
            .endEvent()
            .done();

        // when
        mockController.processEvent(1L, event -> event
                .setEventType(WorkflowDeploymentEventType.CREATE_DEPLOYMENT)
                .setBpmnXml(asBuffer(bpmnModelInstance)));

        // then
        int deployedWorkflowCount = 0;

        final ArrayValueIterator<DeployedWorkflow> deployedWorkflows = mockController.getLastWrittenEventValue().deployedWorkflows();
        while (deployedWorkflows.hasNext())
        {
            deployedWorkflowCount += 1;

            final DeployedWorkflow deployedWorkflow = deployedWorkflows.next();

            assertThatBuffer(deployedWorkflow.getBpmnProcessId()).hasBytes(getBytes("process"));
            assertThat(deployedWorkflow.getVersion()).isEqualTo(1);
        }

        assertThat(deployedWorkflowCount).isEqualTo(1);
    }

    @Test
    public void shouldIncrementVersionForUpdatedWorkflow()
    {
        // given
        final BpmnModelInstance bpmnModelInstance = Bpmn.createExecutableProcess("process")
            .startEvent()
            .endEvent()
            .done();

        mockController.processEvent(1L, event -> event
                .setEventType(WorkflowDeploymentEventType.CREATE_DEPLOYMENT)
                .setBpmnXml(asBuffer(bpmnModelInstance)));

        // when
        mockController.processEvent(2L, event -> event
                .setEventType(WorkflowDeploymentEventType.CREATE_DEPLOYMENT)
                .setBpmnXml(asBuffer(bpmnModelInstance)));

        // then
        int deployedWorkflowCount = 0;

        final ArrayValueIterator<DeployedWorkflow> deployedWorkflows = mockController.getLastWrittenEventValue().deployedWorkflows();
        while (deployedWorkflows.hasNext())
        {
            deployedWorkflowCount += 1;

            final DeployedWorkflow deployedWorkflow = deployedWorkflows.next();

            assertThatBuffer(deployedWorkflow.getBpmnProcessId()).hasBytes(getBytes("process"));
            assertThat(deployedWorkflow.getVersion()).isEqualTo(2);
        }

        assertThat(deployedWorkflowCount).isEqualTo(1);
    }

    @Test
    public void shouldRejectCreateDeploymentIfNotValid()
    {
        // given
        final BpmnModelInstance bpmnModelInstance = Bpmn.createProcess("process")
            .done();

        // when
        mockController.processEvent(1L, event -> event
                .setEventType(WorkflowDeploymentEventType.CREATE_DEPLOYMENT)
                .setBpmnXml(asBuffer(bpmnModelInstance)));

        // then
        assertThat(mockController.getLastWrittenEventValue().getEventType()).isEqualTo(WorkflowDeploymentEventType.DEPLOYMENT_REJECTED);
        assertThat(mockController.getLastWrittenEventValue().getErrorMessage().capacity()).isGreaterThan(0);
        assertThat(mockController.getLastWrittenEventMetadata().getProtocolVersion()).isEqualTo(Constants.PROTOCOL_VERSION);

        verify(mockResponseWriter).key(1L);
        verify(mockResponseWriter).tryWriteResponse();
    }

    @Test
    public void shouldRejectCreateDeploymentIfNotParsable()
    {
        // given
        final DirectBuffer resource = wrapString("foo");

        // when
        mockController.processEvent(1L, event -> event
                .setEventType(WorkflowDeploymentEventType.CREATE_DEPLOYMENT)
                .setBpmnXml(resource));

        // then
        assertThat(mockController.getLastWrittenEventValue().getEventType()).isEqualTo(WorkflowDeploymentEventType.DEPLOYMENT_REJECTED);
        assertThat(mockController.getLastWrittenEventValue().getErrorMessage().capacity()).isGreaterThan(0);
        assertThat(mockController.getLastWrittenEventMetadata().getProtocolVersion()).isEqualTo(Constants.PROTOCOL_VERSION);

        verify(mockResponseWriter).key(1L);
        verify(mockResponseWriter).tryWriteResponse();
    }

    private DirectBuffer asBuffer(BpmnModelInstance bpmnModelInstance)
    {
        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        Bpmn.writeModelToStream(outputStream, bpmnModelInstance);

        return new UnsafeBuffer(outputStream.toByteArray());
    }

}
