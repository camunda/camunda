package org.camunda.tngp.broker.log;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.longThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.agrona.concurrent.UnsafeBuffer;
import org.camunda.tngp.broker.log.LogEntryHeaderReader.EventSource;
import org.camunda.tngp.broker.log.idx.IndexWriter;
import org.camunda.tngp.broker.services.HashIndexManager;
import org.camunda.tngp.broker.util.mocks.StubLogReader;
import org.camunda.tngp.broker.util.mocks.StubLogWriter;
import org.camunda.tngp.broker.util.mocks.StubLogWriters;
import org.camunda.tngp.broker.wf.repository.log.WfDefinitionReader;
import org.camunda.tngp.broker.wf.repository.log.WfDefinitionRequestReader;
import org.camunda.tngp.broker.wf.repository.log.WfDefinitionRequestWriter;
import org.camunda.tngp.broker.wf.repository.log.WfDefinitionWriter;
import org.camunda.tngp.protocol.error.ErrorWriter;
import org.camunda.tngp.taskqueue.data.MessageHeaderEncoder;
import org.camunda.tngp.taskqueue.data.WfDefinitionRequestType;
import org.camunda.tngp.transport.requestresponse.server.DeferredResponse;
import org.camunda.tngp.transport.requestresponse.server.DeferredResponsePool;
import org.camunda.tngp.util.buffer.BufferWriter;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

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

        logReader = new StubLogReader(LogConsumer.LOG_INITIAL_POSITION, null);
    }

    @Test
    public void shouldHandleKnownTemplate()
    {
        // given
        final LogConsumer logConsumer = new LogConsumer(
                0,
                logReader,
                Templates.wfRepositoryLogTemplates(),
                new StubLogWriters(0));

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
        final LogConsumer logConsumer = new LogConsumer(
                0,
                logReader,
                Templates.wfRepositoryLogTemplates(),
                new StubLogWriters(0));

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
        final LogConsumer logConsumer = new LogConsumer(
                0,
                logReader,
                Templates.wfRepositoryLogTemplates(),
                new StubLogWriters(0));

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
        final LogConsumer logConsumer = new LogConsumer(
                0,
                logReader,
                Templates.wfRepositoryLogTemplates(),
                new StubLogWriters(0));

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
        final LogConsumer logConsumer = new LogConsumer(
                0,
                logReader,
                responsePool,
                Templates.wfRepositoryLogTemplates(),
                new StubLogWriters(0));

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
        final LogConsumer logConsumer = new LogConsumer(
                0,
                logReader,
                responsePool,
                Templates.wfRepositoryLogTemplates(),
                new StubLogWriters(0));

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

    @Test
    public void shouldSetLogEntryBackpointerOnSameLog()
    {
        // given
        final StubLogWriters logWriters = new StubLogWriters(10);
        final StubLogWriter targetWriter = new StubLogWriter();
        logWriters.addWriter(10, targetWriter);

        final LogConsumer logConsumer = new LogConsumer(
                10,
                logReader,
                Templates.wfRepositoryLogTemplates(),
                logWriters);

        final SameLogWritingHandler entryHandler = new SameLogWritingHandler();
        logConsumer.addHandler(Templates.WF_DEFINITION, entryHandler);

        final WfDefinitionWriter requestWriter = new WfDefinitionWriter();
        requestWriter.id(123L);
        logReader.addEntry(requestWriter);

        // when
        logConsumer.doConsume();

        // then
        assertThat(targetWriter.size()).isEqualTo(1);
        final LogEntryHeaderReader headerReader = targetWriter.getEntryAs(0, LogEntryHeaderReader.class);
        assertThat(headerReader.sourceEventLogId()).isEqualTo(10);
        assertThat(headerReader.sourceEventPosition()).isEqualTo((int) logReader.getEntryPosition(0)); // TODO: resolve int vs long
    }

    @Test
    public void shouldSetLogEntryBackpointerOnDifferentLog()
    {
        // given
        final StubLogWriters logWriters = new StubLogWriters(10);
        final StubLogWriter targetWriter = new StubLogWriter();
        logWriters.addWriter(11, targetWriter);

        final LogConsumer logConsumer = new LogConsumer(
                10,
                logReader,
                Templates.wfRepositoryLogTemplates(),
                logWriters);

        final DifferentLogWritingHandler entryHandler = new DifferentLogWritingHandler(11);
        logConsumer.addHandler(Templates.WF_DEFINITION, entryHandler);

        final WfDefinitionWriter requestWriter = new WfDefinitionWriter();
        requestWriter.id(123L);
        logReader.addEntry(requestWriter);

        // when
        logConsumer.doConsume();

        // then
        assertThat(targetWriter.size()).isEqualTo(1);
        final LogEntryHeaderReader headerReader = targetWriter.getEntryAs(0, LogEntryHeaderReader.class);
        assertThat(headerReader.sourceEventLogId()).isEqualTo(10);
        assertThat(headerReader.sourceEventPosition()).isEqualTo((int) logReader.getEntryPosition(0)); // TODO: resolve int vs long
    }

    @Test
    public void shouldInvokeIndexWriters()
    {
        // given
        final WfDefinitionWriter writer = new WfDefinitionWriter();
        writer.id(123L);
        logReader.addEntry(writer);

        final LogConsumer logConsumer = new LogConsumer(
                0,
                logReader,
                Templates.wfRepositoryLogTemplates(),
                new StubLogWriters(0));

        final ExampleHandler handler = new ExampleHandler();
        logConsumer.addHandler(Templates.WF_DEFINITION, handler);

        final IndexWriter indexWriter = mock(IndexWriter.class);
        final HashIndexManager indexManager = mock(HashIndexManager.class);
        when(indexWriter.getIndexManager()).thenReturn(indexManager);

        logConsumer.addIndexWriter(indexWriter);

        // when
        logConsumer.doConsume();

        // then
        verify(indexWriter, times(1)).indexLogEntry(eq(logReader.getEntryPosition(0)), any());
        verify(indexWriter, never()).indexLogEntry(longThat(not(equalTo(logReader.getEntryPosition(0)))), any());
    }

    @Test
    public void shouldWriteIndexSavepoints()
    {
        // given
        final LogConsumer logConsumer = new LogConsumer(
                0,
                logReader,
                Templates.wfRepositoryLogTemplates(),
                new StubLogWriters(0));

        final IndexWriter indexWriter = mock(IndexWriter.class);
        final HashIndexManager indexManager = mock(HashIndexManager.class);
        when(indexWriter.getIndexManager()).thenReturn(indexManager);
        when(indexManager.getLastCheckpointPosition()).thenReturn(LogConsumer.LOG_INITIAL_POSITION);

        logConsumer.addIndexWriter(indexWriter);

        // when
        logConsumer.writeSavepoints();

        // then
        verify(indexManager).writeCheckPoint(LogConsumer.LOG_INITIAL_POSITION);
    }

    @Test
    public void shouldWriteIndexSavepointsAfterIndexingEvents()
    {
        // given
        final LogConsumer logConsumer = new LogConsumer(
                0,
                logReader,
                Templates.wfRepositoryLogTemplates(),
                new StubLogWriters(0));

        final IndexWriter indexWriter = mock(IndexWriter.class);
        final HashIndexManager indexManager = mock(HashIndexManager.class);
        when(indexWriter.getIndexManager()).thenReturn(indexManager);
        when(indexManager.getLastCheckpointPosition()).thenReturn(LogConsumer.LOG_INITIAL_POSITION);

        logConsumer.addIndexWriter(indexWriter);

        final WfDefinitionWriter writer = new WfDefinitionWriter();
        writer.id(123L);
        logReader.addEntry(writer);

        logConsumer.doConsume();

        // when
        logConsumer.writeSavepoints();

        // then
        verify(indexManager).writeCheckPoint(logReader.getEntryPosition(0));
    }

    @Test
    public void shouldRecoverIndexWriters()
    {
        // given
        final LogConsumer logConsumer = new LogConsumer(
                0,
                logReader,
                Templates.wfRepositoryLogTemplates(),
                new StubLogWriters(0));

        final IndexWriter indexWriter = mock(IndexWriter.class);
        final HashIndexManager indexManager = mock(HashIndexManager.class);
        when(indexWriter.getIndexManager()).thenReturn(indexManager);
        when(indexManager.getLastCheckpointPosition()).thenReturn(LogConsumer.LOG_INITIAL_POSITION);

        writeEntry(logReader, 123L, MessageHeaderEncoder.sourceEventLogIdNullValue(), MessageHeaderEncoder.sourceEventPositionNullValue());
        writeEntry(logReader, 234L, 0, logReader.getEntryPosition(0));
        writeEntry(logReader, 345L, 0, logReader.getEntryPosition(1));

        when(indexManager.getLastCheckpointPosition()).thenReturn(logReader.getEntryPosition(0));

        // when
        logConsumer.addIndexWriter(indexWriter);
        logConsumer.recover(Arrays.asList(logReader));

        // then
        verify(indexWriter, times(1)).indexLogEntry(eq(logReader.getEntryPosition(1)), any());
        verify(indexWriter, never()).indexLogEntry(longThat(not(equalTo(logReader.getEntryPosition(1)))), any());
    }

    protected void writeEntry(StubLogReader logReader, long entryId, int sourceLogId, long sourcePosition)
    {
        final WfDefinitionWriter writer = new WfDefinitionWriter();

        writer
            .id(entryId)
            .sourceEventLogId(sourceLogId)
            .sourceEventPosition(sourcePosition);

        logReader.addEntry(writer);
    }

    @Test
    public void shouldRecoverLogConsumerPosition()
    {
        // given
        final LogConsumer logConsumer = new LogConsumer(
                0,
                logReader,
                Templates.wfRepositoryLogTemplates(),
                new StubLogWriters(0));

        writeEntry(logReader, 123L, MessageHeaderEncoder.sourceEventLogIdNullValue(), MessageHeaderEncoder.sourceEventPositionNullValue());
        writeEntry(logReader, 234L, 0, logReader.getEntryPosition(0));
        writeEntry(logReader, 345L, 0, logReader.getEntryPosition(1));

        final IdLoggingHandler handler = new IdLoggingHandler();
        logConsumer.addHandler(Templates.WF_DEFINITION, handler);

        // when
        logConsumer.recover(Arrays.asList(logReader));
        logConsumer.doConsume();

        // then
        assertThat(handler.ids).hasSize(1);
        assertThat(handler.ids).containsExactly(345L);
    }

    @Test
    public void shouldRecoverLogConsumerPositionBasedOnExternalLogs()
    {
        // given
        final LogConsumer logConsumer = new LogConsumer(
                0,
                logReader,
                Templates.wfRepositoryLogTemplates(),
                new StubLogWriters(0));

        final StubLogReader targetLogReader1 = new StubLogReader(LogConsumer.LOG_INITIAL_POSITION, null);
        final StubLogReader targetLogReader2 = new StubLogReader(LogConsumer.LOG_INITIAL_POSITION, null);

        writeEntry(logReader, 123L, MessageHeaderEncoder.sourceEventLogIdNullValue(), MessageHeaderEncoder.sourceEventPositionNullValue());
        writeEntry(logReader, 234L, MessageHeaderEncoder.sourceEventLogIdNullValue(), MessageHeaderEncoder.sourceEventPositionNullValue());
        writeEntry(logReader, 345L, MessageHeaderEncoder.sourceEventLogIdNullValue(), MessageHeaderEncoder.sourceEventPositionNullValue());

        writeEntry(targetLogReader1, 456L, 0, logReader.getEntryPosition(0));
        writeEntry(targetLogReader2, 567L, 0, logReader.getEntryPosition(1));

        final IdLoggingHandler handler = new IdLoggingHandler();
        logConsumer.addHandler(Templates.WF_DEFINITION, handler);

        // when
        logConsumer.recover(Arrays.asList(targetLogReader1, targetLogReader2));
        logConsumer.doConsume();

        // then
        assertThat(handler.ids).hasSize(1);
        assertThat(handler.ids).containsExactly(345L);
    }

    public static class ExampleHandler implements LogEntryTypeHandler<WfDefinitionReader>
    {

        protected int numInvocations;

        @Override
        public void handle(WfDefinitionReader reader, ResponseControl responseControl, LogWriters logWriters)
        {
            numInvocations++;
        }
    }

    public static class IdLoggingHandler implements LogEntryTypeHandler<WfDefinitionReader>
    {

        protected List<Long> ids = new ArrayList<>();;

        @Override
        public void handle(WfDefinitionReader reader, ResponseControl responseControl, LogWriters logWriters)
        {
            ids.add(reader.id());
        }
    }

    public static class AcceptingHandler implements LogEntryTypeHandler<WfDefinitionRequestReader>
    {

        BufferWriter responseWriter;

        @Override
        public void handle(WfDefinitionRequestReader reader, ResponseControl responseControl, LogWriters logWriters)
        {
            responseControl.accept(responseWriter);
        }
    }

    public static class RejectingHandler implements LogEntryTypeHandler<WfDefinitionRequestReader>
    {

        ErrorWriter errorWriter;

        @Override
        public void handle(WfDefinitionRequestReader reader, ResponseControl responseControl, LogWriters logWriters)
        {
            responseControl.reject(errorWriter);
        }
    }

    public static class SameLogWritingHandler implements LogEntryTypeHandler<WfDefinitionReader>
    {

        @Override
        public void handle(WfDefinitionReader reader, ResponseControl responseControl, LogWriters logWriters)
        {
            final WfDefinitionWriter writer = new WfDefinitionWriter();
            writer.id(reader.id());

            logWriters.writeToCurrentLog(writer);
        }
    }

    public static class DifferentLogWritingHandler implements LogEntryTypeHandler<WfDefinitionReader>
    {

        protected int targetLog;

        public DifferentLogWritingHandler(int targetLog)
        {
            this.targetLog = targetLog;
        }

        @Override
        public void handle(WfDefinitionReader reader, ResponseControl responseControl, LogWriters logWriters)
        {
            final WfDefinitionWriter writer = new WfDefinitionWriter();
            writer.id(reader.id());

            logWriters.writeToLog(targetLog, writer);
        }
    }


}
