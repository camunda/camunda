/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.property;

import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.engine.Engine;
import io.camunda.zeebe.engine.EngineConfiguration;
import io.camunda.zeebe.engine.processing.EngineProcessors;
import io.camunda.zeebe.engine.processing.message.command.SubscriptionCommandSender;
import io.camunda.zeebe.engine.processing.streamprocessor.JobStreamer;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessorFactory;
import io.camunda.zeebe.engine.property.Action.ExecuteScheduledTask;
import io.camunda.zeebe.engine.property.Action.ProcessRecord;
import io.camunda.zeebe.engine.property.Action.WriteRecord;
import io.camunda.zeebe.engine.property.db.InMemoryDb;
import io.camunda.zeebe.logstreams.impl.log.LogStreamReaderImpl;
import io.camunda.zeebe.logstreams.impl.log.LoggedEventImpl;
import io.camunda.zeebe.logstreams.impl.log.SequencedBatch;
import io.camunda.zeebe.logstreams.log.LogAppendEntry;
import io.camunda.zeebe.logstreams.log.LogStreamReader;
import io.camunda.zeebe.logstreams.log.LogStreamWriter;
import io.camunda.zeebe.logstreams.log.WriteContext;
import io.camunda.zeebe.logstreams.storage.LogStorage;
import io.camunda.zeebe.logstreams.storage.LogStorage.AppendListener;
import io.camunda.zeebe.logstreams.util.ListLogStorage;
import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.stream.api.InterPartitionCommandSender;
import io.camunda.zeebe.stream.api.PostCommitTask;
import io.camunda.zeebe.stream.api.ProcessingResponse;
import io.camunda.zeebe.stream.api.ProcessingResult;
import io.camunda.zeebe.stream.api.ProcessingResultBuilder;
import io.camunda.zeebe.stream.api.records.ImmutableRecordBatch;
import io.camunda.zeebe.stream.api.scheduling.ProcessingScheduleService;
import io.camunda.zeebe.stream.api.scheduling.Task;
import io.camunda.zeebe.stream.api.scheduling.TaskResult;
import io.camunda.zeebe.stream.api.scheduling.TaskResultBuilder;
import io.camunda.zeebe.stream.api.state.KeyGeneratorControls;
import io.camunda.zeebe.stream.impl.RecordProcessorContextImpl;
import io.camunda.zeebe.stream.impl.TypedEventRegistry;
import io.camunda.zeebe.stream.impl.records.RecordBatchEntry;
import io.camunda.zeebe.stream.impl.records.TypedRecordImpl;
import io.camunda.zeebe.util.Either;
import io.camunda.zeebe.util.FeatureFlags;
import io.camunda.zeebe.util.ReflectUtil;
import io.camunda.zeebe.util.buffer.BufferWriter;
import java.time.Duration;
import java.time.InstantSource;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.SortedSet;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.agrona.concurrent.UnsafeBuffer;
import org.jetbrains.annotations.NotNull;

public final class InMemoryEngine implements TestEngines {

  private final Engine engine;
  private final LogStreamWriter[] writers;
  private final int partitionId;
  private final LogStreamReader reader;
  private final LogStorage logStorage;
  private final TestScheduleService scheduleService;

  private InMemoryEngine(
      final int partitionId,
      final int partitionCount,
      final LogStorage logStorage,
      final LogStreamWriter[] writers) {
    this.partitionId = partitionId;
    this.logStorage = logStorage;
    reader = new LogStreamReaderImpl(logStorage.newReader());
    final TypedRecordProcessorFactory processorFactory =
        recordProcessorContext -> {
          final var partitionCommandSender = recordProcessorContext.getPartitionCommandSender();
          final var subscriptionCommandSender =
              new SubscriptionCommandSender(
                  recordProcessorContext.getPartitionId(), partitionCommandSender);

          return EngineProcessors.createEngineProcessors(
              recordProcessorContext,
              partitionCount,
              subscriptionCommandSender,
              partitionCommandSender,
              FeatureFlags.createDefaultForTests(),
              JobStreamer.noop());
        };
    engine = new Engine(processorFactory, new EngineConfiguration());
    final ZeebeDb<?> zeebeDb = new InMemoryDb<>();
    final TransactionContext transactionContext = zeebeDb.createContext();
    // TODO: needs a controllable clock
    final InstantSource clock = InstantSource.system();
    scheduleService = new TestScheduleService(clock);
    this.writers = writers;
    final InterPartitionCommandSender interPartitionCommandSender =
        new TestInterPartitionCommandSender(this.writers);
    final KeyGeneratorControls keyGeneratorControls = new TestKeyGenerator(partitionId);
    engine.init(
        new RecordProcessorContextImpl(
            partitionId,
            scheduleService,
            zeebeDb,
            transactionContext,
            interPartitionCommandSender,
            keyGeneratorControls));
  }

