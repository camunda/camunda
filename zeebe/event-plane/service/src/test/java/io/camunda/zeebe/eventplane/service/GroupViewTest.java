/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.eventplane.service;

import static io.camunda.zeebe.eventplane.service.Fixtures.HISTORY;
import static io.camunda.zeebe.eventplane.service.Fixtures.OTHER_HISTORY;
import static io.camunda.zeebe.eventplane.service.Fixtures.cursor;
import static io.camunda.zeebe.eventplane.service.Fixtures.heartbeat;
import static io.camunda.zeebe.eventplane.service.Fixtures.notice;
import static io.camunda.zeebe.eventplane.service.Fixtures.put;
import static io.camunda.zeebe.eventplane.service.Fixtures.segment;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.eventplane.segment.LogId;
import io.camunda.zeebe.eventplane.store.FileSystemStore;
import io.camunda.zeebe.eventplane.store.ObjectNames;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class GroupViewTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  @TempDir private Path directory;
  private FileSystemStore store;
  private ServiceFixture fixture;

  @BeforeEach
  void startService() throws Exception {
    store = new FileSystemStore(directory);
    fixture = ServiceFixture.start(store);
  }

  @AfterEach
  void stopService() throws Exception {
    fixture.close();
  }

  @Test
  void shouldShowTheWorkersAndTheCursorsWithTheirLag() throws Exception {
    // given workers and cursors as the contract shows them, one log with a notice, one without
    heartbeat(store, "g", "w-1", 42);
    heartbeat(store, "g", "w-2", 7);
    cursor(store, "g", "c", "default", 1, HISTORY, 10, 9, "w-1");
    cursor(store, "g", "c", "default", 2, HISTORY, 5, 4, "w-2");
    segment(store, "c", "default", 2, HISTORY, 1, 25, 0);
    fixture.post(notice("c", "default", 1, HISTORY, 31, 40));

    // when
    final var response = fixture.get("/v1/groups/g");

    // then
    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.headers().firstValue("Content-Type")).contains("application/json");
    assertThat(json(response))
        .isEqualTo(
            MAPPER.readTree(
                """
                {"group":"g",
                 "workers":[{"worker":"w-1","beat":42},{"worker":"w-2","beat":7}],
                 "cursors":[
                   {"log":"c/default/1","history":"%1$s","index":10,"position":9,"worker":"w-1",
                    "newestIndex":40,"lag":30},
                   {"log":"c/default/2","history":"%1$s","index":5,"position":4,"worker":"w-2",
                    "newestIndex":25,"lag":20}]}"""
                    .formatted(HISTORY)));
  }

  @Test
  void shouldKeepTheNoticeWithTheHighestLastIndexOfALog() throws Exception {
    // given
    cursor(store, "g", "c", "default", 1, HISTORY, 10, 9, "w-1");
    fixture.post(notice("c", "default", 1, HISTORY, 21, 40));
    fixture.post(notice("c", "default", 1, HISTORY, 11, 20));

    // when
    final var cursors = json(fixture.get("/v1/groups/g")).get("cursors");

    // then the older notice, which arrived later, didn't replace the newer one
    assertThat(cursors.get(0).get("newestIndex").asLong()).isEqualTo(40);
    assertThat(cursors.get(0).get("lag").asLong()).isEqualTo(30);
  }

  @Test
  void shouldTakeTheNewestIndexFromAListingWhenNoNoticeArrived() throws Exception {
    // given segments of the history, among them a rollup that starts early and ends last
    cursor(store, "g", "c", "default", 1, HISTORY, 10, 9, "w-1");
    segment(store, "c", "default", 1, HISTORY, 1, 30, 1);
    segment(store, "c", "default", 1, HISTORY, 11, 20, 0);
    segment(store, "c", "default", 1, HISTORY, 21, 25, 0);
    segment(store, "c", "default", 1, OTHER_HISTORY, 1, 99, 0);

    // when
    final var cursor = json(fixture.get("/v1/groups/g")).get("cursors").get(0);

    // then the highest last index of the cursor's history counts
    assertThat(cursor.get("newestIndex").asLong()).isEqualTo(30);
    assertThat(cursor.get("lag").asLong()).isEqualTo(20);
  }

  @Test
  void shouldListTheCursorsHistoryWhenTheNewestNoticeIsOfAnotherHistory() throws Exception {
    // given a cursor in the old history, and a notice of the new one
    cursor(store, "g", "c", "default", 1, HISTORY, 10, 9, "w-1");
    segment(store, "c", "default", 1, HISTORY, 1, 60, 0);
    fixture.post(notice("c", "default", 1, OTHER_HISTORY, 1, 100));

    // when
    final var cursor = json(fixture.get("/v1/groups/g")).get("cursors").get(0);

    // then
    assertThat(cursor.get("newestIndex").asLong()).isEqualTo(60);
    assertThat(cursor.get("lag").asLong()).isEqualTo(50);
  }

  @Test
  void shouldNotShowANegativeLag() throws Exception {
    // given a cursor that is past the newest notice, which can happen to a notice that is stale
    cursor(store, "g", "c", "default", 1, HISTORY, 50, 49, "w-1");
    fixture.post(notice("c", "default", 1, HISTORY, 31, 40));

    // when
    final var cursor = json(fixture.get("/v1/groups/g")).get("cursors").get(0);

    // then
    assertThat(cursor.get("newestIndex").asLong()).isEqualTo(40);
    assertThat(cursor.get("lag").asLong()).isZero();
  }

  @Test
  void shouldShowNoNewestIndexForALogWithoutAnySegment() throws Exception {
    // given
    cursor(store, "g", "c", "default", 1, HISTORY, 50, 49, "w-1");

    // when
    final var cursor = json(fixture.get("/v1/groups/g")).get("cursors").get(0);

    // then
    assertThat(cursor.get("newestIndex").isNull()).isTrue();
    assertThat(cursor.get("lag").asLong()).isZero();
  }

  @Test
  void shouldSkipObjectsThatAreNotWhatTheContractSays() throws Exception {
    // given good objects among bad ones
    heartbeat(store, "g", "w-1", 42);
    put(store, ObjectNames.worker("g", "w-broken"), "not json");
    put(store, ObjectNames.worker("g", "w-no-beat"), "{\"version\":1,\"worker\":\"w-no-beat\"}");
    cursor(store, "g", "c", "default", 1, HISTORY, 10, 9, "w-1");
    put(store, ObjectNames.cursor("g", new LogId("c", "default", 2)), "{\"version\":1}");
    put(
        store,
        ObjectNames.cursor("g", new LogId("c", "default", 3)),
        "{\"history\":\"not-a-uuid\",\"index\":1,\"position\":1}");
    put(store, "_groups/g/cursors/c/default/four.json", "{}");
    put(store, "_groups/g/workers/readme.txt", "not a heartbeat");

    // when
    final var group = json(fixture.get("/v1/groups/g"));

    // then
    assertThat(group.get("workers")).hasSize(1);
    assertThat(group.get("workers").get(0).get("worker").asText()).isEqualTo("w-1");
    assertThat(group.get("cursors")).hasSize(1);
    assertThat(group.get("cursors").get(0).get("log").asText()).isEqualTo("c/default/1");
  }

  @Test
  void shouldShowACursorWithoutAWorker() throws Exception {
    // given
    put(
        store,
        ObjectNames.cursor("g", new LogId("c", "default", 1)),
        "{\"version\":1,\"history\":\"%s\",\"index\":1,\"position\":1}".formatted(HISTORY));

    // when
    final var cursor = json(fixture.get("/v1/groups/g")).get("cursors").get(0);

    // then
    assertThat(cursor.get("worker").isNull()).isTrue();
  }

  @Test
  void shouldNameTheLogOfACursorWithTheClusterAndTenantAsTheyAre() throws Exception {
    // given a cluster and a tenant that the keys percent-encode
    cursor(store, "g", "my cluster", "my tenant", 2, HISTORY, 10, 9, "w-1");

    // when
    final var cursor = json(fixture.get("/v1/groups/g")).get("cursors").get(0);

    // then
    assertThat(cursor.get("log").asText()).isEqualTo("my cluster/my tenant/2");
  }

  @Test
  void shouldShowAGroupThatHasOnlyItsGroupObject() throws Exception {
    // given
    put(
        store,
        ObjectNames.group("g"),
        "{\"version\":1,\"name\":\"g\",\"subscription\":[{\"cluster\":\"c\",\"tenant\":\"*\"}]}");

    // when
    final var response = fixture.get("/v1/groups/g");

    // then
    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(json(response))
        .isEqualTo(MAPPER.readTree("{\"group\":\"g\",\"workers\":[],\"cursors\":[]}"));
  }

  @Test
  void shouldDecodeTheGroupNameInThePath() throws Exception {
    // given a group whose name the keys percent-encode, and a group that its name could be mixed up
    // with
    heartbeat(store, "my group/1", "w-1", 3);
    heartbeat(store, "my group", "w-2", 4);

    // when
    final var response = fixture.get("/v1/groups/my%20group%2F1");

    // then
    assertThat(response.statusCode()).isEqualTo(200);
    final var group = json(response);
    assertThat(group.get("group").asText()).isEqualTo("my group/1");
    assertThat(group.get("workers")).hasSize(1);
    assertThat(group.get("workers").get(0).get("worker").asText()).isEqualTo("w-1");
  }

  @Test
  void shouldAnswer404ForAGroupWithoutObjects() throws Exception {
    // given another group's objects
    heartbeat(store, "other", "w-1", 3);

    // when
    final var response = fixture.get("/v1/groups/g");

    // then
    assertThat(response.statusCode()).isEqualTo(404);
    assertThat(json(response).get("error").asText())
        .isEqualTo("Expected the group 'g' to have objects in the store, but found none");
  }

  @Test
  void shouldAnswerFromTheStoreAfterARestart() throws Exception {
    // given a service that got notices, and a group whose logs have segments
    heartbeat(store, "g", "w-1", 42);
    cursor(store, "g", "c", "default", 1, HISTORY, 10, 9, "w-1");
    cursor(store, "g", "c", "default", 2, HISTORY, 5, 4, "w-1");
    segment(store, "c", "default", 1, HISTORY, 1, 20, 0);
    segment(store, "c", "default", 1, HISTORY, 21, 40, 0);
    segment(store, "c", "default", 2, HISTORY, 1, 25, 0);
    fixture.post(notice("c", "default", 1, HISTORY, 21, 40));
    fixture.post(notice("c", "default", 2, HISTORY, 1, 25));
    final var before = json(fixture.get("/v1/groups/g"));
    fixture.close();

    // when a new instance starts on the same store, which has seen no notice
    try (final var restarted = ServiceFixture.start(store)) {
      final var response = restarted.get("/v1/groups/g");

      // then
      assertThat(response.statusCode()).isEqualTo(200);
      assertThat(json(response)).isEqualTo(before);
      assertThat(before.get("cursors").get(0).get("newestIndex").asLong()).isEqualTo(40);
    }
  }

  private static JsonNode json(final HttpResponse<String> response) throws Exception {
    return MAPPER.readTree(response.body());
  }
}
