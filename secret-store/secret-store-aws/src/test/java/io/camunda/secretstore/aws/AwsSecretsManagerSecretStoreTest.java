/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.secretstore.aws;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.camunda.secretstore.SecretErrorCode;
import io.camunda.secretstore.SecretResolutionResult;
import io.camunda.secretstore.SecretResolutionResult.Failed;
import io.camunda.secretstore.SecretResolutionResult.Resolved;
import io.camunda.secretstore.SecretStoreUnavailableException;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.APIErrorType;
import software.amazon.awssdk.services.secretsmanager.model.BatchGetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.BatchGetSecretValueResponse;
import software.amazon.awssdk.services.secretsmanager.model.ListSecretsRequest;
import software.amazon.awssdk.services.secretsmanager.model.ListSecretsResponse;
import software.amazon.awssdk.services.secretsmanager.model.SecretListEntry;
import software.amazon.awssdk.services.secretsmanager.model.SecretValueEntry;
import software.amazon.awssdk.services.secretsmanager.model.SecretsManagerException;

@ExtendWith(MockitoExtension.class)
class AwsSecretsManagerSecretStoreTest {

  @Mock private SecretsManagerClient client;

  @Test
  void shouldResolveKnownSecret() {
    // given
    final var store = new AwsSecretsManagerSecretStore(client, "camunda/");
    when(client.batchGetSecretValue(any(BatchGetSecretValueRequest.class)))
        .thenReturn(
            BatchGetSecretValueResponse.builder()
                .secretValues(
                    SecretValueEntry.builder()
                        .name("camunda/db-password")
                        .secretString("s3cr3t")
                        .build())
                .build());

    // when
    final var ref = new AwsSecretsManagerSecretReference("db-password");
    final var result = store.resolve(Set.of(ref));

    // then
    assertThat(result.get(ref))
        .isInstanceOf(Resolved.class)
        .extracting(r -> ((Resolved) r).value())
        .isEqualTo("s3cr3t");
  }

  @Test
  void shouldPrependPathPrefixToSecretId() {
    // given
    final var store = new AwsSecretsManagerSecretStore(client, "camunda/");
    when(client.batchGetSecretValue(any(BatchGetSecretValueRequest.class)))
        .thenReturn(
            BatchGetSecretValueResponse.builder()
                .secretValues(
                    SecretValueEntry.builder().name("camunda/token").secretString("v").build())
                .build());

    // when
    store.resolve(Set.of(new AwsSecretsManagerSecretReference("token")));

    // then
    final var captor = ArgumentCaptor.forClass(BatchGetSecretValueRequest.class);
    verify(client).batchGetSecretValue(captor.capture());
    assertThat(captor.getValue().secretIdList()).containsExactly("camunda/token");
  }

  @Test
  void shouldUseBareNameWhenPrefixIsNull() {
    // given
    final var store = new AwsSecretsManagerSecretStore(client, null);
    when(client.batchGetSecretValue(any(BatchGetSecretValueRequest.class)))
        .thenReturn(
            BatchGetSecretValueResponse.builder()
                .secretValues(SecretValueEntry.builder().name("token").secretString("v").build())
                .build());

    // when
    store.resolve(Set.of(new AwsSecretsManagerSecretReference("token")));

    // then
    final var captor = ArgumentCaptor.forClass(BatchGetSecretValueRequest.class);
    verify(client).batchGetSecretValue(captor.capture());
    assertThat(captor.getValue().secretIdList()).containsExactly("token");
  }

  @Test
  void shouldReturnNotFoundForMissingSecret() {
    // given
    final var store = new AwsSecretsManagerSecretStore(client, "");
    when(client.batchGetSecretValue(any(BatchGetSecretValueRequest.class)))
        .thenReturn(
            BatchGetSecretValueResponse.builder()
                .errors(
                    APIErrorType.builder()
                        .secretId("missing")
                        .errorCode("ResourceNotFoundException")
                        .message("missing")
                        .build())
                .build());

    // when
    final var ref = new AwsSecretsManagerSecretReference("missing");
    final var result = store.resolve(Set.of(ref));

    // then
    assertThat(result.get(ref)).isInstanceOf(Failed.class);
    assertThat(((Failed) result.get(ref)).code()).isEqualTo(SecretErrorCode.NOT_FOUND);
  }

