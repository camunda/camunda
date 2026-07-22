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
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;
import software.amazon.awssdk.services.secretsmanager.model.ListSecretsRequest;
import software.amazon.awssdk.services.secretsmanager.model.ListSecretsResponse;
import software.amazon.awssdk.services.secretsmanager.model.SecretListEntry;
import software.amazon.awssdk.services.secretsmanager.model.SecretValueEntry;
import software.amazon.awssdk.services.secretsmanager.model.SecretsManagerException;

@ExtendWith(MockitoExtension.class)
class AwsSecretsManagerSecretStoreTest {

  @Mock private SecretsManagerClient client;

  // ---- default path: one GetSecretValue call per reference (batching disabled) ----

  @Test
  void shouldResolveKnownSecret() {
    // given
    final var store = new AwsSecretsManagerSecretStore(client, "camunda/");
    when(client.getSecretValue(any(GetSecretValueRequest.class)))
        .thenReturn(GetSecretValueResponse.builder().secretString("s3cr3t").build());

    // when
    final var ref = "db-password";
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
    when(client.getSecretValue(any(GetSecretValueRequest.class)))
        .thenReturn(GetSecretValueResponse.builder().secretString("v").build());

    // when
    store.resolve(Set.of("token"));

    // then
    final var captor = ArgumentCaptor.forClass(GetSecretValueRequest.class);
    verify(client).getSecretValue(captor.capture());
    assertThat(captor.getValue().secretId()).isEqualTo("camunda/token");
  }

  @Test
  void shouldUseBareNameWhenPrefixIsNull() {
    // given
    final var store = new AwsSecretsManagerSecretStore(client, null);
    when(client.getSecretValue(any(GetSecretValueRequest.class)))
        .thenReturn(GetSecretValueResponse.builder().secretString("v").build());

    // when
    store.resolve(Set.of("token"));

    // then
    final var captor = ArgumentCaptor.forClass(GetSecretValueRequest.class);
    verify(client).getSecretValue(captor.capture());
    assertThat(captor.getValue().secretId()).isEqualTo("token");
  }

  @Test
  void shouldUseBareNameWhenPrefixIsBlank() {
    // given
    final var store = new AwsSecretsManagerSecretStore(client, "   ");
    when(client.getSecretValue(any(GetSecretValueRequest.class)))
        .thenReturn(GetSecretValueResponse.builder().secretString("v").build());

    // when
    store.resolve(Set.of("token"));

    // then
    final var captor = ArgumentCaptor.forClass(GetSecretValueRequest.class);
    verify(client).getSecretValue(captor.capture());
    assertThat(captor.getValue().secretId()).isEqualTo("token");
  }

  @Test
  void shouldReturnNotFoundForMissingSecret() {
    // given
    final var store = new AwsSecretsManagerSecretStore(client, "");
    when(client.getSecretValue(any(GetSecretValueRequest.class)))
        .thenThrow(
            (SecretsManagerException)
                SecretsManagerException.builder()
                    .awsErrorDetails(
                        AwsErrorDetails.builder().errorCode("ResourceNotFoundException").build())
                    .message("missing")
                    .build());

    // when
    final var ref = "missing";
    final var result = store.resolve(Set.of(ref));

    // then
    assertThat(result.get(ref)).isInstanceOf(Failed.class);
    assertThat(((Failed) result.get(ref)).code()).isEqualTo(SecretErrorCode.NOT_FOUND);
  }

  @Test
  void shouldReturnInvalidRefForInvalidParameter() {
    // given
    final var store = new AwsSecretsManagerSecretStore(client, "");
    when(client.getSecretValue(any(GetSecretValueRequest.class)))
        .thenThrow(
            (SecretsManagerException)
                SecretsManagerException.builder()
                    .awsErrorDetails(
                        AwsErrorDetails.builder().errorCode("InvalidParameterException").build())
                    .message("bad")
                    .build());

    // when
    final var ref = "bad";
    final var result = store.resolve(Set.of(ref));

    // then
    assertThat(((Failed) result.get(ref)).code()).isEqualTo(SecretErrorCode.INVALID_REF);
  }

