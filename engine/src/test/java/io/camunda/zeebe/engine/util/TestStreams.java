/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.util;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.ZeebeDbFactory;
import io.camunda.zeebe.engine.Engine;
import io.camunda.zeebe.engine.EngineConfiguration;
import io.camunda.zeebe.engine.Loggers;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessorFactory;
import io.camunda.zeebe.logstreams.log.LogAppendEntry;
import io.camunda.zeebe.logstreams.log.LogStreamReader;
import io.camunda.zeebe.logstreams.log.LogStreamWriter;
import io.camunda.zeebe.logstreams.log.LoggedEvent;
import io.camunda.zeebe.logstreams.storage.LogStorage;
import io.camunda.zeebe.logstreams.util.ListLogStorage;
import io.camunda.zeebe.logstreams.util.SyncLogStream;
import io.camunda.zeebe.logstreams.util.SynchronousLogStream;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.impl.record.CopiedRecord;
import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.scheduler.ActorScheduler;
import io.camunda.zeebe.stream.api.CommandResponseWriter;
import io.camunda.zeebe.stream.api.InterPartitionCommandSender;
import io.camunda.zeebe.stream.api.ReadonlyStreamProcessorContext;
import io.camunda.zeebe.stream.api.StreamProcessorLifecycleAware;
import io.camunda.zeebe.stream.impl.StreamProcessor;
import io.camunda.zeebe.stream.impl.StreamProcessorContext;
import io.camunda.zeebe.stream.impl.StreamProcessorListener;
import io.camunda.zeebe.stream.impl.StreamProcessorMode;
import io.camunda.zeebe.stream.impl.TypedEventRegistry;
import io.camunda.zeebe.test.util.AutoCloseableRule;
import io.camunda.zeebe.util.FileUtil;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.awaitility.Awaitility;
import org.junit.rules.TemporaryFolder;
import org.slf4j.Logger;

public final class TestStreams {

  private static final String SNAPSHOT_FOLDER = "snapshot";
  private static final Map<Class<?>, ValueType> VALUE_TYPES = new HashMap<>();
  private static final Logger LOG = Loggers.STREAM_PROCESSING;

  static {
    TypedEventRegistry.EVENT_REGISTRY.forEach((v, c) -> VALUE_TYPES.put(c, v));
  }

  private final TemporaryFolder dataDirectory;
  private final AutoCloseableRule closeables;
  private final ActorScheduler actorScheduler;

  private final CommandResponseWriter mockCommandResponseWriter;
  private final StreamProcessorListener mockStreamProcessorListener;
  private final Map<String, LogContext> logContextMap = new HashMap<>();
  private final Map<String, ProcessorContext> streamContextMap = new HashMap<>();
  private boolean snapshotWasTaken = false;
  private StreamProcessorMode streamProcessorMode = StreamProcessorMode.PROCESSING;
  private int maxCommandsInBatch = StreamProcessorContext.DEFAULT_MAX_COMMANDS_IN_BATCH;

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

