package org.camunda.tngp.broker.log;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import org.camunda.tngp.broker.log.LogEntryHeaderReader.EventSource;
import org.camunda.tngp.broker.util.mocks.StubLogReader;
import org.camunda.tngp.broker.wf.repository.log.WfDefinitionReader;
import org.camunda.tngp.broker.wf.repository.log.WfDefinitionRequestReader;
import org.camunda.tngp.broker.wf.repository.log.WfDefinitionRequestWriter;
import org.camunda.tngp.broker.wf.repository.log.WfDefinitionWriter;
import org.camunda.tngp.protocol.error.ErrorWriter;
import org.camunda.tngp.taskqueue.data.WfDefinitionRequestType;
import org.camunda.tngp.transport.requestresponse.server.DeferredResponse;
import org.camunda.tngp.transport.requestresponse.server.DeferredResponsePool;
import org.camunda.tngp.util.buffer.BufferWriter;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import uk.co.real_logic.agrona.concurrent.UnsafeBuffer;

public class LogConsumerTest
{
    protected StubLogReader logReader;

    @Mock
    protected DeferredResponsePool responsePool;

    @Mock
    protected DeferredResponse response;

    @Before
    public void setUp()
    {
        MockitoAnnotations.initMocks(this);

        logReader = new StubLogReader(null);
    }

    @Test
    public void shouldHandleKnownTemplate()
    {
        // given
        final LogConsumer logConsumer = new LogConsumer(logReader, Templates.wfRepositoryLogTemplates());

        final ExampleHandler handler = new ExampleHandler();
        logConsumer.addHandler(Templates.WF_DEFINITION, handler);

        final WfDefinitionWriter writer = new WfDefinitionWriter();
        writer.id(123L);
        logReader.addEntry(writer);

        // when
        logConsumer.doConsume();

        // then
        assertThat(handler.numInvocations).isEqualTo(1);

    }

    @Test
    public void shouldNotHandleEventTwice()
    {
        // given
        final LogConsumer logConsumer = new LogConsumer(logReader, Templates.wfRepositoryLogTemplates());

        final ExampleHandler handler = new ExampleHandler();
        logConsumer.addHandler(Templates.WF_DEFINITION, handler);

        final WfDefinitionWriter writer = new WfDefinitionWriter();
        writer.id(123L);
        logReader.addEntry(writer);

        // event is already consumed
        logConsumer.doConsume();
        handler.numInvocations = 0;

        // when consuming a second time
        logConsumer.doConsume();

        // then the handler is not invoked again with that event
        assertThat(handler.numInvocations).isEqualTo(0);
    }

    @Test
    public void shouldHandleNewEvent()
    {
        // given
        final LogConsumer logConsumer = new LogConsumer(logReader, Templates.wfRepositoryLogTemplates());

        final ExampleHandler handler = new ExampleHandler();
        logConsumer.addHandler(Templates.WF_DEFINITION, handler);

        final WfDefinitionWriter writer = new WfDefinitionWriter();
        writer.id(123L);
        logReader.addEntry(writer);

        // first event is already consumed
        logConsumer.doConsume();
        handler.numInvocations = 0;

        // and a new event is written
        writer.id(456L);
        logReader.addEntry(writer);

        // when consuming a second time
        logConsumer.doConsume();

        // then the handler is invoked again
        assertThat(handler.numInvocations).isEqualTo(1);
    }


    @Test
    public void shouldNotHandleWithoutHandler()
    {
        // given
        final LogConsumer logConsumer = new LogConsumer(logReader, Templates.wfRepositoryLogTemplates());

        final ExampleHandler handler = new ExampleHandler();
        logConsumer.addHandler(Templates.WF_DEFINITION, handler);

        final WfDefinitionRequestWriter writer = new WfDefinitionRequestWriter()
            .type(WfDefinitionRequestType.NEW)
            .source(EventSource.API);
        logReader.addEntry(writer);

        // when
        logConsumer.doConsume();

        // then
        assertThat(handler.numInvocations).isEqualTo(0);
    }

    @Test
    public void shouldResolveFirstResponseForAcceptedApiRequest()
    {
        // given
        final LogConsumer logConsumer = new LogConsumer(logReader, responsePool, Templates.wfRepositoryLogTemplates());
        final AcceptingHandler requestHandler = new AcceptingHandler();
        logConsumer.addHandler(Templates.WF_DEFINITION_REQUEST, requestHandler);

        final WfDefinitionRequestWriter requestWriter = new WfDefinitionRequestWriter();
        requestWriter.source(EventSource.API);
        requestWriter.type(WfDefinitionRequestType.NEW);
        requestWriter.resource(new UnsafeBuffer(new byte[3]), 0, 3);
        logReader.addEntry(requestWriter);

        when(responsePool.popDeferred()).thenReturn(response, (DeferredResponse) null);

        // when
        logConsumer.doConsume();

        // then
        final InOrder inOrder = inOrder(response);
        inOrder.verify(response).allocateAndWrite(requestHandler.responseWriter);
        inOrder.verify(response).commit();
        verifyNoMoreInteractions(response);
    }

    @Test
    public void shouldResolveFirstResponseForRejectedApiRequest()
    {
        // given
        final LogConsumer logConsumer = new LogConsumer(logReader, responsePool, Templates.wfRepositoryLogTemplates());
        final RejectingHandler requestHandler = new RejectingHandler();
        logConsumer.addHandler(Templates.WF_DEFINITION_REQUEST, requestHandler);

        final WfDefinitionRequestWriter requestWriter = new WfDefinitionRequestWriter();
        requestWriter.source(EventSource.API);
        requestWriter.type(WfDefinitionRequestType.NEW);
        requestWriter.resource(new UnsafeBuffer(new byte[3]), 0, 3);
        logReader.addEntry(requestWriter);

        when(responsePool.popDeferred()).thenReturn(response, (DeferredResponse) null);

        // when
        logConsumer.doConsume();

        // then
        final InOrder inOrder = inOrder(response);
        inOrder.verify(response).allocateAndWrite(requestHandler.errorWriter);
        inOrder.verify(response).commit();
        verifyNoMoreInteractions(response);
    }

    public static class ExampleHandler implements LogEntryTypeHandler<WfDefinitionReader>
    {

        protected int numInvocations;

        @Override
        public void handle(WfDefinitionReader reader, ResponseControl responseControl)
        {
            numInvocations++;
        }
    }

    public static class AcceptingHandler implements LogEntryTypeHandler<WfDefinitionRequestReader>
    {

        BufferWriter responseWriter;

        @Override
        public void handle(WfDefinitionRequestReader reader, ResponseControl responseControl)
        {
            responseControl.accept(responseWriter);
        }
    }

    public static class RejectingHandler implements LogEntryTypeHandler<WfDefinitionRequestReader>
    {

        ErrorWriter errorWriter;

        @Override
        public void handle(WfDefinitionRequestReader reader, ResponseControl responseControl)
        {
            responseControl.reject(errorWriter);
        }
    }


}