  @Test
  void shouldReturnInvalidRefForInvalidParameter() {
    // given
    final var store = new AwsSecretsManagerSecretStore(client, "");
    when(client.batchGetSecretValue(any(BatchGetSecretValueRequest.class)))
        .thenReturn(
            BatchGetSecretValueResponse.builder()
                .errors(
                    APIErrorType.builder()
                        .secretId("bad")
                        .errorCode("InvalidParameterException")
                        .message("bad")
                        .build())
                .build());

    // when
    final var ref = new AwsSecretsManagerSecretReference("bad");
    final var result = store.resolve(Set.of(ref));

    // then
    assertThat(((Failed) result.get(ref)).code()).isEqualTo(SecretErrorCode.INVALID_REF);
  }

  @Test
  void shouldReturnAccessDeniedForPerItemDecryptionFailure() {
    // given
    final var store = new AwsSecretsManagerSecretStore(client, "");
    when(client.batchGetSecretValue(any(BatchGetSecretValueRequest.class)))
        .thenReturn(
            BatchGetSecretValueResponse.builder()
                .errors(
                    APIErrorType.builder()
                        .secretId("secret")
                        .errorCode("DecryptionFailure")
                        .message("cannot decrypt")
                        .build())
                .build());

    // when
    final var ref = new AwsSecretsManagerSecretReference("secret");
    final var result = store.resolve(Set.of(ref));

    // then
    assertThat(((Failed) result.get(ref)).code()).isEqualTo(SecretErrorCode.ACCESS_DENIED);
  }

  @Test
  void shouldReturnAccessDeniedWhenWholeCallIsDenied() {
    // given — the whole batch call is denied (e.g. IAM policy blocks the store's secret prefix
    // entirely), as opposed to a single secret being denied via a per-item error entry
    final var store = new AwsSecretsManagerSecretStore(client, "");
    final var e =
        (SecretsManagerException)
            SecretsManagerException.builder()
                .awsErrorDetails(
                    AwsErrorDetails.builder().errorCode("AccessDeniedException").build())
                .statusCode(400)
                .message("denied")
                .build();
    when(client.batchGetSecretValue(any(BatchGetSecretValueRequest.class))).thenThrow(e);

    // when
    final var ref = new AwsSecretsManagerSecretReference("secret");
    final var result = store.resolve(Set.of(ref));

    // then
    assertThat(((Failed) result.get(ref)).code()).isEqualTo(SecretErrorCode.ACCESS_DENIED);
  }

  @Test
  void shouldReturnInvalidRefForBinaryOnlySecret() {
    // given — a secret with no string value (binary secret)
    final var store = new AwsSecretsManagerSecretStore(client, "");
    when(client.batchGetSecretValue(any(BatchGetSecretValueRequest.class)))
        .thenReturn(
            BatchGetSecretValueResponse.builder()
                .secretValues(SecretValueEntry.builder().name("binary").build())
                .build());

    // when
    final var ref = new AwsSecretsManagerSecretReference("binary");
    final var result = store.resolve(Set.of(ref));

    // then
    assertThat(((Failed) result.get(ref)).code()).isEqualTo(SecretErrorCode.INVALID_REF);
  }

  @Test
  void shouldThrowUnavailableOnConnectivityError() {
    // given
    final var store = new AwsSecretsManagerSecretStore(client, "");
    when(client.batchGetSecretValue(any(BatchGetSecretValueRequest.class)))
        .thenThrow(SdkClientException.create("connection refused"));

    // when / then
    assertThatThrownBy(() -> store.resolve(Set.of(new AwsSecretsManagerSecretReference("any"))))
        .isInstanceOf(SecretStoreUnavailableException.class);
  }

  @Test
  void shouldReturnResultForEveryRefInBatch() {
    // given
    final var store = new AwsSecretsManagerSecretStore(client, "");
    when(client.batchGetSecretValue(any(BatchGetSecretValueRequest.class)))
        .thenReturn(
            BatchGetSecretValueResponse.builder()
                .secretValues(
                    SecretValueEntry.builder().name("known").secretString("value").build())
                .errors(
                    APIErrorType.builder()
                        .secretId("missing")
                        .errorCode("ResourceNotFoundException")
                        .message("missing")
                        .build())
                .build());

    // when
    final var known = new AwsSecretsManagerSecretReference("known");
    final var missing = new AwsSecretsManagerSecretReference("missing");
    final var result = store.resolve(Set.of(known, missing));

    // then
    assertThat(result).containsKeys(known, missing);
    assertThat(result.get(known)).isInstanceOf(Resolved.class);
    assertThat(result.get(missing)).isInstanceOf(Failed.class);
  }

