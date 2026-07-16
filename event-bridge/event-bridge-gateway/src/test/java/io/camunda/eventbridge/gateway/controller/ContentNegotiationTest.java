/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.gateway.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.google.protobuf.Message;
import com.google.protobuf.util.JsonFormat;
import io.camunda.eventbridge.api.proto.CommitRequest;
import io.camunda.eventbridge.api.proto.CommitResponse;
import io.camunda.eventbridge.api.proto.ConsumerHeartbeatRequest;
import io.camunda.eventbridge.api.proto.ConsumerHeartbeatResponse;
import io.camunda.eventbridge.api.proto.IntList;
import io.camunda.eventbridge.api.proto.JoinRequest;
import io.camunda.eventbridge.api.proto.JoinResponse;
import io.camunda.eventbridge.api.proto.TopicCreateRequest;
import io.camunda.eventbridge.api.proto.TopicListResponse;
import io.camunda.eventbridge.protocol.request.coordination.CommitOffsetResponse;
import io.camunda.eventbridge.protocol.request.coordination.CoordinationErrorCode;
import io.camunda.eventbridge.protocol.request.coordination.HeartbeatResponse;
import io.camunda.eventbridge.protocol.request.coordination.JoinGroupResponse;
import io.camunda.eventbridge.protocol.request.coordination.LeaveGroupResponse;
import io.camunda.eventbridge.protocol.request.coordination.ListTopicsResponse;
import io.camunda.eventbridge.protocol.topic.TopicPartition;
import io.camunda.eventbridge.service.CoordinatorService;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.http.converter.ByteArrayHttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.protobuf.ProtobufHttpMessageConverter;
import org.springframework.http.converter.protobuf.ProtobufJsonFormatHttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Verifies content negotiation on the coordination and topic controllers: every negotiated endpoint
 * behaves equivalently whether the client speaks {@code application/json} or {@code
 * application/x-protobuf}. Slice test via {@code standaloneSetup} with the two protobuf converters
 * registered, mocking the domain service layer.
 */
final class ContentNegotiationTest {

  private static final MediaType PROTOBUF = MediaType.parseMediaType("application/x-protobuf");

