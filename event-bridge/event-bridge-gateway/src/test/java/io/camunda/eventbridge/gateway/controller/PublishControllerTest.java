/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.gateway.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.camunda.eventbridge.broker.actor.PublishActor;
import io.camunda.eventbridge.core.EventData;
import io.camunda.eventbridge.core.EventDataBatch;
import io.camunda.eventbridge.core.config.EventBridgeProperties;
import io.camunda.eventbridge.core.config.EventBridgeProperties.PublishProperties;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Unit tests for {@link PublishController}.
 *
 * <p>Uses {@link MockMvcBuilders#standaloneSetup} with a mocked {@link PublishActor} to exercise
 * the HTTP layer in isolation. Each nested class covers one behaviour of the publish endpoint.
 */
class PublishControllerTest {

  /** Partition registered in the controller under test. */
  private static final int PARTITION_0 = 0;

  /** Tight limits so tests can trigger violations with small payloads. */
  private static final int MAX_BATCH_SIZE = 5;

  private static final int MAX_EVENT_BYTES = 100;
  private static final int MAX_BATCH_BYTES = 1_000;

  private PublishActor publishActor;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    publishActor = mock(PublishActor.class);
    final var publishProps =
        new PublishProperties(MAX_BATCH_SIZE, MAX_EVENT_BYTES, MAX_BATCH_BYTES);
    final var props =
        new EventBridgeProperties(null, null, null, null, publishProps, null, null, null);
    final var controller = new PublishController(Map.of(PARTITION_0, publishActor), props);
    mockMvc =
        MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  // -------------------------------------------------------------------------
  // Helpers

  private static EventDataBatch smallBatch(final byte[]... bodies) {
    final var batch = EventDataBatch.create(MAX_BATCH_BYTES, MAX_EVENT_BYTES, MAX_BATCH_SIZE);
    for (final byte[] body : bodies) {
      batch.tryAdd(new EventData(body));
    }
    return batch;
  }

  // -------------------------------------------------------------------------

  @Nested
  class ValidBatch {

    @Test
    void shouldReturn200WithLogPositions() throws Exception {
      // given
      final var batch = smallBatch(new byte[] {1, 2}, new byte[] {3, 4});
      when(publishActor.publishBatch(any(EventDataBatch.class)))
          .thenReturn(CompletableActorFuture.completed(List.of(101L, 102L)));

      // when / then
      mockMvc
          .perform(
              post("/v1/events/" + PARTITION_0)
                  .contentType(MediaType.APPLICATION_OCTET_STREAM)
                  .content(batch.toBytes()))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.logPositions[0]").value(101))
          .andExpect(jsonPath("$.logPositions[1]").value(102));
    }
  }

  @Nested
  class MalformedBatch {

    @Test
    void shouldReturn400InvalidRequestForTruncatedBytes() throws Exception {
      // given — buffer shorter than the 12-byte minimum header
      final byte[] truncated = new byte[] {0, 0, 0, 4};

      // when / then
      mockMvc
          .perform(
              post("/v1/events/" + PARTITION_0)
                  .contentType(MediaType.APPLICATION_OCTET_STREAM)
                  .content(truncated))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.error").value("INVALID_REQUEST"));
    }

    @Test
    void shouldReturn400InvalidRequestWhenTotalSizeMismatch() throws Exception {
      // given — valid 12-byte buffer but total-size field says 100, not 12
      final var buf = ByteBuffer.allocate(12).order(ByteOrder.BIG_ENDIAN);
      buf.putInt(100); // total-size ≠ actual length
      buf.putInt(0); // payload-size
      buf.putInt(0); // count

      // when / then
      mockMvc
          .perform(
              post("/v1/events/" + PARTITION_0)
                  .contentType(MediaType.APPLICATION_OCTET_STREAM)
                  .content(buf.array()))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.error").value("INVALID_REQUEST"));
    }

    @Test
    void shouldReturn400InvalidRequestForMalformedFramePassingHeaderValidation() throws Exception {
      // given — valid header (total-size=17, payload-size=1, count=1) but the frame
      // declares size=999 which reads past the buffer end; caught during lazy iteration
      final var buf = ByteBuffer.allocate(17).order(ByteOrder.BIG_ENDIAN);
      buf.putInt(17); // total-size matches buffer length (passes fromBytes checks)
      buf.putInt(1); // payload-size
      buf.putInt(1); // count = 1 event
      buf.putInt(999); // frame declares 999 bytes but only 1 byte remains → IAE during iteration
      buf.put((byte) 0); // single byte of "data"

      // when / then — must be 400, not 500
      mockMvc
          .perform(
              post("/v1/events/" + PARTITION_0)
                  .contentType(MediaType.APPLICATION_OCTET_STREAM)
                  .content(buf.array()))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.error").value("INVALID_REQUEST"));
    }
  }

  @Nested
  class BatchCountLimitExceeded {

    @Test
    void shouldReturn400PayloadTooLargeWhenCountExceedsMaxBatchSize() throws Exception {
      // given — build a batch with MAX_BATCH_SIZE+1 events using generous limits, then post the
      // bytes; the controller rejects because its maxBatchSize is MAX_BATCH_SIZE
      final var overBatch =
          EventDataBatch.create(MAX_BATCH_BYTES * 10, MAX_EVENT_BYTES, MAX_BATCH_SIZE + 1);
      for (int i = 0; i <= MAX_BATCH_SIZE; i++) {
        overBatch.tryAdd(new EventData(new byte[] {(byte) i}));
      }

      // when / then
      mockMvc
          .perform(
              post("/v1/events/" + PARTITION_0)
                  .contentType(MediaType.APPLICATION_OCTET_STREAM)
                  .content(overBatch.toBytes()))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.error").value("PAYLOAD_TOO_LARGE"));
    }
  }

  @Nested
  class PerEventSizeLimitExceeded {

    @Test
    void shouldReturn400PayloadTooLargeWhenSingleEventExceedsMaxEventBytes() throws Exception {
      // given — one event with MAX_EVENT_BYTES+1 bytes; the controller's limit is MAX_EVENT_BYTES
      final int oversized = MAX_EVENT_BYTES + 1;
      final var overBatch = EventDataBatch.create(oversized * 4, oversized, MAX_BATCH_SIZE);
      overBatch.tryAdd(new EventData(new byte[oversized]));

      // when / then
      mockMvc
          .perform(
              post("/v1/events/" + PARTITION_0)
                  .contentType(MediaType.APPLICATION_OCTET_STREAM)
                  .content(overBatch.toBytes()))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.error").value("PAYLOAD_TOO_LARGE"));
    }
  }

  @Nested
  class UnknownPartition {

    @Test
    void shouldReturn400PartitionNotFoundForUnregisteredPartition() throws Exception {
      // given — valid batch but targeting partition 99 (not in the actor map)
      final var batch = smallBatch(new byte[] {1});

      // when / then
      mockMvc
          .perform(
              post("/v1/events/99")
                  .contentType(MediaType.APPLICATION_OCTET_STREAM)
                  .content(batch.toBytes()))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.error").value("PARTITION_NOT_FOUND"));
    }
  }
}
