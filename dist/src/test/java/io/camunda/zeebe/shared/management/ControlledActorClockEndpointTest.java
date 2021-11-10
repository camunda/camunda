/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.shared.management;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.shared.management.ActorClockEndpoint.Response;
import io.camunda.zeebe.util.sched.clock.ControlledActorClock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.assertj.core.api.Assertions;
import org.assertj.core.api.InstanceOfAssertFactory;
import org.assertj.core.api.ObjectAssert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

final class ControlledActorClockEndpointTest {

  private final InstanceOfAssertFactory<Response, ObjectAssert<Response>> instanceOfRecord =
      new InstanceOfAssertFactory<>(Response.class, Assertions::assertThat);

  private final ActorClockEndpoint endpoint =
      new ActorClockEndpoint(new ControlledActorClockService(new ControlledActorClock()));

  @BeforeEach
  private void resetClock() {
    endpoint.resetTime();
  }

  @Test
  void canRetrieveCurrentClock() {
    final var response = endpoint.getCurrentClock();
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  void canPinMutableClock() {
    // given
    final var millis = 1635672964533L;

    // when
    final var response = endpoint.modify("pin", millis, null);

    // then

    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(response.getBody())
        .asInstanceOf(instanceOfRecord)
        .satisfies((body) -> assertThat(body.epochMilli).isEqualTo(millis))
        .isNotNull();
  }

  @Test
  void canOffsetMutableClock() {
    // given
    final var offset = Duration.ofMinutes(10);

    // when
    final var response = endpoint.modify("add", null, offset.toMillis());

    // then

    // the minimum time we would expect the actor clock to have. It should have at least advanced
    // by the offset we have specified.
    final var offsetMinimum = Instant.now().plus(offset).truncatedTo(ChronoUnit.MILLIS);
    // the maximum time we would expect the actor clock to have. This can be flaky, but only if the
    // test thread is sleeping for more than one minute.
    final var offsetMaximum = Instant.now().plus(offset.plus(Duration.ofMinutes(1)));

    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(response.getBody())
        .isNotNull()
        .asInstanceOf(instanceOfRecord)
        .satisfies((body) -> assertThat(body.instant).isBetween(offsetMinimum, offsetMaximum));
  }

  @Test
  void offsetClockDoesAdvance() throws InterruptedException {
    // given
    final var offset = 10000L;
    final var firstResponse = endpoint.modify("add", null, offset);
    assertThat(firstResponse.getBody()).isNotNull();
    final var firstMillis = ((Response) firstResponse.getBody()).epochMilli;

    // when
    Thread.sleep(100);
    final var secondResponse = endpoint.getCurrentClock();
    assertThat(secondResponse.getBody()).isNotNull();
    final var secondMillis = secondResponse.getBody().epochMilli;

    // then
    assertThat(firstMillis).isLessThan(secondMillis);
  }

  @Test
  void pinnedClockDoesNotAdvance() throws InterruptedException {
    // given
    final var millis = 1635672964533L;
    final var firstResponse = endpoint.modify("pin", millis, null);
    assertThat(firstResponse.getStatus()).isEqualTo(200);
    assertThat(firstResponse.getBody())
        .isNotNull()
        .asInstanceOf(instanceOfRecord)
        .satisfies((body) -> assertThat(body.epochMilli).isEqualTo(millis));

    // when
    Thread.sleep(100);
    final var secondResponse = endpoint.getCurrentClock();

    // then
    assertThat(secondResponse.getBody()).isNotNull();
    assertThat(secondResponse.getBody().epochMilli).isEqualTo(millis);
  }

  @Test
  void instantMatchesEpochMilli() {
    // when
    final var response = endpoint.getCurrentClock();
    assertThat(response.getBody()).isNotNull();
    final var millis = response.getBody().epochMilli;
    final var instant = response.getBody().instant;

    // then
    assertThat(Instant.ofEpochMilli(millis)).isEqualTo(instant);
  }
}
