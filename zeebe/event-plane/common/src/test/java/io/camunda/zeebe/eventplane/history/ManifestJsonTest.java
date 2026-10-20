/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.eventplane.history;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.camunda.zeebe.eventplane.history.HistoryManifest.Base;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class ManifestJsonTest {

  private static final UUID A = UUID.fromString("00000000-0000-4000-8000-000000000001");
  private static final UUID B = UUID.fromString("00000000-0000-4000-8000-000000000002");
  private static final Instant NOON = Instant.parse("2026-10-19T12:00:00Z");

  /** The manifest of the design's example. */
  private static final String DESIGN_EXAMPLE =
      """
      {
        "version": 1,
        "clusterId": "cluster-1",
        "physicalTenantId": "default",
        "historyId": "00000000-0000-4000-8000-000000000002",
        "createdAt": "2026-10-19T12:00:00Z",
        "partitions": {
          "1": {
            "sourceHistoryId": "00000000-0000-4000-8000-000000000001",
            "index": 900,
            "position": 400
          },
          "2": { "sourceHistoryId": null, "index": 0, "position": -1 }
        }
      }
      """;

  @Test
  void shouldReadTheManifestOfTheDesignsExample() throws IOException {
    // when
    final var manifest = ManifestJson.read(bytes(DESIGN_EXAMPLE));

    // then
    assertThat(manifest)
        .isEqualTo(
            new HistoryManifest(
                1,
                "cluster-1",
                "default",
                B,
                NOON,
                Map.of(1, new Base(A, 900, 400), 2, Base.NOTHING)));
  }

  @Test
  void shouldWriteCompactJsonInTheDesignsFieldOrder() {
    // given
    final var manifest =
        new HistoryManifest(
            1, "cluster-1", "default", B, NOON, Map.of(1, new Base(A, 900, 400), 2, Base.NOTHING));

    // when
    final var json = new String(ManifestJson.write(manifest), StandardCharsets.UTF_8);

    // then
    assertThat(json)
        .isEqualTo(
            "{\"version\":1,\"clusterId\":\"cluster-1\",\"physicalTenantId\":\"default\","
                + "\"historyId\":\"00000000-0000-4000-8000-000000000002\","
                + "\"createdAt\":\"2026-10-19T12:00:00Z\","
                + "\"partitions\":{"
                + "\"1\":{\"sourceHistoryId\":\"00000000-0000-4000-8000-000000000001\","
                + "\"index\":900,\"position\":400},"
                + "\"2\":{\"sourceHistoryId\":null,\"index\":0,\"position\":-1}}}");
  }

  @Test
  void shouldReadBackWhatItWrote() throws IOException {
    // given
    final var manifest =
        new HistoryManifest(
            1,
            "prod/eu \"quoted\" ü",
            "größe",
            B,
            Instant.parse("2026-10-20T12:00:00.123456789Z"),
            Map.of(
                1,
                new Base(A, Long.MAX_VALUE, Long.MAX_VALUE),
                3,
                Base.NOTHING,
                12,
                new Base(A, 1, 1)));

    // when
    final var read = ManifestJson.read(ManifestJson.write(manifest));

    // then
    assertThat(read).isEqualTo(manifest);
  }

  @Test
  void shouldWriteTheSameBytesForTheSameManifest() {
    // given the same partitions, put in another order
    final var forwards = new LinkedHashMap<Integer, Base>();
    final var backwards = new LinkedHashMap<Integer, Base>();
    for (final int partition : new int[] {1, 2, 10, 100}) {
      forwards.put(partition, Base.NOTHING);
    }
    for (final int partition : new int[] {100, 10, 2, 1}) {
      backwards.put(partition, Base.NOTHING);
    }

    // when
    final var one = ManifestJson.write(manifest(forwards));
    final var other = ManifestJson.write(manifest(backwards));

    // then partitions are in numerical order, "2" before "10"
    assertThat(other).isEqualTo(one);
    assertThat(new String(one, StandardCharsets.UTF_8))
        .containsSubsequence("\"1\":", "\"2\":", "\"10\":", "\"100\":");
  }

  @Test
  void shouldWriteTheCreationTimeAsAnIso8601Instant() {
    // when
    final var whole = new String(ManifestJson.write(manifest(Map.of(1, Base.NOTHING))));
    final var fractions =
        new String(
            ManifestJson.write(
                new HistoryManifest(
                    1,
                    "c",
                    "t",
                    A,
                    Instant.parse("2026-10-20T12:00:00.250Z"),
                    Map.of(1, Base.NOTHING))));

    // then
    assertThat(whole).contains("\"createdAt\":\"2026-10-19T12:00:00Z\"");
    assertThat(fractions).contains("\"createdAt\":\"2026-10-20T12:00:00.250Z\"");
  }

  @Test
  void shouldSkipFieldsItDoesNotKnow() throws IOException {
    // given
    final var json =
        """
        {"version":1,"extra":[1,2],"clusterId":"cluster-1","physicalTenantId":"default",
         "historyId":"00000000-0000-4000-8000-000000000001","createdAt":"2026-10-19T12:00:00Z",
         "partitions":{"1":{"index":0,"note":{"a":1},"position":-1,"sourceHistoryId":null}}}
        """;

    // when
    final var manifest = ManifestJson.read(bytes(json));

    // then
    assertThat(manifest).isEqualTo(manifest(Map.of(1, Base.NOTHING)));
  }

  @Test
  void shouldTakeAMissingSourceAsNone() throws IOException {
    // given
    final var json =
        """
        {"version":1,"clusterId":"cluster-1","physicalTenantId":"default",
         "historyId":"00000000-0000-4000-8000-000000000001","createdAt":"2026-10-19T12:00:00Z",
         "partitions":{"1":{"index":0,"position":-1}}}
        """;

    // when, then
    assertThat(ManifestJson.read(bytes(json))).isEqualTo(manifest(Map.of(1, Base.NOTHING)));
  }

  @Test
  void shouldReadUppercaseIdsAndWriteThemInLowercase() throws IOException {
    // given
    final var json =
        DESIGN_EXAMPLE.replace("0000-4000-8000-000000000002", "0000-4000-8000-00000000000A");

    // when
    final var manifest = ManifestJson.read(bytes(json));

    // then
    assertThat(manifest.historyId().toString()).isEqualTo("00000000-0000-4000-8000-00000000000a");
    assertThat(new String(ManifestJson.write(manifest), StandardCharsets.UTF_8))
        .contains("00000000-0000-4000-8000-00000000000a");
  }

  @Test
  void shouldRejectBytesThatAreNotJson() {
    // when, then
    assertThatThrownBy(() -> ManifestJson.read(bytes("{\"version\":")))
        .isInstanceOf(JsonProcessingException.class);
    assertThatThrownBy(() -> ManifestJson.read(bytes("not json")))
        .isInstanceOf(JsonProcessingException.class);
  }

  @Test
  void shouldRejectJsonThatIsNotAnObject() {
    // when, then
    assertThatThrownBy(() -> ManifestJson.read(bytes("")))
        .isInstanceOfSatisfying(
            InvalidHistoryJsonException.class, e -> assertThat(e.field()).isEqualTo("the document"))
        .hasMessage("Expected the document to be a JSON object, but got nothing");
    assertThatThrownBy(() -> ManifestJson.read(bytes("[1]")))
        .isInstanceOf(InvalidHistoryJsonException.class)
        .hasMessage("Expected the document to be a JSON object, but got [1]");
    assertThatThrownBy(() -> ManifestJson.read(bytes("null")))
        .isInstanceOf(InvalidHistoryJsonException.class);
  }

  @Test
  void shouldNameTheFieldThatIsMissingOrOfAnotherType() {
    // when, then
    assertInvalid(
        DESIGN_EXAMPLE.replace("\"clusterId\": \"cluster-1\",", ""),
        "clusterId",
        "text",
        "nothing");
    assertInvalid(DESIGN_EXAMPLE.replace("\"cluster-1\"", "7"), "clusterId", "text", "7");
    assertInvalid(
        DESIGN_EXAMPLE.replace("\"version\": 1", "\"version\": \"1\""),
        "version",
        "a 32-bit integer",
        "\"1\"");
    assertInvalid(
        DESIGN_EXAMPLE.replace("\"version\": 1", "\"version\": 1.5"),
        "version",
        "a 32-bit integer",
        "1.5");
    assertInvalid(
        DESIGN_EXAMPLE.replace("\"00000000-0000-4000-8000-000000000002\"", "\"not-a-uuid\""),
        "historyId",
        "a UUID",
        "\"not-a-uuid\"");
    assertInvalid(
        DESIGN_EXAMPLE.replace("\"2026-10-19T12:00:00Z\"", "\"yesterday\""),
        "createdAt",
        "an ISO-8601 instant",
        "yesterday");
    assertInvalid(
        DESIGN_EXAMPLE.replace("\"2026-10-19T12:00:00Z\"", "1792411200"),
        "createdAt",
        "text",
        "1792411200");
    assertInvalid(
        DESIGN_EXAMPLE.replaceAll(
            "\"partitions\": \\{[\\s\\S]*\\}\\s*\\}\\s*$", "\"partitions\": []}"),
        "partitions",
        "a JSON object",
        "[]");
  }

  @Test
  void shouldNameTheFieldOfAPartitionThatIsWrong() {
    // when, then
    assertInvalid(
        DESIGN_EXAMPLE.replace("\"index\": 900", "\"index\": \"900\""),
        "partitions.1.index",
        "a 64-bit integer",
        "\"900\"");
    assertInvalid(
        DESIGN_EXAMPLE.replace("\"position\": 400", "\"position\": 4.5"),
        "partitions.1.position",
        "a 64-bit integer",
        "4.5");
    assertInvalid(
        DESIGN_EXAMPLE.replace("\"position\": 400", "\"position\": 99999999999999999999"),
        "partitions.1.position",
        "a 64-bit integer",
        "99999999999999999999");
    assertInvalid(
        DESIGN_EXAMPLE.replace("\"00000000-0000-4000-8000-000000000001\"", "\"x\""),
        "partitions.1.sourceHistoryId",
        "a UUID",
        "\"x\"");
    assertInvalid(
        DESIGN_EXAMPLE.replace(
            "\"2\": { \"sourceHistoryId\": null, \"index\": 0, \"position\": -1 }", "\"2\": 5"),
        "partitions.2",
        "a JSON object",
        "5");
  }

  @Test
  void shouldRejectPartitionKeysThatAreNotPartitionNumbers() {
    // when, then
    for (final var key : new String[] {"0", "-1", "x", "01", "+1", "", "1.0", "99999999999"}) {
      assertInvalid(
          DESIGN_EXAMPLE.replace("\"2\":", "\"" + key + "\":").replace("\"1\":", "\"7\":"),
          "partitions." + key,
          "a partition number of 1 or more as the key",
          key);
    }
  }

  @Test
  void shouldRejectAManifestThatBreaksARuleWithTheRuleAsItsCause() {
    // when, then
    assertThatThrownBy(
            () ->
                ManifestJson.read(
                    bytes(DESIGN_EXAMPLE.replace("\"version\": 1", "\"version\": 2"))))
        .isInstanceOf(InvalidHistoryJsonException.class)
        .hasMessageContaining("Expected the manifest to be a valid history manifest")
        .hasRootCauseMessage("Expected manifest version 1, but got 2");
    assertThatThrownBy(
            () ->
                ManifestJson.read(bytes(DESIGN_EXAMPLE.replace("\"index\": 900", "\"index\": 0"))))
        .isInstanceOf(InvalidHistoryJsonException.class)
        .hasMessageContaining("Expected partitions.1 to be a valid base")
        .hasRootCauseInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                ManifestJson.read(
                    bytes(
                        DESIGN_EXAMPLE.replace(
                            "\"00000000-0000-4000-8000-000000000001\"",
                            "\"00000000-0000-4000-8000-000000000002\""))))
        .isInstanceOf(InvalidHistoryJsonException.class)
        .hasRootCauseMessage(
            "Expected history %s to take over another history in partition 1, but got itself"
                .formatted(B));
  }

  private static void assertInvalid(
      final String json, final String field, final String expected, final String found) {
    assertThatThrownBy(() -> ManifestJson.read(bytes(json)))
        .isInstanceOfSatisfying(
            InvalidHistoryJsonException.class, e -> assertThat(e.field()).isEqualTo(field))
        .hasMessage("Expected %s to be %s, but got %s".formatted(field, expected, found));
  }

  private static HistoryManifest manifest(final Map<Integer, Base> partitions) {
    return new HistoryManifest(1, "cluster-1", "default", A, NOON, partitions);
  }

  private static byte[] bytes(final String text) {
    return text.getBytes(StandardCharsets.UTF_8);
  }
}
