/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.streamprocessor;

import static io.camunda.zeebe.test.util.TestUtil.waitUntil;
import static io.camunda.zeebe.util.buffer.BufferUtil.wrapString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.DefaultZeebeDbFactory;
import io.camunda.zeebe.engine.state.instance.TimerInstance;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.util.MockTypedRecord;
import io.camunda.zeebe.engine.util.RecordStream;
import io.camunda.zeebe.engine.util.Records;
import io.camunda.zeebe.engine.util.TestStreams;
import io.camunda.zeebe.logstreams.log.LoggedEvent;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import io.camunda.zeebe.protocol.impl.record.value.error.ErrorRecord;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.impl.record.value.timer.TimerRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.DeploymentIntent;
import io.camunda.zeebe.protocol.record.intent.ErrorIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.TimerIntent;
import io.camunda.zeebe.scheduler.testing.ActorSchedulerRule;
import io.camunda.zeebe.stream.api.CommandResponseWriter;
import io.camunda.zeebe.stream.api.ReadonlyStreamProcessorContext;
import io.camunda.zeebe.stream.api.StreamProcessorLifecycleAware;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import io.camunda.zeebe.stream.impl.StreamProcessor;
import io.camunda.zeebe.test.util.AutoCloseableRule;
import io.camunda.zeebe.test.util.TestUtil;
import io.camunda.zeebe.util.buffer.BufferUtil;
import io.camunda.zeebe.util.health.FailureListener;
import io.camunda.zeebe.util.health.HealthReport;
import io.camunda.zeebe.util.health.HealthStatus;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import org.agrona.CloseHelper;
import org.agrona.collections.MutableLong;
import org.assertj.core.api.Assertions;
import org.awaitility.Awaitility;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TemporaryFolder;

public final class EngineErrorHandlingTest {

  private static final String STREAM_NAME = "foo";
  private static final int PARTITION_ID = 1;

  private final TemporaryFolder tempFolder = new TemporaryFolder();
  private final AutoCloseableRule closeables = new AutoCloseableRule();
  private final ActorSchedulerRule actorSchedulerRule = new ActorSchedulerRule();

  @Rule
  public RuleChain ruleChain =
      RuleChain.outerRule(tempFolder).around(actorSchedulerRule).around(closeables);

  private TestStreams streams;
  private KeyGenerator keyGenerator;
  private CommandResponseWriter mockCommandResponseWriter;
  private MutableProcessingState processingState;
  private StreamProcessor streamProcessor;

  @Before
  public void setUp() {
    streams =
        new TestStreams(tempFolder, closeables, actorSchedulerRule.get(), InstantSource.system());
    mockCommandResponseWriter = streams.getMockedResponseWriter();
    streams.createLogStream(STREAM_NAME, PARTITION_ID);
  }

  @After
  public void tearDown() {
    CloseHelper.close(streamProcessor);
  }

