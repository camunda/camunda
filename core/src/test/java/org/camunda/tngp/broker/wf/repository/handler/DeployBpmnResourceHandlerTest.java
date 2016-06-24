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
import org.camunda.tngp.broker.wf.WfErrors;
import org.camunda.tngp.broker.wf.repository.WfRepositoryContext;
import org.camunda.tngp.broker.wf.repository.log.WfTypeReader;
import org.camunda.tngp.broker.wf.repository.log.WfTypeWriter;
import org.camunda.tngp.log.LogEntryWriter;
import org.camunda.tngp.protocol.error.ErrorWriter;
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
    protected WfTypeWriter wfTypeWriterMock;
    protected WfTypeReader wfTypeReaderMock;

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

        wfTypeWriterMock = mock(WfTypeWriter.class, new FluentAnswer());
        wfTypeReaderMock = mock(WfTypeReader.class);
        responseWriterMock = mock(DeployBpmnResourceAckResponse.class, new FluentAnswer());
        errorResponseWriterMock = mock(ErrorWriter.class, new FluentAnswer());
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
        final long typeId = 101L;
        final String procesId = "someProcessId";
        final byte[] processIdBytes = procesId.getBytes(StandardCharsets.UTF_8);

        final byte[] resource = asByteArray(Bpmn.createExecutableProcess(procesId).startEvent().done());
        final DirectBuffer msgBuffer = writeRequest(resource);
        final int msgLength = msgBuffer.capacity();

        when(context.getWfTypeIdGenerator().nextId()).thenReturn(typeId);
        when(context.getWfTypeKeyIndex().getIndex().get(any(byte[].class), anyLong())).thenReturn(-1L);
        when(context.getWfTypeIdIndex().getIndex().get(anyLong(), anyLong())).thenReturn(-1L);

        when(deferredResponseMock.allocateAndWrite(responseWriterMock)).thenReturn(true);
        when(logEntryWriterMock.write(context.getWfTypeLog(), wfTypeWriterMock)).thenReturn(0l);
        when(deferredResponseMock.defer(0l, handler)).thenReturn(1);

        final long result = handler.onRequest(context, msgBuffer, 0, msgLength, deferredResponseMock);

        assertThat(result).isEqualTo(1);

        verify(wfTypeWriterMock).id(typeId);
        verify(wfTypeWriterMock).version(0);
        verify(wfTypeWriterMock).wfTypeKey(processIdBytes);
        verify(wfTypeWriterMock).prevVersionPosition(-1);
        verify(wfTypeWriterMock).resource(
                argThat(hasBytes(resource).atPosition(0)),
                eq(0),
                eq(resource.length));
        verify(logEntryWriterMock).write(context.getWfTypeLog(), wfTypeWriterMock);

        verify(responseWriterMock).wfTypeId(typeId);
        verify(deferredResponseMock).allocateAndWrite(responseWriterMock);

<<<<<<< HEAD
        verify(deferredResponseMock).defer(0L, handler, null);
=======
        verify(deferredResponseMock).defer(0l, handler);
>>>>>>> test start process instance handling
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

        when(context.getWfTypeIdGenerator().nextId()).thenReturn(typeId);
        when(context.getWfTypeKeyIndex().getIndex().get(any(byte[].class), eq(-1L))).thenReturn(100L);
        when(context.getWfTypeIdIndex().getIndex().get(100L, -1)).thenReturn(200L);

        when(deferredResponseMock.allocateAndWrite(responseWriterMock)).thenReturn(true);
<<<<<<< HEAD
        when(logEntryWriterMock.write(context.getWfTypeLog(), wfTypeWriterMock)).thenReturn(0L);
        when(deferredResponseMock.defer(0L, handler, null)).thenReturn(1);
=======
        when(logEntryWriterMock.write(context.getWfTypeLog(), wfTypeWriterMock)).thenReturn(0l);
        when(deferredResponseMock.defer(0l, handler)).thenReturn(1);
>>>>>>> test start process instance handling
        when(wfTypeReaderMock.version()).thenReturn(4);

        final long result = handler.onRequest(context, msgBuffer, 0, msgLength, deferredResponseMock);

        assertThat(result).isEqualTo(1);

        verify(wfTypeWriterMock).id(typeId);
        verify(wfTypeWriterMock).version(5);
        verify(wfTypeWriterMock).wfTypeKey(processIdBytes);
        verify(wfTypeWriterMock).prevVersionPosition(200L);
        verify(wfTypeWriterMock).resource(
                argThat(hasBytes(resource).atPosition(0)),
                eq(0),
                eq(resource.length));
        verify(logEntryWriterMock).write(context.getWfTypeLog(), wfTypeWriterMock);

        verify(responseWriterMock).wfTypeId(typeId);
        verify(deferredResponseMock).allocateAndWrite(responseWriterMock);

<<<<<<< HEAD
        verify(deferredResponseMock).defer(0L, handler, null);
=======
        verify(deferredResponseMock).defer(0l, handler);
>>>>>>> test start process instance handling
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

        verify(wfTypeWriterMock, never()).write(any(), anyInt());
    }

    @Test
    public void shouldRejectIfIdExceedsMaxLength()
    {
        String procesId = "some-l";

        while (procesId.getBytes(StandardCharsets.UTF_8).length < DeployBpmnResourceHandler.WF_TYPE_KEY_MAX_LENGTH)
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

        verify(wfTypeWriterMock, never()).write(any(), anyInt());
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
