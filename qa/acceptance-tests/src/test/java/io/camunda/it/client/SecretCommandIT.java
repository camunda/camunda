/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ProblemException;
import io.camunda.client.api.search.enums.SecretErrorCode;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.qa.util.multidb.MultiDbTestApplication;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/**
 * Covers the secret client commands against a real gateway: the wire format, the transport and the
 * per-reference outcome mapping, resolved from a real file-based secret store (#58497).
 * Authorization enforcement is covered separately by {@code SecretAuthorizationIT}.
 */
@MultiDbTest
public class SecretCommandIT {

  private static final String TOKEN_VALUE = "token-file-value";

  @MultiDbTestApplication
  static final TestStandaloneBroker BROKER =
      new TestStandaloneBroker()
          .withFileBasedSecretStore(
              directory -> {
                Files.writeString(directory.resolve("token"), TOKEN_VALUE, StandardCharsets.UTF_8);
                Files.writeString(directory.resolve("a"), "a-file-value", StandardCharsets.UTF_8);
                Files.writeString(directory.resolve("b"), "b-file-value", StandardCharsets.UTF_8);
              });

  // Held by the store (one file per secret, the file name being the bare secret name).
  private static final String KNOWN_REFERENCE = "camunda.secrets.token";

  private static final List<String> ALL_KNOWN_REFERENCES =
      List.of(KNOWN_REFERENCE, "camunda.secrets.a", "camunda.secrets.b");

  // Valid but no file of that name exists in the store, so it resolves to NOT_FOUND.
  private static final String UNKNOWN_REFERENCE = "camunda.secrets.doesnotexist";

  /**
   * The cluster's maximum batch size, mirroring {@code SecretRequestValidator#MAX_BATCH_SIZE}. It
   * is repeated rather than imported because {@code gateway-mapping-http} is deliberately not a
   * declared dependency of this module. Should the cluster's cap ever be raised, {@link
   * #shouldRaiseExceptionWhenBatchExceedsTheClusterLimit} fails rather than silently no longer
   * exceeding it.
   */
  private static final int MAX_BATCH_SIZE = 20;

  private static CamundaClient camundaClient;

  @Test
  void shouldResolveKnownReference() {
    // when
    final var response =
        camundaClient.newResolveSecretsCommand().references(List.of(KNOWN_REFERENCE)).send().join();

    // then
    assertThat(response.isFullyResolved()).isTrue();
    assertThat(response.getErrors()).isEmpty();
    assertThat(response.getResolved()).hasSize(1);
    assertThat(response.getResolved().get(0).getReference()).isEqualTo(KNOWN_REFERENCE);
    assertThat(response.getResolved().get(0).getValue()).isEqualTo(TOKEN_VALUE);
  }

  @Test
  void shouldReportUnresolvedReferenceWithoutFailingTheBatch() {
    // when a batch mixes a resolvable and an unresolvable reference
    final var response =
        camundaClient
            .newResolveSecretsCommand()
            .references(List.of(KNOWN_REFERENCE, UNKNOWN_REFERENCE))
            .send()
            .join();

    // then the failure is reported as data rather than as an exception, and the other reference
    // still resolves
    assertThat(response.isFullyResolved()).isFalse();
    assertThat(response.getResolved()).hasSize(1);
    assertThat(response.getResolved().get(0).getReference()).isEqualTo(KNOWN_REFERENCE);
    assertThat(response.getErrors()).hasSize(1);
    assertThat(response.getErrors().get(0).getReference()).isEqualTo(UNKNOWN_REFERENCE);
    assertThat(response.getErrors().get(0).getCode()).isEqualTo(SecretErrorCode.NOT_FOUND);
  }

  @Test
  void shouldReportMalformedReference() {
    // when
    final var response =
        camundaClient
            .newResolveSecretsCommand()
            .references(List.of("not-a-reference"))
            .send()
            .join();

    // then the client passes the reference through and the cluster classifies it
    assertThat(response.getResolved()).isEmpty();
    assertThat(response.getErrors()).hasSize(1);
    assertThat(response.getErrors().get(0).getCode()).isEqualTo(SecretErrorCode.INVALID_REFERENCE);
  }

  @Test
  void shouldRaiseExceptionWhenBatchExceedsTheClusterLimit() {
    // given a batch one over the cluster's cap, which the client deliberately does not enforce
    final List<String> references =
        IntStream.rangeClosed(0, MAX_BATCH_SIZE)
            .mapToObj(index -> "camunda.secrets.reference" + index)
            .toList();

    // when / then a rejected request is the one outcome that completes the command exceptionally,
    // rather than being reported as per-reference errors
    assertThatExceptionOfType(ProblemException.class)
        .isThrownBy(
            () -> camundaClient.newResolveSecretsCommand().references(references).send().join())
        .satisfies(exception -> assertThat(exception.details().getStatus()).isEqualTo(400));
  }

  @Test
  void shouldListReferences() {
    // when
    final var response = camundaClient.newListSecretsCommand().send().join();

    // then the references the store knows are returned, names only
    assertThat(response.getReferences()).containsExactlyInAnyOrderElementsOf(ALL_KNOWN_REFERENCES);
  }
}