  public static InMemoryEngine createEngine() {
    final var logStorage = new ListLogStorage();
    final var logStreamWriter = new TestLogStreamWriter(logStorage, InstantSource.system());

    return new InMemoryEngine(1, 1, logStorage, new LogStreamWriter[] {logStreamWriter});
  }

  public static InMemoryEngine[] createEngines(final int partitionCount) {
    final var logStorages = new ListLogStorage[partitionCount];
    final var logStreamWriters = new TestLogStreamWriter[partitionCount];
    for (int i = 0; i < partitionCount; i++) {
      final var logStorage = new ListLogStorage();
      final var logStreamWriter = new TestLogStreamWriter(logStorage, InstantSource.system());
      logStorages[i] = logStorage;
      logStreamWriters[i] = logStreamWriter;
    }
    final var engines = new InMemoryEngine[partitionCount];
    for (int i = 0; i < partitionCount; i++) {
      engines[i] = new InMemoryEngine(i + 1, partitionCount, logStorages[i], logStreamWriters);
    }
    return engines;
  }

  public void runAction(final Action action) {
    switch (action) {
      case WriteRecord(final var partition, final var entry) -> writeInternal(entry);
      case final ProcessRecord process -> {
        final ProcessingResultBuilder resultBuilder = new TestProcessingResultBuilder();
        final RecordMetadata metadata = new RecordMetadata();
        while (reader.hasNext()) {
          final var logEntry = reader.next();
          if (logEntry.shouldSkipProcessing()) {
            continue;
          }
          logEntry.readMetadata(metadata);
          if (metadata.getRecordType() == RecordType.COMMAND) {
            final var valueClass = TypedEventRegistry.EVENT_REGISTRY.get(metadata.getValueType());
            try {
              final var value = valueClass.getConstructor().newInstance();
              logEntry.readValue(value);
              final var record = new TypedRecordImpl(partitionId);
              record.wrap(logEntry, metadata, value);

              final var processingResult = engine.process(record, resultBuilder);
              final var newRecords = processingResult.getRecordBatch();
              writeInternal(newRecords.entries());
              processingResult.executePostCommitTasks();
              return;
            } catch (final Exception e) {
              throw new RuntimeException(e);
            }
          }
        }
      }
      case final ExecuteScheduledTask scheduledTask -> {
        scheduleService.runNext();
      }

      case null, default -> throw new UnsupportedOperationException();
    }
  }

  private void writeInternal(final LogAppendEntry entry) {
    final var result = writers[partitionId - 1].tryWrite(WriteContext.internal(), entry);
    if (result.isLeft()) {
      throw new IllegalStateException("Failed to write entry: " + result.getLeft());
    }
  }

  private void writeInternal(final List<LogAppendEntry> entries) {
    final var result = writers[partitionId - 1].tryWrite(WriteContext.internal(), entries);
    if (result.isLeft()) {
      throw new IllegalStateException("Failed to write entry: " + result.getLeft());
    }
  }

  public Stream<? extends Record<?>> records() {
    final var logStorageReader = logStorage.newReader();
    final var logStreamReader = new LogStreamReaderImpl(logStorageReader);
    return StreamSupport.stream(
            Spliterators.spliteratorUnknownSize(logStreamReader, Spliterator.ORDERED), false)
        .map(
            logEntry -> {
              final var bufferCopy = new UnsafeBuffer(new byte[logEntry.getLength()]);
              final var logEntryCopy = new LoggedEventImpl();
              final var record = new TypedRecordImpl(partitionId);

              logEntry.write(bufferCopy, 0);
              logEntryCopy.wrap(bufferCopy, 0);

              final var metadata = new RecordMetadata();
              logEntryCopy.readMetadata(metadata);

              final var value =
                  ReflectUtil.newInstance(
                      TypedEventRegistry.EVENT_REGISTRY.get(metadata.getValueType()));
              logEntryCopy.readValue(value);

              record.wrap(logEntryCopy, metadata, value);
              return (Record<?>) record;
            })
        .onClose(logStreamReader::close)
        .onClose(logStorageReader::close);
  }

