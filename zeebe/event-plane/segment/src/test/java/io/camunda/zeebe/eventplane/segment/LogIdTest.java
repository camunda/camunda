/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.eventplane.segment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.eventplane.segment.InvalidSegmentException.InvalidMetadata;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

final class LogIdTest {

  @Test
  void shouldReadTheClusterTheTenantAndThePartition() {
    // when
    final var log = LogId.parse("my cluster/default/3");

    // then
    assertThat(log).isEqualTo(new LogId("my cluster", "default", 3));
    assertThat(log.cluster()).isEqualTo("my cluster");
    assertThat(log.tenant()).isEqualTo("default");
    assertThat(log.partition()).isEqualTo(3);
  }

  @Test
  void shouldWriteTheTextThatItReads() {
    // given
    final var log = new LogId("prod-eu", "größe", 12);

    // when, then
    assertThat(log).hasToString("prod-eu/größe/12");
    assertThat(LogId.parse(log.toString())).isEqualTo(log);
  }

  @Test
  void shouldRejectWhatIsNoLog() {
    // given
    final var texts =
        List.of(
            "",
            "c",
            "c/default",
            "c/default/",
            "c/default/0",
            "c/default/-1",
            "c/default/p",
            "/default/1",
            "c//1",
            " /default/1",
            "c/ /1",
            "c/default/1/2",
            "c/default/99999999999");

    for (final var text : texts) {
      // when, then
      assertThatThrownBy(() -> LogId.parse(text))
          .as("the text '%s'", text)
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage(
              "Expected a log as {cluster}/{tenant}/{partition}, with a partition of 1 or more, but got '%s'"
                  .formatted(text))
          .isInstanceOfSatisfying(
              InvalidLogIdException.class,
              e -> {
                assertThat(e.expected())
                    .isEqualTo(
                        "a log as {cluster}/{tenant}/{partition}, with a partition of 1 or more");
                assertThat(e.text()).isEqualTo(text);
              });
    }
  }

  @Test
  void shouldRejectAClusterATenantOrAPartitionNoLogHas() {
    // when, then
    assertThatThrownBy(() -> new LogId(" ", "default", 1))
        .isInstanceOf(InvalidMetadata.class)
        .hasMessage("Expected a cluster id, but got ' '");
    assertThatThrownBy(() -> new LogId("c", "", 1))
        .isInstanceOf(InvalidMetadata.class)
        .hasMessage("Expected a tenant id, but got ''");
    assertThatThrownBy(() -> new LogId("c", "default", 0))
        .isInstanceOf(InvalidMetadata.class)
        .hasMessage("Expected a partition of at least 1, but got 0");
    assertThatThrownBy(() -> new LogId("c", "default", -1))
        .isInstanceOf(InvalidMetadata.class)
        .hasMessage("Expected a partition of at least 1, but got -1");
  }

  @Test
  void shouldRejectIdsOfMoreThanWhatTheMetadataCanHold() {
    // given
    final var max = "x".repeat(0xFFFF);
    final var tooLong = "x".repeat(0x10000);

    // when, then
    assertThat(new LogId(max, max, 1)).isNotNull();
    assertThatThrownBy(() -> new LogId(tooLong, "default", 1))
        .isInstanceOf(InvalidMetadata.class)
        .hasMessage("Expected a cluster id of at most 65535 UTF-8 bytes, but got 65536");
    assertThatThrownBy(() -> new LogId("c", tooLong, 1))
        .isInstanceOf(InvalidMetadata.class)
        .hasMessage("Expected a tenant id of at most 65535 UTF-8 bytes, but got 65536");
  }

  @Test
  void shouldEncodeAsTheSegmentFormatSays() {
    // when
    final var encoded = new LogId("c", "ü", 3).encode();

    // then: the cluster and the tenant, each as a length and UTF-8, then the partition
    assertThat(encoded)
        .isEqualTo(new byte[] {1, 0, 'c', 2, 0, (byte) 0xC3, (byte) 0xBC, 3, 0, 0, 0});
  }

  @Test
  void shouldDecodeWhatItEncoded() {
    // given
    final var log = new LogId("prod/eu", "größe", 12);

    // when
    final var decoded = LogId.decode(little(log.encode()));

    // then
    assertThat(decoded).isEqualTo(log);
  }

  @Test
  void shouldRejectEncodedBytesThatEndEarlyOrLate() {
    // given
    final var encoded = new LogId("c", "default", 3).encode();
    final var early = little(Arrays.copyOf(encoded, encoded.length - 1));
    final var late = little(Arrays.copyOf(encoded, encoded.length + 1));

    // when, then
    assertThatThrownBy(() -> LogId.decode(early))
        .isInstanceOf(InvalidMetadata.class)
        .hasMessage(
            "Expected a log id with a cluster id, a tenant id and a partition, but the metadata entry ends after %d bytes"
                .formatted(encoded.length - 1));
    assertThatThrownBy(() -> LogId.decode(late))
        .isInstanceOf(InvalidMetadata.class)
        .hasMessage(
            "Expected a log id of %d bytes, but the metadata entry has %d"
                .formatted(encoded.length, encoded.length + 1));
  }

  /** The metadata's entries are little-endian, as {@code Metadata.read} slices them. */
  private static ByteBuffer little(final byte[] bytes) {
    return ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
  }
}
