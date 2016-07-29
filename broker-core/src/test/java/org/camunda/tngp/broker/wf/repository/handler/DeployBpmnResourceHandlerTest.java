package org.camunda.tngp.broker.wf.repository.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.tngp.broker.test.util.BufferMatcher.hasBytes;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.argThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.tngp.broker.test.util.FluentAnswer;
import org.camunda.tngp.broker.wf.WfErrors;
import org.camunda.tngp.broker.wf.repository.WfRepositoryContext;
import org.camunda.tngp.broker.wf.repository.log.WfDefinitionReader;
import org.camunda.tngp.broker.wf.repository.log.WfDefinitionWriter;
import org.camunda.tngp.log.LogEntryWriter;
import org.camunda.tngp.protocol.error.ErrorWriter;
import org.camunda.tngp.protocol.wf.Constants;
import org.camunda.tngp.protocol.wf.DeployBpmnResourceAckResponse;
import org.camunda.tngp.protocol.wf.repository.DeployBpmnResourceEncoder;
import org.camunda.tngp.protocol.wf.repository.MessageHeaderEncoder;
import org.camunda.tngp.transport.requestresponse.server.DeferredResponse;
import org.junit.Before;
import org.junit.Test;

import uk.co.real_logic.agrona.DirectBuffer;
import uk.co.real_logic.agrona.concurrent.UnsafeBuffer;

public class DeployBpmnResourceHandlerTest
{
    protected WfDefinitionWriter wfDefinitionWriterMock;
    protected WfDefinitionReader wfDefinitionReaderMock;

    protected DeployBpmnResourceAckResponse responseWriterMock;
    protected ErrorWriter errorResponseWriterMock;

    protected DeployBpmnResourceHandler handler;

    protected WfRepositoryContext context;
    protected DeferredResponse deferredResponseMock;

    protected LogEntryWriter logEntryWriterMock;

    @Before
    public void setup()
    {
        context = new MockedWfRepositoryContext();

        deferredResponseMock = mock(DeferredResponse.class);
        when(deferredResponseMock.allocate(anyInt())).thenReturn(true);
        when(deferredResponseMock.getBuffer()).thenReturn(new UnsafeBuffer(new byte[2048]));

        wfDefinitionWriterMock = mock(WfDefinitionWriter.class, new FluentAnswer());
        wfDefinitionReaderMock = mock(WfDefinitionReader.class);
        responseWriterMock = mock(DeployBpmnResourceAckResponse.class, new FluentAnswer());
        errorResponseWriterMock = mock(ErrorWriter.class, new FluentAnswer());
        logEntryWriterMock = mock(LogEntryWriter.class);

        handler = new DeployBpmnResourceHandler();

        handler.wfDefinitionWriter = wfDefinitionWriterMock;
        handler.wfDefinitionReader = wfDefinitionReaderMock;
        handler.responseWriter = responseWriterMock;
        handler.errorResponseWriter = errorResponseWriterMock;
        handler.logEntryWriter = logEntryWriterMock;
    }

    @Test
    public void shouldDeployInitialVersion()
    {
        final long typeId = 101L;
        final String procesId = "someProcessId";
        final byte[] processIdBytes = procesId.getBytes(StandardCharsets.UTF_8);

        final byte[] resource = asByteArray(Bpmn.createExecutableProcess(procesId).startEvent().done());
        final DirectBuffer msgBuffer = writeRequest(resource);
        final int msgLength = msgBuffer.capacity();

        when(context.getWfDefinitionIdGenerator().nextId()).thenReturn(typeId);
        when(context.getWfDefinitionKeyIndex().getIndex().get(any(byte[].class), anyLong())).thenReturn(-1L);
        when(context.getWfDefinitionIdIndex().getIndex().get(anyLong(), anyLong())).thenReturn(-1L);

        when(deferredResponseMock.allocateAndWrite(responseWriterMock)).thenReturn(true);
        when(logEntryWriterMock.write(context.getWfDefinitionLog(), wfDefinitionWriterMock)).thenReturn(0L);
        when(deferredResponseMock.defer(0L, handler)).thenReturn(1);

        final long result = handler.onRequest(context, msgBuffer, 0, msgLength, deferredResponseMock);

        assertThat(result).isEqualTo(1);

        verify(wfDefinitionWriterMock).id(typeId);
        verify(wfDefinitionWriterMock).version(0);
        verify(wfDefinitionWriterMock).wfDefinitionKey(processIdBytes);
        verify(wfDefinitionWriterMock).prevVersionPosition(-1);
        verify(wfDefinitionWriterMock).resource(
                argThat(hasBytes(resource).atPosition(0)),
                eq(0),
                eq(resource.length));
        verify(logEntryWriterMock).write(context.getWfDefinitionLog(), wfDefinitionWriterMock);

        verify(responseWriterMock).wfDefinitionId(typeId);
        verify(deferredResponseMock).allocateAndWrite(responseWriterMock);

        verify(deferredResponseMock).defer(0L, handler);
    }