  @Test
  public void shouldAutoRejectCommandOnProcessingFailure() {
    // given
    final MutableLong failingKey = new MutableLong();
    final MutableLong secondKey = new MutableLong();
    streamProcessor =
        streams.startStreamProcessor(
            STREAM_NAME,
            DefaultZeebeDbFactory.defaultFactory(),
            (processingContext) -> {
              processingState = processingContext.getProcessingState();
              keyGenerator = processingContext.getProcessingState().getKeyGenerator();
              failingKey.set(keyGenerator.getCurrentKey());
              secondKey.set(keyGenerator.nextKey());
              return TypedRecordProcessors.processors()
                  .onCommand(
                      ValueType.DEPLOYMENT,
                      DeploymentIntent.CREATE,
                      new TypedRecordProcessor<DeploymentRecord>() {
                        @Override
                        public void processRecord(final TypedRecord<DeploymentRecord> record) {
                          if (Protocol.decodeKeyInPartition(record.getKey()) == 0) {
                            throw new RuntimeException("expected");
                          }
                          processingContext
                              .getWriters()
                              .state()
                              .appendFollowUpEvent(
                                  record.getKey(), DeploymentIntent.CREATED, record.getValue());
                        }
                      });
            });

    streams
        .newRecord(STREAM_NAME)
        .event(deployment("foo"))
        .recordType(RecordType.COMMAND)
        .intent(DeploymentIntent.CREATE)
        .requestId(255L)
        .requestStreamId(99)
        .key(failingKey.get())
        .write();

    final long secondEventPosition =
        streams
            .newRecord(STREAM_NAME)
            .event(deployment("foo2"))
            .recordType(RecordType.COMMAND)
            .intent(DeploymentIntent.CREATE)
            .key(secondKey.get())
            .write();

    // when
    final LoggedEvent writtenEvent =
        TestUtil.doRepeatedly(
                () ->
                    streams
                        .events(STREAM_NAME)
                        .filter(
                            e -> Records.isEvent(e, ValueType.DEPLOYMENT, DeploymentIntent.CREATED))
                        .findFirst())
            .until(o -> o.isPresent())
            .get();

    // then
    assertThat(writtenEvent.getKey()).isEqualTo(secondKey.get());
    assertThat(writtenEvent.getSourceEventPosition()).isEqualTo(secondEventPosition);

    // error response
    verify(mockCommandResponseWriter).tryWriteResponse(eq(99), eq(255L));

    final Record<DeploymentRecord> deploymentRejection =
        new RecordStream(streams.events(STREAM_NAME))
            .onlyDeploymentRecords()
            .onlyRejections()
            .withIntent(DeploymentIntent.CREATE)
            .getFirst();

    assertThat(deploymentRejection.getKey()).isEqualTo(failingKey.get());
    assertThat(deploymentRejection.getRejectionType()).isEqualTo(RejectionType.PROCESSING_ERROR);
  }

  DeploymentRecord deployment(final String name) {
    final DeploymentRecord event = new DeploymentRecord();
    event.resources().add().setResource(wrapString("foo")).setResourceName(wrapString(name));
    return event;
  }

  @Test
  public void shouldFailWhenWritingAnEventWithWrongKey() {
    streamProcessor =
        streams.startStreamProcessor(
            STREAM_NAME,
            DefaultZeebeDbFactory.defaultFactory(),
            (processingContext) -> {
              processingState = processingContext.getProcessingState();
              keyGenerator = processingState.getKeyGenerator();
              return TypedRecordProcessors.processors()
                  .onCommand(
                      ValueType.PROCESS_INSTANCE,
                      ProcessInstanceIntent.ACTIVATE_ELEMENT,
                      new TypedRecordProcessor<UnifiedRecordValue>() {
                        @Override
                        public void processRecord(final TypedRecord<UnifiedRecordValue> record) {
                          processingContext
                              .getWriters()
                              .state()
                              .appendFollowUpEvent(
                                  keyGenerator.getCurrentKey() + 1000,
                                  ProcessInstanceIntent.ELEMENT_ACTIVATED,
                                  record.getValue());
                        }
                      });
            });
    final AtomicReference<HealthReport> reportRef = new AtomicReference<>();
    final AtomicBoolean unrecoverableFailure = new AtomicBoolean(false);
    streamProcessor.addFailureListener(
        new FailureListener() {
          @Override
          public void onFailure(final HealthReport report) {
            reportRef.set(report);
          }

          @Override
          public void onRecovered(final HealthReport report) {
            reportRef.set(report);
          }

          @Override
          public void onUnrecoverableFailure(final HealthReport report) {
            reportRef.set(report);
            unrecoverableFailure.set(true);
          }
        });

    // when
    final long failingEventPosition =
        streams
            .newRecord(STREAM_NAME)
            .event(Records.processInstance(1))
            .recordType(RecordType.COMMAND)
            .intent(ProcessInstanceIntent.ACTIVATE_ELEMENT)
            .write();

    // then
    Awaitility.await()
        .untilAsserted(
            () -> {
              assertThat(streamProcessor.isFailed()).isTrue();
              assertThat(reportRef.get())
                  .isNotNull()
                  .returns(HealthStatus.DEAD, HealthReport::getStatus);
              assertThat(unrecoverableFailure.get()).isTrue();
            });
  }

