/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.eventplane.service;

import static io.camunda.zeebe.eventplane.service.Fixtures.HISTORY;
import static io.camunda.zeebe.eventplane.service.Fixtures.notice;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.eventplane.store.FileSystemStore;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

final class RoutingTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final String CREDENTIALS =
      "{\"type\":\"s3\",\"bucket\":\"b\",\"endpoint\":\"http://minio:9000\"}";
  private static final String NOTICE_WITH_PARTITION =
      """
      {"clusterId":"c","tenantId":"default","partition":%s,"history":"11111111-1111-1111-1111-111111111111",
       "key":"c/default/1/x.seg","firstIndex":%s,"lastIndex":%s,"firstPosition":1,"lastPosition":2,"size":1}""";

  @TempDir private Path directory;
  private ServiceFixture fixture;

  @BeforeEach
  void startService() throws Exception {
    fixture =
        ServiceFixture.start(
            new FileSystemStore(directory), config -> config.withCredentialsJson(CREDENTIALS));
  }

  @AfterEach
  void stopService() throws Exception {
    fixture.close();
  }

  @Test
  void shouldAnswerTheConfiguredCredentials() throws Exception {
    // when
    final var response = fixture.get("/v1/credentials");

    // then
    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.headers().firstValue("Content-Type")).contains("application/json");
    assertThat(response.body()).isEqualTo(CREDENTIALS);
  }

  @Test
  void shouldAnswer202ToAValidNoticeAndNothingElse() throws Exception {
    // when
    final var response = fixture.post("/v1/uploads", NOTICE_WITH_PARTITION.formatted(1, 1, 2));

    // then
    assertThat(response.statusCode()).isEqualTo(202);
    assertThat(response.body()).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "",
        "not json",
        "[]",
        "{}",
        "{\"clusterId\":\"c\"}",
        // a partition of 0
        "{\"clusterId\":\"c\",\"tenantId\":\"default\",\"partition\":0,\"history\":\"11111111-1111-1111-1111-111111111111\",\"key\":\"k\",\"firstIndex\":1,\"lastIndex\":2,\"firstPosition\":1,\"lastPosition\":2,\"size\":1}",
        // a history that is no UUID
        "{\"clusterId\":\"c\",\"tenantId\":\"default\",\"partition\":1,\"history\":\"h\",\"key\":\"k\",\"firstIndex\":1,\"lastIndex\":2,\"firstPosition\":1,\"lastPosition\":2,\"size\":1}",
      })
  void shouldAnswer400ToABodyThatIsNoNotice(final String body) throws Exception {
    // when
    final var response = fixture.post("/v1/uploads", body);

    // then
    assertThat(response.statusCode()).isEqualTo(400);
    assertThat(MAPPER.readTree(response.body()).get("error").asText())
        .startsWith("Expected an upload notice as JSON, but got");
  }

  @Test
  void shouldAnswer400ToANoticeThatEndsBeforeItStarts() throws Exception {
    // when
    final var response = fixture.post("/v1/uploads", NOTICE_WITH_PARTITION.formatted(1, 5, 4));

    // then
    assertThat(response.statusCode()).isEqualTo(400);
  }

  @Test
  void shouldAnswer400ToABodyThatIsTooLarge() throws Exception {
    // when
    final var response = fixture.post("/v1/uploads", "x".repeat(70_000));

    // then
    assertThat(response.statusCode()).isEqualTo(400);
    assertThat(MAPPER.readTree(response.body()).get("error").asText())
        .isEqualTo("Expected an upload notice of at most 65536 bytes, but got more");
  }

  @Test
  void shouldNotPassOnABadNoticeToTheStreams() throws Exception {
    // given
    final var stream = fixture.stream("c/default/1");
    final var notice = notice("c", "default", 1, HISTORY, 1, 40);

    // when
    fixture.post("/v1/uploads", "not json");
    fixture.post(notice);

    // then
    assertThat(stream.nextNotice()).isEqualTo(notice);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "/v1/notifications",
        "/v1/notifications?",
        "/v1/notifications?logs=c/default/1",
        "/v1/notifications?log=",
        "/v1/notifications?log=c/default",
        "/v1/notifications?log=c/default/1/2",
        "/v1/notifications?log=c/default/0",
        "/v1/notifications?log=c/default/x",
        "/v1/notifications?log=/default/1",
        "/v1/notifications?log=c//1",
        "/v1/notifications?log=c/default/1&log=bad",
      })
  void shouldAnswer400ToStreamsWithoutAValidLog(final String path) throws Exception {
    // when
    final var response = fixture.get(path);

    // then
    assertThat(response.statusCode()).isEqualTo(400);
    assertThat(MAPPER.readTree(response.body()).get("error").asText()).startsWith("Expected ");
  }

  @Test
  void shouldTakeTheLogOfAStreamPercentDecoded() throws Exception {
    // given
    final var stream = fixture.stream("my%20cluster/default/1");
    final var notice = notice("my cluster", "default", 1, HISTORY, 1, 40);

    // when
    fixture.post(notice);

    // then
    assertThat(stream.nextNotice()).isEqualTo(notice);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "/",
        "/v1",
        "/v1/",
        "/v1/unknown",
        "/v1/uploads/",
        "/v1/uploads/x",
        "/v1/notifications/",
        "/v1/groups",
        "/v1/groups/",
        "/v1/groups/a/b",
        "/v1/groups//",
        "/v1/clusters/",
        "/v1/clusters/x",
        "/v2/credentials",
        "/v1/credentials/x",
      })
  void shouldAnswer404ToAnythingElse(final String path) throws Exception {
    // when
    final var response = fixture.get(path);

    // then
    assertThat(response.statusCode()).isEqualTo(404);
    assertThat(MAPPER.readTree(response.body()).get("error").asText())
        .startsWith(
            "Expected a path of /v1/uploads, /v1/notifications, /v1/groups/{group}, /v1/clusters"
                + " or /v1/credentials, but got");
  }

  @ParameterizedTest
  @CsvSource({
    "GET,/v1/uploads,POST",
    "PUT,/v1/uploads,POST",
    "DELETE,/v1/uploads,POST",
    "POST,/v1/notifications,GET",
    "POST,/v1/groups/g,GET",
    "DELETE,/v1/groups/g,GET",
    "POST,/v1/credentials,GET",
    "PUT,/v1/credentials,GET",
    "HEAD,/v1/credentials,GET",
    "POST,/v1/clusters,GET",
    "PUT,/v1/clusters,GET",
    "HEAD,/v1/clusters,GET",
    "HEAD,/v1/notifications,GET",
  })
  void shouldAnswer405ToAMethodTheEndpointDoesNotTake(
      final String method, final String path, final String allowed) throws Exception {
    // when
    final var response = fixture.send(method, path, "POST".equals(method) ? "{}" : null);

    // then
    assertThat(response.statusCode()).isEqualTo(405);
    assertThat(response.headers().firstValue("Allow")).contains(allowed);
    if (!"HEAD".equals(method)) {
      assertThat(MAPPER.readTree(response.body()).get("error").asText())
          .isEqualTo("Expected the method %s for %s, but got %s".formatted(allowed, path, method));
    }
  }

  @Test
  void shouldAnswerAfterABadRequestJustAsBefore() throws Exception {
    // given
    fixture.get("/v1/unknown");
    fixture.post("/v1/uploads", "not json");

    // when
    final var response = fixture.get("/v1/credentials");

    // then
    assertThat(response.statusCode()).isEqualTo(200);
  }

  @Test
  void shouldFailToStartTwiceOrAfterClosing() throws Exception {
    // when, then
    assertThatThrownBy(() -> fixture.service().start())
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Expected the service to be new, but it is started");
    fixture.service().close();
    assertThatThrownBy(() -> fixture.service().start())
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Expected the service to be new, but it is closed");
  }
}
