/*
 * Zeebe Workflow Engine
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
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
import io.zeebe.protocol.clientapi.RecordType;
import io.zeebe.protocol.intent.Intent;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;
import io.zeebe.servicecontainer.testing.ServiceContainerRule;
import io.zeebe.test.util.AutoCloseableRule;
import io.zeebe.util.ZbLogger;
import io.zeebe.util.sched.clock.ControlledActorClock;
import io.zeebe.util.sched.testing.ActorSchedulerRule;
import java.time.Duration;
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
  private static final int STREAM_PROCESSOR_ID = 1;

  // environment
  private final TemporaryFolder tempFolder = new TemporaryFolder();
  private final AutoCloseableRule closeables = new AutoCloseableRule();
  private final ControlledActorClock clock = new ControlledActorClock();
  private final ActorSchedulerRule actorSchedulerRule = new ActorSchedulerRule(clock);
  private final ServiceContainerRule serviceContainerRule =
      new ServiceContainerRule(actorSchedulerRule);
  private final ZeebeDbFactory zeebeDbFactory;

  // things provisioned by this rule
  private static final String STREAM_NAME = "stream";

  private TestStreams streams;

  private final SetupRule rule;
  private ZeebeState zeebeState;

  public StreamProcessorRule() {
    this(PARTITION_ID);
  }

  public StreamProcessorRule(int partitionId) {
    this(partitionId, DefaultZeebeDbFactory.DEFAULT_DB_FACTORY);
  }

  public StreamProcessorRule(int partitionId, ZeebeDbFactory dbFactory) {
    rule = new SetupRule(partitionId);

    zeebeDbFactory = dbFactory;
    chain =
        RuleChain.outerRule(tempFolder)
            .around(actorSchedulerRule)
            .around(serviceContainerRule)
            .around(closeables)
            .around(new FailedTestRecordPrinter())
            .around(rule);
  }

  private final RuleChain chain;

  @Override
  public Statement apply(Statement base, Description description) {
    return chain.apply(base, description);
  }

  public StreamProcessor startTypedStreamProcessor(StreamProcessorTestFactory factory) {
    return startTypedStreamProcessor(
        (processingContext) -> {
          zeebeState = processingContext.getZeebeState();
          return factory.build(TypedRecordProcessors.processors(), zeebeState);
        });
  }

  public StreamProcessor startTypedStreamProcessor(TypedRecordProcessorFactory factory) {
    return streams.startStreamProcessor(
        STREAM_NAME,
        STREAM_PROCESSOR_ID,
        zeebeDbFactory,
        (processingContext -> {
          zeebeState = processingContext.getZeebeState();
          return factory.createProcessors(processingContext);
        }));
  }

  public StreamProcessor startTypedStreamProcessor(
      TypedRecordProcessorFactory factory, int maxSnapshot, Duration snapshotPeriod) {
    return streams.startStreamProcessor(
        STREAM_NAME,
        STREAM_PROCESSOR_ID,
        zeebeDbFactory,
        (processingContext -> {
          zeebeState = processingContext.getZeebeState();
          return factory.createProcessors(processingContext);
        }),
        maxSnapshot,
        snapshotPeriod);
  }

  public void closeStreamProcessor() throws Exception {
    streams.closeProcessor(STREAM_NAME);
  }

  public long getCommitPosition() {
    return streams.getLogStream(STREAM_NAME).getCommitPosition();
  }

  public StateSnapshotController getStateSnapshotController() {
    return streams.getStateSnapshotController(STREAM_NAME);
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
    return new RecordStream(streams.events(STREAM_NAME));
  }

  public void printAllRecords() {
    final LogStream logStream = streams.getLogStream(STREAM_NAME);
    LogStreamPrinter.printRecords(logStream);
  }

  private class SetupRule extends ExternalResource {

    private final int partitionId;

    SetupRule(int partitionId) {
      this.partitionId = partitionId;
    }

    @Override
    protected void before() {
      streams =
          new TestStreams(
              tempFolder, closeables, serviceContainerRule.get(), actorSchedulerRule.get());
      streams.createLogStream(STREAM_NAME, partitionId);
    }
  }

  private class FailedTestRecordPrinter extends TestWatcher {

    @Override
    protected void failed(Throwable e, Description description) {
      LOG.info("Test failed, following records where exported:");
      printAllRecords();
    }
  }

  @FunctionalInterface
  public interface StreamProcessorTestFactory {
    TypedRecordProcessors build(TypedRecordProcessors builder, ZeebeState zeebeState);
  }

  public long writeWorkflowInstanceEvent(WorkflowInstanceIntent intent) {
    return writeWorkflowInstanceEvent(intent, 1);
  }

  public long writeWorkflowInstanceEventWithSource(
      WorkflowInstanceIntent intent, int instanceKey, long sourceEventPosition) {
    return streams
        .newRecord(STREAM_NAME)
        .event(workflowInstance(instanceKey))
        .recordType(RecordType.EVENT)
        .sourceRecordPosition(sourceEventPosition)
        .intent(intent)
        .producerId(STREAM_PROCESSOR_ID)
        .write();
  }

  public long writeWorkflowInstanceEventWithDifferentProducerId(
      WorkflowInstanceIntent intent, int instanceKey, int producer) {
    return streams
        .newRecord(STREAM_NAME)
        .event(workflowInstance(instanceKey))
        .recordType(RecordType.EVENT)
        .intent(intent)
        .producerId(producer)
        .write();
  }

  public long writeWorkflowInstanceEventWithDifferentProducerIdAndSource(
      WorkflowInstanceIntent intent, int instanceKey, int producer, long sourceEventPosition) {
    return streams
        .newRecord(STREAM_NAME)
        .event(workflowInstance(instanceKey))
        .recordType(RecordType.EVENT)
        .sourceRecordPosition(sourceEventPosition)
        .intent(intent)
        .producerId(producer)
        .write();
  }

  public long writeWorkflowInstanceEvent(WorkflowInstanceIntent intent, int instanceKey) {
    return streams
        .newRecord(STREAM_NAME)
        .event(workflowInstance(instanceKey))
        .recordType(RecordType.EVENT)
        .intent(intent)
        .producerId(STREAM_PROCESSOR_ID)
        .write();
  }

  public long writeEvent(long key, Intent intent, UnpackedObject value) {
    return streams
        .newRecord(STREAM_NAME)
        .recordType(RecordType.EVENT)
        .key(key)
        .intent(intent)
        .event(value)
        .producerId(STREAM_PROCESSOR_ID)
        .write();
  }

  public long writeEvent(Intent intent, UnpackedObject value) {
    return streams
        .newRecord(STREAM_NAME)
        .recordType(RecordType.EVENT)
        .intent(intent)
        .event(value)
        .producerId(STREAM_PROCESSOR_ID)
        .write();
  }

  public long writeCommand(long key, Intent intent, UnpackedObject value) {
    return streams
        .newRecord(STREAM_NAME)
        .recordType(RecordType.COMMAND)
        .key(key)
        .intent(intent)
        .event(value)
        .write();
  }

  public long writeCommand(Intent intent, UnpackedObject value) {
    return streams
        .newRecord(STREAM_NAME)
        .recordType(RecordType.COMMAND)
        .intent(intent)
        .event(value)
        .write();
  }
}
