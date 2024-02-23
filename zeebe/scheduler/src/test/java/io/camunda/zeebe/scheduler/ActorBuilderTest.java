/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

public class ActorBuilderTest {

  @Test
  void shouldBuildActor() {
    // when
    final var actor = Actor.newActor().build();

    // then
    assertThat(actor).isNotNull().isInstanceOf(Actor.class);
  }

  @Test
  void shouldSetActorName() {
    // given
    final var actorName = "foo";

    // when
    final var actor = Actor.newActor().name(actorName).build();

    // then
    assertThat(actor.getName()).isEqualTo(actorName);
  }

  @Test
  void shouldCallOnActorStartedHandler() throws Exception {
    // given
    final var latch = new CountDownLatch(1);
    final var actorControlRef = new AtomicReference<>();

    final var scheduler = ActorScheduler.newActorScheduler().build();
    scheduler.start();

    // when
    final var actor =
        Actor.newActor()
            .actorStartedHandler(
                (c) -> {
                  actorControlRef.set(c);
                  latch.countDown();
                })
            .build();
    scheduler.submitActor(actor);

    // then
    assertThat(latch.await(5, TimeUnit.SECONDS));
    assertThat(actorControlRef.get()).isNotNull();

    // cleanup
    final var stopFuture = scheduler.stop();
    await().until(stopFuture::isDone);
  }
}
