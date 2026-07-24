/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.secretstore.aws;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.camunda.secretstore.SecretResolutionResult.Failed;
import io.camunda.secretstore.SecretResolutionResult.Resolved;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.BatchGetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.BatchGetSecretValueResponse;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;
import software.amazon.awssdk.services.secretsmanager.model.ListSecretsRequest;
import software.amazon.awssdk.services.secretsmanager.model.ListSecretsResponse;
import software.amazon.awssdk.services.secretsmanager.model.SecretListEntry;
import software.amazon.awssdk.services.secretsmanager.model.SecretValueEntry;
import software.amazon.awssdk.services.secretsmanager.model.SecretsManagerException;

/**
 * Direct unit tests for {@link FlatSecretResolver}, constructed without going through {@link
 * AwsSecretsManagerSecretStore} — the batching/partitioning behavior tested here is this class's
 * own responsibility, not something a store-level test should have to prove indirectly.
 */
@ExtendWith(MockitoExtension.class)
class FlatSecretResolverTest {

  @Mock private SecretsManagerClient client;

  @Test
  void shouldResolveEmptyMapWithoutCallingAwsForEmptyNames() {
    // given
    final var resolver = new FlatSecretResolver(client, "", false, 20);

    // when
    final var result = resolver.resolve(Set.of());

    // then
    assertThat(result).isEmpty();
    verifyNoInteractions(client);
  }

  @Test
  void shouldResolveOneByOneWhenBatchingDisabled() {
    // given
    final var resolver = new FlatSecretResolver(client, "camunda/", false, 20);
    when(client.getSecretValue(any(GetSecretValueRequest.class)))
        .thenReturn(GetSecretValueResponse.builder().secretString("s3cr3t").build());

    // when
    final var result = resolver.resolve(Set.of("db-password"));

    // then
    assertThat(result.get("db-password"))
        .isInstanceOf(Resolved.class)
        .extracting(r -> ((Resolved) r).value())
        .isEqualTo("s3cr3t");
  }

  @Test
  void shouldClassifyAPerSecretFailureWhenBatchingDisabled() {
    // given
    final var resolver = new FlatSecretResolver(client, "", false, 20);
    when(client.getSecretValue(any(GetSecretValueRequest.class)))
        .thenThrow(
            (SecretsManagerException)
                SecretsManagerException.builder()
                    .awsErrorDetails(
                        AwsErrorDetails.builder().errorCode("ResourceNotFoundException").build())
                    .message("missing")
                    .build());

    // when
    final var result = resolver.resolve(Set.of("missing"));

    // then
    assertThat(result.get("missing")).isInstanceOf(Failed.class);
  }

  @Test
  void shouldResolveAllInOneBatchCallWhenCountIsAtOrBelowBatchSize() {
    // given
    final var resolver = new FlatSecretResolver(client, "", true, 20);
    when(client.batchGetSecretValue(any(BatchGetSecretValueRequest.class)))
        .thenReturn(
            BatchGetSecretValueResponse.builder()
                .secretValues(
                    SecretValueEntry.builder().name("db-password").secretString("s3cr3t").build(),
                    SecretValueEntry.builder().name("api-token").secretString("tok3n").build())
                .build());

    // when
    final var result = resolver.resolve(Set.of("db-password", "api-token"));

    // then
    assertThat(result.get("db-password"))
        .isInstanceOf(Resolved.class)
        .extracting(r -> ((Resolved) r).value())
        .isEqualTo("s3cr3t");
    verify(client, times(1)).batchGetSecretValue(any(BatchGetSecretValueRequest.class));
  }

  @Test
  void shouldSplitIntoMultipleBatchCallsWhenCountExceedsBatchSize() {
    // given — 25 references with a batch size of 20 must split into a 20-item and a 5-item call
    final var resolver = new FlatSecretResolver(client, "", true, 20);
    final Set<String> names =
        IntStream.range(0, 25)
            .mapToObj(i -> "ref-" + i)
            .collect(Collectors.toCollection(LinkedHashSet::new));
    when(client.batchGetSecretValue(any(BatchGetSecretValueRequest.class)))
        .thenAnswer(
            invocation -> {
              final BatchGetSecretValueRequest request = invocation.getArgument(0);
              final var entries =
                  request.secretIdList().stream()
                      .map(id -> SecretValueEntry.builder().name(id).secretString("value").build())
                      .toList();
              return BatchGetSecretValueResponse.builder().secretValues(entries).build();
            });

    // when
    final var result = resolver.resolve(names);

    // then — every reference resolved, but AWS was called twice (one call cannot exceed 20 ids)
    assertThat(result).hasSize(25);
    assertThat(result.values()).allMatch(Resolved.class::isInstance);
    verify(client, times(2)).batchGetSecretValue(any(BatchGetSecretValueRequest.class));
  }

  @Test
  void shouldListNamesUnderPrefixWithPrefixStripped() {
    // given
    final var resolver = new FlatSecretResolver(client, "camunda/", false, 20);
    when(client.listSecrets(any(ListSecretsRequest.class)))
        .thenReturn(
            ListSecretsResponse.builder()
                .secretList(
                    SecretListEntry.builder().name("camunda/db-password").build(),
                    SecretListEntry.builder().name("other/unrelated").build())
                .build());

    // when
    final var names = resolver.list();

    // then
    assertThat(names).containsExactly("db-password");
  }
}
