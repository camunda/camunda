/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import static io.camunda.zeebe.protocol.record.RejectionType.INVALID_ARGUMENT;
import static org.junit.jupiter.params.provider.Arguments.of;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.search.security.auth.Authentication;
import io.camunda.service.ClockServices;
import io.camunda.zeebe.gateway.protocol.rest.ClockPinRequest;
import io.camunda.zeebe.gateway.rest.RestControllerTest;
import io.camunda.zeebe.protocol.impl.record.value.clock.ClockRecord;
import java.net.URI;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;

@WebMvcTest(ClockController.class)
public class ClockControllerTest extends RestControllerTest {

  private static final String CLOCK_URL = "/v2/clock";
  private static final String RESET_CLOCK_URL = CLOCK_URL.concat("/reset");

  @MockBean private ClockServices clockServices;

  @BeforeEach
  void setup() {
    when(clockServices.withAuthentication(any(Authentication.class))).thenReturn(clockServices);
  }

  @Test
  void pinClockShouldReturnNoContent() {
    // given
    final long timestamp = 2693098555055L;
    final var request = new ClockPinRequest().timestamp(timestamp);
    final var clockRecord = new ClockRecord().pinAt(timestamp);

    when(clockServices.pinClock(timestamp))
        .thenReturn(CompletableFuture.completedFuture(clockRecord));

    // when - then
    webClient
        .put()
        .uri(CLOCK_URL)
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isNoContent();

    verify(clockServices).pinClock(timestamp);
  }

  static Stream<Arguments> invalidClockPinRequests() {
    return Stream.of(
        of(new ClockPinRequest(), "No timestamp provided."),
        of(
            new ClockPinRequest().timestamp(-1L),
            "The value for timestamp is '-1' but must be not negative."));
  }

  @ParameterizedTest
  @MethodSource("invalidClockPinRequests")
  public void pinClockShouldReturnBadRequestIfInvalidClockPinRequestProvided(
      final ClockPinRequest invalidRequest, final String expectedError) {
    // given
    final var expectedBody = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
    expectedBody.setTitle(INVALID_ARGUMENT.name());
    expectedBody.setInstance(URI.create(CLOCK_URL));
    expectedBody.setDetail(expectedError);

    // when - then
    webClient
        .put()
        .uri(CLOCK_URL)
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(invalidRequest)
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectBody(ProblemDetail.class)
        .isEqualTo(expectedBody);
  }

  @Test
  void restClockShouldReturnNoContent() {
    // given
    when(clockServices.resetClock())
        .thenReturn(CompletableFuture.completedFuture(new ClockRecord()));

    // when - then
    webClient
        .post()
        .uri(RESET_CLOCK_URL)
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isNoContent();

    verify(clockServices).resetClock();
  }
}