  public static final class TestScheduleService implements ProcessingScheduleService {
    private final InstantSource clock;
    private final SortedSet<QueuedTask> taskQueue = new TreeSet<>();

    public TestScheduleService(final InstantSource clock) {
      this.clock = clock;
    }

    public TaskResult runNext() {
      final var now = clock.millis();
      if (taskQueue.isEmpty()) {
        return null;
      }
      final var nextTask = taskQueue.first();
      if (nextTask.scheduledFor() <= now) {
        taskQueue.remove(nextTask);
        final var result = new SimpleTaskResultBuilder();
        nextTask.task().execute(result);
        return result.build();
      }
      return null;
    }

    @Override
    public void runAtFixedRateAsync(final Duration delay, final Task task) {
      throw new UnsupportedOperationException();
    }

    @Override
    public ScheduledTask runDelayedAsync(final Duration delay, final Task task) {
      return runDelayed(delay, task);
    }

    @Override
    public ScheduledTask runAtAsync(final long timestamp, final Task task) {
      return runAt(timestamp, task);
    }

    @Override
    public ScheduledTask runDelayed(final Duration delay, final Runnable task) {
      return runDelayed(
          delay,
          taskResultBuilder -> {
            task.run();
            return taskResultBuilder.build();
          });
    }

    @Override
    public ScheduledTask runDelayed(final Duration delay, final Task task) {
      final var at = clock.instant().plus(delay);
      return runAt(at.toEpochMilli(), task);
    }

    @Override
    public ScheduledTask runAt(final long timestamp, final Task task) {
      final var queuedTask = new QueuedTask(timestamp, task, taskQueue);
      taskQueue.add(queuedTask);
      return queuedTask;
    }

    @Override
    public void runAtFixedRate(final Duration delay, final Task task) {
      throw new UnsupportedOperationException();
    }

    private record QueuedTask(long scheduledFor, Task task, SortedSet<QueuedTask> taskQueue)
        implements Comparable<QueuedTask>, ScheduledTask {

      @Override
      public int compareTo(final QueuedTask o) {
        return Long.compare(scheduledFor, o.scheduledFor);
      }

      @Override
      public void cancel() {
        taskQueue.remove(this);
      }
    }

    private final class SimpleTaskResultBuilder implements TaskResultBuilder {
      private final List<RecordBatchEntry> records = new LinkedList<>();

      @Override
      public boolean appendCommandRecord(
          final long key, final Intent intent, final UnifiedRecordValue value) {
        final ValueType valueType = TypedEventRegistry.TYPE_REGISTRY.get(value.getClass());
        if (valueType == null) {
          // usually happens when the record is not registered at the TypedStreamEnvironment
          throw new IllegalStateException(
              "Missing value type mapping for record: " + value.getClass());
        }

        final var metadata =
            new RecordMetadata()
                .recordType(RecordType.COMMAND)
                .intent(intent)
                .rejectionType(RejectionType.NULL_VAL)
                .rejectionReason("")
                .valueType(valueType);

        records.add(new RecordBatchEntry(metadata, key, -1, value));
        return true;
      }

      @Override
      public TaskResult build() {
        return () ->
            new ImmutableRecordBatch() {

              @Override
              public Iterator<RecordBatchEntry> iterator() {
                return records.iterator();
              }

              @Override
              public List<LogAppendEntry> entries() {
                return Collections.unmodifiableList(records);
              }
            };
      }
    }
  }

  static final class TestKeyGenerator implements KeyGeneratorControls {
    private final AtomicLong key;

    TestKeyGenerator(final int partitionId) {
      key = new AtomicLong(Protocol.encodePartitionId(partitionId, 0));
    }

