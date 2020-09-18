/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.util;

import static io.zeebe.logstreams.impl.log.LogStorageAppender.LOG;
import static io.zeebe.test.util.TestUtil.doRepeatedly;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.zeebe.db.ZeebeDb;
import io.zeebe.db.ZeebeDbFactory;
import io.zeebe.engine.processor.CommandResponseWriter;
import io.zeebe.engine.processor.StreamProcessor;
import io.zeebe.engine.processor.TypedEventRegistry;
import io.zeebe.engine.processor.TypedRecord;
import io.zeebe.engine.processor.TypedRecordProcessorFactory;
import io.zeebe.logstreams.log.LogStreamBatchWriter;
import io.zeebe.logstreams.log.LogStreamReader;
import io.zeebe.logstreams.log.LogStreamRecordWriter;
import io.zeebe.logstreams.log.LoggedEvent;
import io.zeebe.logstreams.spi.LogStorage;
import io.zeebe.logstreams.util.SyncLogStream;
import io.zeebe.logstreams.util.SynchronousLogStream;
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
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.junit.rules.TemporaryFolder;

public final class TestStreams {
  static final Duration SNAPSHOT_INTERVAL = Duration.ofMinutes(1);
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

  public SynchronousLogStream createLogStream(final String name) {
    return createLogStream(name, 0);
  }

  public SynchronousLogStream createLogStream(final String name, final int partitionId) {
    final var listLogStorage = new ListLogStorage();
    return createLogStream(
        name,
        partitionId,
        listLogStorage,
        logStream -> listLogStorage.setPositionListener(logStream::setCommitPosition));
  }

  public SynchronousLogStream createLogStream(
      final String name, final int partitionId, final ListLogStorage listLogStorage) {
    return createLogStream(
        name,
        partitionId,
        listLogStorage,
        logStream -> listLogStorage.setPositionListener(logStream::setCommitPosition));
  }

  public SynchronousLogStream createLogStream(
      final String name,
      final int partitionId,
      final LogStorage logStorage,
      final Consumer<SyncLogStream> logStreamConsumer) {
    final var logStream =
        SyncLogStream.builder()
            .withLogName(name)
            .withLogStorage(logStorage)
            .withPartitionId(partitionId)
            .withActorScheduler(actorScheduler)
            .build();

    logStreamConsumer.accept(logStream);

    final LogContext logContext = LogContext.createLogContext(logStream, logStorage);
    logContextMap.put(name, logContext);
    closeables.manage(logContext);
    closeables.manage(() -> logContextMap.remove(name));
    return logStream;
  }

  public SynchronousLogStream getLogStream(final String name) {
    return logContextMap.get(name).getLogStream();
  }

  public LogStreamRecordWriter getLogStreamRecordWriter(final String name) {
    return logContextMap.get(name).getLogStreamWriter();
  }

  public LogStreamRecordWriter newLogStreamRecordWriter(final String name) {
    return logContextMap.get(name).newLogStreamRecordWriter();
  }

  public Stream<LoggedEvent> events(final String logName) {
    final SynchronousLogStream logStream = getLogStream(logName);

    final LogStreamReader reader = logStream.newLogStreamReader();
    closeables.manage(reader);

    reader.seekToFirstEvent();

    final Iterable<LoggedEvent> iterable = () -> reader;

    return StreamSupport.stream(iterable.spliterator(), false);
  }

  public FluentLogWriter newRecord(final LogStreamRecordWriter logStreamRecordWriter) {
    return new FluentLogWriter(logStreamRecordWriter);
  }

  public FluentLogWriter newRecord(final String logName) {
    return new FluentLogWriter(newLogStreamRecordWriter(logName));
  }

  public Path createRuntimeFolder(final SynchronousLogStream stream) {
    final Path rootDirectory =
        dataDirectory.getRoot().toPath().resolve(stream.getLogName()).resolve("state");

    try {
      Files.createDirectories(rootDirectory);
    } catch (final FileAlreadyExistsException ignored) {
      // totally fine if it already exists
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }

    return rootDirectory.resolve("runtime");
  }

  public StreamProcessor startStreamProcessor(
      final String log,
      final ZeebeDbFactory zeebeDbFactory,
      final TypedRecordProcessorFactory typedRecordProcessorFactory) {
    final SynchronousLogStream stream = getLogStream(log);
    return buildStreamProcessor(
        stream, zeebeDbFactory, typedRecordProcessorFactory, SNAPSHOT_INTERVAL);
  }

