/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.gateway.controller;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.camunda.eventbridge.broker.actor.CoordinatorActor;
import io.camunda.eventbridge.broker.actor.CoordinatorActor.AssignmentResult;
import io.camunda.eventbridge.broker.actor.CoordinatorActor.ConsumerNotRegisteredException;
import io.camunda.eventbridge.broker.actor.PublishActor;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Unit tests for {@link CommitController}.
 *
 * <p>Uses {@link MockMvcBuilders#standaloneSetup} with mocked dependencies to exercise the HTTP
 * layer in isolation. Each nested class covers one behaviour of the commit endpoint.
 */
class CommitControllerTest {

  private static final int PARTITION_0 = 0;
  private static final int UNREGISTERED_PARTITION = 99;
  private static final String GROUP_ID = "grp1";
  private static final String CONSUMER_ID = "consumer-1";
  private static final long POSITION = 42L;
  private static final long EPOCH = 3L;

  private CoordinatorActor coordinatorActor;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    coordinatorActor = mock(CoordinatorActor.class);
    final var publishActor = mock(PublishActor.class);
    final var controller =
        new CommitController(Map.of(PARTITION_0, publishActor), coordinatorActor);
    mockMvc =
        MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  // -------------------------------------------------------------------------
  // Happy path — partition exists, consumer registered, partition assigned

  @Nested
  class HappyPath {

    @Test
    void shouldReturn204WhenCommitSucceeds() throws Exception {
      // given
      when(coordinatorActor.getAssignment(eq(GROUP_ID), eq(CONSUMER_ID)))
          .thenReturn(
              CompletableActorFuture.completed(new AssignmentResult(List.of(PARTITION_0), EPOCH)));
      when(coordinatorActor.commitOffset(
              eq(GROUP_ID), eq(CONSUMER_ID), eq(PARTITION_0), eq(POSITION)))
          .thenReturn(CompletableActorFuture.completed(null));

      // when / then
      mockMvc
          .perform(
              post("/v1/events/" + PARTITION_0 + "/commit")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      "{\"groupId\":\""
                          + GROUP_ID
                          + "\",\"consumerId\":\""
                          + CONSUMER_ID
                          + "\",\"position\":"
                          + POSITION
                          + "}"))
          .andExpect(status().isNoContent());
    }
  }

  // -------------------------------------------------------------------------
  // Unknown partition — not present in publishActors map

  @Nested
  class UnknownPartition {

    @Test
    void shouldReturn404WhenPartitionDoesNotExist() throws Exception {
      // when / then — partition 99 is not registered
      mockMvc
          .perform(
              post("/v1/events/" + UNREGISTERED_PARTITION + "/commit")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      "{\"groupId\":\""
                          + GROUP_ID
                          + "\",\"consumerId\":\""
                          + CONSUMER_ID
                          + "\",\"position\":"
                          + POSITION
                          + "}"))
          .andExpect(status().isNotFound())
          .andExpect(jsonPath("$.error").value("PARTITION_NOT_FOUND"));
    }
  }

  // -------------------------------------------------------------------------
  // Consumer not registered — getAssignment throws ConsumerNotRegisteredException

  @Nested
  class ConsumerNotRegistered {

    @Test
    void shouldReturn400WhenConsumerIsNotRegistered() throws Exception {
      // given — consumer is unknown in this group
      final var future = new CompletableActorFuture<AssignmentResult>();
      future.completeExceptionally(new ConsumerNotRegisteredException(GROUP_ID, CONSUMER_ID));
      when(coordinatorActor.getAssignment(eq(GROUP_ID), eq(CONSUMER_ID))).thenReturn(future);

      // when / then
      mockMvc
          .perform(
              post("/v1/events/" + PARTITION_0 + "/commit")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      "{\"groupId\":\""
                          + GROUP_ID
                          + "\",\"consumerId\":\""
                          + CONSUMER_ID
                          + "\",\"position\":"
                          + POSITION
                          + "}"))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.error").value("CONSUMER_NOT_REGISTERED"));
    }
  }

  // -------------------------------------------------------------------------
  // Partition not assigned — consumer registered but does not own the partition

  @Nested
  class PartitionNotAssigned {

    @Test
    void shouldReturn403WhenPartitionIsNotAssignedToConsumer() throws Exception {
      // given — consumer is registered but owns no partitions
      when(coordinatorActor.getAssignment(eq(GROUP_ID), eq(CONSUMER_ID)))
          .thenReturn(CompletableActorFuture.completed(new AssignmentResult(List.of(), EPOCH)));

      // when / then
      mockMvc
          .perform(
              post("/v1/events/" + PARTITION_0 + "/commit")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      "{\"groupId\":\""
                          + GROUP_ID
                          + "\",\"consumerId\":\""
                          + CONSUMER_ID
                          + "\",\"position\":"
                          + POSITION
                          + "}"))
          .andExpect(status().isForbidden())
          .andExpect(jsonPath("$.error").value("PARTITION_NOT_ASSIGNED"));
    }
  }

  // -------------------------------------------------------------------------
  // Coordinator unavailable — getAssignment completes exceptionally (non-consumer error)

  @Nested
  class CoordinatorUnavailableDuringAssignmentLookup {

    @Test
    void shouldReturn503WhenGetAssignmentFailsWithUnexpectedException() throws Exception {
      // given — coordinator future fails with a generic error
      final var future = new CompletableActorFuture<AssignmentResult>();
      future.completeExceptionally(new RuntimeException("coordinator down"));
      when(coordinatorActor.getAssignment(anyString(), anyString())).thenReturn(future);

      // when / then
      mockMvc
          .perform(
              post("/v1/events/" + PARTITION_0 + "/commit")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      "{\"groupId\":\""
                          + GROUP_ID
                          + "\",\"consumerId\":\""
                          + CONSUMER_ID
                          + "\",\"position\":"
                          + POSITION
                          + "}"))
          .andExpect(status().isServiceUnavailable())
          .andExpect(jsonPath("$.error").value("COORDINATOR_UNAVAILABLE"));
    }
  }

  // -------------------------------------------------------------------------
  // Coordinator unavailable — commitOffset completes exceptionally

  @Nested
  class CoordinatorUnavailableDuringCommit {

    @Test
    void shouldReturn503WhenCommitOffsetFails() throws Exception {
      // given — getAssignment succeeds but commitOffset fails
      when(coordinatorActor.getAssignment(eq(GROUP_ID), eq(CONSUMER_ID)))
          .thenReturn(
              CompletableActorFuture.completed(new AssignmentResult(List.of(PARTITION_0), EPOCH)));
      final var future = new CompletableActorFuture<Void>();
      future.completeExceptionally(new RuntimeException("coordinator down"));
      when(coordinatorActor.commitOffset(anyString(), anyString(), anyInt(), anyLong()))
          .thenReturn(future);

      // when / then
      mockMvc
          .perform(
              post("/v1/events/" + PARTITION_0 + "/commit")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      "{\"groupId\":\""
                          + GROUP_ID
                          + "\",\"consumerId\":\""
                          + CONSUMER_ID
                          + "\",\"position\":"
                          + POSITION
                          + "}"))
          .andExpect(status().isServiceUnavailable())
          .andExpect(jsonPath("$.error").value("COORDINATOR_UNAVAILABLE"));
    }
  }

  // -------------------------------------------------------------------------
  // Race: consumer deregisters between getAssignment and commitOffset

  @Nested
  class ConsumerDeregisteredDuringCommit {

    @Test
    void shouldReturn400WhenConsumerDeregistersBeforeCommitIsProcessed() throws Exception {
      // given — getAssignment succeeds, but commitOffset sees the consumer as gone
      when(coordinatorActor.getAssignment(eq(GROUP_ID), eq(CONSUMER_ID)))
          .thenReturn(
              CompletableActorFuture.completed(new AssignmentResult(List.of(PARTITION_0), EPOCH)));
      final var future = new CompletableActorFuture<Void>();
      future.completeExceptionally(new ConsumerNotRegisteredException(GROUP_ID, CONSUMER_ID));
      when(coordinatorActor.commitOffset(anyString(), anyString(), anyInt(), anyLong()))
          .thenReturn(future);

      // when / then
      mockMvc
          .perform(
              post("/v1/events/" + PARTITION_0 + "/commit")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      "{\"groupId\":\""
                          + GROUP_ID
                          + "\",\"consumerId\":\""
                          + CONSUMER_ID
                          + "\",\"position\":"
                          + POSITION
                          + "}"))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.error").value("CONSUMER_NOT_REGISTERED"));
    }
  }

  // -------------------------------------------------------------------------
  // Error: malformed request body

  @Nested
  class MalformedBody {

    @Test
    void shouldReturn400ForInvalidJson() throws Exception {
      // when / then — body is not valid JSON
      mockMvc
          .perform(
              post("/v1/events/" + PARTITION_0 + "/commit")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{bad json"))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.error").value("INVALID_REQUEST"));
    }
  }
}
