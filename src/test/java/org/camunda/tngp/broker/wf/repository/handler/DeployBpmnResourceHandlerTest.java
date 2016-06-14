package org.camunda.tngp.broker.wf.repository.handler;

import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.tngp.broker.wf.repository.WfRepositoryContext;
import org.camunda.tngp.broker.wf.repository.log.WfTypeReader;
import org.camunda.tngp.broker.wf.repository.log.WfTypeWriter;
import org.camunda.tngp.broker.wf.repository.response.DeployBpmnResourceAckResponse;
import org.camunda.tngp.broker.wf.repository.response.DeployBpmnResourceErrorResponseWriter;
import org.camunda.tngp.dispatcher.ClaimedFragment;
import org.camunda.tngp.log.LogEntryWriter;
import org.camunda.tngp.protocol.wf.DeployBpmnResourceEncoder;
import org.camunda.tngp.transport.requestresponse.server.DeferredResponse;
import org.junit.Before;
import org.junit.Test;

import uk.co.real_logic.agrona.DirectBuffer;
import uk.co.real_logic.agrona.concurrent.UnsafeBuffer;

public class DeployBpmnResourceHandlerTest
{
    WfTypeWriter wfTypeWriterMock;
    WfTypeReader wfTypeReaderMock;

    DeployBpmnResourceAckResponse responseWriterMock;
    DeployBpmnResourceErrorResponseWriter errorResponseWriterMock;

    DeployBpmnResourceHandler handler;

    WfRepositoryContext context;
    DeferredResponse deferredResponseMock;

    ClaimedFragment claimedLogFragment;
    LogEntryWriter logEntryWriterMock;

    @Before
    public void setup()
    {
        context = new MockedWfRepositoryContext();

        deferredResponseMock = mock(DeferredResponse.class);
        when(deferredResponseMock.allocate(anyInt())).thenReturn(true);
        when(deferredResponseMock.getBuffer()).thenReturn(new UnsafeBuffer(new byte[2048]));

        wfTypeWriterMock = mock(WfTypeWriter.class, new FluentAnswer());
        wfTypeReaderMock = mock(WfTypeReader.class);
        responseWriterMock = mock(DeployBpmnResourceAckResponse.class, new FluentAnswer());
        errorResponseWriterMock = mock(DeployBpmnResourceErrorResponseWriter.class, new FluentAnswer());
        logEntryWriterMock = mock(LogEntryWriter.class);

        handler = new DeployBpmnResourceHandler();

        handler.wfTypeWriter = wfTypeWriterMock;
        handler.wfTypeReader = wfTypeReaderMock;
        handler.responseWriter = responseWriterMock;
        handler.errorResponseWriter = errorResponseWriterMock;
        handler.logEntryWriter = logEntryWriterMock;
    }

    @Test
    public void shouldDeployInitialVersion()
    {
        final long typeId = 101l;
        final String procesId = "someProcessId";
        final byte[] processIdBytes = procesId.getBytes(StandardCharsets.UTF_8);

        final byte[] resource = asByteArray(Bpmn.createExecutableProcess(procesId).startEvent().done());
        final DirectBuffer msgBuffer = writeRequest(resource);
        final int msgLength = msgBuffer.capacity();

        when(context.getWfTypeIdGenerator().nextId()).thenReturn(typeId);
        when(context.getWfTypeKeyIndex().getIndex().get(any(byte[].class), anyLong())).thenReturn(-1l);
        when(context.getWfTypeIdIndex().getIndex().get(anyLong(), anyLong())).thenReturn(-1l);

        when(deferredResponseMock.allocateAndWrite(responseWriterMock)).thenReturn(true);
        when(logEntryWriterMock.write(context.getWfTypeLog(), wfTypeWriterMock)).thenReturn(0l);
        when(deferredResponseMock.defer(0l, handler, null)).thenReturn(1);

        long result = handler.onRequest(context, msgBuffer, 0, msgLength, deferredResponseMock, DeployBpmnResourceEncoder.BLOCK_LENGTH, DeployBpmnResourceEncoder.SCHEMA_VERSION);

        assertThat(result).isEqualTo(1);

        verify(wfTypeWriterMock).id(typeId);
        verify(wfTypeWriterMock).version(0);
        verify(wfTypeWriterMock).wfTypeKey(processIdBytes);
        verify(wfTypeWriterMock).prevVersionPosition(-1);
        verify(wfTypeWriterMock).resource(msgBuffer, DeployBpmnResourceEncoder.BLOCK_LENGTH + DeployBpmnResourceEncoder.resourceHeaderLength(), resource.length);
        verify(logEntryWriterMock).write(context.getWfTypeLog(), wfTypeWriterMock);

        verify(responseWriterMock).wfTypeId(typeId);
        verify(deferredResponseMock).allocateAndWrite(responseWriterMock);

        verify(deferredResponseMock).defer(0l, handler, null);
    }