  private StreamProcessor buildStreamProcessor(
      final SynchronousLogStream stream,
      final ZeebeDbFactory zeebeDbFactory,
      final TypedRecordProcessorFactory factory,
      final Duration snapshotInterval) {
    final var storage = createRuntimeFolder(stream);
    final String logName = stream.getLogName();

    final var zeebeDb = zeebeDbFactory.createDb(storage.toFile());
    streamProcessor =
        StreamProcessor.builder()
            .logStream(stream.getAsyncLogStream())
            .zeebeDb(zeebeDb)
            .actorScheduler(actorScheduler)
            .commandResponseWriter(mockCommandResponseWriter)
            .onProcessedListener(mockOnProcessedListener)
            .streamProcessorFactory(factory)
            .build();
    streamProcessor.openAsync().join(15, TimeUnit.SECONDS);

    final LogContext context = logContextMap.get(logName);
    final ProcessorContext processorContext =
        ProcessorContext.createStreamContext(context, streamProcessor, zeebeDb);
    streamContextMap.put(logName, processorContext);
    closeables.manage(processorContext);

    return streamProcessor;
  }

  public void closeProcessor(final String streamName) throws Exception {
    streamContextMap.remove(streamName).close();
    LOG.info("Closed stream {}", streamName);
  }

  public StreamProcessor getStreamProcessor(final String streamName) {
    return Optional.ofNullable(streamContextMap.get(streamName))
        .map(c -> c.streamProcessor)
        .orElseThrow(
            () -> new NoSuchElementException("No stream processor found with name: " + streamName));
  }

  public long writeBatch(final String logName, final RecordToWrite[] recordToWrites) {
    final SynchronousLogStream logStream = getLogStream(logName);
    final LogStreamBatchWriter logStreamBatchWriter = logStream.newLogStreamBatchWriter();

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

    protected final RecordMetadata metadata = new RecordMetadata();
    protected UnpackedObject value;
    protected final LogStreamRecordWriter writer;
    protected long key = -1;
    private long sourceRecordPosition = -1;

    public FluentLogWriter(final LogStreamRecordWriter logStreamRecordWriter) {
      this.writer = logStreamRecordWriter;

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
      writer.sourceRecordPosition(sourceRecordPosition);

      if (key >= 0) {
        writer.key(key);
      } else {
        writer.keyNull();
      }

      writer.metadataWriter(metadata);
      writer.valueWriter(value);

      return doRepeatedly(writer::tryWrite).until(p -> p >= 0);
    }
  }

  private static final class LogContext implements AutoCloseable {
    private final SynchronousLogStream logStream;
    private final LogStorage logStorage;
    private final LogStreamRecordWriter logStreamWriter;

    private LogContext(final SynchronousLogStream logStream, final LogStorage logStorage) {
      this.logStream = logStream;
      logStreamWriter = logStream.newLogStreamRecordWriter();
      this.logStorage = logStorage;
    }

    public static LogContext createLogContext(
        final SyncLogStream logStream, final LogStorage logStorage) {
      return new LogContext(logStream, logStorage);
    }

    @Override
    public void close() {
      logStream.close();
      logStorage.close();
    }

    public LogStreamRecordWriter getLogStreamWriter() {
      return logStreamWriter;
    }

    public SynchronousLogStream getLogStream() {
      return logStream;
    }

    public LogStreamRecordWriter newLogStreamRecordWriter() {
      return logStream.newLogStreamRecordWriter();
    }
  }

  private static final class ProcessorContext implements AutoCloseable {

    private final LogContext logContext;
    private final ZeebeDb zeebeDb;

    private boolean closed = false;
    private final StreamProcessor streamProcessor;

    private ProcessorContext(
        final LogContext logContext, final StreamProcessor streamProcessor, final ZeebeDb zeebeDb) {
      this.logContext = logContext;
      this.streamProcessor = streamProcessor;
      this.zeebeDb = zeebeDb;
    }

    public static ProcessorContext createStreamContext(
        final LogContext logContext, final StreamProcessor streamProcessor, final ZeebeDb zeebeDb) {
      return new ProcessorContext(logContext, streamProcessor, zeebeDb);
    }

    public SynchronousLogStream getLogStream() {
      return logContext.getLogStream();
    }

    @Override
    public void close() throws Exception {
      if (closed) {
        return;
      }
      Loggers.IO_LOGGER.debug("Close stream processor");
      streamProcessor.closeAsync().join();
      zeebeDb.close();
      closed = true;
    }
  }
}
