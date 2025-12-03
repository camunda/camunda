/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.util;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.ZeebeDbFactory;
import io.camunda.zeebe.engine.Engine;
import io.camunda.zeebe.engine.EngineConfiguration;
import io.camunda.zeebe.engine.Loggers;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessorFactory;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.state.processing.DbBannedInstanceState;
import io.camunda.zeebe.logstreams.log.LogAppendEntry;
import io.camunda.zeebe.logstreams.log.LogStreamReader;
import io.camunda.zeebe.logstreams.log.LogStreamWriter;
import io.camunda.zeebe.logstreams.log.LoggedEvent;
import io.camunda.zeebe.logstreams.log.WriteContext;
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
import io.camunda.zeebe.stream.api.StreamClock;
import io.camunda.zeebe.stream.api.StreamProcessorLifecycleAware;
import io.camunda.zeebe.stream.impl.StreamProcessor;
import io.camunda.zeebe.stream.impl.StreamProcessorBuilder;
import io.camunda.zeebe.stream.impl.StreamProcessorContext;
import io.camunda.zeebe.stream.impl.StreamProcessorListener;
import io.camunda.zeebe.stream.impl.StreamProcessorMode;
import io.camunda.zeebe.test.util.AutoCloseableRule;
import io.camunda.zeebe.util.Either;
import io.camunda.zeebe.util.FileUtil;
import io.camunda.zeebe.util.micrometer.MicrometerUtil;
import io.camunda.zeebe.util.micrometer.MicrometerUtil.PartitionKeyNames;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.awaitility.Awaitility;
import org.junit.rules.TemporaryFolder;
import org.slf4j.Logger;

public final class TestStreams {

  private static final String SNAPSHOT_FOLDER = "snapshot";
  private static final Logger LOG = Loggers.STREAM_PROCESSING;

  private final TemporaryFolder dataDirectory;
  private final AutoCloseableRule closeables;
  private final ActorScheduler actorScheduler;
  private final InstantSource clock;

  private final CommandResponseWriter mockCommandResponseWriter;
  private final Map<String, LogContext> logContextMap = new HashMap<>();
  private final Map<String, ProcessorContext> streamContextMap = new HashMap<>();
  private boolean snapshotWasTaken = false;
  private StreamProcessorMode streamProcessorMode = StreamProcessorMode.PROCESSING;
  private int maxCommandsInBatch = StreamProcessorContext.DEFAULT_MAX_COMMANDS_IN_BATCH;
  private ListLogStorage listLogStorage;

  public TestStreams(
      final TemporaryFolder dataDirectory,
      final AutoCloseableRule closeables,
      final ActorScheduler actorScheduler,
      final InstantSource clock) {
    this.dataDirectory = dataDirectory;
    this.closeables = closeables;
    this.actorScheduler = actorScheduler;
    this.clock = clock;

    mockCommandResponseWriter = mock(CommandResponseWriter.class);
    when(mockCommandResponseWriter.intent(any())).thenReturn(mockCommandResponseWriter);
    when(mockCommandResponseWriter.key(anyLong())).thenReturn(mockCommandResponseWriter);
    when(mockCommandResponseWriter.partitionId(anyInt())).thenReturn(mockCommandResponseWriter);
    when(mockCommandResponseWriter.recordType(any())).thenReturn(mockCommandResponseWriter);
    when(mockCommandResponseWriter.rejectionType(any())).thenReturn(mockCommandResponseWriter);
    when(mockCommandResponseWriter.rejectionReason(any())).thenReturn(mockCommandResponseWriter);
    when(mockCommandResponseWriter.valueType(any())).thenReturn(mockCommandResponseWriter);
    when(mockCommandResponseWriter.valueWriter(any())).thenReturn(mockCommandResponseWriter);
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
    listLogStorage = new ListLogStorage();
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
    final var meterRegistry = new SimpleMeterRegistry();
    final var logStream =
        SyncLogStream.builder()
            .withLogName(name)
            .withLogStorage(logStorage)
            .withPartitionId(partitionId)
            .withClock(clock)
            .withActorSchedulingService(actorScheduler)
            .withMeterRegistry(meterRegistry)
            .build();

    logStreamConsumer.accept(logStream);

    final LogContext logContext = new LogContext(logStream, meterRegistry);
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
    return startStreamProcessor(
        log, zeebeDbFactory, typedRecordProcessorFactory, Optional.empty(), cfg -> {}, true);
  }

  public StreamProcessor startStreamProcessor(
      final String log,
      final ZeebeDbFactory zeebeDbFactory,
      final TypedRecordProcessorFactory typedRecordProcessorFactory,
      final Optional<StreamProcessorListener> streamProcessorListenerOpt,
      final Consumer<StreamProcessorBuilder> processorConfiguration,
      final boolean awaitOpening) {
    final SynchronousLogStream stream = getLogStream(log);
    return buildStreamProcessor(
        stream,
        zeebeDbFactory,
        processorConfiguration,
        typedRecordProcessorFactory,
        awaitOpening,
        streamProcessorListenerOpt);
  }

