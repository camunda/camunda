/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.streamprocessor;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import akka.actor.testkit.typed.javadsl.ActorTestKit;
import akka.actor.testkit.typed.javadsl.BehaviorTestKit;
import akka.actor.testkit.typed.javadsl.TestInbox;
import io.camunda.zeebe.engine.processing.streamprocessor.AkkaStreamProcessing.ProcessNextEvent;
import io.camunda.zeebe.engine.processing.streamprocessor.AkkaStreamProcessor.Command;
import io.camunda.zeebe.engine.processing.streamprocessor.AkkaStreamProcessor.GetPositions;
import io.camunda.zeebe.engine.processing.streamprocessor.AkkaStreamProcessor.Positions;
import io.camunda.zeebe.engine.processing.streamprocessor.AkkaStreamProcessor.RecordAvailable;
import io.camunda.zeebe.engine.processing.streamprocessor.AkkaStreamProcessor.StreamProcessorResponse;
import io.camunda.zeebe.engine.state.ZeebeDbState;
import io.camunda.zeebe.engine.state.mutable.MutableLastProcessedPositionState;
import io.camunda.zeebe.logstreams.log.LogStream;
import io.camunda.zeebe.logstreams.log.LogStreamReader;
import io.camunda.zeebe.logstreams.log.LoggedEvent;
import java.util.ArrayList;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

final class AkkaStreamProcessingTest {

  @Test
  void shouldWaitForNewRecordWhenNoneAvailable() {
    final var testKit = ActorTestKit.create();
    final var processingContext = new ProcessingContext();
    final var compat = mock(AkkaCompatActor.class);
    final var logStream = mock(LogStream.class);
    when(logStream.getPartitionId()).thenReturn(1);
    final var streamProcessor =
        new AkkaStreamProcessor(compat, new StreamProcessorBuilder().logStream(logStream));
    final var zeebeDbState = mock(ZeebeDbState.class);
    final var logstreamReader = mock(LogStreamReader.class);
    when(zeebeDbState.getLastProcessedPositionState())
        .thenReturn(
            new MutableLastProcessedPositionState() {
              @Override
              public void markAsProcessed(final long position) {}

              @Override
              public long getLastSuccessfulProcessedRecordPosition() {
                return 0;
              }
            });
    when(logstreamReader.hasNext()).thenReturn(false);
    processingContext.zeebeState(zeebeDbState);
    processingContext.logStreamReader(logstreamReader);
    final var actor =
        testKit.spawn(
            new AkkaStreamProcessing(streamProcessor, processingContext).startProcessing());
    actor.tell(new RecordAvailable());

    final var replyTo = testKit.createTestProbe(StreamProcessorResponse.class);
    actor.tell(new GetPositions(replyTo.ref()));
    replyTo.expectMessageClass(Positions.class);
  }

  @Test
  void shouldWaitForNewRecordWhenNoneAvailable2() {
    final var processingContext = new ProcessingContext();
    final var compat = mock(AkkaCompatActor.class);
    final var logStream = mock(LogStream.class);
    when(logStream.getPartitionId()).thenReturn(1);
    final var streamProcessor =
        new AkkaStreamProcessor(compat, new StreamProcessorBuilder().logStream(logStream));
    final var zeebeDbState = mock(ZeebeDbState.class);
    final var logstreamReader = mock(LogStreamReader.class);
    when(zeebeDbState.getLastProcessedPositionState())
        .thenReturn(
            new MutableLastProcessedPositionState() {
              @Override
              public void markAsProcessed(final long position) {}

              @Override
              public long getLastSuccessfulProcessedRecordPosition() {
                return 0;
              }
            });
    when(logstreamReader.hasNext()).thenReturn(false);
    processingContext.zeebeState(zeebeDbState);
    processingContext.logStreamReader(logstreamReader);

    final var test =
        BehaviorTestKit.create(
            new AkkaStreamProcessing(streamProcessor, processingContext).startProcessing());
    assertThat(test.isAlive()).isTrue();
    test.run(new RecordAvailable());
    assertThat(test.selfInbox().hasMessages()).isTrue();
    test.selfInbox().expectMessage(new ProcessNextEvent());

    //    System.out.println(test.getAllEffects());
    assertThat(test.isAlive()).isTrue();
  }

