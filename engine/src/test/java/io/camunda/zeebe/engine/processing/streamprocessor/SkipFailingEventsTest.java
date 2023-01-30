/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.streamprocessor;

import static io.camunda.zeebe.test.util.TestUtil.waitUntil;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.engine.api.CommandResponseWriter;
import io.camunda.zeebe.engine.api.ReadonlyStreamProcessorContext;
import io.camunda.zeebe.engine.api.StreamProcessorLifecycleAware;
import io.camunda.zeebe.engine.api.TypedRecord;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.DefaultZeebeDbFactory;
import io.camunda.zeebe.engine.state.KeyGenerator;
import io.camunda.zeebe.engine.state.instance.TimerInstance;
import io.camunda.zeebe.engine.state.mutable.MutableZeebeState;
import io.camunda.zeebe.engine.util.MockTypedRecord;
import io.camunda.zeebe.engine.util.RecordStream;
import io.camunda.zeebe.engine.util.Records;
import io.camunda.zeebe.engine.util.TestStreams;
import io.camunda.zeebe.logstreams.log.LoggedEvent;
import io.camunda.zeebe.logstreams.util.SynchronousLogStream;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import io.camunda.zeebe.protocol.impl.record.value.error.ErrorRecord;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.impl.record.value.timer.TimerRecord;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.DeploymentIntent;
import io.camunda.zeebe.protocol.record.intent.ErrorIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.TimerIntent;
import io.camunda.zeebe.scheduler.testing.ActorSchedulerRule;
import io.camunda.zeebe.test.util.AutoCloseableRule;
import io.camunda.zeebe.test.util.TestUtil;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TemporaryFolder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public final class SkipFailingEventsTest {

  private static final String STREAM_NAME = "foo";
  private static final ProcessInstanceRecord PROCESS_INSTANCE_RECORD = Records.processInstance(1);
  private static final JobRecord JOB_RECORD = Records.job(1);

  public final TemporaryFolder tempFolder = new TemporaryFolder();
  public final AutoCloseableRule closeables = new AutoCloseableRule();

  public final ActorSchedulerRule actorSchedulerRule = new ActorSchedulerRule();

  @Rule
  public RuleChain ruleChain =
      RuleChain.outerRule(tempFolder).around(actorSchedulerRule).around(closeables);

  protected TestStreams streams;
  protected SynchronousLogStream stream;

  @Mock protected CommandResponseWriter commandResponseWriter;
  private KeyGenerator keyGenerator;
  private MutableZeebeState zeebeState;

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
        DefaultZeebeDbFactory.defaultFactory(),
        (processingContext) -> {
          zeebeState = processingContext.getZeebeState();
          return TypedRecordProcessors.processors(
                  zeebeState.getKeyGenerator(), processingContext.getWriters())
              .onCommand(
                  ValueType.PROCESS_INSTANCE,
                  ProcessInstanceIntent.ACTIVATE_ELEMENT,
                  errorProneProcessor);
        });

    final long failingEventPosition =
        streams
            .newRecord(STREAM_NAME)
            .event(PROCESS_INSTANCE_RECORD)
            .recordType(RecordType.COMMAND)
            .intent(ProcessInstanceIntent.ACTIVATE_ELEMENT)
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
    assertThat(errorRecord.getProcessInstanceKey()).isEqualTo(1);
  }

  @Test
  public void shouldWriteErrorEventWithNoMessage() {
    // given
    streams.startStreamProcessor(
        STREAM_NAME,
        DefaultZeebeDbFactory.defaultFactory(),
        (processingContext) -> {
          zeebeState = processingContext.getZeebeState();
          return TypedRecordProcessors.processors(
                  zeebeState.getKeyGenerator(), processingContext.getWriters())
              .onCommand(
                  ValueType.PROCESS_INSTANCE,
                  ProcessInstanceIntent.ACTIVATE_ELEMENT,
                  new TypedRecordProcessor<>() {
                    @Override
                    public void processRecord(final TypedRecord<UnifiedRecordValue> record) {
                      throw new NullPointerException();
                    }
                  });
        });

    final long failingEventPosition =
        streams
            .newRecord(STREAM_NAME)
            .event(PROCESS_INSTANCE_RECORD)
            .recordType(RecordType.COMMAND)
            .intent(ProcessInstanceIntent.ACTIVATE_ELEMENT)
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
    assertThat(errorRecord.getProcessInstanceKey()).isEqualTo(1);
  }

  @Test
  public void shouldBlacklistInstance() {
    // given
    final AtomicReference<DumpProcessor> dumpProcessorRef = new AtomicReference<>();
    final ErrorProneProcessor processor = new ErrorProneProcessor();

    streams.startStreamProcessor(
        STREAM_NAME,
        DefaultZeebeDbFactory.defaultFactory(),
        (processingContext) -> {
          dumpProcessorRef.set(spy(new DumpProcessor(processingContext.getWriters())));
          zeebeState = processingContext.getZeebeState();
          return TypedRecordProcessors.processors(
                  zeebeState.getKeyGenerator(), processingContext.getWriters())
              .onCommand(
                  ValueType.PROCESS_INSTANCE, ProcessInstanceIntent.ACTIVATE_ELEMENT, processor)
              .onCommand(
                  ValueType.PROCESS_INSTANCE,
                  ProcessInstanceIntent.COMPLETE_ELEMENT,
                  dumpProcessorRef.get());
        });

    streams
        .newRecord(STREAM_NAME)
        .event(PROCESS_INSTANCE_RECORD)
        .recordType(RecordType.COMMAND)
        .intent(ProcessInstanceIntent.ACTIVATE_ELEMENT)
        .key(keyGenerator.nextKey())
        .write();
    streams
        .newRecord(STREAM_NAME)
        .event(PROCESS_INSTANCE_RECORD)
        .recordType(RecordType.COMMAND)
        .intent(ProcessInstanceIntent.COMPLETE_ELEMENT)
        .key(keyGenerator.nextKey())
        .write();

    // other instance
    streams
        .newRecord(STREAM_NAME)
        .event(Records.processInstance(2))
        .recordType(RecordType.COMMAND)
        .intent(ProcessInstanceIntent.COMPLETE_ELEMENT)
        .key(keyGenerator.nextKey())
        .write();

    // when
    waitForRecordWhichSatisfies(
        e ->
            Records.isEvent(
                e, ValueType.PROCESS_INSTANCE, ProcessInstanceIntent.ELEMENT_COMPLETED));

    // then
    assertThat(processor.getProcessCount()).isEqualTo(1);

    final RecordMetadata metadata = new RecordMetadata();
    metadata.valueType(ValueType.PROCESS_INSTANCE);
    final MockTypedRecord<ProcessInstanceRecord> mockTypedRecord =
        new MockTypedRecord<>(0, metadata, PROCESS_INSTANCE_RECORD);
    Assertions.assertThat(zeebeState.getBlackListState().isOnBlacklist(mockTypedRecord)).isTrue();

    verify(dumpProcessorRef.get(), times(1)).processRecord(any());
    assertThat(dumpProcessorRef.get().processedInstances).containsExactly(2L);
  }

  @Test
  public void shouldBlacklistInstanceOnReplay() throws Exception {
    // given
    when(commandResponseWriter.tryWriteResponse(anyInt(), anyLong())).thenReturn(true);

    final long failedPos =
        streams
            .newRecord(STREAM_NAME)
            .event(PROCESS_INSTANCE_RECORD)
            .recordType(RecordType.COMMAND)
            .intent(ProcessInstanceIntent.ACTIVATE_ELEMENT)
            .key(keyGenerator.nextKey())
            .write();
    streams
        .newRecord(STREAM_NAME)
        .event(Records.error((int) PROCESS_INSTANCE_RECORD.getProcessInstanceKey(), failedPos))
        .recordType(RecordType.EVENT)
        .sourceRecordPosition(failedPos)
        .intent(ErrorIntent.CREATED)
        .key(keyGenerator.nextKey())
        .write();

    final CountDownLatch latch = new CountDownLatch(1);
    streams.startStreamProcessor(
        STREAM_NAME,
        DefaultZeebeDbFactory.defaultFactory(),
        (processingContext) -> {
          zeebeState = processingContext.getZeebeState();
          return TypedRecordProcessors.processors(
                  zeebeState.getKeyGenerator(), processingContext.getWriters())
              .withListener(
                  new StreamProcessorLifecycleAware() {
                    @Override
                    public void onRecovered(final ReadonlyStreamProcessorContext ctx) {
                      latch.countDown();
                    }
                  })
              .onCommand(
                  ValueType.PROCESS_INSTANCE,
                  ProcessInstanceIntent.ACTIVATE_ELEMENT,
                  new DumpProcessor(processingContext.getWriters()));
        });

    // when
    latch.await(2000, TimeUnit.MILLISECONDS);

    // then
    final RecordMetadata metadata = new RecordMetadata();
    metadata.valueType(ValueType.PROCESS_INSTANCE);
    final MockTypedRecord<ProcessInstanceRecord> mockTypedRecord =
        new MockTypedRecord<>(0, metadata, PROCESS_INSTANCE_RECORD);
    waitUntil(() -> zeebeState.getBlackListState().isOnBlacklist(mockTypedRecord));
  }

  @Test
  public void shouldNotBlacklistInstanceOnJobCommand() {
    // given
    when(commandResponseWriter.tryWriteResponse(anyInt(), anyLong())).thenReturn(true);
    final List<Long> processedInstances = new ArrayList<>();
    final AtomicReference<TypedRecordProcessor<JobRecord>> dumpProcessorRef =
        new AtomicReference<>();

    final TypedRecordProcessor<JobRecord> errorProneProcessor =
        new TypedRecordProcessor<>() {
          @Override
          public void processRecord(final TypedRecord<JobRecord> record) {
            throw new RuntimeException("expected");
          }
        };

    streams.startStreamProcessor(
        STREAM_NAME,
        DefaultZeebeDbFactory.defaultFactory(),
        (processingContext) -> {
          zeebeState = processingContext.getZeebeState();
          dumpProcessorRef.set(
              spy(
                  new TypedRecordProcessor<>() {
                    @Override
                    public void processRecord(final TypedRecord<JobRecord> record) {
                      processedInstances.add(record.getValue().getProcessInstanceKey());
                      final var processInstanceKey =
                          (int) record.getValue().getProcessInstanceKey();
                      processingContext
                          .getWriters()
                          .command()
                          .appendFollowUpCommand(
                              record.getKey(),
                              ProcessInstanceIntent.COMPLETE_ELEMENT,
                              Records.processInstance(processInstanceKey));
                    }
                  }));

          return TypedRecordProcessors.processors(
                  zeebeState.getKeyGenerator(), processingContext.getWriters())
              .onCommand(ValueType.JOB, JobIntent.COMPLETE, errorProneProcessor)
              .onCommand(ValueType.JOB, JobIntent.THROW_ERROR, dumpProcessorRef.get());
        });

    streams
        .newRecord(STREAM_NAME)
        .event(JOB_RECORD)
        .recordType(RecordType.COMMAND)
        .intent(JobIntent.COMPLETE)
        .key(keyGenerator.nextKey())
        .write();
    streams
        .newRecord(STREAM_NAME)
        .event(JOB_RECORD)
        .recordType(RecordType.COMMAND)
        .intent(JobIntent.THROW_ERROR)
        .key(keyGenerator.nextKey())
        .write();

    // other instance
    streams
        .newRecord(STREAM_NAME)
        .event(Records.job(2))
        .recordType(RecordType.COMMAND)
        .intent(JobIntent.THROW_ERROR)
        .key(keyGenerator.nextKey())
        .write();

    // when
    waitForRecordWhichSatisfies(
        e ->
            Records.isCommand(
                e, ValueType.PROCESS_INSTANCE, ProcessInstanceIntent.COMPLETE_ELEMENT));

    // then
    final RecordMetadata metadata = new RecordMetadata();
    metadata.valueType(ValueType.PROCESS_INSTANCE);
    final MockTypedRecord<ProcessInstanceRecord> mockTypedRecord =
        new MockTypedRecord<>(0, metadata, PROCESS_INSTANCE_RECORD);
    Assertions.assertThat(zeebeState.getBlackListState().isOnBlacklist(mockTypedRecord)).isFalse();

    verify(dumpProcessorRef.get(), timeout(1000).times(2)).processRecord(any());
    assertThat(processedInstances).containsExactly(1L, 2L);
  }

  @Test
  public void shouldNotBlacklistInstanceAndIgnoreTimerStartEvents() {
    // given
    when(commandResponseWriter.tryWriteResponse(anyInt(), anyLong())).thenReturn(true);
    final List<Long> processedInstances = new ArrayList<>();

    final BpmnModelInstance process =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .timerWithDuration("PT1S")
            .endEvent()
            .done();
    final DeploymentRecord deploymentRecord = new DeploymentRecord();
    deploymentRecord
        .resources()
        .add()
        .setResourceName("process.bpmn")
        .setResource(Bpmn.convertToString(process).getBytes());

    streams.startStreamProcessor(
        STREAM_NAME,
        DefaultZeebeDbFactory.defaultFactory(),
        (processingContext) -> {
          zeebeState = processingContext.getZeebeState();
          return TypedRecordProcessors.processors(
                  zeebeState.getKeyGenerator(), processingContext.getWriters())
              .onCommand(
                  ValueType.DEPLOYMENT,
                  DeploymentIntent.CREATE,
                  new TypedRecordProcessor<DeploymentRecord>() {
                    @Override
                    public void processRecord(final TypedRecord<DeploymentRecord> record) {
                      if (record.getKey() == 0) {
                        throw new RuntimeException("expected");
                      }
                      processedInstances.add(TimerInstance.NO_ELEMENT_INSTANCE);
                      processingContext
                          .getWriters()
                          .state()
                          .appendFollowUpEvent(
                              record.getKey(),
                              TimerIntent.CREATED,
                              Records.timer(TimerInstance.NO_ELEMENT_INSTANCE));
                    }
                  });
        });

    streams
        .newRecord(STREAM_NAME)
        .event(deploymentRecord)
        .recordType(RecordType.COMMAND)
        .intent(DeploymentIntent.CREATE)
        .key(0)
        .write();
    streams
        .newRecord(STREAM_NAME)
        .event(deploymentRecord)
        .recordType(RecordType.COMMAND)
        .intent(DeploymentIntent.CREATE)
        .key(1)
        .write();

    // when
    waitForRecordWhichSatisfies(e -> Records.isEvent(e, ValueType.TIMER, TimerIntent.CREATED));

    // then
    final RecordMetadata metadata = new RecordMetadata();
    metadata.valueType(ValueType.TIMER);
    final MockTypedRecord<TimerRecord> mockTypedRecord =
        new MockTypedRecord<>(0, metadata, Records.timer(TimerInstance.NO_ELEMENT_INSTANCE));
    Assertions.assertThat(zeebeState.getBlackListState().isOnBlacklist(mockTypedRecord)).isFalse();
    assertThat(processedInstances).containsExactly(TimerInstance.NO_ELEMENT_INSTANCE);
  }

  private void waitForRecordWhichSatisfies(final Predicate<LoggedEvent> filter) {
    TestUtil.doRepeatedly(() -> streams.events(STREAM_NAME).filter(filter).findFirst())
        .until(o -> o.isPresent())
        .get();
  }

  protected static class ErrorProneProcessor
      implements TypedRecordProcessor<ProcessInstanceRecord> {

    public final AtomicLong processCount = new AtomicLong(0);

    @Override
    public void processRecord(final TypedRecord<ProcessInstanceRecord> record) {
      processCount.incrementAndGet();
      throw new RuntimeException("expected");
    }

    public long getProcessCount() {
      return processCount.get();
    }
  }

  protected static class DumpProcessor implements TypedRecordProcessor<ProcessInstanceRecord> {
    final List<Long> processedInstances = new ArrayList<>();
    private final StateWriter stateWriter;

    public DumpProcessor(final Writers writers) {
      stateWriter = writers.state();
    }

    @Override
    public void processRecord(final TypedRecord<ProcessInstanceRecord> record) {
      processedInstances.add(record.getValue().getProcessInstanceKey());
      stateWriter.appendFollowUpEvent(
          record.getKey(), ProcessInstanceIntent.ELEMENT_COMPLETED, record.getValue());
    }
  }
}
