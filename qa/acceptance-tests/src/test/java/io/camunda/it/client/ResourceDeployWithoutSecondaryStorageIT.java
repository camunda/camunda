/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.client;

import static io.camunda.spring.utils.DatabaseTypeUtils.CAMUNDA_DATABASE_TYPE_NONE;
import static io.camunda.spring.utils.DatabaseTypeUtils.UNIFIED_CONFIG_PROPERTY_CAMUNDA_DATABASE_TYPE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ProblemException;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.apache.commons.io.IOUtils;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

@ZeebeIntegration
class ResourceDeployWithoutSecondaryStorageIT {

  @TestZeebe(purgeAfterEach = false)
  private static final TestStandaloneBroker BROKER =
      new TestStandaloneBroker()
          .withProperty(UNIFIED_CONFIG_PROPERTY_CAMUNDA_DATABASE_TYPE, CAMUNDA_DATABASE_TYPE_NONE);

  private static long rpaResourceKey;
  private static long nonRpaResourceKey;
  private static final String NON_RPA_CONTENT = "## Some markdown";
  private static final String RPA_CONTENT = loadRpaContent();

  private static CamundaClient camundaClient;

  private static String loadRpaContent() {
    try (final var stream =
        ResourceDeployWithoutSecondaryStorageIT.class.getResourceAsStream("/rpa/test-rpa.rpa")) {
      return IOUtils.toString(stream, StandardCharsets.UTF_8);
    } catch (final IOException e) {
      throw new RuntimeException("Failed to load test-rpa.rpa", e);
    }
  }

  @BeforeAll
  static void deployResources() {
    camundaClient = BROKER.newClientBuilder().preferRestOverGrpc(true).build();

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
  }

  @AfterAll
  static void tearDown() {
    if (camundaClient != null) {
      camundaClient.close();
    }
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
