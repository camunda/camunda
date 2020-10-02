/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.util;

import static io.zeebe.engine.util.StreamProcessingComposite.getLogName;

import io.zeebe.db.ZeebeDbFactory;
import io.zeebe.engine.processor.CommandResponseWriter;
import io.zeebe.engine.processor.StreamProcessor;
import io.zeebe.engine.processor.TypedRecord;
import io.zeebe.engine.processor.TypedRecordProcessorFactory;
import io.zeebe.engine.state.DefaultZeebeDbFactory;
import io.zeebe.engine.state.ZeebeState;
import io.zeebe.engine.util.StreamProcessingComposite.StreamProcessorTestFactory;
import io.zeebe.logstreams.log.LogStreamRecordWriter;
import io.zeebe.logstreams.util.SynchronousLogStream;
import io.zeebe.msgpack.UnpackedObject;
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

public final class StreamProcessorRule implements TestRule {

  private static final Logger LOG = new ZbLogger("io.zeebe.broker.test");

  private static final int PARTITION_ID = 0;

  // environment
  private final TemporaryFolder tempFolder;
  private final AutoCloseableRule closeables = new AutoCloseableRule();
  private final ControlledActorClock clock = new ControlledActorClock();
  private final ActorSchedulerRule actorSchedulerRule = new ActorSchedulerRule(clock);
  private final ZeebeDbFactory zeebeDbFactory;
  private final SetupRule rule;
  private final int startPartitionId;
  private final int partitionCount;
  private final RuleChain chain;
  private TestStreams streams;
  private StreamProcessingComposite streamProcessingComposite;

  public StreamProcessorRule() {
    this(new TemporaryFolder());
  }

  public StreamProcessorRule(final TemporaryFolder temporaryFolder) {
    this(PARTITION_ID, temporaryFolder);
  }

  public StreamProcessorRule(final int partitionId) {
    this(partitionId, 1, DefaultZeebeDbFactory.DEFAULT_DB_FACTORY);
  }

  public StreamProcessorRule(final int partitionId, final TemporaryFolder temporaryFolder) {
    this(partitionId, 1, DefaultZeebeDbFactory.DEFAULT_DB_FACTORY, temporaryFolder);
  }

  public StreamProcessorRule(
      final int startPartitionId, final int partitionCount, final ZeebeDbFactory dbFactory) {
    this(startPartitionId, partitionCount, dbFactory, new TemporaryFolder());
  }

  public StreamProcessorRule(
      final int startPartitionId,
      final int partitionCount,
      final ZeebeDbFactory dbFactory,
      final TemporaryFolder temporaryFolder) {
    this.startPartitionId = startPartitionId;
    this.partitionCount = partitionCount;

    rule = new SetupRule(startPartitionId, partitionCount);

    tempFolder = temporaryFolder;
    zeebeDbFactory = dbFactory;
    chain =
        RuleChain.outerRule(tempFolder)
            .around(actorSchedulerRule)
            .around(new CleanUpRule(tempFolder::getRoot))
            .around(closeables)
            .around(rule)
            .around(new FailedTestRecordPrinter());
  }

  public ActorSchedulerRule getActorSchedulerRule() {
    return actorSchedulerRule;
  }

  @Override
  public Statement apply(final Statement base, final Description description) {
    return chain.apply(base, description);
  }

  public LogStreamRecordWriter getLogStreamRecordWriter(final int partitionId) {
    return streamProcessingComposite.getLogStreamRecordWriter(partitionId);
  }

  public StreamProcessor startTypedStreamProcessor(final StreamProcessorTestFactory factory) {
    return streamProcessingComposite.startTypedStreamProcessor(factory, r -> {});
  }

  public StreamProcessor startTypedStreamProcessor(
      final StreamProcessorTestFactory factory, final Consumer<TypedRecord> onProcessedListener) {
    return streamProcessingComposite.startTypedStreamProcessor(factory, onProcessedListener);
  }

  public StreamProcessor startTypedStreamProcessor(final TypedRecordProcessorFactory factory) {
    return startTypedStreamProcessor(startPartitionId, factory);
  }

  public StreamProcessor startTypedStreamProcessor(
      final int partitionId, final TypedRecordProcessorFactory factory) {
    return streamProcessingComposite.startTypedStreamProcessor(partitionId, factory);
  }

  public void closeStreamProcessor(final int partitionId) {
    streamProcessingComposite.closeStreamProcessor(partitionId);
  }

  public void closeStreamProcessor() {
    closeStreamProcessor(startPartitionId);
  }

  public StreamProcessor getStreamProcessor(final int partitionId) {
    return streamProcessingComposite.getStreamProcessor(partitionId);
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
    return streamProcessingComposite.getZeebeState();
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
    return streamProcessingComposite.writeWorkflowInstanceEventWithSource(
        intent, instanceKey, sourceEventPosition);
  }

  public long writeWorkflowInstanceEvent(
      final WorkflowInstanceIntent intent, final int instanceKey) {
    return streamProcessingComposite.writeWorkflowInstanceEvent(intent, instanceKey);
  }

  public long writeEvent(final long key, final Intent intent, final UnpackedObject value) {
    return streamProcessingComposite.writeEvent(key, intent, value);
  }

  public long writeEvent(final Intent intent, final UnpackedObject value) {
    return streamProcessingComposite.writeEvent(intent, value);
  }

  public long writeBatch(final RecordToWrite... recordToWrites) {
    return streamProcessingComposite.writeBatch(recordToWrites);
  }

  public long writeCommandOnPartition(
      final int partition, final Intent intent, final UnpackedObject value) {
    return streamProcessingComposite.writeCommandOnPartition(partition, intent, value);
  }

  public long writeCommandOnPartition(
      final int partition, final long key, final Intent intent, final UnpackedObject value) {
    return streamProcessingComposite.writeCommandOnPartition(partition, key, intent, value);
  }

  public long writeCommand(final long key, final Intent intent, final UnpackedObject value) {
    return streamProcessingComposite.writeCommand(key, intent, value);
  }

  public long writeCommand(final Intent intent, final UnpackedObject value) {
    return streamProcessingComposite.writeCommand(intent, value);
  }

  public long writeCommand(
      final int requestStreamId,
      final long requestId,
      final Intent intent,
      final UnpackedObject value) {
    return streamProcessingComposite.writeCommand(requestStreamId, requestId, intent, value);
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

      streamProcessingComposite =
          new StreamProcessingComposite(streams, startPartitionId, zeebeDbFactory);
    }

    @Override
    protected void after() {
      streams = null;
      streamProcessingComposite = null;
    }
  }

  private class FailedTestRecordPrinter extends TestWatcher {

    @Override
    protected void failed(final Throwable e, final Description description) {
      LOG.info("Test failed, following records were exported:");
      printAllRecords();
    }
  }

  private class CleanUpRule extends ExternalResource {

    private File root;
    private final Supplier<File> rootSupplier;

    CleanUpRule(final Supplier<File> rootSupplier) {
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
      } catch (final IOException e) {
        LOG.error("Error on deleting root test folder", e);
      }
    }
  }
}
