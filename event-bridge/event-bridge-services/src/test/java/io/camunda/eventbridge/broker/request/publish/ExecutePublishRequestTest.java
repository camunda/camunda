/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.broker.request.publish;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.concurrent.ThreadLocalRandom;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

final class ExecutePublishRequestTest {

  @Test
  void shouldWriteHeaderEquivalentToFullSerialization() {
    // given
    final byte[] batch = new byte[512];
    ThreadLocalRandom.current().nextBytes(batch);
    final var request = new ExecutePublishRequest();
    request.wrapValue(new UnsafeBuffer(batch), 0, batch.length);

    // when: serialize the whole request, and separately only the header
    final byte[] full = new byte[request.getLength()];
    final int fullLength = request.write(new UnsafeBuffer(full), 0);

    final int headerLength = request.getLength() - batch.length;
    final byte[] header = new byte[headerLength];
    final int writtenHeader = request.writeHeader(new UnsafeBuffer(header), 0);

    // then: header + verbatim batch bytes reproduce the full frame
    assertThat(fullLength).isEqualTo(request.getLength());
    assertThat(writtenHeader).isEqualTo(headerLength);
    assertThat(header).isEqualTo(Arrays.copyOfRange(full, 0, headerLength));
    assertThat(Arrays.copyOfRange(full, headerLength, full.length)).isEqualTo(batch);
  }

  @Test
  void shouldRoundtripHeaderPlusBatchThroughTheDecoder() {
    // given
    final byte[] batch = new byte[64];
    ThreadLocalRandom.current().nextBytes(batch);
    final var request = new ExecutePublishRequest();
    request.wrapValue(new UnsafeBuffer(batch), 0, batch.length);

    final byte[] frame = new byte[request.getLength()];
    final var frameBuffer = new UnsafeBuffer(frame);
    final int headerLength = request.writeHeader(frameBuffer, 0);
    frameBuffer.putBytes(headerLength, batch);

    // when
    final var decoded = new ExecutePublishRequest();
    decoded.wrap(frameBuffer, 0, frame.length);

    // then
    final byte[] decodedBatch = new byte[decoded.getValue().capacity()];
    decoded.getValue().getBytes(0, decodedBatch, 0, decodedBatch.length);
    assertThat(decodedBatch).isEqualTo(batch);
  }
}
