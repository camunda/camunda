/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.util;

import static io.zeebe.test.util.TestUtil.doRepeatedly;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import io.zeebe.db.ZeebeDb;
import io.zeebe.db.ZeebeDbFactory;
import io.zeebe.engine.processor.AsyncSnapshotDirector;
import io.zeebe.engine.processor.CommandResponseWriter;
import io.zeebe.engine.processor.ReadonlyProcessingContext;
import io.zeebe.engine.processor.StreamProcessor;
import io.zeebe.engine.processor.StreamProcessorLifecycleAware;
import io.zeebe.engine.processor.TypedEventRegistry;
import io.zeebe.engine.processor.TypedRecord;
import io.zeebe.engine.processor.TypedRecordProcessorFactory;
import io.zeebe.engine.processor.TypedRecordProcessors;
import io.zeebe.logstreams.LogStreams;
import io.zeebe.logstreams.log.BufferedLogStreamReader;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.logstreams.log.LogStreamBatchWriterImpl;
import io.zeebe.logstreams.log.LogStreamReader;
import io.zeebe.logstreams.log.LogStreamRecordWriter;
import io.zeebe.logstreams.log.LogStreamWriterImpl;
import io.zeebe.logstreams.log.LoggedEvent;
import io.zeebe.logstreams.state.SnapshotStorage;
import io.zeebe.logstreams.state.StateSnapshotController;
import io.zeebe.logstreams.util.AtomixLogStorageRule;
import io.zeebe.logstreams.util.TestSnapshotStorage;
import io.zeebe.msgpack.UnpackedObject;
import io.zeebe.protocol.Protocol;
import io.zeebe.protocol.impl.record.CopiedRecord;
import io.zeebe.protocol.impl.record.RecordMetadata;
import io.zeebe.protocol.record.RecordType;
import io.zeebe.protocol.record.ValueType;
import io.zeebe.protocol.record.intent.Intent;
import io.zeebe.test.util.AutoCloseableRule;
import io.zeebe.util.Loggers;
import io.zeebe.util.sched.ActorScheduler;
import io.zeebe.util.sched.future.ActorFuture;
import io.zeebe.util.sched.future.CompletableActorFuture;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.junit.rules.TemporaryFolder;

public class TestStreams {
  private static final Duration SNAPSHOT_INTERVAL = Duration.ofMinutes(1);

  private static final Map<Class<?>, ValueType> VALUE_TYPES = new HashMap<>();

  static {
    TypedEventRegistry.EVENT_REGISTRY.forEach((v, c) -> VALUE_TYPES.put(c, v));
  }

  private final TemporaryFolder dataDirectory;
  private final AutoCloseableRule closeables;
  private final ActorScheduler actorScheduler;

  private final CommandResponseWriter mockCommandResponseWriter;
  private final Consumer<TypedRecord> mockOnProcessedListener;
  private final Map<String, LogContext> logContextMap = new HashMap<>();
  private final Map<String, ProcessorContext> streamContextMap = new HashMap<>();
  private StreamProcessor streamProcessor;

  public TestStreams(
      final TemporaryFolder dataDirectory,
      final AutoCloseableRule closeables,
      final ActorScheduler actorScheduler) {
    this.dataDirectory = dataDirectory;
    this.closeables = closeables;
    this.actorScheduler = actorScheduler;

    mockCommandResponseWriter = mock(CommandResponseWriter.class);
    when(mockCommandResponseWriter.intent(any())).thenReturn(mockCommandResponseWriter);
    when(mockCommandResponseWriter.key(anyLong())).thenReturn(mockCommandResponseWriter);
    when(mockCommandResponseWriter.partitionId(anyInt())).thenReturn(mockCommandResponseWriter);
    when(mockCommandResponseWriter.recordType(any())).thenReturn(mockCommandResponseWriter);
    when(mockCommandResponseWriter.rejectionType(any())).thenReturn(mockCommandResponseWriter);
    when(mockCommandResponseWriter.rejectionReason(any())).thenReturn(mockCommandResponseWriter);
    when(mockCommandResponseWriter.valueType(any())).thenReturn(mockCommandResponseWriter);
    when(mockCommandResponseWriter.valueWriter(any())).thenReturn(mockCommandResponseWriter);

    when(mockCommandResponseWriter.tryWriteResponse(anyInt(), anyLong())).thenReturn(true);
    mockOnProcessedListener = mock(Consumer.class);
  }

  public CommandResponseWriter getMockedResponseWriter() {
    return mockCommandResponseWriter;
  }

  public Consumer<TypedRecord> getMockedOnProcessedListener() {
    return mockOnProcessedListener;
  }

  public LogStream createLogStream(final String name) {
    return createLogStream(name, 0);
  }

