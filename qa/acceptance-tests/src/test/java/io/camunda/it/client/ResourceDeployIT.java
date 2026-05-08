/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.client;

import static io.camunda.qa.util.multidb.CamundaMultiDBExtension.TIMEOUT_DATA_AVAILABILITY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;
import static org.awaitility.Awaitility.await;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ProblemException;
import io.camunda.qa.util.multidb.MultiDbTest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.apache.commons.io.IOUtils;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

@MultiDbTest
class ResourceDeployIT {

  private static CamundaClient camundaClient;

  private static long rpaResourceKey;
  private static long nonRpaResourceKey;
  private static final String NON_RPA_CONTENT = "## Some markdown";
  private static final String RPA_CONTENT = loadRpaContent();

  private static String loadRpaContent() {
    try (final var stream = ResourceDeployIT.class.getResourceAsStream("/rpa/test-rpa.rpa")) {
      return IOUtils.toString(stream, StandardCharsets.UTF_8);
    } catch (final IOException e) {
      throw new RuntimeException("Failed to load test-rpa.rpa", e);
    }
  }

  @BeforeAll
  static void deployResources() {
    rpaResourceKey =
        camundaClient
            .newDeployResourceCommand()
            .addResourceFromClasspath("rpa/test-rpa.rpa")
            .execute()
            .getResource()
            .getFirst()
            .getResourceKey();

    nonRpaResourceKey =
        camundaClient
            .newDeployResourceCommand()
            .addResourceBytes(NON_RPA_CONTENT.getBytes(StandardCharsets.UTF_8), "doc.md")
            .execute()
            .getResource()
            .getFirst()
            .getResourceKey();

    // wait for secondary storage to have both resources
    await("resources should be available in secondary storage")
        .atMost(TIMEOUT_DATA_AVAILABILITY)
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              assertThat(camundaClient.newResourceGetRequest(rpaResourceKey).execute()).isNotNull();
              assertThat(camundaClient.newResourceGetRequest(nonRpaResourceKey).execute())
                  .isNotNull();
            });
  }

  @Test
  void shouldGetRpaResourceMetadata() {
    // when
    final var resource = camundaClient.newResourceGetRequest(rpaResourceKey).execute();

    // then
    assertThat(resource).isNotNull();
    assertThat(resource.getResourceKey()).isEqualTo(rpaResourceKey);
    assertThat(resource.getResourceId()).isEqualTo("RPA_auditlog_test");
    assertThat(resource.getResourceName()).isEqualTo("rpa/test-rpa.rpa");
    assertThat(resource.getVersion()).isEqualTo(1);
  }

  @Test
  void shouldGetNonRpaResourceMetadata() {
    // when
    final var resource = camundaClient.newResourceGetRequest(nonRpaResourceKey).execute();

    // then
    assertThat(resource.getResourceKey()).isEqualTo(nonRpaResourceKey);
    assertThat(resource.getResourceName()).isEqualTo("doc.md");
    assertThat(resource.getVersion()).isEqualTo(1);
  }

  @Test
  void shouldGetRpaResourceContent() {
    // when
    final var content = camundaClient.newResourceContentGetRequest(rpaResourceKey).execute();

    // then
    assertThat(content).isEqualTo(RPA_CONTENT);
  }

  @Test
  void shouldReturnNotFoundForGetContentOnNonRpaResource() {
    // given - /content endpoint only serves resources with type "rpa"

    // when
    final ThrowingCallable execute =
        () -> camundaClient.newResourceContentGetRequest(nonRpaResourceKey).execute();

    // then
    final var problemException =
        assertThatExceptionOfType(ProblemException.class).isThrownBy(execute).actual();
    assertThat(problemException.code()).isEqualTo(404);
  }

  @Test
  void shouldGetRpaResourceContentBinary() {
    // when
    final var content = camundaClient.newResourceContentBinaryGetRequest(rpaResourceKey).execute();

    // then - /content/binary has no type restriction
    assertThat(content).isEqualTo(RPA_CONTENT);
  }

  @Test
  void shouldGetNonRpaResourceContentBinary() {
    // given - /content/binary serves any resource type

    // when
    final var content =
        camundaClient.newResourceContentBinaryGetRequest(nonRpaResourceKey).execute();

    // then
    assertThat(content).isEqualTo(NON_RPA_CONTENT);
  }
}
