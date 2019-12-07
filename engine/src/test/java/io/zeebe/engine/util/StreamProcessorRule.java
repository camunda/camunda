/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.util;

import static io.zeebe.engine.util.Records.workflowInstance;

import io.zeebe.db.ZeebeDbFactory;
import io.zeebe.engine.processor.CommandResponseWriter;
import io.zeebe.engine.processor.StreamProcessor;
import io.zeebe.engine.processor.TypedRecord;
import io.zeebe.engine.processor.TypedRecordProcessorFactory;
import io.zeebe.engine.processor.TypedRecordProcessors;
import io.zeebe.engine.state.DefaultZeebeDbFactory;
import io.zeebe.engine.state.ZeebeState;
import io.zeebe.logstreams.log.LogStreamRecordWriter;
import io.zeebe.logstreams.state.StateSnapshotController;
import io.zeebe.logstreams.util.SynchronousLogStream;
import io.zeebe.msgpack.UnpackedObject;
import io.zeebe.protocol.record.RecordType;
import io.zeebe.protocol.record.intent.Intent;
import io.zeebe.protocol.record.intent.WorkflowInstanceIntent;
import io.zeebe.test.util.AutoCloseableRule;
import io.zeebe.util.FileUtil;
import io.zeebe.util.ZbLogger;
import io.zeebe.util.allocation.DirectBufferAllocator;
import io.zeebe.util.sched.clock.ControlledActorClock;
import io.zeebe.util.sched.testing.ActorSchedulerRule;
import java.io.File;
import java.io.IOException;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.junit.rules.ExternalResource;
import org.junit.rules.RuleChain;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.TestRule;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.slf4j.Logger;

public class StreamProcessorRule implements TestRule {

  private static final Logger LOG = new ZbLogger("io.zeebe.broker.test");

  private static final int PARTITION_ID = 0;
  // things provisioned by this rule
  private static final String STREAM_NAME = "stream-";
  // environment
  private final TemporaryFolder tempFolder = new TemporaryFolder();
  private final AutoCloseableRule closeables = new AutoCloseableRule();
  private final ControlledActorClock clock = new ControlledActorClock();
  private final ActorSchedulerRule actorSchedulerRule = new ActorSchedulerRule(clock);
  private final ZeebeDbFactory zeebeDbFactory;
  private final SetupRule rule;
  private final int startPartitionId;
  private final int partitionCount;
  private final RuleChain chain;
  private TestStreams streams;
  private ZeebeState zeebeState;

  public StreamProcessorRule() {
    this(PARTITION_ID);
  }

  public StreamProcessorRule(final int partitionId) {
    this(partitionId, 1, DefaultZeebeDbFactory.DEFAULT_DB_FACTORY);
  }

  public StreamProcessorRule(
      final int startPartitionId, final int partitionCount, final ZeebeDbFactory dbFactory) {
    this.startPartitionId = startPartitionId;
    this.partitionCount = partitionCount;

    rule = new SetupRule(startPartitionId, partitionCount);

    zeebeDbFactory = dbFactory;
    chain =
        RuleChain.outerRule(tempFolder)
            .around(actorSchedulerRule)
            .around(new CleanUpRule(tempFolder::getRoot))
            .around(closeables)
            .around(rule)
            .around(new FailedTestRecordPrinter());
  }

  @Override
  public Statement apply(final Statement base, final Description description) {
    return chain.apply(base, description);
  }

  public LogStreamRecordWriter getLogStreamRecordWriter(final int partitionId) {
    final String logName = getLogName(partitionId);
    return streams.getLogStreamRecordWriter(logName);
  }

  public LogStreamRecordWriter newLogStreamRecordWriter(final int partitionId) {
    final String logName = getLogName(partitionId);
    return streams.newLogStreamRecordWriter(logName);
  }

  public StreamProcessor startTypedStreamProcessor(final StreamProcessorTestFactory factory) {
    return startTypedStreamProcessor(
        (processingContext) -> {
          zeebeState = processingContext.getZeebeState();
          return factory.build(TypedRecordProcessors.processors(), zeebeState);
        });
  }

  public StreamProcessor startTypedStreamProcessor(final TypedRecordProcessorFactory factory) {
    return startTypedStreamProcessor(startPartitionId, factory);
  }

  public StreamProcessor startTypedStreamProcessor(
      final int partitionId, final TypedRecordProcessorFactory factory) {
    return streams.startStreamProcessor(
        getLogName(partitionId),
        zeebeDbFactory,
        (processingContext -> {
          zeebeState = processingContext.getZeebeState();
          return factory.createProcessors(processingContext);
        }));
  }

  public void closeStreamProcessor(final int partitionId) {
    try {
      streams.closeProcessor(getLogName(partitionId));
    } catch (final Exception e) {
      throw new RuntimeException(e);
    }
  }

  public void closeStreamProcessor() {
    closeStreamProcessor(startPartitionId);
  }

  public StateSnapshotController getStateSnapshotController(final int partitionId) {
    return streams.getStateSnapshotController(getLogName(partitionId));
  }

  public StateSnapshotController getStateSnapshotController() {
    return getStateSnapshotController(startPartitionId);
  }

  public CommandResponseWriter getCommandResponseWriter() {
    return streams.getMockedResponseWriter();
  }

  public Consumer<TypedRecord> getProcessedListener() {
    return streams.getMockedOnProcessedListener();
  }

  public ControlledActorClock getClock() {
    return clock;
  }

  public ZeebeState getZeebeState() {
    return zeebeState;
  }