  @Test
  void shouldReturnAccessDeniedForAccessDeniedError() {
    // given
    final var store = new AwsSecretsManagerSecretStore(client, "");
    when(client.getSecretValue(any(GetSecretValueRequest.class)))
        .thenThrow(
            (SecretsManagerException)
                SecretsManagerException.builder()
                    .awsErrorDetails(
                        AwsErrorDetails.builder().errorCode("AccessDeniedException").build())
                    .statusCode(400)
                    .message("denied")
                    .build());

    // when
    final var ref = "secret";
    final var result = store.resolve(Set.of(ref));

    // then
    assertThat(((Failed) result.get(ref)).code()).isEqualTo(SecretErrorCode.ACCESS_DENIED);
  }

  @Test
  void shouldReturnInvalidRefForBinaryOnlySecret() {
    // given — a secret with no string value (binary secret)
    final var store = new AwsSecretsManagerSecretStore(client, "");
    when(client.getSecretValue(any(GetSecretValueRequest.class)))
        .thenReturn(GetSecretValueResponse.builder().build());

    // when
    final var ref = "binary";
    final var result = store.resolve(Set.of(ref));

    // then
    assertThat(((Failed) result.get(ref)).code()).isEqualTo(SecretErrorCode.INVALID_REF);
  }

  @Test
  void shouldThrowUnavailableOnConnectivityError() {
    // given
    final var store = new AwsSecretsManagerSecretStore(client, "");
    when(client.getSecretValue(any(GetSecretValueRequest.class)))
        .thenThrow(SdkClientException.create("connection refused"));

    // when / then
    assertThatThrownBy(() -> store.resolve(Set.of("any")))
        .isInstanceOf(SecretStoreUnavailableException.class);
  }

  @Test
  void shouldThrowUnavailableForUnrecognizedServiceError() {
    // given — an AWS error code that isn't one of the known per-secret failures (e.g. throttling
    // exhausted, internal service error) must propagate as a store-wide failure, not be silently
    // swallowed as a per-secret Failed result
    final var store = new AwsSecretsManagerSecretStore(client, "");
    when(client.getSecretValue(any(GetSecretValueRequest.class)))
        .thenThrow(
            (SecretsManagerException)
                SecretsManagerException.builder()
                    .awsErrorDetails(
                        AwsErrorDetails.builder()
                            .errorCode("InternalServiceErrorException")
                            .build())
                    .message("internal error")
                    .build());

    // when / then
    assertThatThrownBy(() -> store.resolve(Set.of("any")))
        .isInstanceOf(SecretStoreUnavailableException.class);
  }

