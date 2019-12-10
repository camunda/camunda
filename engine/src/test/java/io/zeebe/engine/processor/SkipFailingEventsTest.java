/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processor;

import static io.zeebe.test.util.TestUtil.waitUntil;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.zeebe.engine.state.DefaultZeebeDbFactory;
import io.zeebe.engine.state.ZeebeState;
import io.zeebe.engine.state.instance.TimerInstance;
import io.zeebe.engine.util.MockTypedRecord;
import io.zeebe.engine.util.RecordStream;
import io.zeebe.engine.util.Records;
import io.zeebe.engine.util.TestStreams;
import io.zeebe.logstreams.log.LoggedEvent;
import io.zeebe.logstreams.util.SynchronousLogStream;
import io.zeebe.protocol.impl.record.RecordMetadata;
import io.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.zeebe.protocol.impl.record.value.error.ErrorRecord;
import io.zeebe.protocol.impl.record.value.job.JobRecord;
import io.zeebe.protocol.impl.record.value.timer.TimerRecord;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceRecord;
import io.zeebe.protocol.record.RecordType;
import io.zeebe.protocol.record.ValueType;
import io.zeebe.protocol.record.intent.ErrorIntent;
import io.zeebe.protocol.record.intent.JobIntent;
import io.zeebe.protocol.record.intent.TimerIntent;
import io.zeebe.protocol.record.intent.WorkflowInstanceIntent;
import io.zeebe.test.util.AutoCloseableRule;
import io.zeebe.test.util.TestUtil;
import io.zeebe.util.buffer.BufferUtil;
import io.zeebe.util.sched.testing.ActorSchedulerRule;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;
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

  public TemporaryFolder tempFolder = new TemporaryFolder();
  public AutoCloseableRule closeables = new AutoCloseableRule();

  public ActorSchedulerRule actorSchedulerRule = new ActorSchedulerRule();

  @Rule
  public RuleChain ruleChain =
      RuleChain.outerRule(tempFolder).around(actorSchedulerRule).around(closeables);

  protected TestStreams streams;
  protected SynchronousLogStream stream;

  @Mock protected CommandResponseWriter commandResponseWriter;
  private KeyGenerator keyGenerator;
  private ZeebeState zeebeState;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);

    streams = new TestStreams(tempFolder, closeables, actorSchedulerRule.get());
    commandResponseWriter = streams.getMockedResponseWriter();
    stream = streams.createLogStream(STREAM_NAME);

    final AtomicLong key = new AtomicLong();
    keyGenerator = () -> key.getAndIncrement();
  }

  @Test
  public void shouldWriteErrorEvent() {
    // given
    final ErrorProneProcessor errorProneProcessor = new ErrorProneProcessor();

    streams.startStreamProcessor(
        STREAM_NAME,
        DefaultZeebeDbFactory.DEFAULT_DB_FACTORY,
        (processingContext) -> {
          zeebeState = processingContext.getZeebeState();
          return TypedRecordProcessors.processors()
              .onEvent(
                  ValueType.WORKFLOW_INSTANCE,
                  WorkflowInstanceIntent.ELEMENT_ACTIVATING,
                  errorProneProcessor);
        });

    final long failingEventPosition =
        streams
            .newRecord(STREAM_NAME)
            .event(Records.workflowInstance(1))
            .recordType(RecordType.EVENT)
            .intent(WorkflowInstanceIntent.ELEMENT_ACTIVATING)
            .key(keyGenerator.nextKey())
            .write();

    // when
    waitForRecordWhichSatisfies(e -> Records.isEvent(e, ValueType.ERROR, ErrorIntent.CREATED));

    // then
    assertThat(errorProneProcessor.getProcessCount()).isEqualTo(1);

    final ErrorRecord errorRecord =
        new RecordStream(streams.events(STREAM_NAME)).onlyErrorRecords().getFirst().getValue();

    assertThat(errorRecord.getErrorEventPosition()).isEqualTo(failingEventPosition);
    assertThat(BufferUtil.bufferAsString(errorRecord.getExceptionMessageBuffer()))
        .isEqualTo("expected");
    assertThat(errorRecord.getWorkflowInstanceKey()).isEqualTo(1);
  }

  @Test
  public void shouldWriteErrorEventWithNoMessage() {
    // given
    streams.startStreamProcessor(
        STREAM_NAME,
        DefaultZeebeDbFactory.DEFAULT_DB_FACTORY,
        (processingContext) -> {
          zeebeState = processingContext.getZeebeState();
          return TypedRecordProcessors.processors()
              .onEvent(
                  ValueType.WORKFLOW_INSTANCE,
                  WorkflowInstanceIntent.ELEMENT_ACTIVATING,
                  new TypedRecordProcessor<UnifiedRecordValue>() {
                    @Override
                    public void processRecord(
                        TypedRecord<UnifiedRecordValue> record,
                        TypedResponseWriter responseWriter,
                        TypedStreamWriter streamWriter) {
                      throw new NullPointerException();
                    }
                  });
        });

    final long failingEventPosition =
        streams
            .newRecord(STREAM_NAME)
            .event(Records.workflowInstance(1))
            .recordType(RecordType.EVENT)
            .intent(WorkflowInstanceIntent.ELEMENT_ACTIVATING)
            .key(keyGenerator.nextKey())
            .write();

    // when
    waitForRecordWhichSatisfies(e -> Records.isEvent(e, ValueType.ERROR, ErrorIntent.CREATED));

    // then
    final ErrorRecord errorRecord =
        new RecordStream(streams.events(STREAM_NAME)).onlyErrorRecords().getFirst().getValue();

    assertThat(errorRecord.getErrorEventPosition()).isEqualTo(failingEventPosition);
    assertThat(BufferUtil.bufferAsString(errorRecord.getExceptionMessageBuffer()))
        .isEqualTo("Without exception message.");
    assertThat(errorRecord.getWorkflowInstanceKey()).isEqualTo(1);
  }

  @Test
  public void shouldBlacklistInstance() {
    // given
    final DumpProcessor dumpProcessor = spy(new DumpProcessor());
    final ErrorProneProcessor processor = new ErrorProneProcessor();

    streams.startStreamProcessor(
        STREAM_NAME,
        DefaultZeebeDbFactory.DEFAULT_DB_FACTORY,
        (processingContext) -> {
          zeebeState = processingContext.getZeebeState();
          return TypedRecordProcessors.processors()
              .onEvent(
                  ValueType.WORKFLOW_INSTANCE, WorkflowInstanceIntent.ELEMENT_ACTIVATING, processor)
              .onEvent(
                  ValueType.WORKFLOW_INSTANCE,
                  WorkflowInstanceIntent.ELEMENT_ACTIVATED,
                  dumpProcessor);
        });

    streams
        .newRecord(STREAM_NAME)
        .event(Records.workflowInstance(1))
        .recordType(RecordType.EVENT)
        .intent(WorkflowInstanceIntent.ELEMENT_ACTIVATING)
        .key(keyGenerator.nextKey())
        .write();
    streams
        .newRecord(STREAM_NAME)
        .event(Records.workflowInstance(1))
        .recordType(RecordType.EVENT)
        .intent(WorkflowInstanceIntent.ELEMENT_ACTIVATED)
        .key(keyGenerator.nextKey())
        .write();

    // other instance
    streams
        .newRecord(STREAM_NAME)
        .event(Records.workflowInstance(2))
        .recordType(RecordType.EVENT)
        .intent(WorkflowInstanceIntent.ELEMENT_ACTIVATED)
        .key(keyGenerator.nextKey())
        .write();

    // when
    waitForRecordWhichSatisfies(
        e ->
            Records.isEvent(
                e, ValueType.WORKFLOW_INSTANCE, WorkflowInstanceIntent.ELEMENT_COMPLETED));

    // then
    assertThat(processor.getProcessCount()).isEqualTo(1);

    final RecordMetadata metadata = new RecordMetadata();
    metadata.valueType(ValueType.WORKFLOW_INSTANCE);
    final MockTypedRecord<WorkflowInstanceRecord> mockTypedRecord =
        new MockTypedRecord<>(0, metadata, Records.workflowInstance(1));
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
            .event(Records.job(1))
            .recordType(RecordType.EVENT)
            .intent(JobIntent.ACTIVATED)
            .key(keyGenerator.nextKey())
            .write();
    streams
        .newRecord(STREAM_NAME)
        .event(Records.error(1, failedPos))
        .recordType(RecordType.EVENT)
        .sourceRecordPosition(failedPos)
        .intent(ErrorIntent.CREATED)
        .key(keyGenerator.nextKey())
        .write();

    final CountDownLatch latch = new CountDownLatch(1);
    streams.startStreamProcessor(
        STREAM_NAME,
        DefaultZeebeDbFactory.DEFAULT_DB_FACTORY,
        (processingContext) -> {
          zeebeState = processingContext.getZeebeState();
          return TypedRecordProcessors.processors()
              .withListener(
                  new StreamProcessorLifecycleAware() {
                    @Override
                    public void onRecovered(ReadonlyProcessingContext ctx) {
                      latch.countDown();
                    }
                  })
              .onEvent(ValueType.JOB, JobIntent.ACTIVATED, new DumpProcessor());
        });

    // when
    latch.await(2000, TimeUnit.MILLISECONDS);

    // then
    final RecordMetadata metadata = new RecordMetadata();
    metadata.valueType(ValueType.WORKFLOW_INSTANCE);
    final MockTypedRecord<WorkflowInstanceRecord> mockTypedRecord =
        new MockTypedRecord<>(0, metadata, Records.workflowInstance(1));
    waitUntil(() -> zeebeState.isOnBlacklist(mockTypedRecord));
  }

  @Test
  public void shouldNotBlacklistInstanceOnCommand() {
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
                    record.getKey(),
                    WorkflowInstanceIntent.ELEMENT_COMPLETED,
                    Records.workflowInstance(2));
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

    streams.startStreamProcessor(
        STREAM_NAME,
        DefaultZeebeDbFactory.DEFAULT_DB_FACTORY,
        (processingContext) -> {
          zeebeState = processingContext.getZeebeState();
          return TypedRecordProcessors.processors()
              .onCommand(ValueType.JOB, JobIntent.COMPLETE, errorProneProcessor)
              .onEvent(ValueType.JOB, JobIntent.ACTIVATED, dumpProcessor);
        });

    streams
        .newRecord(STREAM_NAME)
        .event(Records.job(1))
        .recordType(RecordType.COMMAND)
        .intent(JobIntent.COMPLETE)
        .key(keyGenerator.nextKey())
        .write();
    streams
        .newRecord(STREAM_NAME)
        .event(Records.job(1))
        .recordType(RecordType.EVENT)
        .intent(JobIntent.ACTIVATED)
        .key(keyGenerator.nextKey())
        .write();

    // other instance
    streams
        .newRecord(STREAM_NAME)
        .event(Records.job(2))
        .recordType(RecordType.EVENT)
        .intent(JobIntent.ACTIVATED)
        .key(keyGenerator.nextKey())
        .write();

    // when
    waitForRecordWhichSatisfies(
        e ->
            Records.isEvent(
                e, ValueType.WORKFLOW_INSTANCE, WorkflowInstanceIntent.ELEMENT_COMPLETED));

    // then
    final RecordMetadata metadata = new RecordMetadata();
    metadata.valueType(ValueType.WORKFLOW_INSTANCE);
    final MockTypedRecord<WorkflowInstanceRecord> mockTypedRecord =
        new MockTypedRecord<>(0, metadata, Records.workflowInstance(1));
    Assertions.assertThat(zeebeState.isOnBlacklist(mockTypedRecord)).isFalse();

    verify(dumpProcessor, timeout(1000).times(2)).processRecord(any(), any(), any(), any());
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
                record.getKey(),
                TimerIntent.CREATED,
                Records.timer(TimerInstance.NO_ELEMENT_INSTANCE));
          }
        };

    streams.startStreamProcessor(
        STREAM_NAME,
        DefaultZeebeDbFactory.DEFAULT_DB_FACTORY,
        (processingContext) -> {
          zeebeState = processingContext.getZeebeState();
          return TypedRecordProcessors.processors()
              .onCommand(ValueType.TIMER, TimerIntent.CREATE, errorProneProcessor);
        });

    streams
        .newRecord(STREAM_NAME)
        .event(Records.timer(TimerInstance.NO_ELEMENT_INSTANCE))
        .recordType(RecordType.COMMAND)
        .intent(TimerIntent.CREATE)
        .key(keyGenerator.nextKey())
        .write();
    streams
        .newRecord(STREAM_NAME)
        .event(Records.timer(TimerInstance.NO_ELEMENT_INSTANCE))
        .recordType(RecordType.COMMAND)
        .intent(TimerIntent.CREATE)
        .key(keyGenerator.nextKey())
        .write();

    // when
    waitForRecordWhichSatisfies(e -> Records.isEvent(e, ValueType.TIMER, TimerIntent.CREATED));

    // then
    final RecordMetadata metadata = new RecordMetadata();
    metadata.valueType(ValueType.TIMER);
    final MockTypedRecord<TimerRecord> mockTypedRecord =
        new MockTypedRecord<>(0, metadata, Records.timer(TimerInstance.NO_ELEMENT_INSTANCE));
    Assertions.assertThat(zeebeState.isOnBlacklist(mockTypedRecord)).isFalse();
    assertThat(processedInstances).containsExactly((long) TimerInstance.NO_ELEMENT_INSTANCE);
  }

  private void waitForRecordWhichSatisfies(Predicate<LoggedEvent> filter) {
    TestUtil.doRepeatedly(() -> streams.events(STREAM_NAME).filter(filter).findFirst())
        .until(o -> o.isPresent())
        .get();
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
