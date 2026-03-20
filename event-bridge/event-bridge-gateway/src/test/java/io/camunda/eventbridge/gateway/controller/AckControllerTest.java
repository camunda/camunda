/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.gateway.controller;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.camunda.eventbridge.broker.actor.CoordinatorActor;
import io.camunda.eventbridge.broker.actor.CoordinatorActor.AckResult;
import io.camunda.eventbridge.broker.coordinator.ConsumerGroupRegistry.AckStatus;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Unit tests for {@link AckController}.
 *
 * <p>Uses {@link MockMvcBuilders#standaloneSetup} with a mocked {@link CoordinatorActor} to
 * exercise the HTTP layer in isolation. Each nested class covers one behaviour of the ack endpoint.
 */
class AckControllerTest {

  private CoordinatorActor coordinatorActor;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    coordinatorActor = mock(CoordinatorActor.class);
    final var controller = new AckController(coordinatorActor);
    mockMvc =
        MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  // -------------------------------------------------------------------------
  // Happy path — coordinator returns OK

  @Nested
  class HappyPath {

    @Test
    void shouldReturn200WithOkStatusForSuccessfulAck() throws Exception {
      // given
      when(coordinatorActor.ack(
              eq("grp1"), eq("consumer-1"), eq(3L), eq(List.of(1)), eq(List.of(2))))
          .thenReturn(CompletableActorFuture.completed(new AckResult(AckStatus.OK)));

      // when / then
      mockMvc
          .perform(
              post("/v1/consumers/grp1/consumer-1/ack")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"epoch\":3,\"revoked\":[1],\"assigned\":[2]}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.status").value("OK"));
    }

    @Test
    void shouldReturn200WithEmptyListsDefaultedWhenRevokesAndAssignsAreAbsent() throws Exception {
      // given — client sends only epoch; revoked/assigned absent → defaulted to []
      when(coordinatorActor.ack(eq("grp1"), eq("consumer-1"), eq(1L), eq(List.of()), eq(List.of())))
          .thenReturn(CompletableActorFuture.completed(new AckResult(AckStatus.OK)));

      // when / then
      mockMvc
          .perform(
              post("/v1/consumers/grp1/consumer-1/ack")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"epoch\":1}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.status").value("OK"));
    }
  }

  // -------------------------------------------------------------------------
  // Stale epoch — coordinator returns EPOCH_MISMATCH, surfaced in response body

  @Nested
  class StaleEpoch {

    @Test
    void shouldReturn200WithEpochMismatchStatusForStaleEpoch() throws Exception {
      // given — stale ACK: coordinator returns EPOCH_MISMATCH
      when(coordinatorActor.ack(anyString(), anyString(), anyLong(), anyList(), anyList()))
          .thenReturn(CompletableActorFuture.completed(new AckResult(AckStatus.EPOCH_MISMATCH)));

      // when / then — HTTP 200 with EPOCH_MISMATCH status in body
      mockMvc
          .perform(
              post("/v1/consumers/grp1/consumer-1/ack")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"epoch\":1,\"revoked\":[],\"assigned\":[]}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.status").value("EPOCH_MISMATCH"));
    }
  }

  // -------------------------------------------------------------------------
  // Consumer not found — coordinator returns CONSUMER_NOT_FOUND

  @Nested
  class ConsumerNotFound {

    @Test
    void shouldReturn200WithConsumerNotFoundStatusWhenConsumerIsNotRegistered() throws Exception {
      // given — consumer ID is unknown in this group
      when(coordinatorActor.ack(anyString(), anyString(), anyLong(), anyList(), anyList()))
          .thenReturn(
              CompletableActorFuture.completed(new AckResult(AckStatus.CONSUMER_NOT_FOUND)));

      // when / then — HTTP 200 with CONSUMER_NOT_FOUND status in body
      mockMvc
          .perform(
              post("/v1/consumers/grp1/unknown-consumer/ack")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"epoch\":1,\"revoked\":[],\"assigned\":[]}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.status").value("CONSUMER_NOT_FOUND"));
    }
  }

  // -------------------------------------------------------------------------
  // Error: coordinator unavailable

  @Nested
  class CoordinatorUnavailable {

    @Test
    void shouldReturn503WhenCoordinatorFutureCompletesExceptionally() throws Exception {
      // given
      final var future = new CompletableActorFuture<AckResult>();
      future.completeExceptionally(new RuntimeException("coordinator down"));
      when(coordinatorActor.ack(anyString(), anyString(), anyLong(), anyList(), anyList()))
          .thenReturn(future);

      // when / then
      mockMvc
          .perform(
              post("/v1/consumers/grp1/consumer-1/ack")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"epoch\":1,\"revoked\":[],\"assigned\":[]}"))
          .andExpect(status().isServiceUnavailable())
          .andExpect(jsonPath("$.error").value("COORDINATOR_UNAVAILABLE"));
    }
  }

  // -------------------------------------------------------------------------
  // Error: malformed request body

  @Nested
  class MalformedBody {

    @Test
    void shouldReturn400ForInvalidJson() throws Exception {
      // when / then
      mockMvc
          .perform(
              post("/v1/consumers/grp1/consumer-1/ack")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{bad json"))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.error").value("INVALID_REQUEST"));
    }
  }
}
