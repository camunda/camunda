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
import io.camunda.zeebe.engine.Loggers;
import io.camunda.zeebe.engine.api.EmptyProcessingResult;
import io.camunda.zeebe.engine.api.ReadonlyStreamProcessorContext;
import io.camunda.zeebe.engine.api.RecordProcessor;
import io.camunda.zeebe.engine.api.StreamProcessorLifecycleAware;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.CommandResponseWriter;
import io.camunda.zeebe.engine.state.appliers.EventAppliers;
import io.camunda.zeebe.logstreams.log.LogStreamBatchWriter;
import io.camunda.zeebe.logstreams.log.LogStreamReader;
import io.camunda.zeebe.logstreams.log.LoggedEvent;
import io.camunda.zeebe.logstreams.storage.LogStorage;
import io.camunda.zeebe.logstreams.util.ListLogStorage;
import io.camunda.zeebe.logstreams.util.SyncLogStream;
import io.camunda.zeebe.logstreams.util.SynchronousLogStream;
import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.scheduler.ActorScheduler;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.streamprocessor.LegacyTypedStreamWriter;
import io.camunda.zeebe.streamprocessor.StreamProcessor;
import io.camunda.zeebe.streamprocessor.StreamProcessorMode;
import io.camunda.zeebe.util.FileUtil;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.slf4j.Logger;

public final class StreamPlatform {

  private static final String SNAPSHOT_FOLDER = "snapshot";
  private static final Logger LOG = Loggers.STREAM_PROCESSING;
  private static final int DEFAULT_PARTITION = 1;
  private static final String STREAM_NAME = "stream-";

  private final Path dataDirectory;
  private final List<AutoCloseable> closeables;
  private final ActorScheduler actorScheduler;
  private final CommandResponseWriter mockCommandResponseWriter;
  private final Map<String, LogContext> logContextMap = new HashMap<>();
  private final Map<String, ProcessorContext> streamContextMap = new HashMap<>();
  private boolean snapshotWasTaken = false;
  private final StreamProcessorMode streamProcessorMode = StreamProcessorMode.PROCESSING;
  private RecordProcessor recordProcessor;

  private final WriteActor writeActor = new WriteActor();
  private final ZeebeDbFactory zeebeDbFactory;

  public StreamPlatform(
      final Path dataDirectory,
      final List<AutoCloseable> closeables,
      final ActorScheduler actorScheduler,
      final ZeebeDbFactory zeebeDbFactory) {
    this.dataDirectory = dataDirectory;
    this.closeables = closeables;
    this.actorScheduler = actorScheduler;
    this.zeebeDbFactory = zeebeDbFactory;

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
    actorScheduler.submitActor(writeActor);
  }

  public SynchronousLogStream createLogStream(final String name, final int partitionId) {
    final var listLogStorage = new ListLogStorage();
    return createLogStream(
        name,
        partitionId,
        listLogStorage,
        logStream -> listLogStorage.setPositionListener(logStream::setLastWrittenPosition));
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
    closeables.add(logContext);
    closeables.add(() -> logContextMap.remove(name));
    return logStream;
  }

  public SynchronousLogStream getLogStream(final String name) {
    return logContextMap.get(name).getLogStream();
  }

  public Stream<LoggedEvent> events(final String logName) {
    final SynchronousLogStream logStream = getLogStream(logName);

    final LogStreamReader reader = logStream.newLogStreamReader();
    closeables.add(reader);

    reader.seekToFirstEvent();

    final Iterable<LoggedEvent> iterable = () -> reader;

    return StreamSupport.stream(iterable.spliterator(), false);
  }

  public Path createRuntimeFolder(final SynchronousLogStream stream) {
    final Path rootDirectory = dataDirectory.resolve(stream.getLogName()).resolve("state");

    try {
      Files.createDirectories(rootDirectory);
    } catch (final FileAlreadyExistsException ignored) {
      // totally fine if it already exists
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }

    return rootDirectory.resolve("runtime");
  }

  public StreamProcessor startStreamProcessor() {
    final var logName = getLogName(DEFAULT_PARTITION);
    final SynchronousLogStream stream = getLogStream(logName);
    return buildStreamProcessor(stream, null, true);
  }

  public StreamProcessor buildStreamProcessor(
      final SynchronousLogStream stream,
      final Function<LogStreamBatchWriter, LegacyTypedStreamWriter> streamWriterFactory,
      final boolean awaitOpening) {
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

    final ZeebeDb<?> zeebeDb;
    if (snapshotWasTaken) {
      zeebeDb = zeebeDbFactory.createDb(snapshot.toFile());
    } else {
      zeebeDb = zeebeDbFactory.createDb(storage.toFile());
    }
    final String logName = stream.getLogName();
    recordProcessor = mock(RecordProcessor.class);
    when(recordProcessor.process(any(), any())).thenReturn(EmptyProcessingResult.INSTANCE);
    when(recordProcessor.onProcessingError(any(), any(), any()))
        .thenReturn(EmptyProcessingResult.INSTANCE);

    final var builder =
        StreamProcessor.builder()
            .logStream(stream.getAsyncLogStream())
            .zeebeDb(zeebeDb)
            .actorSchedulingService(actorScheduler)
            .commandResponseWriter(mockCommandResponseWriter)
            .recordProcessor(recordProcessor)
            .eventApplierFactory(EventAppliers::new) // todo remove this soon
            .streamProcessorMode(streamProcessorMode);

    builder.getLifecycleListeners().add(recoveredAwaiter);

    if (streamWriterFactory != null) {
      builder.typedStreamWriterFactory(streamWriterFactory);
    }
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
    closeables.add(processorContext);

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

  public RecordProcessor getRecordProcessor() {
    return recordProcessor;
  }

  public StreamProcessor getStreamProcessor() {
    return getStreamProcessor(getLogName(DEFAULT_PARTITION));
  }

  public StreamProcessor getStreamProcessor(final String streamName) {
    return Optional.ofNullable(streamContextMap.get(streamName))
        .map(c -> c.streamProcessor)
        .orElseThrow(
            () -> new NoSuchElementException("No stream processor found with name: " + streamName));
  }

  public LogStreamBatchWriter setupBatchWriter(
      final String logName, final RecordToWrite[] recordToWrites) {
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
    return logStreamBatchWriter;
  }

  public static String getLogName(final int partitionId) {
    return STREAM_NAME + partitionId;
  }

  public long writeBatch(final RecordToWrite... recordsToWrite) {
    final var batchWriter = setupBatchWriter(getLogName(DEFAULT_PARTITION), recordsToWrite);
    return writeActor.submit(batchWriter::tryWrite).join();
  }

  /** Used to run writes within an actor thread. */
  private static final class WriteActor extends Actor {
    public ActorFuture<Long> submit(final Callable<Long> write) {
      return actor.call(write);
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
