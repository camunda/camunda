/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.scheduler;

import static io.camunda.zeebe.scheduler.Actor.ACTOR_PROP_NAME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.camunda.zeebe.scheduler.testing.ActorSchedulerExtension;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.MDC;

final class ActorMdcContextTest {

  @RegisterExtension
  private final ActorSchedulerExtension scheduler = new ActorSchedulerExtension(1);

  @Test
  void shouldExposeActorContextOnEveryExecution() {
    // given
    final List<String> observed = new CopyOnWriteArrayList<>();
    final var actor = new NamedActor("first-actor");
    scheduler.submitActor(actor).join();

    // when
    for (int i = 0; i < 50; i++) {
      actor.run(() -> observed.add(MDC.get(ACTOR_PROP_NAME)));
    }

    // then
    await().until(() -> observed.size() == 50);
    assertThat(observed).containsOnly("first-actor");
  }

  @Test
  void shouldSwitchActorContextWhenExecutingAnotherActor() {
    // given
    final List<String> observed = new CopyOnWriteArrayList<>();
    final var first = new NamedActor("first-actor");
    final var second = new NamedActor("second-actor");
    scheduler.submitActor(first).join();
    scheduler.submitActor(second).join();

    // when
    for (int i = 0; i < 20; i++) {
      first.run(() -> observed.add(MDC.get(ACTOR_PROP_NAME)));
      second.run(() -> observed.add(MDC.get(ACTOR_PROP_NAME)));
    }

    // then
    await().until(() -> observed.size() == 40);
    assertThat(observed).containsOnly("first-actor", "second-actor");
    assertThat(observed.stream().filter("first-actor"::equals)).hasSize(20);
  }

  @Test
  void shouldNotLeakActorContextIntoSchedulerCallbacks() {
    // given
    final var actor = new NamedActor("leaky-actor");
    final var thread = new AtomicReference<ActorThread>();
    final var inActor = new AtomicReference<String>();
    scheduler.submitActor(actor).join();
    actor.run(
        () -> {
          inActor.set(MDC.get(ACTOR_PROP_NAME));
          thread.set(ActorThread.current());
        });
    await().until(() -> thread.get() != null);

    // when
    final var inCallback = new AtomicReference<String>("not-run");
    thread.get().submittedCallbacks.offer(() -> inCallback.set(MDC.get(ACTOR_PROP_NAME)));

    // then
    await().until(() -> !"not-run".equals(inCallback.get()));
    assertThat(inActor).hasValue("leaky-actor");
    assertThat(inCallback.get()).isNull();
  }

  private static final class NamedActor extends Actor {
    private NamedActor(final String name) {
      super(name);
    }
  }
}