  public RecordStream events() {
    return new RecordStream(streams.events(getLogName(startPartitionId)));
  }

  public void printAllRecords() {
    int partitionId = startPartitionId;
    for (int i = 0; i < partitionCount; i++) {
      final SynchronousLogStream logStream = streams.getLogStream(getLogName(partitionId++));
      LogStreamPrinter.printRecords(logStream);
    }
  }

  public long writeWorkflowInstanceEvent(final WorkflowInstanceIntent intent) {
    return writeWorkflowInstanceEvent(intent, 1);
  }

  public long writeWorkflowInstanceEventWithSource(
      final WorkflowInstanceIntent intent, final int instanceKey, final long sourceEventPosition) {
    return streams
        .newRecord(getLogName(startPartitionId))
        .event(workflowInstance(instanceKey))
        .recordType(RecordType.EVENT)
        .sourceRecordPosition(sourceEventPosition)
        .intent(intent)
        .write();
  }

  public long writeWorkflowInstanceEvent(
      final WorkflowInstanceIntent intent, final int instanceKey) {
    return streams
        .newRecord(getLogName(startPartitionId))
        .event(workflowInstance(instanceKey))
        .recordType(RecordType.EVENT)
        .intent(intent)
        .write();
  }

  public long writeEvent(final long key, final Intent intent, final UnpackedObject value) {
    return streams
        .newRecord(getLogName(startPartitionId))
        .recordType(RecordType.EVENT)
        .key(key)
        .intent(intent)
        .event(value)
        .write();
  }

  public long writeEvent(final Intent intent, final UnpackedObject value) {
    return streams
        .newRecord(getLogName(startPartitionId))
        .recordType(RecordType.EVENT)
        .intent(intent)
        .event(value)
        .write();
  }

  public long writeBatch(final RecordToWrite... recordToWrites) {
    return streams.writeBatch(getLogName(startPartitionId), recordToWrites);
  }

  public long writeCommandOnPartition(
      final int partition, final Intent intent, final UnpackedObject value) {
    return streams
        .newRecord(getLogName(partition))
        .recordType(RecordType.COMMAND)
        .intent(intent)
        .event(value)
        .write();
  }

  public long writeCommandOnPartition(
      final LogStreamRecordWriter writer,
      final long key,
      final Intent intent,
      final UnpackedObject value) {
    return streams
        .newRecord(writer)
        .key(key)
        .recordType(RecordType.COMMAND)
        .intent(intent)
        .event(value)
        .write();
  }

  public long writeCommandOnPartition(
      final int partition, final long key, final Intent intent, final UnpackedObject value) {
    return streams
        .newRecord(getLogName(partition))
        .key(key)
        .recordType(RecordType.COMMAND)
        .intent(intent)
        .event(value)
        .write();
  }

  public long writeCommand(final long key, final Intent intent, final UnpackedObject value) {
    return streams
        .newRecord(getLogName(startPartitionId))
        .recordType(RecordType.COMMAND)
        .key(key)
        .intent(intent)
        .event(value)
        .write();
  }

  public long writeCommand(final Intent intent, final UnpackedObject value) {
    return streams
        .newRecord(getLogName(startPartitionId))
        .recordType(RecordType.COMMAND)
        .intent(intent)
        .event(value)
        .write();
  }

  public long writeCommand(
      int requestStreamId, long requestId, final Intent intent, final UnpackedObject value) {
    return streams
        .newRecord(getLogName(startPartitionId))
        .recordType(RecordType.COMMAND)
        .requestId(requestId)
        .requestStreamId(requestStreamId)
        .intent(intent)
        .event(value)
        .write();
  }

  private static String getLogName(final int partitionId) {
    return STREAM_NAME + partitionId;
  }

  @FunctionalInterface
  public interface StreamProcessorTestFactory {
    TypedRecordProcessors build(TypedRecordProcessors builder, ZeebeState zeebeState);
  }

  private class SetupRule extends ExternalResource {

    private final int startPartitionId;
    private final int partitionCount;

    SetupRule(final int startPartitionId, final int partitionCount) {
      this.startPartitionId = startPartitionId;
      this.partitionCount = partitionCount;
    }

    @Override
    protected void before() {
      streams = new TestStreams(tempFolder, closeables, actorSchedulerRule.get());

      int partitionId = startPartitionId;
      for (int i = 0; i < partitionCount; i++) {
        streams.createLogStream(getLogName(partitionId), partitionId++);
      }
    }

    @Override
    protected void after() {
      streams = null;
    }
  }

  private class FailedTestRecordPrinter extends TestWatcher {

    @Override
    protected void failed(final Throwable e, final Description description) {
      LOG.info("Test failed, following records where exported:");
      printAllRecords();
    }
  }

  private class CleanUpRule extends ExternalResource {

    private File root;
    private final Supplier<File> rootSupplier;

    CleanUpRule(Supplier<File> rootSupplier) {
      this.rootSupplier = rootSupplier;
    }

    @Override
    protected void before() {
      root = rootSupplier.get();
    }

    @Override
    protected void after() {
      try {
        LOG.debug("Clean up test files on path {}", root);
        FileUtil.deleteFolder(root.toPath());

        final long allocatedMemoryInKb = DirectBufferAllocator.getAllocatedMemoryInKb();
        if (allocatedMemoryInKb > 0) {
          LOG.warn(
              "There are still allocated direct buffers of a total size of {}kB.",
              allocatedMemoryInKb);
        }
      } catch (IOException e) {
        LOG.error("Error on deleting root test folder", e);
      }
    }
  }
}