    @Test
    public void shouldDeploySecondVersion()
    {
        final long typeId = 101L;
        final String procesId = "someProcessId";
        final byte[] processIdBytes = procesId.getBytes(StandardCharsets.UTF_8);

        final byte[] resource = asByteArray(Bpmn.createExecutableProcess(procesId).startEvent().done());
        final DirectBuffer msgBuffer = writeRequest(resource);
        final int msgLength = msgBuffer.capacity();

        when(context.getWfDefinitionIdGenerator().nextId()).thenReturn(typeId);
        when(context.getWfDefinitionKeyIndex().getIndex().get(any(byte[].class), eq(-1L))).thenReturn(100L);
        when(context.getWfDefinitionIdIndex().getIndex().get(100L, -1)).thenReturn(200L);

        when(deferredResponseMock.allocateAndWrite(responseWriterMock)).thenReturn(true);
        when(logEntryWriterMock.write(context.getWfDefinitionLog(), wfDefinitionWriterMock)).thenReturn(0L);
        when(deferredResponseMock.defer(0L, handler)).thenReturn(1);
        when(wfDefinitionReaderMock.version()).thenReturn(4);

        final long result = handler.onRequest(context, msgBuffer, 0, msgLength, deferredResponseMock);

        assertThat(result).isEqualTo(1);

        verify(wfDefinitionWriterMock).id(typeId);
        verify(wfDefinitionWriterMock).version(5);
        verify(wfDefinitionWriterMock).wfDefinitionKey(processIdBytes);
        verify(wfDefinitionWriterMock).prevVersionPosition(200L);
        verify(wfDefinitionWriterMock).resource(
                argThat(hasBytes(resource).atPosition(0)),
                eq(0),
                eq(resource.length));
        verify(logEntryWriterMock).write(context.getWfDefinitionLog(), wfDefinitionWriterMock);

        verify(responseWriterMock).wfDefinitionId(typeId);
        verify(deferredResponseMock).allocateAndWrite(responseWriterMock);

        verify(deferredResponseMock).defer(0L, handler);
    }

    @Test
    public void shouldRejectInvalidProcess()
    {
        final byte[] resource = "not-bpmn".getBytes(StandardCharsets.UTF_8);
        final DirectBuffer msgBuffer = writeRequest(resource);
        final int msgLength = msgBuffer.capacity();

        when(deferredResponseMock.allocateAndWrite(errorResponseWriterMock)).thenReturn(true);

        final long result = handler.onRequest(context, msgBuffer, 0, msgLength, deferredResponseMock);

        assertThat(result).isEqualTo(1);

        verify(errorResponseWriterMock).componentCode(WfErrors.COMPONENT_CODE);
        verify(errorResponseWriterMock).detailCode(WfErrors.DEPLOYMENT_ERROR);
        verify(errorResponseWriterMock).errorMessage(any(String.class));
        verify(deferredResponseMock).allocateAndWrite(errorResponseWriterMock);
        verify(deferredResponseMock).commit();

        verify(wfDefinitionWriterMock, never()).write(any(), anyInt());
    }

    @Test
    public void shouldRejectIfIdExceedsMaxLength()
    {
        String procesId = "some-l";

        while (procesId.getBytes(StandardCharsets.UTF_8).length < Constants.WF_DEF_KEY_MAX_LENGTH)
        {
            procesId += "o";
        }

        procesId += "ng-process-id";

        final byte[] resource = asByteArray(Bpmn.createExecutableProcess(procesId).startEvent().done());
        final DirectBuffer msgBuffer = writeRequest(resource);
        final int msgLength = msgBuffer.capacity();

        when(deferredResponseMock.allocateAndWrite(errorResponseWriterMock)).thenReturn(true);

        final long result = handler.onRequest(context, msgBuffer, 0, msgLength, deferredResponseMock);

        assertThat(result).isEqualTo(1);

        verify(errorResponseWriterMock).componentCode(WfErrors.COMPONENT_CODE);
        verify(errorResponseWriterMock).detailCode(WfErrors.DEPLOYMENT_ERROR);
        verify(errorResponseWriterMock).errorMessage(any(String.class));
        verify(deferredResponseMock).allocateAndWrite(errorResponseWriterMock);
        verify(deferredResponseMock).commit();

        verify(wfDefinitionWriterMock, never()).write(any(), anyInt());
    }

    private static byte[] asByteArray(final BpmnModelInstance bpmnModelInstance)
    {
        final ByteArrayOutputStream modelStream = new ByteArrayOutputStream();
        Bpmn.writeModelToStream(modelStream, bpmnModelInstance);
        final byte[] resource = modelStream.toByteArray();
        return resource;
    }

    private DirectBuffer writeRequest(final byte[] resource)
    {
        final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();

        final DeployBpmnResourceEncoder reqEncoder = new DeployBpmnResourceEncoder();

        final int msgLength =
                MessageHeaderEncoder.ENCODED_LENGTH +
                reqEncoder.sbeBlockLength() +
                DeployBpmnResourceEncoder.resourceHeaderLength() +
                resource.length;

        final UnsafeBuffer msgBuffer = new UnsafeBuffer(new byte[msgLength]);

        headerEncoder.wrap(msgBuffer, 0)
            .blockLength(reqEncoder.sbeBlockLength())
            .resourceId(425252)
            .schemaId(reqEncoder.sbeSchemaId())
            .shardId(526285)
            .templateId(reqEncoder.sbeTemplateId())
            .version(reqEncoder.sbeSchemaVersion());

        reqEncoder.wrap(msgBuffer, MessageHeaderEncoder.ENCODED_LENGTH)
            .putResource(resource, 0, resource.length);

        return msgBuffer;
    }

}