  @Test
  void shouldReturnResultForEveryRefInSet() {
    // given
    final var store = new AwsSecretsManagerSecretStore(client, "");
    when(client.getSecretValue(any(GetSecretValueRequest.class)))
        .thenAnswer(
            invocation -> {
              final GetSecretValueRequest req = invocation.getArgument(0);
              if (req.secretId().equals("known")) {
                return GetSecretValueResponse.builder().secretString("value").build();
              }
              throw (SecretsManagerException)
                  SecretsManagerException.builder()
                      .awsErrorDetails(
                          AwsErrorDetails.builder().errorCode("ResourceNotFoundException").build())
                      .message("missing")
                      .build();
            });

    // when
    final var known = "known";
    final var missing = "missing";
    final var result = store.resolve(Set.of(known, missing));

    // then
    assertThat(result).containsKeys(known, missing);
    assertThat(result.get(known)).isInstanceOf(Resolved.class);
    assertThat(result.get(missing)).isInstanceOf(Failed.class);
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

  // ---- opt-in path: BatchGetSecretValue (batchEnabled=true) ----

  @Test
  void shouldResolveKnownSecretWhenBatchEnabled() {
    // given
    final var store = new AwsSecretsManagerSecretStore(client, "camunda/", true, 20);
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
    final var ref = "db-password";
    final var result = store.resolve(Set.of(ref));

    // then
    assertThat(result.get(ref))
        .isInstanceOf(Resolved.class)
        .extracting(r -> ((Resolved) r).value())
        .isEqualTo("s3cr3t");
  }

  @Test
  void shouldNotCallBatchApiWhenBatchDisabled() {
    // given — the default 2-arg constructor must never call batchGetSecretValue
    final var store = new AwsSecretsManagerSecretStore(client, "");
    when(client.getSecretValue(any(GetSecretValueRequest.class)))
        .thenReturn(GetSecretValueResponse.builder().secretString("v").build());

    // when
    store.resolve(Set.of("token"));

    // then
    verify(client, times(0)).batchGetSecretValue(any(BatchGetSecretValueRequest.class));
  }

  @Test
  void shouldReturnNotFoundForMissingSecretWhenBatchEnabled() {
    // given
    final var store = new AwsSecretsManagerSecretStore(client, "", true, 20);
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
    final var ref = "missing";
    final var result = store.resolve(Set.of(ref));

    // then
    assertThat(result.get(ref)).isInstanceOf(Failed.class);
    assertThat(((Failed) result.get(ref)).code()).isEqualTo(SecretErrorCode.NOT_FOUND);
  }

  @Test
  void shouldReturnInvalidRefForInvalidParameterWhenBatchEnabled() {
    // given
    final var store = new AwsSecretsManagerSecretStore(client, "", true, 20);
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
    final var ref = "bad";
    final var result = store.resolve(Set.of(ref));

    // then
    assertThat(((Failed) result.get(ref)).code()).isEqualTo(SecretErrorCode.INVALID_REF);
  }

  @Test
  void shouldReturnUnreadableForUnrecognizedBatchErrorCodeWithoutAbortingOtherItems() {
    // given — one item has an error code the classifier doesn't recognize, the other resolves
    final var store = new AwsSecretsManagerSecretStore(client, "", true, 20);
    when(client.batchGetSecretValue(any(BatchGetSecretValueRequest.class)))
        .thenReturn(
            BatchGetSecretValueResponse.builder()
                .errors(
                    APIErrorType.builder()
                        .secretId("mystery")
                        .errorCode("SomeNewAwsExceptionType")
                        .message("unexpected")
                        .build())
                .secretValues(SecretValueEntry.builder().name("ok").secretString("value").build())
                .build());

    // when
    final var result = store.resolve(Set.of("mystery", "ok"));

    // then — the unrecognized code doesn't mask as INVALID_REF, and doesn't abort the sibling
    assertThat(((Failed) result.get("mystery")).code()).isEqualTo(SecretErrorCode.UNREADABLE);
    assertThat(result.get("ok")).isInstanceOf(Resolved.class);
  }

  @Test
  void shouldReturnAccessDeniedForOnePerItemDeniedSecretWithoutAbortingOtherItems() {
    // given — one specific secret is denied via a per-item error entry (as opposed to the whole
    // batch call being denied), the other resolves
    final var store = new AwsSecretsManagerSecretStore(client, "", true, 20);
    when(client.batchGetSecretValue(any(BatchGetSecretValueRequest.class)))
        .thenReturn(
            BatchGetSecretValueResponse.builder()
                .errors(
                    APIErrorType.builder()
                        .secretId("denied")
                        .errorCode("AccessDeniedException")
                        .message("no permission for this secret")
                        .build())
                .secretValues(SecretValueEntry.builder().name("ok").secretString("value").build())
                .build());

    // when
    final var result = store.resolve(Set.of("denied", "ok"));

    // then — only the denied secret fails, the sibling still resolves
    assertThat(((Failed) result.get("denied")).code()).isEqualTo(SecretErrorCode.ACCESS_DENIED);
    assertThat(result.get("ok")).isInstanceOf(Resolved.class);
  }

  @Test
  void shouldReturnAccessDeniedForPerItemDecryptionFailureWhenBatchEnabled() {
    // given
    final var store = new AwsSecretsManagerSecretStore(client, "", true, 20);
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
    final var ref = "secret";
    final var result = store.resolve(Set.of(ref));

    // then
    assertThat(((Failed) result.get(ref)).code()).isEqualTo(SecretErrorCode.ACCESS_DENIED);
  }

  @Test
  void shouldReturnAccessDeniedWhenWholeBatchCallIsDenied() {
    // given — the whole batch call is denied (e.g. IAM policy blocks the store's secret prefix
    // entirely), as opposed to a single secret being denied via a per-item error entry
    final var store = new AwsSecretsManagerSecretStore(client, "", true, 20);
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
    final var ref = "secret";
    final var result = store.resolve(Set.of(ref));

    // then — message distinguishes this from a per-secret denial, not just the same generic text
    assertThat(((Failed) result.get(ref)).code()).isEqualTo(SecretErrorCode.ACCESS_DENIED);
    assertThat(((Failed) result.get(ref)).message()).contains("entire batch request was denied");
  }

  @Test
  void shouldReturnInvalidRefForBinaryOnlySecretWhenBatchEnabled() {
    // given — a secret with no string value (binary secret)
    final var store = new AwsSecretsManagerSecretStore(client, "", true, 20);
    when(client.batchGetSecretValue(any(BatchGetSecretValueRequest.class)))
        .thenReturn(
            BatchGetSecretValueResponse.builder()
                .secretValues(SecretValueEntry.builder().name("binary").build())
                .build());

    // when
    final var ref = "binary";
    final var result = store.resolve(Set.of(ref));

    // then
    assertThat(((Failed) result.get(ref)).code()).isEqualTo(SecretErrorCode.INVALID_REF);
  }

  @Test
  void shouldThrowUnavailableOnConnectivityErrorWhenBatchEnabled() {
    // given
    final var store = new AwsSecretsManagerSecretStore(client, "", true, 20);
    when(client.batchGetSecretValue(any(BatchGetSecretValueRequest.class)))
        .thenThrow(SdkClientException.create("connection refused"));

    // when / then
    assertThatThrownBy(() -> store.resolve(Set.of("any")))
        .isInstanceOf(SecretStoreUnavailableException.class);
  }

  @Test
  void shouldReturnResultForEveryRefInBatch() {
    // given
    final var store = new AwsSecretsManagerSecretStore(client, "", true, 20);
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
    final var known = "known";
    final var missing = "missing";
    final var result = store.resolve(Set.of(known, missing));

    // then
    assertThat(result).containsKeys(known, missing);
    assertThat(result.get(known)).isInstanceOf(Resolved.class);
    assertThat(result.get(missing)).isInstanceOf(Failed.class);
  }

  @Test
  void shouldGuaranteeResultForEveryRefEvenWhenAwsOmitsOne() {
    // given — AWS returns neither a value nor an error entry for one requested id
    final var store = new AwsSecretsManagerSecretStore(client, "", true, 20);
    when(client.batchGetSecretValue(any(BatchGetSecretValueRequest.class)))
        .thenReturn(
            BatchGetSecretValueResponse.builder()
                .secretValues(
                    SecretValueEntry.builder().name("known").secretString("value").build())
                .build());

    // when
    final var known = "known";
    final var omitted = "omitted";
    final var result = store.resolve(Set.of(known, omitted));

    // then
    assertThat(result.get(known)).isInstanceOf(Resolved.class);
    assertThat(result.get(omitted)).isInstanceOf(Failed.class);
    assertThat(((Failed) result.get(omitted)).code()).isEqualTo(SecretErrorCode.NOT_FOUND);
  }

  @Test
  void shouldBatchMoreThan20RefsAcrossMultipleCalls() {
    // given 25 refs, one more than two full 20-id and 5-id AWS BatchGetSecretValue batches
    final var store = new AwsSecretsManagerSecretStore(client, "", true, 20);
    final var refs =
        IntStream.range(0, 25)
            .mapToObj(i -> "secret-" + i)
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
  void shouldRespectConfiguredBatchSizeSmallerThanAwsLimit() {
    // given 12 refs with a configured batch size of 5: 5 + 5 + 2
    final var store = new AwsSecretsManagerSecretStore(client, "", true, 5);
    final var refs =
        IntStream.range(0, 12)
            .mapToObj(i -> "secret-" + i)
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
    verify(client, times(3)).batchGetSecretValue(captor.capture());
    assertThat(captor.getAllValues())
        .extracting(req -> req.secretIdList().size())
        .containsExactlyInAnyOrder(5, 5, 2);
    assertThat(result).hasSize(12);
  }

  @Test
  void shouldRejectBatchSizeOutsideValidRange() {
    // when / then
    assertThatThrownBy(() -> new AwsSecretsManagerSecretStore(client, "", true, 21))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("batchSize");
    assertThatThrownBy(() -> new AwsSecretsManagerSecretStore(client, "", true, 0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("batchSize");
  }

  // ---- list(), unaffected by batching ----

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
    assertThat(refs).containsExactlyInAnyOrder("db", "api");
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
    assertThat(refs).containsExactlyInAnyOrder("a", "b");
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