  @Test
  void shouldGuaranteeResultForEveryRefEvenWhenAwsOmitsOne() {
    // given — AWS returns neither a value nor an error entry for one requested id
    final var store = new AwsSecretsManagerSecretStore(client, "");
    when(client.batchGetSecretValue(any(BatchGetSecretValueRequest.class)))
        .thenReturn(
            BatchGetSecretValueResponse.builder()
                .secretValues(
                    SecretValueEntry.builder().name("known").secretString("value").build())
                .build());

    // when
    final var known = new AwsSecretsManagerSecretReference("known");
    final var omitted = new AwsSecretsManagerSecretReference("omitted");
    final var result = store.resolve(Set.of(known, omitted));

    // then
    assertThat(result.get(known)).isInstanceOf(Resolved.class);
    assertThat(result.get(omitted)).isInstanceOf(Failed.class);
    assertThat(((Failed) result.get(omitted)).code()).isEqualTo(SecretErrorCode.NOT_FOUND);
  }

  @Test
  void shouldBatchMoreThan20RefsAcrossMultipleCalls() {
    // given 25 refs, one more than two full 20-id and 5-id AWS BatchGetSecretValue batches
    final var store = new AwsSecretsManagerSecretStore(client, "");
    final var refs =
        IntStream.range(0, 25)
            .mapToObj(i -> new AwsSecretsManagerSecretReference("secret-" + i))
            .collect(Collectors.toCollection(HashSet::new));
    when(client.batchGetSecretValue(any(BatchGetSecretValueRequest.class)))
        .thenAnswer(
            invocation -> {
              final BatchGetSecretValueRequest req = invocation.getArgument(0);
              return BatchGetSecretValueResponse.builder()
                  .secretValues(
                      req.secretIdList().stream()
                          .map(id -> SecretValueEntry.builder().name(id).secretString("v").build())
                          .toList())
                  .build();
            });

    // when
    final var result = store.resolve(refs);

    // then
    final var captor = ArgumentCaptor.forClass(BatchGetSecretValueRequest.class);
    verify(client, times(2)).batchGetSecretValue(captor.capture());
    assertThat(captor.getAllValues())
        .extracting(req -> req.secretIdList().size())
        .containsExactlyInAnyOrder(20, 5);
    assertThat(result).hasSize(25);
    assertThat(result.values()).allMatch(Resolved.class::isInstance);
  }

  @Test
  void shouldReturnEmptyMapForEmptyRefs() {
    // given
    final var store = new AwsSecretsManagerSecretStore(client, "");

    // when
    final var result = store.resolve(Set.of());

    // then
    assertThat(result).isEmpty();
    verifyNoInteractions(client);
  }

  @Test
  void shouldListSecretsFilteredByPrefixAndStripIt() {
    // given
    final var store = new AwsSecretsManagerSecretStore(client, "camunda/");
    when(client.listSecrets(any(ListSecretsRequest.class)))
        .thenReturn(
            ListSecretsResponse.builder()
                .secretList(
                    SecretListEntry.builder().name("camunda/db").build(),
                    SecretListEntry.builder().name("camunda/api").build(),
                    SecretListEntry.builder().name("other/ignored").build())
                .build());

    // when
    final var refs = store.list();

    // then — only prefixed secrets, with the prefix stripped
    assertThat(refs)
        .containsExactlyInAnyOrder(
            new AwsSecretsManagerSecretReference("db"),
            new AwsSecretsManagerSecretReference("api"));
  }

  @Test
  void shouldPaginateListSecrets() {
    // given — two pages joined by a nextToken
    final var store = new AwsSecretsManagerSecretStore(client, "");
    when(client.listSecrets(any(ListSecretsRequest.class)))
        .thenReturn(
            ListSecretsResponse.builder()
                .secretList(SecretListEntry.builder().name("a").build())
                .nextToken("page2")
                .build())
        .thenReturn(
            ListSecretsResponse.builder()
                .secretList(SecretListEntry.builder().name("b").build())
                .build());

    // when
    final var refs = store.list();

    // then
    assertThat(refs)
        .containsExactlyInAnyOrder(
            new AwsSecretsManagerSecretReference("a"), new AwsSecretsManagerSecretReference("b"));
  }

  @Test
  void shouldThrowUnavailableWhenListFails() {
    // given
    final var store = new AwsSecretsManagerSecretStore(client, "");
    when(client.listSecrets(any(ListSecretsRequest.class)))
        .thenThrow(SdkClientException.create("boom"));

    // when / then
    assertThatThrownBy(store::list).isInstanceOf(SecretStoreUnavailableException.class);
  }

  @Test
  void shouldNotLeakSecretValueInResolvedToString() {
    // given
    final SecretResolutionResult resolved = new Resolved("super-secret");

    // when / then — value must be masked in toString
    assertThat(resolved.toString()).doesNotContain("super-secret");
  }
}