  private BehaviorTestKit<AkkaStreamProcessor.Command> actorForStream(
      final Consumer<LogStreamReader> configureStream) {
    final var compat = mock(AkkaCompatActor.class);
    final var logStream = mock(LogStream.class);
    when(logStream.getPartitionId()).thenReturn(0);
    final var streamProcessor =
        new AkkaStreamProcessor(compat, new StreamProcessorBuilder().logStream(logStream));
    final var zeebeDbState = mock(ZeebeDbState.class);
    final var logstreamReader = mock(LogStreamReader.class);

    when(zeebeDbState.getLastProcessedPositionState())
        .thenReturn(
            new MutableLastProcessedPositionState() {
              @Override
              public void markAsProcessed(final long position) {}

              @Override
              public long getLastSuccessfulProcessedRecordPosition() {
                return 0;
              }
            });

    configureStream.accept(logstreamReader);

    final var processingContext = new ProcessingContext();
    processingContext.zeebeState(zeebeDbState);
    processingContext.logStreamReader(logstreamReader);

    return BehaviorTestKit.create(
        new AkkaStreamProcessing(streamProcessor, processingContext).startProcessing());
  }

  @Test
  void shouldContinueProcessing() {
    // given log stream has three records
    final var actor =
        actorForStream(
            (reader) -> {
              when(reader.hasNext()).thenReturn(true, true, true, false);
              when(reader.next()).thenReturn(mock(LoggedEvent.class));
            });

    // when running the actor in a loop
    final var messages = new ArrayList<Command>();
    while (actor.selfInbox().hasMessages()) {
      final var message = actor.selfInbox().receiveMessage();
      messages.add(message);
      actor.run(message);
    }

    // then actor sends three ProcessNextEvents to itself
    assertThat(messages)
        .asList()
        .containsExactly(new ProcessNextEvent(), new ProcessNextEvent(), new ProcessNextEvent());
  }

  @Test
  void shouldRespondToGetPositionsWhileIdle() {
    // given empty log stream
    final var actor =
        actorForStream(
            (reader) -> {
              when(reader.hasNext()).thenReturn(false);
            });

    // when starting processing & requesting position
    final var responseInbox = TestInbox.<StreamProcessorResponse>create();
    actor.run(new GetPositions(responseInbox.getRef()));

    // then we get back positions
    assertThat(responseInbox.hasMessages()).isTrue();
    assertThat(responseInbox.getAllReceived()).asList().containsExactly(new Positions(0, 0));
  }

  @Test
  void shouldRespondToGetPositionsWhileProcessing() {
    // given a full logs stream & having started processing
    final var actor =
        actorForStream(
            (reader) -> {
              when(reader.hasNext()).thenReturn(true);
              when(reader.next()).thenReturn(mock(LoggedEvent.class));
            });
    actor.runOne();
    actor.runOne();

    // when requesting position
    final var responseInbox = TestInbox.<StreamProcessorResponse>create();
    actor.run(new GetPositions(responseInbox.getRef()));

    // then we get back positions
    assertThat(responseInbox.hasMessages()).isTrue();
    assertThat(responseInbox.getAllReceived()).asList().containsExactly(new Positions(0, 0));
  }

  @Test
  void afterProcessingPositionsAreCorrect() {
    // given a logstream with one record & processing that record
    final var event = mock(LoggedEvent.class);
    when(event.getPosition()).thenReturn(74L);
    // TODO: This doesn't work yet because the record can't be processed.

    final var actor =
        actorForStream(
            (reader) -> {
              when(reader.hasNext()).thenReturn(true);
              when(reader.next()).thenReturn(mock(LoggedEvent.class));
            });

    // when requesting positions
    final var responseInbox = TestInbox.<StreamProcessorResponse>create();
    actor.run(new GetPositions(responseInbox.getRef()));

    // then positions match record
    assertThat(responseInbox.receiveMessage()).isEqualTo(new Positions(74L, 74L));
  }

  @Test
  void afterProcessingManyRecordsPositionsAreCorrect() {
    // given a long logstream & processing has started

    // when processing is "done"

    // then positions are correct
  }

  @Test
  void failureOnUpdatingStateShouldFailActor() {
    // given a mocked component that throws

    // when processing

    // then the actor is failed -> isAlive() is false & termination reason matches exception
  }
}