  public LogStream createLogStream(final String name, final int partitionId) {
    final File segments;
    try {
      segments = dataDirectory.newFolder(name, "segments");
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }

    final AtomixLogStorageRule logStorageRule =
        new AtomixLogStorageRule(dataDirectory, partitionId);
    logStorageRule.open(
        b ->
            b.withDirectory(segments)
                .withMaxEntrySize(4 * 1024 * 1024)
                .withMaxSegmentSize(128 * 1024 * 1024));
    final var logStream =
        spy(
            LogStreams.createLogStream()
                .withLogName(name)
                .withLogStorage(logStorageRule.getStorage())
                .withPartitionId(partitionId)
                .withActorScheduler(actorScheduler)
                .build());
    logStorageRule.setPositionListener(logStream::setCommitPosition);
    logStream.openAppender().join();

    final LogContext logContext = LogContext.createLogContext(logStream, logStorageRule);
    logContextMap.put(name, logContext);
    closeables.manage(logContext);

    return logStream;
  }

  public LogStream getLogStream(final String name) {
    return logContextMap.get(name).getLogStream();
  }

  public Stream<LoggedEvent> events(final String logName) {
    final LogStream logStream = getLogStream(logName);

    final LogStreamReader reader = new BufferedLogStreamReader(logStream.getLogStorage());
    closeables.manage(reader);

    reader.seekToFirstEvent();

    final Iterable<LoggedEvent> iterable = () -> reader;

    return StreamSupport.stream(iterable.spliterator(), false);
  }

  public FluentLogWriter newRecord(final String logName) {
    final LogStream logStream = getLogStream(logName);
    return new FluentLogWriter(logStream);
  }

  public SnapshotStorage createSnapshotStorage(final LogStream stream) {
    final Path rootDirectory =
        dataDirectory.getRoot().toPath().resolve(stream.getLogName()).resolve("state");

    try {
      Files.createDirectories(rootDirectory);
    } catch (final FileAlreadyExistsException ignored) {
      // totally fine if it already exists
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }

    return new TestSnapshotStorage(rootDirectory);
  }

  public StreamProcessor startStreamProcessor(
      final String log,
      final ZeebeDbFactory zeebeDbFactory,
      final TypedRecordProcessorFactory typedRecordProcessorFactory) {
    final LogStream stream = getLogStream(log);
    return buildStreamProcessor(
        stream, zeebeDbFactory, typedRecordProcessorFactory, SNAPSHOT_INTERVAL);
  }

  private StreamProcessor buildStreamProcessor(
      final LogStream stream,
      final ZeebeDbFactory zeebeDbFactory,
      final TypedRecordProcessorFactory factory,
      final Duration snapshotInterval) {
    final SnapshotStorage storage = createSnapshotStorage(stream);
    final StateSnapshotController currentSnapshotController =
        spy(new StateSnapshotController(zeebeDbFactory, storage));
    final String logName = stream.getLogName();

    final ActorFuture<Void> openFuture = new CompletableActorFuture<>();

    try {
      currentSnapshotController.recover();
    } catch (final Exception e) {
      throw new RuntimeException(e);
    }
    final var zeebeDb = currentSnapshotController.openDb();
    streamProcessor =
        StreamProcessor.builder()
            .logStream(stream)
            .zeebeDb(zeebeDb)
            .actorScheduler(actorScheduler)
            .commandResponseWriter(mockCommandResponseWriter)
            .onProcessedListener(mockOnProcessedListener)
            .writeBuffer(stream.getWriteBuffer())
            .logStorage(stream.getLogStorage())
            .streamProcessorFactory(
                (context) -> {
                  final TypedRecordProcessors processors = factory.createProcessors(context);
                  processors.withListener(
                      new StreamProcessorLifecycleAware() {
                        @Override
                        public void onOpen(final ReadonlyProcessingContext context) {
                          openFuture.complete(null);
                        }
                      });
                  return processors;
                })
            .build();
    streamProcessor.openAsync().join();
    openFuture.join();

    final var asyncSnapshotDirector =
        new AsyncSnapshotDirector(
            streamProcessor, currentSnapshotController, stream, snapshotInterval);
    actorScheduler.submitActor(asyncSnapshotDirector);

    final LogContext context = logContextMap.get(logName);
    final ProcessorContext processorContext =
        ProcessorContext.createStreamContext(
            context, streamProcessor, currentSnapshotController, asyncSnapshotDirector, zeebeDb);
    streamContextMap.put(logName, processorContext);
    closeables.manage(processorContext);
    closeables.manage(storage);

    return streamProcessor;
  }

  public StateSnapshotController getStateSnapshotController(final String stream) {
    return streamContextMap.get(stream).getStateSnapshotController();
  }

  public void closeProcessor(final String streamName) throws Exception {
    streamContextMap.get(streamName).close();
  }