  @Test
  public void shouldWriteErrorEvent() {
    // given
    final ErrorProneProcessor errorProneProcessor = new ErrorProneProcessor();

    streamProcessor =
        streams.startStreamProcessor(
            STREAM_NAME,
            DefaultZeebeDbFactory.defaultFactory(),
            (processingContext) -> {
              processingState = processingContext.getProcessingState();
              keyGenerator = processingState.getKeyGenerator();
              return TypedRecordProcessors.processors()
                  .onCommand(
                      ValueType.PROCESS_INSTANCE,
                      ProcessInstanceIntent.ACTIVATE_ELEMENT,
                      errorProneProcessor);
            });

    final long failingEventPosition =
        streams
            .newRecord(STREAM_NAME)
            .event(Records.processInstance(1))
            .recordType(RecordType.COMMAND)
            .intent(ProcessInstanceIntent.ACTIVATE_ELEMENT)
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
    streamProcessor =
        streams.startStreamProcessor(
            STREAM_NAME,
            DefaultZeebeDbFactory.defaultFactory(),
            (processingContext) -> {
              processingState = processingContext.getProcessingState();
              keyGenerator = processingState.getKeyGenerator();
              return TypedRecordProcessors.processors()
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
            .event(Records.processInstance(1))
            .recordType(RecordType.COMMAND)
            .intent(ProcessInstanceIntent.ACTIVATE_ELEMENT)
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
  public void shouldBanInstance() {
    // given
    final AtomicReference<DumpProcessor> dumpProcessorRef = new AtomicReference<>();
    final ErrorProneProcessor processor = new ErrorProneProcessor();

    streamProcessor =
        streams.startStreamProcessor(
            STREAM_NAME,
            DefaultZeebeDbFactory.defaultFactory(),
            (processingContext) -> {
              dumpProcessorRef.set(spy(new DumpProcessor(processingContext.getWriters())));
              processingState = processingContext.getProcessingState();
              keyGenerator = processingState.getKeyGenerator();
              return TypedRecordProcessors.processors()
                  .onCommand(
                      ValueType.PROCESS_INSTANCE, ProcessInstanceIntent.ACTIVATE_ELEMENT, processor)
                  .onCommand(
                      ValueType.PROCESS_INSTANCE,
                      ProcessInstanceIntent.COMPLETE_ELEMENT,
                      dumpProcessorRef.get());
            });

    streams
        .newRecord(STREAM_NAME)
        .event(Records.processInstance(1))
        .recordType(RecordType.COMMAND)
        .intent(ProcessInstanceIntent.ACTIVATE_ELEMENT)
        .write();
    streams
        .newRecord(STREAM_NAME)
        .event(Records.processInstance(1))
        .recordType(RecordType.COMMAND)
        .intent(ProcessInstanceIntent.COMPLETE_ELEMENT)
        .write();

    // other instance
    streams
        .newRecord(STREAM_NAME)
        .event(Records.processInstance(2))
        .recordType(RecordType.COMMAND)
        .intent(ProcessInstanceIntent.COMPLETE_ELEMENT)
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
        new MockTypedRecord<>(0, metadata, Records.processInstance(1));
    Assertions.assertThat(processingState.getBannedInstanceState().isBanned(mockTypedRecord))
        .isTrue();

    verify(dumpProcessorRef.get(), times(1)).processRecord(any());
    assertThat(dumpProcessorRef.get().processedInstances).containsExactly(2L);
  }

  @Test
  public void shouldBanInstanceOnReplay() throws Exception {
    // given
    final long failedPos =
        streams
            .newRecord(STREAM_NAME)
            .event(Records.processInstance(1))
            .recordType(RecordType.COMMAND)
            .intent(ProcessInstanceIntent.ACTIVATE_ELEMENT)
            .write();
    streams
        .newRecord(STREAM_NAME)
        .event(Records.error((int) Records.processInstance(1).getProcessInstanceKey(), failedPos))
        .recordType(RecordType.EVENT)
        .sourceRecordPosition(failedPos)
        .intent(ErrorIntent.CREATED)
        .write();

    final CountDownLatch latch = new CountDownLatch(1);
    streamProcessor =
        streams.startStreamProcessor(
            STREAM_NAME,
            DefaultZeebeDbFactory.defaultFactory(),
            (processingContext) -> {
              processingState = processingContext.getProcessingState();
              keyGenerator = processingState.getKeyGenerator();
              return TypedRecordProcessors.processors()
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
        new MockTypedRecord<>(0, metadata, Records.processInstance(1));
    waitUntil(() -> processingState.getBannedInstanceState().isBanned(mockTypedRecord));
  }

  @Test
  public void shouldNotBanInstanceOnJobCommand() {
    // given
    final List<Long> processedInstances = new ArrayList<>();

    final TypedRecordProcessor<JobRecord> errorProneProcessor =
        new TypedRecordProcessor<>() {
          @Override
          public void processRecord(final TypedRecord<JobRecord> record) {
            throw new RuntimeException("expected");
          }
        };

    streamProcessor =
        streams.startStreamProcessor(
            STREAM_NAME,
            DefaultZeebeDbFactory.defaultFactory(),
            (processingContext) -> {
              processingState = processingContext.getProcessingState();
              keyGenerator = processingState.getKeyGenerator();

              return TypedRecordProcessors.processors()
                  .onCommand(ValueType.JOB, JobIntent.COMPLETE, errorProneProcessor)
                  .onCommand(
                      ValueType.JOB,
                      JobIntent.THROW_ERROR,
                      new TypedRecordProcessor<JobRecord>() {
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
                      });
            });

    streams
        .newRecord(STREAM_NAME)
        .event(Records.job(1))
        .recordType(RecordType.COMMAND)
        .intent(JobIntent.COMPLETE)
        .write();
    streams
        .newRecord(STREAM_NAME)
        .event(Records.job(1))
        .recordType(RecordType.COMMAND)
        .intent(JobIntent.THROW_ERROR)
        .write();

    // other instance
    streams
        .newRecord(STREAM_NAME)
        .event(Records.job(2))
        .recordType(RecordType.COMMAND)
        .intent(JobIntent.THROW_ERROR)
        .write();

    // when
    TestUtil.doRepeatedly(
            () ->
                streams
                    .events(STREAM_NAME)
                    .filter(
                        e ->
                            Records.isCommand(
                                e,
                                ValueType.PROCESS_INSTANCE,
                                ProcessInstanceIntent.COMPLETE_ELEMENT))
                    .toList())
        .until(o -> o.size() == 2);

    // then
    final RecordMetadata metadata = new RecordMetadata();
    metadata.valueType(ValueType.PROCESS_INSTANCE);
    final MockTypedRecord<ProcessInstanceRecord> mockTypedRecord =
        new MockTypedRecord<>(0, metadata, Records.processInstance(1));
    Assertions.assertThat(processingState.getBannedInstanceState().isBanned(mockTypedRecord))
        .isFalse();

    assertThat(processedInstances).containsExactly(1L, 2L);
  }

  @Test
  public void shouldNotBanInstanceAndIgnoreTimerStartEvents() {
    // given
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

    streamProcessor =
        streams.startStreamProcessor(
            STREAM_NAME,
            DefaultZeebeDbFactory.defaultFactory(),
            (processingContext) -> {
              processingState = processingContext.getProcessingState();
              return TypedRecordProcessors.processors()
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
    Assertions.assertThat(processingState.getBannedInstanceState().isBanned(mockTypedRecord))
        .isFalse();
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
