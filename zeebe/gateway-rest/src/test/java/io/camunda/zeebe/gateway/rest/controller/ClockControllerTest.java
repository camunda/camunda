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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.camunda.gateway.protocol.model.CamundaProblemDetail;
import io.camunda.gateway.protocol.model.ClockAddRequest;
import io.camunda.gateway.protocol.model.ClockPinRequest;
import io.camunda.security.api.context.CamundaAuthenticationProvider;
import io.camunda.service.ClockServices;
import io.camunda.service.exception.ServiceException;
import io.camunda.service.exception.ServiceException.Status;
import io.camunda.service.registry.ServiceRegistry;
import io.camunda.zeebe.gateway.rest.RestControllerTest;
import io.camunda.zeebe.protocol.impl.record.value.clock.ClockRecord;
import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;

@WebMvcTest(ClockController.class)
public class ClockControllerTest extends RestControllerTest {

  private static final String CLOCK_URL = "/v2/clock";
  private static final String ADD_CLOCK_URL = CLOCK_URL.concat("/add");
  private static final String RESET_CLOCK_URL = CLOCK_URL.concat("/reset");

  @MockitoBean private ClockServices clockServices;
  @MockitoBean private CamundaAuthenticationProvider authenticationProvider;
  @MockitoBean private ServiceRegistry serviceRegistry;

  @BeforeEach
  void setup() {
    when(serviceRegistry.clockServices(any())).thenReturn(clockServices);
    when(authenticationProvider.getCamundaAuthentication())
        .thenReturn(AUTHENTICATION_WITH_DEFAULT_TENANT);
  }

  @Test
  void pinClockShouldReturnNoContent() {
    // given
    final long timestamp = 2693098555055L;
    final var request = ClockPinRequest.Builder.create().timestamp(timestamp).build();
    final var clockRecord = new ClockRecord().pinAt(timestamp);

    when(clockServices.pinClock(eq(timestamp), any()))
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

    verify(clockServices).pinClock(eq(timestamp), any());
  }

  static Stream<Arguments> invalidClockPinRequests() {
    return Stream.of(
        of(Map.of(), "No timestamp provided."),
        of(
            ClockPinRequest.Builder.create().timestamp(-1L).build(),
            "The value for timestamp is '-1' but must be not negative."));
  }

