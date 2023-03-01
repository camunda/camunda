/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.streamprocessor;

import static io.camunda.zeebe.util.buffer.BufferUtil.wrapString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;

import io.camunda.zeebe.engine.api.CommandResponseWriter;
import io.camunda.zeebe.engine.api.TypedRecord;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.DefaultZeebeDbFactory;
import io.camunda.zeebe.engine.state.KeyGenerator;
import io.camunda.zeebe.engine.util.RecordStream;
import io.camunda.zeebe.engine.util.Records;
import io.camunda.zeebe.engine.util.TestStreams;
import io.camunda.zeebe.logstreams.log.LoggedEvent;
import io.camunda.zeebe.logstreams.util.SynchronousLogStream;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.DeploymentIntent;
import io.camunda.zeebe.scheduler.testing.ActorSchedulerRule;
import io.camunda.zeebe.test.util.AutoCloseableRule;
import io.camunda.zeebe.test.util.TestUtil;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TemporaryFolder;
import org.mockito.MockitoAnnotations;

public final class TypedStreamProcessorTest {

  private static final String STREAM_NAME = "foo";
  protected SynchronousLogStream stream;
  private final TemporaryFolder tempFolder = new TemporaryFolder();
  private final AutoCloseableRule closeables = new AutoCloseableRule();
  private final ActorSchedulerRule actorSchedulerRule = new ActorSchedulerRule();

  @Rule
  public RuleChain ruleChain =
      RuleChain.outerRule(tempFolder).around(actorSchedulerRule).around(closeables);

  private TestStreams streams;
  private KeyGenerator keyGenerator;
  private CommandResponseWriter mockCommandResponseWriter;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);

    streams = new TestStreams(tempFolder, closeables, actorSchedulerRule.get());
    mockCommandResponseWriter = streams.getMockedResponseWriter();
    stream = streams.createLogStream(STREAM_NAME);

    final AtomicLong key = new AtomicLong();
    keyGenerator = () -> key.getAndIncrement();
  }

  @Test
  public void shouldWriteSourceEventAndProducerOnBatch() {
    // given
    streams.startStreamProcessor(
        STREAM_NAME,
        DefaultZeebeDbFactory.defaultFactory(),
        (processingContext) ->
            TypedRecordProcessors.processors(keyGenerator, processingContext.getWriters())
                .onCommand(
                    ValueType.DEPLOYMENT,
                    DeploymentIntent.CREATE,
                    new BatchProcessor(processingContext.getWriters())));
    final long firstEventPosition =
        streams
            .newRecord(STREAM_NAME)
            .event(deployment("foo"))
            .recordType(RecordType.COMMAND)
            .intent(DeploymentIntent.CREATE)
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
    assertThat(writtenEvent.getSourceEventPosition()).isEqualTo(firstEventPosition);
  }

  @Test
  public void shouldSkipFailingEvent() {
    // given
    streams.startStreamProcessor(
        STREAM_NAME,
        DefaultZeebeDbFactory.defaultFactory(),
        (processingContext) ->
            TypedRecordProcessors.processors(keyGenerator, processingContext.getWriters())
                .onCommand(
                    ValueType.DEPLOYMENT,
                    DeploymentIntent.CREATE,
                    new ErrorProneProcessor(processingContext.getWriters())));
    final AtomicLong requestId = new AtomicLong(0);
    final AtomicInteger requestStreamId = new AtomicInteger(0);

    doAnswer(
            (invocationOnMock -> {
              final int streamIdArg = invocationOnMock.getArgument(0);
              final long requestIdArg = invocationOnMock.getArgument(1);

              requestId.set(requestIdArg);
              requestStreamId.set(streamIdArg);

              return null;
            }))
        .when(mockCommandResponseWriter)
        .tryWriteResponse(anyInt(), anyLong());

    final long failingKey = keyGenerator.nextKey();
    streams
        .newRecord(STREAM_NAME)
        .event(deployment("foo"))
        .recordType(RecordType.COMMAND)
        .intent(DeploymentIntent.CREATE)
        .requestId(255L)
        .requestStreamId(99)
        .key(failingKey)
        .write();
    final long secondEventPosition =
        streams
            .newRecord(STREAM_NAME)
            .event(deployment("foo2"))
            .recordType(RecordType.COMMAND)
            .intent(DeploymentIntent.CREATE)
            .key(keyGenerator.nextKey())
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
    assertThat(writtenEvent.getKey()).isEqualTo(1);
    assertThat(writtenEvent.getSourceEventPosition()).isEqualTo(secondEventPosition);

    // error response
    verify(mockCommandResponseWriter).tryWriteResponse(anyInt(), anyLong());

    assertThat(requestId.get()).isEqualTo(255L);
    assertThat(requestStreamId.get()).isEqualTo(99);

    final Record<DeploymentRecord> deploymentRejection =
        new RecordStream(streams.events(STREAM_NAME))
            .onlyDeploymentRecords()
            .onlyRejections()
            .withIntent(DeploymentIntent.CREATE)
            .getFirst();

    assertThat(deploymentRejection.getKey()).isEqualTo(failingKey);
    assertThat(deploymentRejection.getRejectionType()).isEqualTo(RejectionType.PROCESSING_ERROR);
  }

  protected DeploymentRecord deployment(final String name) {
    final DeploymentRecord event = new DeploymentRecord();
    event.resources().add().setResource(wrapString("foo")).setResourceName(wrapString(name));
    return event;
  }

  protected static class ErrorProneProcessor implements TypedRecordProcessor<DeploymentRecord> {

    private final Writers writers;

    public ErrorProneProcessor(final Writers writers) {
      this.writers = writers;
    }

    @Override
    public void processRecord(final TypedRecord<DeploymentRecord> record) {
      if (record.getKey() == 0) {
        throw new RuntimeException("expected");
      }
      writers
          .state()
          .appendFollowUpEvent(record.getKey(), DeploymentIntent.CREATED, record.getValue());
    }
  }

  protected class BatchProcessor implements TypedRecordProcessor<DeploymentRecord> {

    private final StateWriter stateWriter;

    public BatchProcessor(final Writers writers) {
      stateWriter = writers.state();
    }

    @Override
    public void processRecord(final TypedRecord<DeploymentRecord> record) {
      stateWriter.appendFollowUpEvent(
          keyGenerator.nextKey(), DeploymentIntent.CREATED, record.getValue());
    }
  }
}