  private CoordinatorService coordinatorService;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    coordinatorService = Mockito.mock(CoordinatorService.class);
    mockMvc =
        MockMvcBuilders.standaloneSetup(
                new ConsumerGroupController(coordinatorService),
                new TopicController(coordinatorService))
            .setControllerAdvice(new GlobalExceptionHandler())
            .setMessageConverters(
                new ByteArrayHttpMessageConverter(),
                new StringHttpMessageConverter(),
                new ProtobufHttpMessageConverter(),
                new ProtobufJsonFormatHttpMessageConverter())
            .build();
  }

  // -------------------------------------------------------------------------
  // Join

  @Test
  void shouldJoinViaJson() throws Exception {
    givenJoinResponse();
    final var body = JoinRequest.newBuilder().addTopics("t1").setInstanceId("c1").build();

    mockMvc
        .perform(
            asyncPost(
                "/v1/groups/g1/members",
                MediaType.APPLICATION_JSON,
                MediaType.APPLICATION_JSON,
                body))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.memberId").value("m1"))
        .andExpect(jsonPath("$.memberEpoch").value(7));
  }

  @Test
  void shouldJoinViaProtobuf() throws Exception {
    givenJoinResponse();
    final var body = JoinRequest.newBuilder().addTopics("t1").setInstanceId("c1").build();

    final MvcResult result =
        mockMvc
            .perform(asyncPost("/v1/groups/g1/members", PROTOBUF, PROTOBUF, body))
            .andExpect(status().isCreated())
            .andReturn();

    final var response = JoinResponse.parseFrom(result.getResponse().getContentAsByteArray());
    assertThat(response.getMemberId()).isEqualTo("m1");
    assertThat(response.getMemberEpoch()).isEqualTo(7L);
  }

  private void givenJoinResponse() {
    final var res =
        new JoinGroupResponse()
            .setErrorCode(CoordinationErrorCode.NONE)
            .setMemberId("m1")
            .setMemberEpoch(7L);
    when(coordinatorService.joinGroup(any())).thenReturn(CompletableFuture.completedFuture(res));
  }

  // -------------------------------------------------------------------------
  // Heartbeat

  @Test
  void shouldHeartbeatViaJson() throws Exception {
    givenHeartbeatResponse();
    final var body =
        ConsumerHeartbeatRequest.newBuilder()
            .setEpoch(7L)
            .putOwnedPartitions("t1", IntList.newBuilder().addValues(0).build())
            .build();

    mockMvc
        .perform(
            asyncPost(
                "/v1/groups/g1/members/m1/heartbeat",
                MediaType.APPLICATION_JSON,
                MediaType.APPLICATION_JSON,
                body))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.memberEpoch").value(8))
        .andExpect(jsonPath("$.assignment.t1.values[0]").value(0));
  }

  @Test
  void shouldHeartbeatViaProtobuf() throws Exception {
    givenHeartbeatResponse();
    final var body =
        ConsumerHeartbeatRequest.newBuilder()
            .setEpoch(7L)
            .putOwnedPartitions("t1", IntList.newBuilder().addValues(0).build())
            .build();

    final MvcResult result =
        mockMvc
            .perform(asyncPost("/v1/groups/g1/members/m1/heartbeat", PROTOBUF, PROTOBUF, body))
            .andExpect(status().isOk())
            .andReturn();

    final var response =
        ConsumerHeartbeatResponse.parseFrom(result.getResponse().getContentAsByteArray());
    assertThat(response.getMemberEpoch()).isEqualTo(8L);
    assertThat(response.getAssignmentMap().get("t1").getValuesList()).containsExactly(0);
  }

  private void givenHeartbeatResponse() {
    final var res =
        new HeartbeatResponse()
            .setErrorCode(CoordinationErrorCode.NONE)
            .setMemberId("m1")
            .setMemberEpoch(8L)
            .setAssignment(List.of(new TopicPartition("t1", 0)))
            .setAssignmentEpoch(8L)
            .setCommittedOffsets(Map.of());
    when(coordinatorService.heartbeat(any())).thenReturn(CompletableFuture.completedFuture(res));
  }

  // -------------------------------------------------------------------------
  // Commit

  @Test
  void shouldCommitViaJson() throws Exception {
    givenCommitResponse();
    final var body =
        CommitRequest.newBuilder()
            .setTopic("t1")
            .setPartitionId(0)
            .setPosition(42L)
            .setMemberEpoch(7L)
            .build();

    mockMvc
        .perform(
            asyncPost(
                "/v1/groups/g1/members/m1/offsets",
                MediaType.APPLICATION_JSON,
                MediaType.APPLICATION_JSON,
                body))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.committedPosition").value(42));
  }

  @Test
  void shouldCommitViaProtobuf() throws Exception {
    givenCommitResponse();
    final var body =
        CommitRequest.newBuilder()
            .setTopic("t1")
            .setPartitionId(0)
            .setPosition(42L)
            .setMemberEpoch(7L)
            .build();

    final MvcResult result =
        mockMvc
            .perform(asyncPost("/v1/groups/g1/members/m1/offsets", PROTOBUF, PROTOBUF, body))
            .andExpect(status().isOk())
            .andReturn();

    final var response = CommitResponse.parseFrom(result.getResponse().getContentAsByteArray());
    assertThat(response.getCommittedPosition()).isEqualTo(42L);
  }

  private void givenCommitResponse() {
    final var res =
        new CommitOffsetResponse()
            .setErrorCode(CoordinationErrorCode.NONE)
            .setCommittedPosition(42L);
    when(coordinatorService.commit(any())).thenReturn(CompletableFuture.completedFuture(res));
  }

  // -------------------------------------------------------------------------
  // Leave

  @Test
  void shouldLeaveViaDelete() throws Exception {
    // given
    final var res = new LeaveGroupResponse().setErrorCode(CoordinationErrorCode.NONE);
    when(coordinatorService.leaveGroup(any())).thenReturn(CompletableFuture.completedFuture(res));

    // when / then: a leave is a DELETE carrying the epoch as a query parameter, replying 204 with
    // no body.
    final MvcResult result =
        mockMvc
            .perform(dispatchAsync(delete("/v1/groups/g1/members/m1").param("epoch", "7")))
            .andExpect(status().isNoContent())
            .andReturn();

    assertThat(result.getResponse().getContentAsByteArray()).isEmpty();
  }

  // -------------------------------------------------------------------------
  // Create topic

  @Test
  void shouldCreateTopicViaJson() throws Exception {
    when(coordinatorService.createTopic(any())).thenReturn(CompletableFuture.completedFuture(null));
    final var body =
        TopicCreateRequest.newBuilder()
            .setName("t1")
            .setPartitionCount(3)
            .setReplicationFactor(1)
            .build();

    mockMvc
        .perform(
            asyncPost("/v1/topics", MediaType.APPLICATION_JSON, MediaType.APPLICATION_JSON, body))
        .andExpect(status().isCreated());
  }

  @Test
  void shouldCreateTopicViaProtobuf() throws Exception {
    when(coordinatorService.createTopic(any())).thenReturn(CompletableFuture.completedFuture(null));
    final var body =
        TopicCreateRequest.newBuilder()
            .setName("t1")
            .setPartitionCount(3)
            .setReplicationFactor(1)
            .build();

    mockMvc
        .perform(asyncPost("/v1/topics", PROTOBUF, PROTOBUF, body))
        .andExpect(status().isCreated());
  }

  // -------------------------------------------------------------------------
  // List topics

  @Test
  void shouldListTopicsViaJson() throws Exception {
    givenListTopicsResponse();

    mockMvc
        .perform(asyncGet("/v1/topics", MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.topics[0].name").value("t1"))
        .andExpect(jsonPath("$.topics[0].partitionCount").value(3))
        .andExpect(jsonPath("$.topics[0].status").value("READY"))
        .andExpect(jsonPath("$.topics[0].cleanupPolicy").value("DELETE"));
  }

  @Test
  void shouldListTopicsViaProtobuf() throws Exception {
    givenListTopicsResponse();

    final MvcResult result =
        mockMvc.perform(asyncGet("/v1/topics", PROTOBUF)).andExpect(status().isOk()).andReturn();

    final var response = TopicListResponse.parseFrom(result.getResponse().getContentAsByteArray());
    assertThat(response.getTopicsList()).hasSize(1);
    assertThat(response.getTopics(0).getName()).isEqualTo("t1");
    assertThat(response.getTopics(0).getPartitionCount()).isEqualTo(3);
    assertThat(response.getTopics(0).getStatus()).isEqualTo("READY");
    assertThat(response.getTopics(0).getCleanupPolicy()).isEqualTo("DELETE");
  }

  // -------------------------------------------------------------------------
  // Reassign topic

  @Test
  void shouldAcceptReassignment() throws Exception {
    // given
    when(coordinatorService.reassignTopic(any()))
        .thenReturn(CompletableFuture.completedFuture(null));

    // when / then: a reassignment is provisioned asynchronously, so it is acknowledged with 202.
    mockMvc
        .perform(dispatchAsync(post("/v1/topics/t1/reassignments").param("replicationFactor", "3")))
        .andExpect(status().isAccepted());
  }

  private void givenListTopicsResponse() {
    final var res =
        new ListTopicsResponse()
            .setErrorCode(CoordinationErrorCode.NONE)
            .addTopic("t1", 3, 1, "READY", Map.of(), "DELETE");
    when(coordinatorService.listTopics()).thenReturn(CompletableFuture.completedFuture(res));
  }

  // -------------------------------------------------------------------------

  private RequestBuilder asyncPost(
      final String path, final MediaType contentType, final MediaType accept, final Message body)
      throws Exception {
    final byte[] content =
        MediaType.APPLICATION_JSON.equals(contentType)
            ? JsonFormat.printer().print(body).getBytes()
            : body.toByteArray();
    return dispatchAsync(post(path).contentType(contentType).accept(accept).content(content));
  }

  private RequestBuilder asyncGet(final String path, final MediaType accept) throws Exception {
    return dispatchAsync(get(path).accept(accept));
  }

  /**
   * Performs the initial request (starting async processing) and returns a builder that dispatches
   * the completed async result — controllers return {@link CompletableFuture}, which Spring MVC
   * handles asynchronously.
   */
  private RequestBuilder dispatchAsync(final MockHttpServletRequestBuilder builder)
      throws Exception {
    final MvcResult mvcResult = mockMvc.perform(builder).andReturn();
    return asyncDispatch(mvcResult);
  }
}