  @ParameterizedTest
  @MethodSource("invalidClockPinRequests")
  public void pinClockShouldReturnBadRequestIfInvalidClockPinRequestProvided(
      final Object invalidRequest, final String expectedError) {
    // given
    final var expectedBody = CamundaProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
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

  static Stream<Arguments> validClockOffsets() {
    return Stream.of(
        of("PT1H", 3_600_000L),
        of("-PT30M", -1_800_000L),
        of("P1D", 86_400_000L),
        of("PT0.5S", 500L),
        of("PT0S", 0L));
  }

  @ParameterizedTest
  @MethodSource("validClockOffsets")
  void addClockShouldReturnNewTime(final String offset, final long expectedOffsetMillis) {
    // given
    final var request = ClockAddRequest.Builder.create().offset(offset).build();
    final var newTime = Instant.parse("2055-05-05T02:55:55.055Z");
    when(clockServices.addClock(eq(expectedOffsetMillis), any()))
        .thenReturn(
            CompletableFuture.completedFuture(new ClockRecord().setTime(newTime.toEpochMilli())));

    // when - then
    webClient
        .post()
        .uri(ADD_CLOCK_URL)
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .json("{\"timestamp\":\"2055-05-05T02:55:55.055Z\"}", JsonCompareMode.STRICT);

    verify(clockServices).addClock(eq(expectedOffsetMillis), any());
  }

  static Stream<Arguments> invalidClockAddRequests() {
    return Stream.of(
        of(Map.of(), "No offset provided."),
        of(Map.of("offset", ""), "No offset provided."),
        of(
            Map.of("offset", "1h"),
            "The provided offset '1h' cannot be parsed as a duration according to ISO 8601 (e.g. PT1M, PT1H)."),
        of(
            Map.of("offset", "P1Y"),
            "The provided offset 'P1Y' cannot be parsed as a duration according to ISO 8601 (e.g. PT1M, PT1H)."),
        of(
            Map.of("offset", "PT9223372036854776S"),
            "The value for offset is 'PT9223372036854776S' but must be small enough to fit into a 64-bit number of milliseconds."));
  }

  @ParameterizedTest
  @MethodSource("invalidClockAddRequests")
  void addClockShouldReturnBadRequestIfInvalidClockAddRequestProvided(
      final Object invalidRequest, final String expectedError) {
    // given
    final var expectedBody = CamundaProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
    expectedBody.setTitle(INVALID_ARGUMENT.name());
    expectedBody.setInstance(URI.create(ADD_CLOCK_URL));
    expectedBody.setDetail(expectedError);

    // when - then
    webClient
        .post()
        .uri(ADD_CLOCK_URL)
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(invalidRequest)
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectBody(ProblemDetail.class)
        .isEqualTo(expectedBody);

    verifyNoInteractions(clockServices);
  }

  @Test
  void addClockShouldReturnBadRequestWhenRejected() {
    // given
    final var request = ClockAddRequest.Builder.create().offset("-PT1H").build();
    final var rejectionReason =
        "Expected the new clock time to be not negative, but adding -3600000 ms to the current"
            + " time 1000 makes it -3599000";
    when(clockServices.addClock(eq(-3_600_000L), any()))
        .thenReturn(
            CompletableFuture.failedFuture(
                new ServiceException(rejectionReason, Status.INVALID_ARGUMENT)));

    // when - then
    webClient
        .post()
        .uri(ADD_CLOCK_URL)
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectBody()
        .jsonPath("$.title")
        .isEqualTo(INVALID_ARGUMENT.name())
        .jsonPath("$.detail")
        .isEqualTo(rejectionReason);
  }

  @Test
  void addClockShouldReturnForbiddenWhenUnauthorized() {
    // given
    final var request = ClockAddRequest.Builder.create().offset("PT1H").build();
    when(clockServices.addClock(eq(3_600_000L), any()))
        .thenReturn(
            CompletableFuture.failedFuture(
                new ServiceException(
                    "Insufficient permissions to perform operation 'UPDATE' on resource 'SYSTEM'",
                    Status.FORBIDDEN)));

    // when - then
    webClient
        .post()
        .uri(ADD_CLOCK_URL)
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isForbidden();
  }

  @Test
  void restClockShouldReturnNoContent() {
    // given
    when(clockServices.resetClock(any()))
        .thenReturn(CompletableFuture.completedFuture(new ClockRecord()));

    // when - then
    webClient
        .post()
        .uri(RESET_CLOCK_URL)
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isNoContent();

    verify(clockServices).resetClock(any());
  }

  @Test
  void getClockShouldReturnCurrentTime() {
    // given
    final var time = Instant.parse("2055-05-05T02:55:55.055Z");
    when(clockServices.getClock(any()))
        .thenReturn(
            CompletableFuture.completedFuture(new ClockRecord().setTime(time.toEpochMilli())));

    // when - then
    webClient
        .get()
        .uri(CLOCK_URL)
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .json("{\"timestamp\":\"2055-05-05T02:55:55.055Z\"}", JsonCompareMode.STRICT);

    verify(clockServices).getClock(any());
  }

  @Test
  void getClockShouldReturnForbiddenWhenUnauthorized() {
    // given
    when(clockServices.getClock(any()))
        .thenReturn(
            CompletableFuture.failedFuture(
                new ServiceException(
                    "Insufficient permissions to perform operation 'READ' on resource 'SYSTEM'",
                    Status.FORBIDDEN)));

    // when - then
    webClient
        .get()
        .uri(CLOCK_URL)
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isForbidden();
  }
}
