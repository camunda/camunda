/*
 * Zeebe Broker Core
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
package io.zeebe.broker.logstreams.processor;

import static io.zeebe.broker.workflow.state.TimerInstance.NO_ELEMENT_INSTANCE;
import static io.zeebe.test.util.TestUtil.doRepeatedly;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.zeebe.broker.logstreams.state.DefaultZeebeDbFactory;
import io.zeebe.broker.logstreams.state.ZeebeState;
import io.zeebe.broker.util.MockTypedRecord;
import io.zeebe.broker.util.Records;
import io.zeebe.broker.util.StreamProcessorControl;
import io.zeebe.broker.util.TestStreams;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.protocol.clientapi.RecordType;
import io.zeebe.protocol.clientapi.ValueType;
import io.zeebe.protocol.impl.record.RecordMetadata;
import io.zeebe.protocol.impl.record.value.job.JobRecord;
import io.zeebe.protocol.impl.record.value.timer.TimerRecord;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceRecord;
import io.zeebe.protocol.intent.JobIntent;
import io.zeebe.protocol.intent.TimerIntent;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;
import io.zeebe.servicecontainer.testing.ServiceContainerRule;
import io.zeebe.test.util.AutoCloseableRule;
import io.zeebe.transport.ServerOutput;
import io.zeebe.util.buffer.BufferUtil;
import io.zeebe.util.sched.testing.ActorSchedulerRule;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TemporaryFolder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class SkipFailingEventsTest {
  public static final String STREAM_NAME = "foo";
  public static final int STREAM_PROCESSOR_ID = 144144;

  public TemporaryFolder tempFolder = new TemporaryFolder();
  public AutoCloseableRule closeables = new AutoCloseableRule();

  public ActorSchedulerRule actorSchedulerRule = new ActorSchedulerRule();
  public ServiceContainerRule serviceContainerRule = new ServiceContainerRule(actorSchedulerRule);

  @Rule
  public RuleChain ruleChain =
      RuleChain.outerRule(tempFolder)
          .around(actorSchedulerRule)
          .around(serviceContainerRule)
          .around(closeables);

  protected TestStreams streams;
  protected LogStream stream;

  @Mock protected ServerOutput output;
  private StreamProcessorControl streamProcessorControl;
  private KeyGenerator keyGenerator;
  private TypedStreamEnvironment env;
  private ZeebeState zeebeState;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);

    streams =
        new TestStreams(
            tempFolder.getRoot(), closeables, serviceContainerRule.get(), actorSchedulerRule.get());

    stream = streams.createLogStream(STREAM_NAME);
    env = new TypedStreamEnvironment(streams.getLogStream(STREAM_NAME), output);

    final AtomicLong key = new AtomicLong();
    keyGenerator = () -> key.getAndIncrement();
  }

  @Test
  public void shouldBlacklistInstance() {
    // given
    final DumpProcessor dumpProcessor = spy(new DumpProcessor());
    final ErrorProneProcessor processor = new ErrorProneProcessor();

    streamProcessorControl =
        streams.initStreamProcessor(
            STREAM_NAME,
            STREAM_PROCESSOR_ID,
            DefaultZeebeDbFactory.DEFAULT_DB_FACTORY,
            (db) -> {
              zeebeState = new ZeebeState(db);
              return env.newStreamProcessor()
                  .zeebeState(zeebeState)
                  .onEvent(
                      ValueType.WORKFLOW_INSTANCE,
                      WorkflowInstanceIntent.ELEMENT_ACTIVATING,
                      processor)
                  .onEvent(
                      ValueType.WORKFLOW_INSTANCE,
                      WorkflowInstanceIntent.ELEMENT_ACTIVATED,
                      dumpProcessor)
                  .build();
            });
    streamProcessorControl.start();

    streams
        .newRecord(STREAM_NAME)
        .event(workflowInstance(1))
        .recordType(RecordType.EVENT)
        .intent(WorkflowInstanceIntent.ELEMENT_ACTIVATING)
        .key(keyGenerator.nextKey())
        .write();
    streams
        .newRecord(STREAM_NAME)
        .event(workflowInstance(1))
        .recordType(RecordType.EVENT)
        .intent(WorkflowInstanceIntent.ELEMENT_ACTIVATED)
        .key(keyGenerator.nextKey())
        .write();

    // other instance
    streams
        .newRecord(STREAM_NAME)
        .event(workflowInstance(2))
        .recordType(RecordType.EVENT)
        .intent(WorkflowInstanceIntent.ELEMENT_ACTIVATED)
        .key(keyGenerator.nextKey())
        .write();
    // when
    streamProcessorControl.unblock();

    doRepeatedly(
            () ->
                streams
                    .events(STREAM_NAME)
                    .filter(
                        e ->
                            Records.isEvent(
                                e,
                                ValueType.WORKFLOW_INSTANCE,
                                WorkflowInstanceIntent.ELEMENT_COMPLETED))
                    .findFirst())
        .until(o -> o.isPresent())
        .get();

    // then
    assertThat(processor.getProcessCount()).isEqualTo(1);

    final RecordMetadata metadata = new RecordMetadata();
    metadata.valueType(ValueType.WORKFLOW_INSTANCE);
    final MockTypedRecord<WorkflowInstanceRecord> mockTypedRecord =
        new MockTypedRecord<>(0, metadata, workflowInstance(1));
    assertThat(zeebeState.isOnBlacklist(mockTypedRecord)).isTrue();

    verify(dumpProcessor, times(1)).processRecord(any(), any(), any(), any());
    assertThat(dumpProcessor.processedInstances).containsExactly(2L);
  }

  @Test
  public void shouldNotBlacklistInstance() {
    // given
    when(output.sendResponse(any())).thenReturn(true);
    final List<Long> processedInstances = new ArrayList<>();
    final TypedRecordProcessor<JobRecord> dumpProcessor =
        spy(
            new TypedRecordProcessor<JobRecord>() {
              @Override
              public void processRecord(
                  TypedRecord<JobRecord> record,
                  TypedResponseWriter responseWriter,
                  TypedStreamWriter streamWriter) {
                processedInstances.add(record.getValue().getWorkflowInstanceKey());
                streamWriter.appendFollowUpEvent(
                    record.getKey(), WorkflowInstanceIntent.ELEMENT_COMPLETED, workflowInstance(2));
              }
            });
    final TypedRecordProcessor<JobRecord> errorProneProcessor =
        new TypedRecordProcessor<JobRecord>() {
          @Override
          public void processRecord(
              TypedRecord<JobRecord> record,
              TypedResponseWriter responseWriter,
              TypedStreamWriter streamWriter) {
            throw new RuntimeException("expected");
          }
        };

    streamProcessorControl =
        streams.initStreamProcessor(
            STREAM_NAME,
            STREAM_PROCESSOR_ID,
            DefaultZeebeDbFactory.DEFAULT_DB_FACTORY,
            (db) -> {
              zeebeState = new ZeebeState(db);
              return env.newStreamProcessor()
                  .zeebeState(zeebeState)
                  .onCommand(ValueType.JOB, JobIntent.CREATE, errorProneProcessor)
                  .onEvent(ValueType.JOB, JobIntent.ACTIVATED, dumpProcessor)
                  .build();
            });
    streamProcessorControl.start();

    streams
        .newRecord(STREAM_NAME)
        .event(job(1))
        .recordType(RecordType.COMMAND)
        .intent(JobIntent.COMPLETE)
        .key(keyGenerator.nextKey())
        .write();
    streams
        .newRecord(STREAM_NAME)
        .event(job(1))
        .recordType(RecordType.EVENT)
        .intent(JobIntent.ACTIVATED)
        .key(keyGenerator.nextKey())
        .write();

    // other instance
    streams
        .newRecord(STREAM_NAME)
        .event(job(2))
        .recordType(RecordType.EVENT)
        .intent(JobIntent.ACTIVATED)
        .key(keyGenerator.nextKey())
        .write();
    // when
    streamProcessorControl.unblock();

    doRepeatedly(
            () ->
                streams
                    .events(STREAM_NAME)
                    .filter(
                        e ->
                            Records.isEvent(
                                e,
                                ValueType.WORKFLOW_INSTANCE,
                                WorkflowInstanceIntent.ELEMENT_COMPLETED))
                    .findFirst())
        .until(o -> o.isPresent())
        .get();

    // then
    final RecordMetadata metadata = new RecordMetadata();
    metadata.valueType(ValueType.WORKFLOW_INSTANCE);
    final MockTypedRecord<WorkflowInstanceRecord> mockTypedRecord =
        new MockTypedRecord<>(0, metadata, workflowInstance(1));
    assertThat(zeebeState.isOnBlacklist(mockTypedRecord)).isFalse();

    verify(dumpProcessor, times(2)).processRecord(any(), any(), any(), any());
    assertThat(processedInstances).containsExactly(1L, 2L);
  }

  @Test
  public void shouldNotBlacklistInstanceAndIgnoreTimerStartEvents() {
    // given
    when(output.sendResponse(any())).thenReturn(true);
    final List<Long> processedInstances = new ArrayList<>();
    final TypedRecordProcessor<TimerRecord> errorProneProcessor =
        new TypedRecordProcessor<TimerRecord>() {
          @Override
          public void processRecord(
              TypedRecord<TimerRecord> record,
              TypedResponseWriter responseWriter,
              TypedStreamWriter streamWriter) {
            if (record.getKey() == 0) {
              throw new RuntimeException("expected");
            }
            processedInstances.add(record.getValue().getWorkflowInstanceKey());
            streamWriter.appendFollowUpEvent(
                record.getKey(), TimerIntent.CREATED, timer(NO_ELEMENT_INSTANCE));
          }
        };

    streamProcessorControl =
        streams.initStreamProcessor(
            STREAM_NAME,
            STREAM_PROCESSOR_ID,
            DefaultZeebeDbFactory.DEFAULT_DB_FACTORY,
            (db) -> {
              zeebeState = new ZeebeState(db);
              return env.newStreamProcessor()
                  .zeebeState(zeebeState)
                  .onCommand(ValueType.TIMER, TimerIntent.CREATE, errorProneProcessor)
                  .build();
            });
    streamProcessorControl.start();

    streams
        .newRecord(STREAM_NAME)
        .event(timer(NO_ELEMENT_INSTANCE))
        .recordType(RecordType.COMMAND)
        .intent(TimerIntent.CREATE)
        .key(keyGenerator.nextKey())
        .write();
    streams
        .newRecord(STREAM_NAME)
        .event(timer(NO_ELEMENT_INSTANCE))
        .recordType(RecordType.COMMAND)
        .intent(TimerIntent.CREATE)
        .key(keyGenerator.nextKey())
        .write();

    // when
    streamProcessorControl.unblock();
    doRepeatedly(
            () ->
                streams
                    .events(STREAM_NAME)
                    .filter(e -> Records.isEvent(e, ValueType.TIMER, TimerIntent.CREATED))
                    .findFirst())
        .until(o -> o.isPresent())
        .get();

    // then
    final RecordMetadata metadata = new RecordMetadata();
    metadata.valueType(ValueType.TIMER);
    final MockTypedRecord<TimerRecord> mockTypedRecord =
        new MockTypedRecord<>(0, metadata, timer(NO_ELEMENT_INSTANCE));
    assertThat(zeebeState.isOnBlacklist(mockTypedRecord)).isFalse();
    assertThat(processedInstances).containsExactly((long) NO_ELEMENT_INSTANCE);
  }

  protected WorkflowInstanceRecord workflowInstance(final int instanceKey) {
    final WorkflowInstanceRecord event = new WorkflowInstanceRecord();
    event.setWorkflowInstanceKey(instanceKey);
    return event;
  }

  protected JobRecord job(final int instanceKey) {
    final JobRecord event = new JobRecord();
    event.getHeaders().setWorkflowInstanceKey(instanceKey);
    return event;
  }

  protected TimerRecord timer(final int instanceKey) {
    final TimerRecord event = new TimerRecord();
    event
        .setWorkflowInstanceKey(instanceKey)
        .setElementInstanceKey(instanceKey)
        .setDueDate(1245)
        .setHandlerNodeId(BufferUtil.wrapString("foo"))
        .setRepetitions(0)
        .setWorkflowKey(1);
    return event;
  }

  protected static class ErrorProneProcessor
      implements TypedRecordProcessor<WorkflowInstanceRecord> {

    public final AtomicLong processCount = new AtomicLong(0);

    @Override
    public void processRecord(
        final TypedRecord<WorkflowInstanceRecord> record,
        final TypedResponseWriter responseWriter,
        final TypedStreamWriter streamWriter) {
      processCount.incrementAndGet();
      throw new RuntimeException("expected");
    }

    public long getProcessCount() {
      return processCount.get();
    }
  }

  protected static class DumpProcessor implements TypedRecordProcessor<WorkflowInstanceRecord> {
    final List<Long> processedInstances = new ArrayList<>();

    @Override
    public void processRecord(
        TypedRecord<WorkflowInstanceRecord> record,
        TypedResponseWriter responseWriter,
        TypedStreamWriter streamWriter) {
      processedInstances.add(record.getValue().getWorkflowInstanceKey());
      streamWriter.appendFollowUpEvent(
          record.getKey(), WorkflowInstanceIntent.ELEMENT_COMPLETED, record.getValue());
    }
  }
}