    @Test
    public void shouldDeploySecondVersion()
    {
        final long typeId = 101l;
        final String procesId = "someProcessId";
        final byte[] processIdBytes = procesId.getBytes(StandardCharsets.UTF_8);

        final byte[] resource = asByteArray(Bpmn.createExecutableProcess(procesId).startEvent().done());
        final DirectBuffer msgBuffer = writeRequest(resource);
        final int msgLength = msgBuffer.capacity();

        when(context.getWfTypeIdGenerator().nextId()).thenReturn(typeId);
        when(context.getWfTypeKeyIndex().getIndex().get(any(byte[].class), eq(-1l))).thenReturn(100l);
        when(context.getWfTypeIdIndex().getIndex().get(100l, -1)).thenReturn(200l);

        when(deferredResponseMock.allocateAndWrite(responseWriterMock)).thenReturn(true);
        when(logEntryWriterMock.write(context.getWfTypeLog(), wfTypeWriterMock)).thenReturn(0l);
        when(deferredResponseMock.defer(0l, handler, null)).thenReturn(1);
        when(wfTypeReaderMock.version()).thenReturn(4);

        long result = handler.onRequest(context, msgBuffer, 0, msgLength, deferredResponseMock, DeployBpmnResourceEncoder.BLOCK_LENGTH, DeployBpmnResourceEncoder.SCHEMA_VERSION);

        assertThat(result).isEqualTo(1);

        verify(wfTypeWriterMock).id(typeId);
        verify(wfTypeWriterMock).version(5);
        verify(wfTypeWriterMock).wfTypeKey(processIdBytes);
        verify(wfTypeWriterMock).prevVersionPosition(200l);
        verify(wfTypeWriterMock).resource(msgBuffer, DeployBpmnResourceEncoder.BLOCK_LENGTH + DeployBpmnResourceEncoder.resourceHeaderLength(), resource.length);
        verify(logEntryWriterMock).write(context.getWfTypeLog(), wfTypeWriterMock);

        verify(responseWriterMock).wfTypeId(typeId);
        verify(deferredResponseMock).allocateAndWrite(responseWriterMock);

        verify(deferredResponseMock).defer(0l, handler, null);
    }

    @Test
    public void shouldRejectInvalidProcess()
    {
        final byte[] resource = "not-bpmn".getBytes(StandardCharsets.UTF_8);
        final DirectBuffer msgBuffer = writeRequest(resource);
        final int msgLength = msgBuffer.capacity();

        when(deferredResponseMock.allocateAndWrite(errorResponseWriterMock)).thenReturn(true);

        long result = handler.onRequest(context, msgBuffer, 0, msgLength, deferredResponseMock, DeployBpmnResourceEncoder.BLOCK_LENGTH, DeployBpmnResourceEncoder.SCHEMA_VERSION);

        assertThat(result).isEqualTo(1);

        verify(errorResponseWriterMock).errorMessage(any(byte[].class));
        verify(deferredResponseMock).allocateAndWrite(errorResponseWriterMock);
        verify(deferredResponseMock).commit();
    }

    @Test
    public void shouldRejectIfIdExceedsMaxLength()
    {
        String procesId = "some-l";

        while(procesId.getBytes(StandardCharsets.UTF_8).length < DeployBpmnResourceHandler.WF_TYPE_KEY_MAX_LENGTH)
        {
            procesId += "o";
        }

        procesId += "ng-process-id";

        final byte[] resource = asByteArray(Bpmn.createExecutableProcess(procesId).startEvent().done());
        final DirectBuffer msgBuffer = writeRequest(resource);
        final int msgLength = msgBuffer.capacity();

        when(deferredResponseMock.allocateAndWrite(errorResponseWriterMock)).thenReturn(true);

        long result = handler.onRequest(context, msgBuffer, 0, msgLength, deferredResponseMock, DeployBpmnResourceEncoder.BLOCK_LENGTH, DeployBpmnResourceEncoder.SCHEMA_VERSION);

        assertThat(result).isEqualTo(1);

        verify(errorResponseWriterMock).errorMessage(any(byte[].class));
        verify(deferredResponseMock).allocateAndWrite(errorResponseWriterMock);
        verify(deferredResponseMock).commit();
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
        final DeployBpmnResourceEncoder reqEncoder = new DeployBpmnResourceEncoder();

        final int msgLength = reqEncoder.sbeBlockLength()
                + DeployBpmnResourceEncoder.resourceHeaderLength()
                + resource.length;

        final UnsafeBuffer msgBuffer = new UnsafeBuffer(new byte[msgLength]);

        reqEncoder.wrap(msgBuffer, 0)
            .putResource(resource, 0, resource.length);

        return msgBuffer;
    }

}
