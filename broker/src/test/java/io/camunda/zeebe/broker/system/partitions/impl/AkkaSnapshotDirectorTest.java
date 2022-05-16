/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.system.partitions.impl;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;

import akka.actor.testkit.typed.javadsl.ActorTestKit;
import akka.actor.testkit.typed.javadsl.ManualTime;
import akka.actor.testkit.typed.javadsl.TestProbe;
import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.Behaviors;
import io.camunda.zeebe.broker.system.partitions.StateController;
import io.camunda.zeebe.broker.system.partitions.impl.AkkaSnapshotDirector.SnapshotDirectorCommands;
import io.camunda.zeebe.engine.processing.streamprocessor.StreamProcessor;
import java.time.Duration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class AkkaSnapshotDirectorTest {

  static final ActorTestKit testKit = ActorTestKit.create();

  // This must be te command expected by StateController
  Behavior<SnapshotDirectorCommands> mockedStateControllerBehavior =
      Behaviors.receiveMessage(
          message -> {
            return Behaviors.same();
          });
  private final ManualTime manualTime = ManualTime.get(testKit.system());

  @AfterAll
  public static void cleanup() {
    testKit.shutdownTestKit();
  }

  @Test
  void shouldTakeTransientSnapshotOnForce() {
    // Mock actors for StateController and StreamProcessor
    final TestProbe<SnapshotDirectorCommands> probe = testKit.createTestProbe();
    final ActorRef<SnapshotDirectorCommands> mockedStateController =
        testKit.spawn(
            Behaviors.monitor(
                SnapshotDirectorCommands.class, probe.ref(), mockedStateControllerBehavior));

    // pass mockedStateController to snapshot director
    final AkkaSnapshotDirector director = new AkkaSnapshotDirector(null, null, null, null);
    final ActorRef<SnapshotDirectorCommands> snapshotDirector = testKit.spawn(director.create());

    // when
    director.forceSnapshot();

    // then
    // verify that stateController received message StartTransientSnapshot (or what ever the message
    // type is)
    // probe.expectMessageClass(StateController.TakeTransientSnapshot.class)

  }

  @Test
  void shouldTakeTransientSnapshotOnForceALTERNATE() {
    // Mock actors for StateController and StreamProcessor
    final StateController stateController = mock(StateController.class);
    final StreamProcessor streamProcessor = mock(StreamProcessor.class);

    // pass mockedStateController to snapshot director
    final AkkaSnapshotDirector director =
        new AkkaSnapshotDirector(null, streamProcessor, null, stateController);
    final ActorRef<SnapshotDirectorCommands> snapshotDirector = testKit.spawn(director.create());

    // mock
    // when stateController::takeTransientSnapshot, snapshotDirector.tell(new
    // TransientSnapshotTaken())
    // when streamProcessor::getLastProcessedPosition, snapshotDirector.tell(new
    // LastProcessPosition())

    // when
    director.forceSnapshot();

    // then
    Mockito.verify(stateController, times(1)).takeTransientSnapshot(any());
  }

  @Test
  void shouldTakeTransientSnapshotOnTimer() {
    final AkkaSnapshotDirector director = new AkkaSnapshotDirector(null, null, null, null);
    final ActorRef<SnapshotDirectorCommands> snapshotDirector = testKit.spawn(director.create());

    // when
    manualTime.timePasses(Duration.ofMinutes(1));

    // then
    // verify that stateController received message TakeTransientSnapshot (or what ever the message
    // type is)
    // probe.expectMessageClass(StateController.TakeTransientSnapshot.class)

  }

  // Test that something doesn't happen
  @Test
  void shouldNotCommitSnapshotWhenXXX() {
    // Mock actors for StateController and StreamProcessor
    final TestProbe<SnapshotDirectorCommands> probe = testKit.createTestProbe();
    final ActorRef<SnapshotDirectorCommands> mockedStateController =
        testKit.spawn(
            Behaviors.monitor(
                SnapshotDirectorCommands.class, probe.ref(), mockedStateControllerBehavior));

    // pass mockedStateController to snapshot director
    final AkkaSnapshotDirector director = new AkkaSnapshotDirector(null, null, null, null);
    final ActorRef<SnapshotDirectorCommands> snapshotDirector = testKit.spawn(director.create());

    // when
    director.forceSnapshot();


    // then
    // verify that stateController received message StartTransientSnapshot (or what ever the message
    // type is)
    // probe.expectMessageClass(StateController.TakeTransientSnapshot.class)

    // Then receive no more message
    probe.expectNoMessage(Duration.ofSeconds(10));
  }


  // Inspecting state of snapshotDirector
  @Test
  void shouldNotCommitSnapshotWhenXXX() {
    // Mock actors for StateController and StreamProcessor
    final TestProbe<SnapshotDirectorCommands> probe = testKit.createTestProbe()

    // pass mockedStateController to snapshot director
    final AkkaSnapshotDirector director = new AkkaSnapshotDirector(null, null, null, null);
    final ActorRef<SnapshotDirectorCommands> snapshotDirector = testKit.spawn(Behaviors.monitor(SnapshotDirectorCommands.class, probe.ref(), director.create()));

    // when
    director.forceSnapshot();


    // then
    // probe.expectMessage(something) // to test if it sends a message itself
    // probe.fishForMessage() // inspects all messages it receives
    probe.fishForMessage()
  }
}