  public StreamProcessor buildStreamProcessor(
      final SynchronousLogStream stream,
      final ZeebeDbFactory zeebeDbFactory,
      final Consumer<StreamProcessorBuilder> processorConfiguration,
      final TypedRecordProcessorFactory factory,
      final boolean awaitOpening,
      final Optional<StreamProcessorListener> streamProcessorListenerOpt) {
    final var storage = createRuntimeFolder(stream);
    final var snapshot = storage.getParent().resolve(SNAPSHOT_FOLDER);

    final AtomicReference<StreamClock> streamClockRef = new AtomicReference<>();
    final AtomicReference<MutableProcessingState> processingStateRef = new AtomicReference<>();
    final var recoveredLatch = new CountDownLatch(1);
    final var recoveredAwaiter =
        new StreamProcessorLifecycleAware() {
          @Override
          public void onRecovered(final ReadonlyStreamProcessorContext context) {
            streamClockRef.set(context.getClock());
            recoveredLatch.countDown();
          }
        };
    final TypedRecordProcessorFactory wrappedFactory =
        (ctx) -> {
          processingStateRef.set(ctx.getProcessingState());
          return factory.createProcessors(ctx).withListener(recoveredAwaiter);
        };

    final ZeebeDb<?> zeebeDb;
    if (snapshotWasTaken) {
      zeebeDb = zeebeDbFactory.createDb(snapshot.toFile());
    } else {
      zeebeDb = zeebeDbFactory.createDb(storage.toFile());
    }
    final String logName = stream.getLogName();

    final var streamProcessorListeners = new ArrayList<StreamProcessorListener>();
    streamProcessorListenerOpt.ifPresent(streamProcessorListeners::add);

    final var meterRegistry = zeebeDb.getMeterRegistry();
    meterRegistry
        .config()
        .commonTags(
            Tags.of(
                PartitionKeyNames.PARTITION.asString(), String.valueOf(stream.getPartitionId())));

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
            .partitionCommandSender(mock(InterPartitionCommandSender.class))
            .meterRegistry(meterRegistry)
            .clock(StreamClock.controllable(clock));

    processorConfiguration.accept(builder);

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
        ProcessorContext.createStreamContext(
            streamProcessor,
            zeebeDb,
            storage,
            snapshot,
            streamClockRef.get(),
            processingStateRef.get(),
            meterRegistry);
    streamContextMap.put(logName, processorContext);
    closeables.manage(processorContext);

    return streamProcessor;
  }

  public void pauseProcessing(final String streamName) {
    streamContextMap.get(streamName).streamProcessor.pauseProcessing().join();
    LOG.info("Paused processing for stream {}", streamName);
  }

  public void banInstanceInNewTransaction(final String streamName, final long processInstanceKey) {
    final ZeebeDb zeebeDbLocal = streamContextMap.get(streamName).zeebeDb;
    final TransactionContext context = zeebeDbLocal.createContext();
    new DbBannedInstanceState(zeebeDbLocal, context).banProcessInstance(processInstanceKey);
  }

  public void resumeProcessing(final String streamName) {
    streamContextMap.get(streamName).streamProcessor.resumeProcessing();
    LOG.info("Resume processing for stream {}", streamName);
  }

  public MeterRegistry getMeterRegistry(final String streamName) {
    return streamContextMap.get(streamName).meterRegistry;
  }

  public void resetLog() {
    listLogStorage.reset();
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

  public StreamClock getStreamClock(final String streamName) {
    return Optional.ofNullable(streamContextMap.get(streamName))
        .map(c -> c.streamClock)
        .orElseThrow(
            () -> new NoSuchElementException("No stream clock found with name: " + streamName));
  }

  public void maxCommandsInBatch(final int maxCommandsInBatch) {
    this.maxCommandsInBatch = maxCommandsInBatch;
  }

  public MutableProcessingState getProcessingState(final String streamName) {
    return Optional.ofNullable(streamContextMap.get(streamName))
        .map(c -> c.processingState)
        .orElseThrow(
            () -> new NoSuchElementException("No processing state found with name: " + streamName));
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

    public FluentLogWriter authorizations(final String... tenantIds) {
      metadata.authorization(AuthorizationUtil.getAuthInfo(tenantIds));
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
      final ValueType eventType = event.valueType();
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
          .until(
              () -> writer.tryWrite(WriteContext.internal(), entry, sourceRecordPosition),
              Either::isRight)
          .get();
    }
  }

  public record LogContext(SynchronousLogStream logStream, MeterRegistry meterRegistry)
      implements AutoCloseable {

    @Override
    public void close() {
      logStream.close();
      MicrometerUtil.close(meterRegistry);
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
    private final StreamClock streamClock;
    private final MutableProcessingState processingState;
    private final MeterRegistry meterRegistry;
    private boolean closed = false;

    private ProcessorContext(
        final StreamProcessor streamProcessor,
        final ZeebeDb zeebeDb,
        final Path runtimePath,
        final Path snapshotPath,
        final StreamClock streamClock,
        final MutableProcessingState processingState,
        final MeterRegistry meterRegistry) {
      this.streamProcessor = streamProcessor;
      this.zeebeDb = zeebeDb;
      this.runtimePath = runtimePath;
      this.snapshotPath = snapshotPath;
      this.streamClock = streamClock;
      this.processingState = processingState;
      this.meterRegistry = meterRegistry;
    }

    public static ProcessorContext createStreamContext(
        final StreamProcessor streamProcessor,
        final ZeebeDb zeebeDb,
        final Path runtimePath,
        final Path snapshotPath,
        final StreamClock streamClock,
        final MutableProcessingState processingState,
        final MeterRegistry meterRegistry) {
      return new ProcessorContext(
          streamProcessor,
          zeebeDb,
          runtimePath,
          snapshotPath,
          streamClock,
          processingState,
          meterRegistry);
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