  public long writeBatch(final String logName, final RecordToWrite[] recordToWrites) {
    final LogStream logStream = getLogStream(logName);
    final LogStreamBatchWriterImpl logStreamBatchWriter =
        new LogStreamBatchWriterImpl(logStream.getPartitionId(), logStream.getWriteBuffer());

    for (final RecordToWrite recordToWrite : recordToWrites) {
      logStreamBatchWriter
          .event()
          .key(recordToWrite.getKey())
          .sourceIndex(recordToWrite.getSourceIndex())
          .metadataWriter(recordToWrite.getRecordMetadata())
          .valueWriter(recordToWrite.getUnifiedRecordValue())
          .done();
    }
    return logStreamBatchWriter.tryWrite();
  }

  public static class FluentLogWriter {

    protected RecordMetadata metadata = new RecordMetadata();
    protected UnpackedObject value;
    protected LogStream logStream;
    protected long key = -1;
    private long sourceRecordPosition = -1;

    public FluentLogWriter(final LogStream logStream) {
      this.logStream = logStream;

      metadata.protocolVersion(Protocol.PROTOCOL_VERSION);
    }

    public FluentLogWriter record(final CopiedRecord record) {
      intent(record.getIntent());
      key(record.getKey());
      sourceRecordPosition(record.getSourceRecordPosition());
      recordType(record.getRecordType());
      event(record.getValue());
      return this;
    }

    public FluentLogWriter intent(final Intent intent) {
      this.metadata.intent(intent);
      return this;
    }

    public FluentLogWriter requestId(final long requestId) {
      this.metadata.requestId(requestId);
      return this;
    }

    public FluentLogWriter sourceRecordPosition(final long sourceRecordPosition) {
      this.sourceRecordPosition = sourceRecordPosition;
      return this;
    }

    public FluentLogWriter requestStreamId(final int requestStreamId) {
      this.metadata.requestStreamId(requestStreamId);
      return this;
    }

    public FluentLogWriter recordType(final RecordType recordType) {
      this.metadata.recordType(recordType);
      return this;
    }

    public FluentLogWriter key(final long key) {
      this.key = key;
      return this;
    }

    public FluentLogWriter event(final UnpackedObject event) {
      final ValueType eventType = VALUE_TYPES.get(event.getClass());
      if (eventType == null) {
        throw new RuntimeException("No event type registered for getValue " + event.getClass());
      }

      this.metadata.valueType(eventType);
      this.value = event;
      return this;
    }

    public long write() {
      final LogStreamRecordWriter writer = new LogStreamWriterImpl(logStream);

      writer.sourceRecordPosition(sourceRecordPosition);

      if (key >= 0) {
        writer.key(key);
      } else {
        writer.keyNull();
      }

      writer.metadataWriter(metadata);
      writer.valueWriter(value);

      return doRepeatedly(() -> writer.tryWrite()).until(p -> p >= 0);
    }
  }

  private static final class LogContext implements AutoCloseable {
    private final LogStream logStream;
    private final AtomixLogStorageRule logStorageRule;

    private LogContext(final LogStream logStream, final AtomixLogStorageRule logStorageRule) {
      this.logStream = logStream;
      this.logStorageRule = logStorageRule;
    }

    public static LogContext createLogContext(
        final LogStream logStream, final AtomixLogStorageRule logStorageRule) {
      return new LogContext(logStream, logStorageRule);
    }

    @Override
    public void close() {
      logStream.close();
      logStorageRule.close();
    }

    public LogStream getLogStream() {
      return logStream;
    }
  }

  private static final class ProcessorContext implements AutoCloseable {

    private final LogContext logContext;
    private final StateSnapshotController stateSnapshotController;
    private final AsyncSnapshotDirector asyncSnapshotDirector;
    private final ZeebeDb zeebeDb;

    private boolean closed = false;
    private final StreamProcessor streamProcessor;

    private ProcessorContext(
        final LogContext logContext,
        final StreamProcessor streamProcessor,
        final StateSnapshotController stateSnapshotController,
        final AsyncSnapshotDirector asyncSnapshotDirector,
        final ZeebeDb zeebeDb) {
      this.logContext = logContext;
      this.streamProcessor = streamProcessor;
      this.stateSnapshotController = stateSnapshotController;
      this.asyncSnapshotDirector = asyncSnapshotDirector;
      this.zeebeDb = zeebeDb;
    }

    public static ProcessorContext createStreamContext(
        final LogContext logContext,
        final StreamProcessor streamProcessor,
        final StateSnapshotController stateSnapshotController,
        final AsyncSnapshotDirector asyncSnapshotDirector,
        final ZeebeDb zeebeDb) {
      return new ProcessorContext(
          logContext, streamProcessor, stateSnapshotController, asyncSnapshotDirector, zeebeDb);
    }

    public LogStream getLogStream() {
      return logContext.getLogStream();
    }

    public StateSnapshotController getStateSnapshotController() {
      return stateSnapshotController;
    }

    @Override
    public void close() throws Exception {
      if (closed) {
        return;
      }

      asyncSnapshotDirector.closeAsync().join();
      Loggers.IO_LOGGER.debug("Close stream processor");
      streamProcessor.closeAsync().join();
      zeebeDb.close();
      closed = true;
    }
  }
}
