/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.system.partitions.impl;

import akka.actor.testkit.typed.javadsl.ActorTestKit;
import akka.actor.testkit.typed.javadsl.TestProbe;
import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.Behaviors;
import com.typesafe.config.ConfigFactory;
import io.camunda.zeebe.broker.system.partitions.impl.AkkaSnapshotDirector.SnapshotDirectorCommands;
import io.camunda.zeebe.broker.system.partitions.impl.AkkaSnapshotDirector.WaitForCommitPosition;
import io.camunda.zeebe.broker.system.partitions.impl.AkkaSnapshotInProgress.CommitPositionReached;
import io.camunda.zeebe.broker.system.partitions.impl.AkkaSnapshotInProgress.SnapshotResponse;
import io.camunda.zeebe.broker.system.partitions.impl.AkkaSnapshotInProgress.SnapshotSucceeded;
import io.camunda.zeebe.broker.system.partitions.impl.AkkaStateController.PersistTransientSnapshot;
import io.camunda.zeebe.broker.system.partitions.impl.AkkaStateController.PersistedSnapshotReply;
import io.camunda.zeebe.broker.system.partitions.impl.AkkaStateController.StateControllerCommand;
import io.camunda.zeebe.broker.system.partitions.impl.AkkaStateController.TakeTransientSnapshot;
import io.camunda.zeebe.broker.system.partitions.impl.AkkaStateController.TransientSnapshotReply;
import io.camunda.zeebe.broker.system.partitions.impl.AkkaStreamProcessor.GetLastProcessedPosition;
import io.camunda.zeebe.broker.system.partitions.impl.AkkaStreamProcessor.GetLastWrittenPosition;
import io.camunda.zeebe.broker.system.partitions.impl.AkkaStreamProcessor.LastProcessedPosition;
import io.camunda.zeebe.broker.system.partitions.impl.AkkaStreamProcessor.LastWrittenPosition;
import io.camunda.zeebe.broker.system.partitions.impl.AkkaStreamProcessor.StreamProcessorCommands;
import io.camunda.zeebe.engine.processing.streamprocessor.StreamProcessorMode;
import io.camunda.zeebe.snapshots.PersistedSnapshot;
import io.camunda.zeebe.snapshots.TransientSnapshot;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class AkkaSnapshotInProgressTest {

  static final ActorTestKit testKit =
      ActorTestKit.create(
          ConfigFactory.parseString(
              "akka.log-config-on-start = on \n akka.loglevel = DEBUG \n akka.actor.debug.receive = true"));
  private ActorRef<StateControllerCommand> akkaStateController;
  private ActorRef<SnapshotDirectorCommands> akkaSnapshotDirector;

  // This must be te command expected by StateController
  //    Behavior<SnapshotDirectorCommands> mockedStateControllerBehavior =
  //        Behaviors.receiveMessage(
  //            message -> {
  //              return Behaviors.same();
  //            });
  //    private final ManualTime manualTime = ManualTime.get(testKit.system());

  @AfterAll
  public static void cleanup() {
    testKit.shutdownTestKit();
  }

  @Test
  void shouldSendProcessPositionRequestOnStart() {
    // Mock actors for StateController and StreamProcessor
    final TestProbe<StreamProcessorCommands> streamProcessorProbe = testKit.createTestProbe();
    final Behavior<StreamProcessorCommands> mockedStreamProcessorBehavior =
        Behaviors.receiveMessage(message -> Behaviors.same());
    final ActorRef<StreamProcessorCommands> mockedStreamProcessor =
        testKit.spawn(
            Behaviors.monitor(
                StreamProcessorCommands.class,
                streamProcessorProbe.ref(),
                mockedStreamProcessorBehavior));

    // pass mockedStateController to snapshot director
    final var snapshotInProgressBehavior =
        AkkaSnapshotInProgress.create(
            null, StreamProcessorMode.PROCESSING, mockedStreamProcessor, null, null);

    // when
    testKit.spawn(snapshotInProgressBehavior);

    // then
    streamProcessorProbe.expectMessageClass(GetLastProcessedPosition.class);
  }

  @Test
  void shouldTakeTransientSnapshotOnForce() {
    // Mock actors for StateController and StreamProcessor
    // final TestProbe<StreamProcessorCommands> streamProcessorProbe = testKit.createTestProbe();
    final Behavior<StreamProcessorCommands> mockedStreamProcessorBehavior =
        Behaviors.receive(StreamProcessorCommands.class)
            .onMessage(
                GetLastProcessedPosition.class,
                (msg) -> {
                  msg.replyTo().tell(new LastProcessedPosition(8));
                  return Behaviors.same();
                })
            .onMessage(
                GetLastWrittenPosition.class,
                (msg) -> {
                  msg.replyTo().tell(new LastWrittenPosition(9));
                  return Behaviors.same();
                })
            .build();
    final ActorRef<StreamProcessorCommands> mockedStreamProcessor =
        testKit.spawn(mockedStreamProcessorBehavior);

    final var responseReceiver = mockAkkaSnapshotResponseReceiver();
    final var stateController = mockAkkaStateController();
    final var snapshotDirector = mockAkkaSnapshotDirector();

    // pass mockedStateController to snapshot director
    final var snapshotInProgressBehavior =
        AkkaSnapshotInProgress.create(
            responseReceiver.ref(),
            StreamProcessorMode.PROCESSING,
            mockedStreamProcessor,
            akkaSnapshotDirector,
            akkaStateController);

    // when
    testKit.spawn(snapshotInProgressBehavior);

    // then
    stateController.expectMessageClass(TakeTransientSnapshot.class);
    stateController.expectMessageClass(PersistTransientSnapshot.class);
    responseReceiver.expectMessageClass(SnapshotSucceeded.class);
  }

  private TestProbe<SnapshotDirectorCommands> mockAkkaSnapshotDirector() {
    final TestProbe<SnapshotDirectorCommands> probe = testKit.createTestProbe();
    final var behavior =
        Behaviors.receive(SnapshotDirectorCommands.class)
            .onMessage(
                WaitForCommitPosition.class,
                (msg) -> {
                  msg.replyTo().tell(new CommitPositionReached(msg.commitPosition()));
                  return Behaviors.same();
                })
            .onAnyMessage((msg) -> Behaviors.same())
            .build();

    akkaSnapshotDirector =
        testKit.spawn(Behaviors.monitor(SnapshotDirectorCommands.class, probe.ref(), behavior));

    return probe;
  }

  private TestProbe<SnapshotResponse> mockAkkaSnapshotResponseReceiver() {
    final TestProbe<SnapshotResponse> probe = testKit.createTestProbe();
    final var behavior =
        Behaviors.receive(SnapshotResponse.class).onAnyMessage((msg) -> Behaviors.same()).build();

    testKit.spawn(Behaviors.monitor(SnapshotResponse.class, probe.ref(), behavior));

    return probe;
  }

  private TestProbe<StateControllerCommand> mockAkkaStateController() {
    final TestProbe<StateControllerCommand> stateControllerProbe = testKit.createTestProbe();
    final Behavior<StateControllerCommand> mockedStateControllerBehavior =
        Behaviors.receive(StateControllerCommand.class)
            .onMessage(
                TakeTransientSnapshot.class,
                (msg) -> {
                  msg.replyTo()
                      .tell(new TransientSnapshotReply(Mockito.mock(TransientSnapshot.class)));
                  return Behaviors.same();
                })
            .onMessage(
                PersistTransientSnapshot.class,
                msg -> {
                  msg.replyTo()
                      .tell(new PersistedSnapshotReply(Mockito.mock(PersistedSnapshot.class)));
                  return Behaviors.same();
                })
            .build();
    akkaStateController =
        testKit.spawn(
            Behaviors.monitor(
                StateControllerCommand.class,
                stateControllerProbe.ref(),
                mockedStateControllerBehavior));
    return stateControllerProbe;
  }
}