    @Override
    public void setKeyIfHigher(final long key) {
      this.key.updateAndGet(current -> Math.max(current, key));
    }

    @Override
    public long nextKey() {
      return key.incrementAndGet();
    }
  }

  static final class TestInterPartitionCommandSender implements InterPartitionCommandSender {
    private final LogStreamWriter[] writers;

    TestInterPartitionCommandSender(final LogStreamWriter[] writers) {
      this.writers = writers;
    }

    @Override
    public void sendCommand(
        final int receiverPartitionId,
        final ValueType valueType,
        final Intent intent,
        final UnifiedRecordValue command) {
      final var metadata =
          new RecordMetadata().recordType(RecordType.COMMAND).intent(intent).valueType(valueType);
      writers[receiverPartitionId - 1].tryWrite(
          WriteContext.interPartition(), LogAppendEntry.of(metadata, command));
    }

    @Override
    public void sendCommand(
        final int receiverPartitionId,
        final ValueType valueType,
        final Intent intent,
        final Long recordKey,
        final UnifiedRecordValue command) {
      final var metadata =
          new RecordMetadata().recordType(RecordType.COMMAND).intent(intent).valueType(valueType);
      writers[receiverPartitionId - 1].tryWrite(
          WriteContext.interPartition(), LogAppendEntry.of(recordKey, metadata, command));
    }
  }

  static final class TestLogStreamWriter implements LogStreamWriter {
    private final LogStorage logStorage;
    private final InstantSource clock;
    private long position = 1;

    TestLogStreamWriter(final LogStorage logStorage, final InstantSource clock) {
      this.logStorage = logStorage;
      this.clock = clock;
    }

    @Override
    public Either<WriteFailure, Long> tryWrite(
        final WriteContext context,
        final List<LogAppendEntry> appendEntries,
        final long sourcePosition) {
      logStorage.append(
          position,
          position + appendEntries.size() - 1,
          new SequencedBatch(clock.millis(), position, sourcePosition, appendEntries),
          new AppendListener() {});
      position = position + appendEntries.size();
      return Either.right(position - 1);
    }
  }

  private static final class TestProcessingResultBuilder implements ProcessingResultBuilder {
    private final List<RecordBatchEntry> entries = new LinkedList<>();
    private final List<PostCommitTask> postCommitTasks = new LinkedList<>();

    @Override
    public Either<RuntimeException, ProcessingResultBuilder> appendRecordReturnEither(
        final long key, final RecordValue value, final RecordMetadata metadata) {
      metadata.valueType(TypedEventRegistry.TYPE_REGISTRY.get(value.getClass()));
      final var entry = RecordBatchEntry.createEntry(key, metadata, -1, (BufferWriter) value);
      entries.add(entry);
      return Either.right(this);
    }

    @Override
    public ProcessingResultBuilder withResponse(
        final RecordType type,
        final long key,
        final Intent intent,
        final UnpackedObject value,
        final ValueType valueType,
        final RejectionType rejectionType,
        final String rejectionReason,
        final long requestId,
        final int requestStreamId) {
      return this;
    }

    @Override
    public ProcessingResultBuilder appendPostCommitTask(final PostCommitTask task) {
      postCommitTasks.add(task);
      return this;
    }

    @Override
    public ProcessingResultBuilder resetPostCommitTasks() {
      postCommitTasks.clear();
      return this;
    }

    @Override
    public ProcessingResult build() {
      return new ProcessingResult() {
        @Override
        public ImmutableRecordBatch getRecordBatch() {
          return new ImmutableRecordBatch() {
            @Override
            public List<LogAppendEntry> entries() {
              return Collections.unmodifiableList(entries);
            }

            @Override
            public @NotNull Iterator<RecordBatchEntry> iterator() {
              return entries.iterator();
            }
          };
        }

        @Override
        public Optional<ProcessingResponse> getProcessingResponse() {
          return Optional.empty();
        }

        @Override
        public boolean executePostCommitTasks() {
          postCommitTasks.forEach(PostCommitTask::flush);
          return true;
        }

        @Override
        public boolean isEmpty() {
          return entries.isEmpty();
        }
      };
    }

    @Override
    public boolean canWriteEventOfLength(final int eventLength) {
      return true;
    }
  }
}