    mockStreamProcessorListener = mock(StreamProcessorListener.class);
  }

  public void withStreamProcessorMode(final StreamProcessorMode streamProcessorMode) {
    this.streamProcessorMode = streamProcessorMode;
  }

  public CommandResponseWriter getMockedResponseWriter() {
    return mockCommandResponseWriter;
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
        logStream -> listLogStorage.setPositionListener(logStream::setLastWrittenPosition));
  }

  public SynchronousLogStream createLogStream(
      final String name, final int partitionId, final ListLogStorage sharedStorage) {
    return createLogStream(
        name,
        partitionId,
        sharedStorage,
        logStream -> sharedStorage.setPositionListener(logStream::setLastWrittenPosition));
  }

  private SynchronousLogStream createLogStream(
      final String name,
      final int partitionId,
      final LogStorage logStorage,
      final Consumer<SyncLogStream> logStreamConsumer) {
    final var logStream =
        SyncLogStream.builder()
            .withLogName(name)
            .withLogStorage(logStorage)
            .withPartitionId(partitionId)
            .withActorSchedulingService(actorScheduler)
            .build();

    logStreamConsumer.accept(logStream);

    final LogContext logContext = LogContext.createLogContext(logStream);
    logContextMap.put(name, logContext);
    closeables.manage(logContext);
    closeables.manage(() -> logContextMap.remove(name));
    return logStream;
  }

  public SynchronousLogStream getLogStream(final String name) {
    return logContextMap.get(name).getLogStream();
  }

  public LogStreamWriter newLogStreamWriter(final String name) {
    return logContextMap.get(name).newLogStreamWriter();
  }

  public Stream<LoggedEvent> events(final String logName) {
    final SynchronousLogStream logStream = getLogStream(logName);

    final LogStreamReader reader = logStream.newLogStreamReader();
    closeables.manage(reader);

    reader.seekToFirstEvent();

    final Iterable<LoggedEvent> iterable = () -> reader;

    return StreamSupport.stream(iterable.spliterator(), false);
  }

  public FluentLogWriter newRecord(final String logName) {
    return new FluentLogWriter(newLogStreamWriter(logName));
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
    return startStreamProcessor(log, zeebeDbFactory, typedRecordProcessorFactory, Optional.empty());
  }

  public StreamProcessor startStreamProcessor(
      final String log,
      final ZeebeDbFactory zeebeDbFactory,
      final TypedRecordProcessorFactory typedRecordProcessorFactory,
      final Optional<StreamProcessorListener> streamProcessorListenerOpt) {
    final SynchronousLogStream stream = getLogStream(log);
    return buildStreamProcessor(
        stream, zeebeDbFactory, typedRecordProcessorFactory, true, streamProcessorListenerOpt);
  }

  public StreamProcessor buildStreamProcessor(
      final SynchronousLogStream stream,
      final ZeebeDbFactory zeebeDbFactory,
      final TypedRecordProcessorFactory factory,
      final boolean awaitOpening,
      final Optional<StreamProcessorListener> streamProcessorListenerOpt) {
    final var storage = createRuntimeFolder(stream);
    final var snapshot = storage.getParent().resolve(SNAPSHOT_FOLDER);

    final var recoveredLatch = new CountDownLatch(1);
    final var recoveredAwaiter =
        new StreamProcessorLifecycleAware() {
          @Override
          public void onRecovered(final ReadonlyStreamProcessorContext context) {
            recoveredLatch.countDown();
          }
        };
    final TypedRecordProcessorFactory wrappedFactory =
        (ctx) -> factory.createProcessors(ctx).withListener(recoveredAwaiter);

    final ZeebeDb<?> zeebeDb;
    if (snapshotWasTaken) {
      zeebeDb = zeebeDbFactory.createDb(snapshot.toFile());
    } else {
      zeebeDb = zeebeDbFactory.createDb(storage.toFile());
    }
    final String logName = stream.getLogName();

    final var streamProcessorListeners = new ArrayList<StreamProcessorListener>();
    streamProcessorListeners.add(mockStreamProcessorListener);
    streamProcessorListenerOpt.ifPresent(streamProcessorListeners::add);

    final var builder =
        StreamProcessor.builder()
            .logStream(stream.getAsyncLogStream())
            .zeebeDb(zeebeDb)
            .actorSchedulingService(actorScheduler)
            .commandResponseWriter(mockCommandResponseWriter)
            .listener(new StreamProcessorListenerRelay(streamProcessorListeners))
            .recordProcessors(List.of(new Engine(wrappedFactory, new EngineConfiguration())))
            .streamProcessorMode(streamProcessorMode)
            .maxCommandsInBatch(maxCommandsInBatch)
            .partitionCommandSender(mock(InterPartitionCommandSender.class));

    final StreamProcessor streamProcessor = builder.build();
    final var openFuture = streamProcessor.openAsync(false);

    if (awaitOpening) { // and recovery
      try {
        recoveredLatch.await(15, TimeUnit.SECONDS);
      } catch (final InterruptedException e) {
        Thread.interrupted();
      }
    }
    openFuture.join(15, TimeUnit.SECONDS);

    final ProcessorContext processorContext =
        ProcessorContext.createStreamContext(streamProcessor, zeebeDb, storage, snapshot);
    streamContextMap.put(logName, processorContext);
    closeables.manage(processorContext);

    return streamProcessor;
  }

  public void pauseProcessing(final String streamName) {
    streamContextMap.get(streamName).streamProcessor.pauseProcessing().join();
    LOG.info("Paused processing for stream {}", streamName);
  }

  public void resumeProcessing(final String streamName) {
    streamContextMap.get(streamName).streamProcessor.resumeProcessing();
    LOG.info("Resume processing for stream {}", streamName);
  }

  public void snapshot(final String streamName) {
    streamContextMap.get(streamName).snapshot();
    snapshotWasTaken = true;
    LOG.info("Snapshot database for stream {}", streamName);
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

  public void maxCommandsInBatch(final int maxCommandsInBatch) {
    this.maxCommandsInBatch = maxCommandsInBatch;
  }

  public static class FluentLogWriter {

    protected final RecordMetadata metadata = new RecordMetadata();
    protected final LogStreamWriter writer;
    protected UnifiedRecordValue value;
    protected long key = -1;
    private long sourceRecordPosition = -1;

    public FluentLogWriter(final LogStreamWriter logStreamWriter) {
      writer = logStreamWriter;

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
      metadata.intent(intent);
      return this;
    }

    public FluentLogWriter requestId(final long requestId) {
      metadata.requestId(requestId);
      return this;
    }

    public FluentLogWriter sourceRecordPosition(final long sourceRecordPosition) {
      this.sourceRecordPosition = sourceRecordPosition;
      return this;
    }

    public FluentLogWriter requestStreamId(final int requestStreamId) {
      metadata.requestStreamId(requestStreamId);
      return this;
    }

    public FluentLogWriter recordType(final RecordType recordType) {
      metadata.recordType(recordType);
      return this;
    }

    public FluentLogWriter key(final long key) {
      this.key = key;
      return this;
    }

    public FluentLogWriter event(final UnifiedRecordValue event) {
      final ValueType eventType = VALUE_TYPES.get(event.getClass());
      if (eventType == null) {
        throw new RuntimeException("No event type registered for getValue " + event.getClass());
      }

      metadata.valueType(eventType);
      value = event;
      return this;
    }

    public long write() {
      final LogAppendEntry entry;
      if (key >= 0) {
        entry = LogAppendEntry.of(key, metadata, value);
      } else {
        entry = LogAppendEntry.of(metadata, value);
      }

      return Awaitility.await("until entry is written")
          .pollInSameThread()
          .pollDelay(Duration.ZERO)
          .pollInterval(Duration.ofMillis(50))
          .until(() -> writer.tryWrite(entry, sourceRecordPosition), p -> p >= 0);
    }
  }

  private static final class LogContext implements AutoCloseable {
    private final SynchronousLogStream logStream;

    private LogContext(final SynchronousLogStream logStream) {
      this.logStream = logStream;
    }

    public static LogContext createLogContext(final SyncLogStream logStream) {
      return new LogContext(logStream);
    }

    @Override
    public void close() {
      logStream.close();
    }

    public SynchronousLogStream getLogStream() {
      return logStream;
    }

    public LogStreamWriter newLogStreamWriter() {
      return logStream.newLogStreamWriter();
    }
  }

  private static final class ProcessorContext implements AutoCloseable {
    private final ZeebeDb zeebeDb;
    private final StreamProcessor streamProcessor;
    private final Path runtimePath;
    private final Path snapshotPath;
    private boolean closed = false;

    private ProcessorContext(
        final StreamProcessor streamProcessor,
        final ZeebeDb zeebeDb,
        final Path runtimePath,
        final Path snapshotPath) {
      this.streamProcessor = streamProcessor;
      this.zeebeDb = zeebeDb;
      this.runtimePath = runtimePath;
      this.snapshotPath = snapshotPath;
    }

    public static ProcessorContext createStreamContext(
        final StreamProcessor streamProcessor,
        final ZeebeDb zeebeDb,
        final Path runtimePath,
        final Path snapshotPath) {
      return new ProcessorContext(streamProcessor, zeebeDb, runtimePath, snapshotPath);
    }

    public void snapshot() {
      zeebeDb.createSnapshot(snapshotPath.toFile());
    }

    @Override
    public void close() throws Exception {
      if (closed) {
        return;
      }

      LOG.debug("Close stream processor");
      streamProcessor.closeAsync().join();
      zeebeDb.close();
      if (runtimePath.toFile().exists()) {
        FileUtil.deleteFolder(runtimePath);
      }
      closed = true;
    }
  }
}
