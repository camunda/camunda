/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.shared.management;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.byLessThan;

import io.camunda.zeebe.broker.StandaloneBroker;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@SpringBootTest(
    webEnvironment = WebEnvironment.RANDOM_PORT,
    classes = StandaloneBroker.class,
    properties = "zeebe.clock.controlled=true")
@ActiveProfiles("test")
final class ControlledActorClockEndpointTest {

  @Autowired private TestRestTemplate restTemplate;

  @BeforeEach
  private void resetClock() {
    restTemplate.delete("/actuator/clock/");
  }

  @Test
  void canRetrieveCurrentClock() {
    final var response =
        restTemplate.getForEntity("/actuator/clock/", ActorClockEndpoint.Response.class);
    assertThat(response.getStatusCode()).matches(HttpStatus::is2xxSuccessful);
  }

  @Test
  void canPinMutableClock() {
    // given
    final var millis = 1635672964533L;
    final var request = jsonRequest(String.format("{\"epochMilli\": %s}", millis));

    // when
    final var response =
        restTemplate.postForEntity(
            "/actuator/clock/pin", request, ActorClockEndpoint.Response.class);

    // then
    assertThat(response.getStatusCode()).matches(HttpStatus::is2xxSuccessful);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().epochMilli).isEqualTo(millis);
  }

  @Test
  void canOffsetMutableClock() {
    // given
    final var offset = 10000L;
    final var request = jsonRequest(String.format("{\"offsetMilli\": %s}", offset));

    // when
    final var response =
        restTemplate.postForEntity(
            "/actuator/clock/add", request, ActorClockEndpoint.Response.class);

    // then
    assertThat(response.getStatusCode()).matches(HttpStatus::is2xxSuccessful);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().epochMilli)
        .isCloseTo(Instant.now().toEpochMilli(), byLessThan(offset));
  }

  @Test
  void offsetClockDoesAdvance() throws InterruptedException {
    // given
    final var offset = 10000L;
    final var request = jsonRequest(String.format("{\"offsetMilli\": %s}", offset));
    final var firstResponse =
        restTemplate.postForEntity(
            "/actuator/clock/add", request, ActorClockEndpoint.Response.class);
    assertThat(firstResponse.getBody()).isNotNull();
    final var firstMillis = firstResponse.getBody().epochMilli;

    // when
    Thread.sleep(100);
    final var secondResponse =
        restTemplate.getForEntity("/actuator/clock", ActorClockEndpoint.Response.class);
    assertThat(secondResponse.getBody()).isNotNull();
    final var secondMillis = secondResponse.getBody().epochMilli;

    // then
    assertThat(firstMillis).isLessThan(secondMillis);
  }

  @Test
  void pinnedClockDoesNotAdvance() throws InterruptedException {
    // given
    final var millis = 1635672964533L;
    final var request = jsonRequest(String.format("{\"epochMilli\": %s}", millis));
    final var firstResponse =
        restTemplate.postForEntity(
            "/actuator/clock/pin", request, ActorClockEndpoint.Response.class);
    assertThat(firstResponse.getStatusCode()).matches(HttpStatus::is2xxSuccessful);
    assertThat(firstResponse.getBody()).isNotNull();
    assertThat(firstResponse.getBody().epochMilli).isEqualTo(millis);

    // when
    Thread.sleep(100);
    final var secondResponse =
        restTemplate.getForEntity("/actuator/clock", ActorClockEndpoint.Response.class);

    // then
    assertThat(secondResponse.getBody()).isNotNull();
    assertThat(secondResponse.getBody().epochMilli).isEqualTo(millis);
  }

  @Test
  void instantMatchesEpochMilli() {
    // when
    final var response =
        restTemplate.getForEntity("/actuator/clock", ActorClockEndpoint.Response.class);
    assertThat(response.getBody()).isNotNull();
    final var millis = response.getBody().epochMilli;
    final var instant = response.getBody().instant;

    // then
    assertThat(Instant.ofEpochMilli(millis)).isEqualTo(instant);
  }

  private HttpEntity<String> jsonRequest(String body) {
    final var headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    return new HttpEntity<>(body, headers);
  }
}
