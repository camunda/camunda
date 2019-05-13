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
package io.zeebe.engine.processor;

import static io.zeebe.test.util.TestUtil.waitUntil;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.zeebe.engine.state.DefaultZeebeDbFactory;
import io.zeebe.engine.state.ZeebeState;
import io.zeebe.engine.state.instance.TimerInstance;
import io.zeebe.engine.util.MockTypedRecord;
import io.zeebe.engine.util.RecordStream;
import io.zeebe.engine.util.Records;
import io.zeebe.engine.util.StreamProcessorControl;
import io.zeebe.engine.util.TestStreams;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.msgpack.UnpackedObject;
import io.zeebe.protocol.clientapi.RecordType;
import io.zeebe.protocol.clientapi.ValueType;
import io.zeebe.protocol.impl.record.RecordMetadata;
import io.zeebe.protocol.impl.record.value.error.ErrorRecord;
import io.zeebe.protocol.impl.record.value.job.JobRecord;
import io.zeebe.protocol.impl.record.value.timer.TimerRecord;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceRecord;
import io.zeebe.protocol.intent.ErrorIntent;
import io.zeebe.protocol.intent.JobIntent;
import io.zeebe.protocol.intent.TimerIntent;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;
import io.zeebe.servicecontainer.testing.ServiceContainerRule;
import io.zeebe.test.util.AutoCloseableRule;
import io.zeebe.test.util.TestUtil;
import io.zeebe.util.buffer.BufferUtil;
import io.zeebe.util.sched.testing.ActorSchedulerRule;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.assertj.core.api.Assertions;
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

  @Mock protected CommandResponseWriter commandResponseWriter;
  private StreamProcessorControl streamProcessorControl;
  private KeyGenerator keyGenerator;
  private TypedStreamEnvironment env;
  private ZeebeState zeebeState;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);

    when(commandResponseWriter.intent(any())).thenReturn(commandResponseWriter);
    when(commandResponseWriter.key(anyLong())).thenReturn(commandResponseWriter);
    when(commandResponseWriter.partitionId(anyInt())).thenReturn(commandResponseWriter);
    when(commandResponseWriter.recordType(any())).thenReturn(commandResponseWriter);
    when(commandResponseWriter.rejectionType(any())).thenReturn(commandResponseWriter);
    when(commandResponseWriter.rejectionReason(any())).thenReturn(commandResponseWriter);
    when(commandResponseWriter.valueType(any())).thenReturn(commandResponseWriter);
    when(commandResponseWriter.valueWriter(any())).thenReturn(commandResponseWriter);

    streams =
        new TestStreams(
            tempFolder, closeables, serviceContainerRule.get(), actorSchedulerRule.get());

    stream = streams.createLogStream(STREAM_NAME);
    env = new TypedStreamEnvironment(streams.getLogStream(STREAM_NAME), commandResponseWriter);

    final AtomicLong key = new AtomicLong();
    keyGenerator = () -> key.getAndIncrement();
  }

  @Test
  public void shouldWriteErrorEvent() {
    // given
    final DumpProcessor dumpProcessor = spy(new DumpProcessor());
    final ErrorProneProcessor processor = new ErrorProneProcessor();

    streamProcessorControl =
        streams.initStreamProcessor(
            STREAM_NAME,
            STREAM_PROCESSOR_ID,
            DefaultZeebeDbFactory.DEFAULT_DB_FACTORY,
            (actor, db, dbContext) -> {
              zeebeState = new ZeebeState(db, dbContext);
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

    final long failingEventPosition =
        streams
            .newRecord(STREAM_NAME)
            .event(workflowInstance(1))
            .recordType(RecordType.EVENT)
            .intent(WorkflowInstanceIntent.ELEMENT_ACTIVATING)
            .key(keyGenerator.nextKey())
            .write();

    // when
    streamProcessorControl.unblock();

    TestUtil.doRepeatedly(
            () ->
                streams
                    .events(STREAM_NAME)
                    .filter(e -> Records.isEvent(e, ValueType.ERROR, ErrorIntent.CREATED))
                    .findFirst())
        .until(o -> o.isPresent())
        .get();

    // then
    assertThat(processor.getProcessCount()).isEqualTo(1);

    final ErrorRecord errorRecord =
        new RecordStream(streams.events(STREAM_NAME)).onlyErrorRecords().getFirst().getValue();

    assertThat(errorRecord.getErrorEventPosition()).isEqualTo(failingEventPosition);
    assertThat(BufferUtil.bufferAsString(errorRecord.getExceptionMessage())).isEqualTo("expected");
    assertThat(errorRecord.getWorkflowInstanceKey()).isEqualTo(1);
  }

  @Test
  public void shouldWriteErrorEventWithNoMessage() {
    // given
    streamProcessorControl =
        streams.initStreamProcessor(
            STREAM_NAME,
            STREAM_PROCESSOR_ID,
            DefaultZeebeDbFactory.DEFAULT_DB_FACTORY,
            (actor, db, dbContext) -> {
              zeebeState = new ZeebeState(db, dbContext);
              return env.newStreamProcessor()
                  .zeebeState(zeebeState)
                  .onEvent(
                      ValueType.WORKFLOW_INSTANCE,
                      WorkflowInstanceIntent.ELEMENT_ACTIVATING,
                      new TypedRecordProcessor<UnpackedObject>() {
                        @Override
                        public void processRecord(
                            TypedRecord<UnpackedObject> record,
                            TypedResponseWriter responseWriter,
                            TypedStreamWriter streamWriter) {
                          throw new NullPointerException();
                        }
                      })
                  .build();
            });
    streamProcessorControl.start();

    final long failingEventPosition =
        streams
            .newRecord(STREAM_NAME)
            .event(workflowInstance(1))
            .recordType(RecordType.EVENT)
            .intent(WorkflowInstanceIntent.ELEMENT_ACTIVATING)
            .key(keyGenerator.nextKey())
            .write();

    // when
    TestUtil.doRepeatedly(
            () ->
                streams
                    .events(STREAM_NAME)
                    .filter(e -> Records.isEvent(e, ValueType.ERROR, ErrorIntent.CREATED))
                    .findFirst())
        .until(o -> o.isPresent())
        .get();

    // then
    final ErrorRecord errorRecord =
        new RecordStream(streams.events(STREAM_NAME)).onlyErrorRecords().getFirst().getValue();

    assertThat(errorRecord.getErrorEventPosition()).isEqualTo(failingEventPosition);
    assertThat(BufferUtil.bufferAsString(errorRecord.getExceptionMessage()))
        .isEqualTo("Without exception message.");
    assertThat(errorRecord.getWorkflowInstanceKey()).isEqualTo(1);
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
            (actor, db, dbContext) -> {
              zeebeState = new ZeebeState(db, dbContext);
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

    TestUtil.doRepeatedly(
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
    Assertions.assertThat(zeebeState.isOnBlacklist(mockTypedRecord)).isTrue();

    verify(dumpProcessor, times(1)).processRecord(any(), any(), any(), any());
    assertThat(dumpProcessor.processedInstances).containsExactly(2L);
  }

  @Test
  public void shouldFindFailedEventsOnReprocessing() throws Exception {
    // given
    when(commandResponseWriter.tryWriteResponse(anyInt(), anyLong())).thenReturn(true);

    final long failedPos =
        streams
            .newRecord(STREAM_NAME)
            .event(job(1))
            .recordType(RecordType.EVENT)
            .intent(JobIntent.ACTIVATED)
            .producerId(STREAM_PROCESSOR_ID)
            .key(keyGenerator.nextKey())
            .write();
    streams
        .newRecord(STREAM_NAME)
        .event(error(1, failedPos))
        .recordType(RecordType.EVENT)
        .producerId(STREAM_PROCESSOR_ID)
        .sourceRecordPosition(failedPos)
        .intent(ErrorIntent.CREATED)
        .key(keyGenerator.nextKey())
        .write();

    final CountDownLatch latch = new CountDownLatch(1);
    streamProcessorControl =
        streams.initStreamProcessor(
            STREAM_NAME,
            STREAM_PROCESSOR_ID,
            DefaultZeebeDbFactory.DEFAULT_DB_FACTORY,
            (actor, db, dbContext) -> {
              zeebeState = new ZeebeState(db, dbContext);
              return env.newStreamProcessor()
                  .zeebeState(zeebeState)
                  .withListener(
                      new StreamProcessorLifecycleAware() {
                        @Override
                        public void onRecovered(TypedStreamProcessor streamProcessor) {
                          latch.countDown();
                        }
                      })
                  .onEvent(ValueType.JOB, JobIntent.ACTIVATED, new DumpProcessor())
                  .build();
            });

    // when
    streamProcessorControl.start();
    latch.await(2000, TimeUnit.MILLISECONDS);

    // then
    final RecordMetadata metadata = new RecordMetadata();
    metadata.valueType(ValueType.WORKFLOW_INSTANCE);
    final MockTypedRecord<WorkflowInstanceRecord> mockTypedRecord =
        new MockTypedRecord<>(0, metadata, workflowInstance(1));
    waitUntil(() -> zeebeState.isOnBlacklist(mockTypedRecord));
  }

  @Test
  public void shouldNotBlacklistInstance() {
    // given
    when(commandResponseWriter.tryWriteResponse(anyInt(), anyLong())).thenReturn(true);
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
            (actor, db, dbContext) -> {
              zeebeState = new ZeebeState(db, dbContext);
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

    TestUtil.doRepeatedly(
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
    Assertions.assertThat(zeebeState.isOnBlacklist(mockTypedRecord)).isFalse();

    verify(dumpProcessor, times(2)).processRecord(any(), any(), any(), any());
    assertThat(processedInstances).containsExactly(1L, 2L);
  }

  @Test
  public void shouldNotBlacklistInstanceAndIgnoreTimerStartEvents() {
    // given
    when(commandResponseWriter.tryWriteResponse(anyInt(), anyLong())).thenReturn(true);
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
                record.getKey(), TimerIntent.CREATED, timer(TimerInstance.NO_ELEMENT_INSTANCE));
          }
        };

    streamProcessorControl =
        streams.initStreamProcessor(
            STREAM_NAME,
            STREAM_PROCESSOR_ID,
            DefaultZeebeDbFactory.DEFAULT_DB_FACTORY,
            (actor, db, dbContext) -> {
              zeebeState = new ZeebeState(db, dbContext);
              return env.newStreamProcessor()
                  .zeebeState(zeebeState)
                  .onCommand(ValueType.TIMER, TimerIntent.CREATE, errorProneProcessor)
                  .build();
            });
    streamProcessorControl.start();

    streams
        .newRecord(STREAM_NAME)
        .event(timer(TimerInstance.NO_ELEMENT_INSTANCE))
        .recordType(RecordType.COMMAND)
        .intent(TimerIntent.CREATE)
        .key(keyGenerator.nextKey())
        .write();
    streams
        .newRecord(STREAM_NAME)
        .event(timer(TimerInstance.NO_ELEMENT_INSTANCE))
        .recordType(RecordType.COMMAND)
        .intent(TimerIntent.CREATE)
        .key(keyGenerator.nextKey())
        .write();

    // when
    streamProcessorControl.unblock();
    TestUtil.doRepeatedly(
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
        new MockTypedRecord<>(0, metadata, timer(TimerInstance.NO_ELEMENT_INSTANCE));
    Assertions.assertThat(zeebeState.isOnBlacklist(mockTypedRecord)).isFalse();
    assertThat(processedInstances).containsExactly((long) TimerInstance.NO_ELEMENT_INSTANCE);
  }

  protected WorkflowInstanceRecord workflowInstance(final int instanceKey) {
    final WorkflowInstanceRecord event = new WorkflowInstanceRecord();
    event.setWorkflowInstanceKey(instanceKey);
    return event;
  }

  protected ErrorRecord error(final int instanceKey, final long pos) {
    final ErrorRecord event = new ErrorRecord();
    event.initErrorRecord(new Exception("expected"), pos);
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
