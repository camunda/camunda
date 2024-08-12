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
import io.camunda.zeebe.stream.api.ReadonlyStreamProcessorContext;
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
import java.time.Instant;
import java.time.InstantSource;
import java.util.Collections;
import java.util.Comparator;
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

public final class InMemoryEngine implements ControllableEngine {

  private final Engine engine;
  private final LogStreamWriter[] writers;
  private final int partitionId;
  private final LogStreamReader reader;
  private final LogStorage logStorage;
  private final TestScheduleService scheduleService;
  private final ControllableInstantSource clock;
  private final TestInterPartitionCommandSender interPartitionCommandSender;

  InMemoryEngine(
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
    clock = new ControllableInstantSource(InstantSource.system().millis());
    scheduleService = new TestScheduleService(clock);
    this.writers = writers;
    interPartitionCommandSender = new TestInterPartitionCommandSender(this.writers);
    final KeyGeneratorControls keyGeneratorControls = new TestKeyGenerator(partitionId);
    final var recordProcessorContext =
        new RecordProcessorContextImpl(
            partitionId,
            scheduleService,
            zeebeDb,
            transactionContext,
            interPartitionCommandSender,
            keyGeneratorControls,
            clock);
    engine.init(recordProcessorContext);
    for (final var lifecycleListener : recordProcessorContext.getLifecycleListeners()) {
      lifecycleListener.onRecovered(
          new ReadonlyStreamProcessorContext() {
            @Override
            public ProcessingScheduleService getScheduleService() {
              return scheduleService;
            }

            @Override
            public int getPartitionId() {
              return partitionId;
            }

            @Override
            public boolean enableAsyncScheduledTasks() {
              return true;
            }

            @Override
            public InstantSource getClock() {
              return clock;
            }
          });
    }
  }

  @Override
  public void writeRecord(final LogAppendEntry entry) {
    writeInternal(entry);
  }

  @Override
  public void processNextCommand(final boolean deliverIpc) {
    final ProcessingResultBuilder resultBuilder = new TestProcessingResultBuilder();
    final RecordMetadata metadata = new RecordMetadata();
    if (!deliverIpc) {
      interPartitionCommandSender.disable();
    }
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
    interPartitionCommandSender.enable();
  }

  @Override
  public void executeScheduledTask(final boolean deliverIpc) {
    final var taskResult = scheduleService.runNext();
    if (!deliverIpc) {
      interPartitionCommandSender.disable();
    }
    if (taskResult != null && !taskResult.getRecordBatch().isEmpty()) {
      writeInternal(taskResult.getRecordBatch().entries());
    }
    interPartitionCommandSender.enable();
  }

  @Override
  public void updateClock(final Duration duration) {
    clock.add(duration);
  }

  @Override
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

  public static final class TestScheduleService implements ProcessingScheduleService {
    private final InstantSource clock;
    private final SortedSet<QueuedTask> taskQueue = new TreeSet<>();

    public TestScheduleService(final InstantSource clock) {
      this.clock = clock;
    }

    public TaskResult runNext() {
      final var now = clock.instant();
      if (taskQueue.isEmpty()) {
        return null;
      }
      final var nextTask = taskQueue.first();
      if (nextTask.scheduledFor().isBefore(now)) {
        taskQueue.removeFirst();
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
      final var queuedTask = new QueuedTask(Instant.ofEpochMilli(timestamp), task);
      taskQueue.add(queuedTask);
      return queuedTask;
    }

    @Override
    public ScheduledTask runAt(final long timestamp, final Runnable task) {
      return runAt(
          timestamp,
          taskResultBuilder -> {
            task.run();
            return taskResultBuilder.build();
          });
    }

    @Override
    public void runAtFixedRate(final Duration delay, final Task task) {
      throw new UnsupportedOperationException();
    }

    private record QueuedTask(Instant scheduledFor, Task task)
        implements Comparable<QueuedTask>, ScheduledTask {

      @Override
      public int compareTo(final QueuedTask o) {
        return Comparator.comparing(QueuedTask::scheduledFor)
            .thenComparing(java.lang.Record::hashCode)
            .compare(this, o);
      }

      @Override
      public void cancel() {
        throw new UnsupportedOperationException();
        // taskQueue.remove(this);
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
    private boolean enabled = true;

    TestInterPartitionCommandSender(final LogStreamWriter[] writers) {
      this.writers = writers;
    }

    void disable() {
      enabled = false;
    }

    void enable() {
      enabled = true;
    }

    @Override
    public void sendCommand(
        final int receiverPartitionId,
        final ValueType valueType,
        final Intent intent,
        final UnifiedRecordValue command) {
      if (!enabled) {
        return;
      }
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
      if (!enabled) {
        return;
      }
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

  private static final class ControllableInstantSource implements InstantSource {
    private long currentTime;

    ControllableInstantSource(final long currentTime) {
      this.currentTime = currentTime;
    }

    @Override
    public Instant instant() {
      return Instant.ofEpochMilli(currentTime);
    }

    @Override
    public long millis() {
      return currentTime;
    }

    public void add(final Duration difference) {
      currentTime += difference.toMillis();
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
