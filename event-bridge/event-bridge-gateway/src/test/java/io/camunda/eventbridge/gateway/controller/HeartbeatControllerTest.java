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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.camunda.eventbridge.broker.actor.CoordinatorActor;
import io.camunda.eventbridge.broker.actor.CoordinatorActor.HeartbeatResult;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Unit tests for {@link HeartbeatController}.
 *
 * <p>Uses {@link MockMvcBuilders#standaloneSetup} with a mocked {@link CoordinatorActor} to
 * exercise the HTTP layer in isolation. Each nested class covers one behaviour of the heartbeat
 * endpoint.
 */
class HeartbeatControllerTest {

  private CoordinatorActor coordinatorActor;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    coordinatorActor = mock(CoordinatorActor.class);
    final var controller = new HeartbeatController(coordinatorActor);
    mockMvc =
        MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  // -------------------------------------------------------------------------
  // Happy path — delta response (epoch matches coordinator)

  @Nested
  class DeltaResponse {

    @Test
    void shouldReturn200WithRevokeAndAssignForMatchingEpoch() throws Exception {
      // given
      final long epoch = 3L;
      final var result = new HeartbeatResult(epoch, List.of(1), List.of(2), List.of());
      when(coordinatorActor.heartbeat(eq("grp1"), eq("consumer-1"), eq(2L), eq(List.of(0))))
          .thenReturn(CompletableActorFuture.completed(result));

      // when / then
      mockMvc
          .perform(
              post("/v1/consumers/grp1/consumer-1/heartbeat")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"epoch\":2,\"ownedPartitions\":[0]}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.epoch").value(3))
          .andExpect(jsonPath("$.revoke[0]").value(1))
          .andExpect(jsonPath("$.assign[0]").value(2))
          .andExpect(jsonPath("$.fullAssignment").isEmpty());
    }

    @Test
    void shouldReturn200WithEmptyListsWhenNoDeltaRequired() throws Exception {
      // given — all partitions already correct; coordinator sends empty delta
      final var result = new HeartbeatResult(5L, List.of(), List.of(), List.of());
      when(coordinatorActor.heartbeat(eq("grp1"), eq("consumer-1"), eq(5L), eq(List.of(0, 1, 2))))
          .thenReturn(CompletableActorFuture.completed(result));

      // when / then
      mockMvc
          .perform(
              post("/v1/consumers/grp1/consumer-1/heartbeat")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"epoch\":5,\"ownedPartitions\":[0,1,2]}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.epoch").value(5))
          .andExpect(jsonPath("$.revoke").isEmpty())
          .andExpect(jsonPath("$.assign").isEmpty())
          .andExpect(jsonPath("$.fullAssignment").isEmpty());
    }
  }

  // -------------------------------------------------------------------------
  // Full-assignment response (consumer epoch behind coordinator)

  @Nested
  class FullAssignmentResponse {

    @Test
    void shouldReturn200WithFullAssignmentWhenClientEpochIsStale() throws Exception {
      // given — epoch advance: coordinator is at 5, consumer last saw epoch 2
      final var result = new HeartbeatResult(5L, List.of(), List.of(), List.of(0, 1, 2, 3));
      when(coordinatorActor.heartbeat(eq("grp1"), eq("consumer-1"), eq(2L), anyList()))
          .thenReturn(CompletableActorFuture.completed(result));

      // when / then
      mockMvc
          .perform(
              post("/v1/consumers/grp1/consumer-1/heartbeat")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"epoch\":2,\"ownedPartitions\":[0,1]}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.epoch").value(5))
          .andExpect(jsonPath("$.revoke").isEmpty())
          .andExpect(jsonPath("$.assign").isEmpty())
          .andExpect(jsonPath("$.fullAssignment[0]").value(0))
          .andExpect(jsonPath("$.fullAssignment[1]").value(1))
          .andExpect(jsonPath("$.fullAssignment[2]").value(2))
          .andExpect(jsonPath("$.fullAssignment[3]").value(3));
    }
  }

  // -------------------------------------------------------------------------
  // Auto-registration: no prior subscribe required

  @Nested
  class AutoRegistration {

    @Test
    void shouldReturn200ForFirstHeartbeatFromUnknownConsumer() throws Exception {
      // given — brand-new consumer: epoch=0, no owned partitions
      final var result = new HeartbeatResult(1L, List.of(), List.of(), List.of());
      when(coordinatorActor.heartbeat(eq("new-group"), eq("new-consumer"), eq(0L), eq(List.of())))
          .thenReturn(CompletableActorFuture.completed(result));

      // when / then — must not return 404; auto-registration produces a 200
      mockMvc
          .perform(
              post("/v1/consumers/new-group/new-consumer/heartbeat")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"epoch\":0,\"ownedPartitions\":[]}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.epoch").value(1));
    }
  }

  // -------------------------------------------------------------------------
  // Default handling: absent or minimal request body

  @Nested
  class DefaultRequestBody {

    @Test
    void shouldDefaultToEpochZeroAndEmptyPartitionsWhenBodyIsAbsent() throws Exception {
      // given — request has no body at all
      final var result = new HeartbeatResult(1L, List.of(), List.of(), List.of());
      when(coordinatorActor.heartbeat(eq("grp1"), eq("consumer-1"), eq(0L), eq(List.of())))
          .thenReturn(CompletableActorFuture.completed(result));

      // when — POST with no body
      mockMvc
          .perform(post("/v1/consumers/grp1/consumer-1/heartbeat"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.epoch").value(1));

      verify(coordinatorActor).heartbeat("grp1", "consumer-1", 0L, List.of());
    }

    @Test
    void shouldDefaultToEpochZeroAndEmptyPartitionsWhenBodyIsEmpty() throws Exception {
      // given — request body is `{}` (all fields absent — Jackson supplies null)
      final var result = new HeartbeatResult(1L, List.of(), List.of(), List.of());
      when(coordinatorActor.heartbeat(eq("grp1"), eq("consumer-1"), eq(0L), eq(List.of())))
          .thenReturn(CompletableActorFuture.completed(result));

      // when — POST with empty JSON object
      mockMvc
          .perform(
              post("/v1/consumers/grp1/consumer-1/heartbeat")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{}"))
          .andExpect(status().isOk());

      verify(coordinatorActor).heartbeat("grp1", "consumer-1", 0L, List.of());
    }
  }

  // -------------------------------------------------------------------------
  // Error: coordinator unavailable

  @Nested
  class CoordinatorUnavailable {

    @Test
    void shouldReturn503WhenCoordinatorFutureCompletesExceptionally() throws Exception {
      // given — coordinator future wraps a runtime exception
      final var future = new CompletableActorFuture<HeartbeatResult>();
      future.completeExceptionally(new RuntimeException("coordinator down"));
      when(coordinatorActor.heartbeat(anyString(), anyString(), anyLong(), anyList()))
          .thenReturn(future);

      // when / then
      mockMvc
          .perform(
              post("/v1/consumers/grp1/consumer-1/heartbeat")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"epoch\":0,\"ownedPartitions\":[]}"))
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
      // given — body is not valid JSON
      // when / then
      mockMvc
          .perform(
              post("/v1/consumers/grp1/consumer-1/heartbeat")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{bad json"))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.error").value("INVALID_REQUEST"));
    }
  }
}
