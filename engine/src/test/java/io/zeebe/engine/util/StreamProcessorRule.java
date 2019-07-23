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
import io.zeebe.engine.processor.TypedRecordProcessorFactory;
import io.zeebe.engine.processor.TypedRecordProcessors;
import io.zeebe.engine.state.DefaultZeebeDbFactory;
import io.zeebe.engine.state.ZeebeState;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.logstreams.state.StateSnapshotController;
import io.zeebe.msgpack.UnpackedObject;
import io.zeebe.protocol.record.RecordType;
import io.zeebe.protocol.record.intent.Intent;
import io.zeebe.protocol.record.intent.WorkflowInstanceIntent;
import io.zeebe.servicecontainer.testing.ServiceContainerRule;
import io.zeebe.test.util.AutoCloseableRule;
import io.zeebe.util.ZbLogger;
import io.zeebe.util.sched.clock.ControlledActorClock;
import io.zeebe.util.sched.testing.ActorSchedulerRule;
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
  private final ServiceContainerRule serviceContainerRule =
      new ServiceContainerRule(actorSchedulerRule);
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

  public StreamProcessorRule(int partitionId) {
    this(partitionId, 1, DefaultZeebeDbFactory.DEFAULT_DB_FACTORY);
  }

  public StreamProcessorRule(int startPartitionId, int partitionCount, ZeebeDbFactory dbFactory) {
    this.startPartitionId = startPartitionId;
    this.partitionCount = partitionCount;

    rule = new SetupRule(startPartitionId, partitionCount);

    zeebeDbFactory = dbFactory;
    chain =
        RuleChain.outerRule(tempFolder)
            .around(actorSchedulerRule)
            .around(serviceContainerRule)
            .around(closeables)
            .around(new FailedTestRecordPrinter())
            .around(rule);
  }

  @Override
  public Statement apply(Statement base, Description description) {
    return chain.apply(base, description);
  }

  public LogStream getLogStream(int partitionId) {
    return streams.getLogStream(getLogName(partitionId));
  }

  public StreamProcessor startTypedStreamProcessor(StreamProcessorTestFactory factory) {
    return startTypedStreamProcessor(
        (processingContext) -> {
          zeebeState = processingContext.getZeebeState();
          return factory.build(TypedRecordProcessors.processors(), zeebeState);
        });
  }

  public StreamProcessor startTypedStreamProcessor(TypedRecordProcessorFactory factory) {
    return startTypedStreamProcessor(startPartitionId, factory);
  }

  public StreamProcessor startTypedStreamProcessor(
      int partitionId, TypedRecordProcessorFactory factory) {
    return streams.startStreamProcessor(
        getLogName(partitionId),
        zeebeDbFactory,
        (processingContext -> {
          zeebeState = processingContext.getZeebeState();
          return factory.createProcessors(processingContext);
        }));
  }

  public void closeStreamProcessor(int partitionId) throws Exception {
    streams.closeProcessor(getLogName(partitionId));
  }

  public void closeStreamProcessor() throws Exception {
    closeStreamProcessor(startPartitionId);
  }

  public long getCommitPosition() {
    return streams.getLogStream(getLogName(startPartitionId)).getCommitPosition();
  }

  public StateSnapshotController getStateSnapshotController(int partitionId) {
    return streams.getStateSnapshotController(getLogName(partitionId));
  }

  public StateSnapshotController getStateSnapshotController() {
    return getStateSnapshotController(startPartitionId);
  }

  public CommandResponseWriter getCommandResponseWriter() {
    return streams.getMockedResponseWriter();
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
      final LogStream logStream = streams.getLogStream(getLogName(partitionId++));
      LogStreamPrinter.printRecords(logStream);
    }
  }

  public long writeWorkflowInstanceEvent(WorkflowInstanceIntent intent) {
    return writeWorkflowInstanceEvent(intent, 1);
  }

  public long writeWorkflowInstanceEventWithSource(
      WorkflowInstanceIntent intent, int instanceKey, long sourceEventPosition) {
    return streams
        .newRecord(getLogName(startPartitionId))
        .event(workflowInstance(instanceKey))
        .recordType(RecordType.EVENT)
        .sourceRecordPosition(sourceEventPosition)
        .intent(intent)
        .write();
  }

  public long writeWorkflowInstanceEvent(WorkflowInstanceIntent intent, int instanceKey) {
    return streams
        .newRecord(getLogName(startPartitionId))
        .event(workflowInstance(instanceKey))
        .recordType(RecordType.EVENT)
        .intent(intent)
        .write();
  }

  public long writeEvent(long key, Intent intent, UnpackedObject value) {
    return streams
        .newRecord(getLogName(startPartitionId))
        .recordType(RecordType.EVENT)
        .key(key)
        .intent(intent)
        .event(value)
        .write();
  }

  public long writeEvent(Intent intent, UnpackedObject value) {
    return streams
        .newRecord(getLogName(startPartitionId))
        .recordType(RecordType.EVENT)
        .intent(intent)
        .event(value)
        .write();
  }

  public long writeBatch(RecordToWrite... recordToWrites) {
    return streams.writeBatch(getLogName(startPartitionId), recordToWrites);
  }

  public long writeCommandOnPartition(int partition, Intent intent, UnpackedObject value) {
    return streams
        .newRecord(getLogName(partition))
        .recordType(RecordType.COMMAND)
        .intent(intent)
        .event(value)
        .write();
  }

  public long writeCommandOnPartition(
      int partition, long key, Intent intent, UnpackedObject value) {
    return streams
        .newRecord(getLogName(partition))
        .key(key)
        .recordType(RecordType.COMMAND)
        .intent(intent)
        .event(value)
        .write();
  }

  public long writeCommand(long key, Intent intent, UnpackedObject value) {
    return streams
        .newRecord(getLogName(startPartitionId))
        .recordType(RecordType.COMMAND)
        .key(key)
        .intent(intent)
        .event(value)
        .write();
  }

  public long writeCommand(Intent intent, UnpackedObject value) {
    return streams
        .newRecord(getLogName(startPartitionId))
        .recordType(RecordType.COMMAND)
        .intent(intent)
        .event(value)
        .write();
  }

  private static String getLogName(int partitionId) {
    return STREAM_NAME + partitionId;
  }

  @FunctionalInterface
  public interface StreamProcessorTestFactory {
    TypedRecordProcessors build(TypedRecordProcessors builder, ZeebeState zeebeState);
  }

  private class SetupRule extends ExternalResource {

    private final int startPartitionId;
    private final int partitionCount;

    SetupRule(int startPartitionId, int partitionCount) {
      this.startPartitionId = startPartitionId;
      this.partitionCount = partitionCount;
    }

    @Override
    protected void before() {
      streams =
          new TestStreams(
              tempFolder, closeables, serviceContainerRule.get(), actorSchedulerRule.get());

      int partitionId = startPartitionId;
      for (int i = 0; i < partitionCount; i++) {
        streams.createLogStream(getLogName(partitionId), partitionId++);
      }
    }
  }

  private class FailedTestRecordPrinter extends TestWatcher {

    @Override
    protected void failed(Throwable e, Description description) {
      LOG.info("Test failed, following records where exported:");
      printAllRecords();
    }
  }
}
