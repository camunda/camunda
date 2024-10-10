/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.util;

import static io.camunda.zeebe.engine.util.StreamProcessingComposite.getLogName;

import io.camunda.zeebe.db.ZeebeDbFactory;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessorFactory;
import io.camunda.zeebe.engine.state.DefaultZeebeDbFactory;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.util.StreamProcessingComposite.StreamProcessorTestFactory;
import io.camunda.zeebe.engine.util.client.CommandWriter;
import io.camunda.zeebe.logstreams.log.LogStreamWriter;
import io.camunda.zeebe.logstreams.util.ListLogStorage;
import io.camunda.zeebe.logstreams.util.TestLogStream;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.scheduler.clock.ControlledActorClock;
import io.camunda.zeebe.scheduler.testing.ActorSchedulerRule;
import io.camunda.zeebe.stream.api.CommandResponseWriter;
import io.camunda.zeebe.stream.api.StreamClock;
import io.camunda.zeebe.stream.impl.StreamProcessor;
import io.camunda.zeebe.stream.impl.StreamProcessorBuilder;
import io.camunda.zeebe.stream.impl.StreamProcessorContext;
import io.camunda.zeebe.stream.impl.StreamProcessorListener;
import io.camunda.zeebe.stream.impl.StreamProcessorMode;
import io.camunda.zeebe.test.util.AutoCloseableRule;
import io.camunda.zeebe.util.FileUtil;
import io.camunda.zeebe.util.allocation.DirectBufferAllocator;
import java.io.File;
import java.io.IOException;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.junit.rules.ExternalResource;
import org.junit.rules.RuleChain;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class StreamProcessorRule implements TestRule, CommandWriter {

  private static final Logger LOG = LoggerFactory.getLogger("io.camunda.zeebe.broker.test");

  private static final int PARTITION_ID = 0;

  // environment
  private final TemporaryFolder tempFolder;
  private final AutoCloseableRule closeables = new AutoCloseableRule();
  private final ControlledActorClock clock = new ControlledActorClock();
  private final ActorSchedulerRule actorSchedulerRule = new ActorSchedulerRule(clock);
  private final ZeebeDbFactory zeebeDbFactory;
  private final int startPartitionId;
  private final int partitionCount;
  private final RuleChain chain;
  private TestStreams streams;
  private StreamProcessingComposite streamProcessingComposite;
  private ListLogStorage sharedStorage = null;
  private StreamProcessorMode streamProcessorMode = StreamProcessorMode.PROCESSING;
  private int maxCommandsInBatch = StreamProcessorContext.DEFAULT_MAX_COMMANDS_IN_BATCH;

  public StreamProcessorRule() {
    this(new TemporaryFolder());
  }

  public StreamProcessorRule(final TemporaryFolder temporaryFolder) {
    this(PARTITION_ID, temporaryFolder);
  }

  public StreamProcessorRule(final int partitionId) {
    this(partitionId, 1, DefaultZeebeDbFactory.defaultFactory(), new TemporaryFolder());
  }

  public StreamProcessorRule(final int partitionId, final TemporaryFolder temporaryFolder) {
    this(partitionId, 1, DefaultZeebeDbFactory.defaultFactory(), temporaryFolder);
  }

  public StreamProcessorRule(
      final int startPartitionId,
      final int partitionCount,
      final ZeebeDbFactory dbFactory,
      final ListLogStorage sharedStorage) {
    this(startPartitionId, partitionCount, dbFactory, new TemporaryFolder());
    this.sharedStorage = sharedStorage;
  }

  public StreamProcessorRule(
      final int startPartitionId,
      final int partitionCount,
      final ZeebeDbFactory dbFactory,
      final TemporaryFolder temporaryFolder) {
    this.startPartitionId = startPartitionId;
    this.partitionCount = partitionCount;

    final SetupRule rule = new SetupRule(startPartitionId, partitionCount);

    tempFolder = temporaryFolder;
    zeebeDbFactory = dbFactory;
    chain =
        RuleChain.outerRule(tempFolder)
            .around(actorSchedulerRule)
            .around(new CleanUpRule(tempFolder::getRoot))
            .around(closeables)
            .around(rule);
  }

  @Override
  public Statement apply(final Statement base, final Description description) {
    return chain.apply(base, description);
  }

  public StreamProcessorRule withStreamProcessorMode(
      final StreamProcessorMode streamProcessorMode) {
    this.streamProcessorMode = streamProcessorMode;
    return this;
  }

  public LogStreamWriter newLogStreamWriter(final int partitionId) {
    return streamProcessingComposite.newLogStreamWriter(partitionId);
  }

  public StreamProcessor startTypedStreamProcessor(final StreamProcessorTestFactory factory) {
    return startTypedStreamProcessor(factory, Optional.empty());
  }

  public StreamProcessor startTypedStreamProcessor(
      final StreamProcessorTestFactory factory,
      final Optional<StreamProcessorListener> streamProcessorListenerOpt) {
    return streamProcessingComposite.startTypedStreamProcessor(factory, streamProcessorListenerOpt);
  }

  public StreamProcessor startTypedStreamProcessor(
      final int partitionId,
      final TypedRecordProcessorFactory factory,
      final Optional<StreamProcessorListener> streamProcessorListenerOpt,
      final Consumer<StreamProcessorBuilder> processorConfiguration,
      final boolean awaitOpening) {
    return streamProcessingComposite.startTypedStreamProcessor(
        partitionId, factory, streamProcessorListenerOpt, processorConfiguration, awaitOpening);
  }

  public void pauseProcessing(final int partitionId) {
    streamProcessingComposite.pauseProcessing(partitionId);
  }

  public void banInstanceInNewTransaction(final int partitionId, final long processInstanceKey) {
    streamProcessingComposite.banInstanceInNewTransaction(partitionId, processInstanceKey);
  }

  public void resumeProcessing(final int partitionId) {
    streamProcessingComposite.resumeProcessing(partitionId);
  }

  public void closeStreamProcessor(final int partitionId) {
    streamProcessingComposite.closeStreamProcessor(partitionId);
  }

  public StreamProcessor getStreamProcessor(final int partitionId) {
    return streamProcessingComposite.getStreamProcessor(partitionId);
  }

  public StreamClock getStreamClock(final int partitionId) {
    return streamProcessingComposite.getStreamClock(partitionId);
  }

  public TestLogStream getLogStream(final int partitionId) {
    return streamProcessingComposite.getLogStream(partitionId);
  }

  public CommandResponseWriter getCommandResponseWriter() {
    return streams.getMockedResponseWriter();
  }

  public ControlledActorClock getClock() {
    return clock;
  }

  public MutableProcessingState getProcessingState() {
    return streamProcessingComposite.getProcessingState();
  }

  public MutableProcessingState getProcessingState(final int partitionId) {
    return streamProcessingComposite.getProcessingState(getLogName(partitionId));
  }

  public RecordStream events() {
    return new RecordStream(streams.events(getLogName(startPartitionId)));
  }

  public void printAllRecords() {
    int partitionId = startPartitionId;
    for (int i = 0; i < partitionCount; i++) {
      final TestLogStream logStream = streams.getLogStream(getLogName(partitionId++));
      LogStreamPrinter.printRecords(logStream);
    }
  }

  public long writeBatch(final RecordToWrite... recordToWrites) {
    return streamProcessingComposite.writeBatch(recordToWrites);
  }

  @Override
  public long writeCommand(final Intent intent, final UnifiedRecordValue value) {
    return streamProcessingComposite.writeCommand(intent, value);
  }

  @Override
  public long writeCommand(
      final Intent intent, final UnifiedRecordValue value, final String... authorizedTenants) {
    return streamProcessingComposite.writeCommand(intent, value, authorizedTenants);
  }

  @Override
  public long writeCommand(final long key, final Intent intent, final UnifiedRecordValue value) {
    return streamProcessingComposite.writeCommand(key, intent, value);
  }

  @Override
  public long writeCommand(
      final long key,
      final Intent intent,
      final UnifiedRecordValue recordValue,
      final String... authorizedTenants) {
    return streamProcessingComposite.writeCommand(key, intent, recordValue, authorizedTenants);
  }

  @Override
  public long writeCommand(
      final int requestStreamId,
      final long requestId,
      final Intent intent,
      final UnifiedRecordValue value) {
    return streamProcessingComposite.writeCommand(requestStreamId, requestId, intent, value);
  }

  @Override
  public long writeCommandOnPartition(
      final int partition, final Intent intent, final UnifiedRecordValue value) {
    return streamProcessingComposite.writeCommandOnPartition(partition, intent, value);
  }

  @Override
  public long writeCommandOnPartition(
      final int partition, final long key, final Intent intent, final UnifiedRecordValue value) {
    return streamProcessingComposite.writeCommandOnPartition(partition, key, intent, value);
  }

  @Override
  public long writeCommandOnPartition(
      final int partition,
      final long key,
      final Intent intent,
      final UnifiedRecordValue value,
      final String... authorizedTenants) {
    return streamProcessingComposite.writeCommandOnPartition(
        partition, key, intent, value, authorizedTenants);
  }

  public void snapshot() {
    final var partitionId = startPartitionId;
    streamProcessingComposite.snapshot(partitionId);
  }

  public void maxCommandsInBatch(final int maxCommandsInBatch) {
    this.maxCommandsInBatch = maxCommandsInBatch;
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
      streams = new TestStreams(tempFolder, closeables, actorSchedulerRule.get(), clock);
      streams.withStreamProcessorMode(streamProcessorMode);
      streams.maxCommandsInBatch(maxCommandsInBatch);

      int partitionId = startPartitionId;
      for (int i = 0; i < partitionCount; i++) {
        if (sharedStorage != null) {
          streams.createLogStream(getLogName(partitionId), partitionId++, sharedStorage);
        } else {
          streams.createLogStream(getLogName(partitionId), partitionId++);
        }
      }

      streamProcessingComposite =
          new StreamProcessingComposite(
              streams, startPartitionId, zeebeDbFactory, actorSchedulerRule.get());
    }

    @Override
    protected void after() {
      streams = null;
      streamProcessingComposite = null;
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
