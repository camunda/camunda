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
import static org.mockito.Mockito.when;

import io.camunda.secretstore.SecretErrorCode;
import io.camunda.secretstore.SecretResolutionResult;
import io.camunda.secretstore.SecretResolutionResult.Failed;
import io.camunda.secretstore.SecretResolutionResult.Resolved;
import io.camunda.secretstore.SecretStoreUnavailableException;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;
import software.amazon.awssdk.services.secretsmanager.model.InvalidParameterException;
import software.amazon.awssdk.services.secretsmanager.model.ListSecretsRequest;
import software.amazon.awssdk.services.secretsmanager.model.ListSecretsResponse;
import software.amazon.awssdk.services.secretsmanager.model.ResourceNotFoundException;
import software.amazon.awssdk.services.secretsmanager.model.SecretListEntry;
import software.amazon.awssdk.services.secretsmanager.model.SecretsManagerException;

@ExtendWith(MockitoExtension.class)
class AwsSecretsManagerSecretStoreTest {

  @Mock private SecretsManagerClient client;

  @Test
  void shouldResolveKnownSecret() {
    // given
    final var store = new AwsSecretsManagerSecretStore(client, "camunda/");
    when(client.getSecretValue(any(GetSecretValueRequest.class)))
        .thenReturn(GetSecretValueResponse.builder().secretString("s3cr3t").build());

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
    when(client.getSecretValue(any(GetSecretValueRequest.class)))
        .thenReturn(GetSecretValueResponse.builder().secretString("v").build());

    // when
    store.resolve(Set.of(new AwsSecretsManagerSecretReference("token")));

    // then
    final var captor = org.mockito.ArgumentCaptor.forClass(GetSecretValueRequest.class);
    org.mockito.Mockito.verify(client).getSecretValue(captor.capture());
    assertThat(captor.getValue().secretId()).isEqualTo("camunda/token");
  }

  @Test
  void shouldUseBareNameWhenPrefixIsNull() {
    // given
    final var store = new AwsSecretsManagerSecretStore(client, null);
    when(client.getSecretValue(any(GetSecretValueRequest.class)))
        .thenReturn(GetSecretValueResponse.builder().secretString("v").build());

    // when
    store.resolve(Set.of(new AwsSecretsManagerSecretReference("token")));

    // then
    final var captor = org.mockito.ArgumentCaptor.forClass(GetSecretValueRequest.class);
    org.mockito.Mockito.verify(client).getSecretValue(captor.capture());
    assertThat(captor.getValue().secretId()).isEqualTo("token");
  }

  @Test
  void shouldReturnNotFoundForMissingSecret() {
    // given
    final var store = new AwsSecretsManagerSecretStore(client, "");
    when(client.getSecretValue(any(GetSecretValueRequest.class)))
        .thenThrow(ResourceNotFoundException.builder().message("missing").build());

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
    when(client.getSecretValue(any(GetSecretValueRequest.class)))
        .thenThrow(InvalidParameterException.builder().message("bad").build());

    // when
    final var ref = new AwsSecretsManagerSecretReference("bad");
    final var result = store.resolve(Set.of(ref));

    // then
    assertThat(((Failed) result.get(ref)).code()).isEqualTo(SecretErrorCode.INVALID_REF);
  }

  @Test
  void shouldReturnAccessDeniedForAccessDeniedError() {
    // given
    final var store = new AwsSecretsManagerSecretStore(client, "");
    final var e =
        (SecretsManagerException)
            SecretsManagerException.builder()
                .awsErrorDetails(
                    AwsErrorDetails.builder().errorCode("AccessDeniedException").build())
                .statusCode(400)
                .message("denied")
                .build();
    when(client.getSecretValue(any(GetSecretValueRequest.class))).thenThrow(e);

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
    when(client.getSecretValue(any(GetSecretValueRequest.class)))
        .thenReturn(GetSecretValueResponse.builder().build());

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
    when(client.getSecretValue(any(GetSecretValueRequest.class)))
        .thenThrow(SdkClientException.create("connection refused"));

    // when / then
    assertThatThrownBy(() -> store.resolve(Set.of(new AwsSecretsManagerSecretReference("any"))))
        .isInstanceOf(SecretStoreUnavailableException.class);
  }

  @Test
  void shouldReturnResultForEveryRefInBatch() {
    // given
    final var store = new AwsSecretsManagerSecretStore(client, "");
    when(client.getSecretValue(any(GetSecretValueRequest.class)))
        .thenAnswer(
            invocation -> {
              final GetSecretValueRequest req = invocation.getArgument(0);
              if (req.secretId().equals("known")) {
                return GetSecretValueResponse.builder().secretString("value").build();
              }
              throw ResourceNotFoundException.builder().message("missing").build();
            });

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
  void shouldReturnEmptyMapForEmptyRefs() {
    // given
    final var store = new AwsSecretsManagerSecretStore(client, "");

    // when
    final var result = store.resolve(Set.of());

    // then
    assertThat(result).isEmpty();
    org.mockito.Mockito.verifyNoInteractions(client);
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

  // ---- JSON container mode ----

  @Test
  void shouldResolveMultipleKeysFromOneContainerFetch() {
    // given
    final var store = new AwsSecretsManagerSecretStore(client, "camunda/", "app-config");
    when(client.getSecretValue(any(GetSecretValueRequest.class)))
        .thenReturn(
            GetSecretValueResponse.builder()
                .secretString("{\"DB_PASSWORD\":\"s3cr3t\",\"API_KEY\":\"k3y\"}")
                .build());

    // when
    final var dbPassword = new AwsSecretsManagerSecretReference("DB_PASSWORD");
    final var apiKey = new AwsSecretsManagerSecretReference("API_KEY");
    final var result = store.resolve(Set.of(dbPassword, apiKey));

    // then — only one GetSecretValue call for both keys
    assertThat(result.get(dbPassword))
        .isInstanceOf(Resolved.class)
        .extracting(r -> ((Resolved) r).value())
        .isEqualTo("s3cr3t");
    assertThat(result.get(apiKey))
        .isInstanceOf(Resolved.class)
        .extracting(r -> ((Resolved) r).value())
        .isEqualTo("k3y");
    org.mockito.Mockito.verify(client, org.mockito.Mockito.times(1))
        .getSecretValue(any(GetSecretValueRequest.class));
  }

  @Test
  void shouldFetchContainerAtPrefixedSecretId() {
    // given
    final var store = new AwsSecretsManagerSecretStore(client, "camunda/", "app-config");
    when(client.getSecretValue(any(GetSecretValueRequest.class)))
        .thenReturn(GetSecretValueResponse.builder().secretString("{\"K\":\"v\"}").build());

    // when
    store.resolve(Set.of(new AwsSecretsManagerSecretReference("K")));

    // then
    final var captor = org.mockito.ArgumentCaptor.forClass(GetSecretValueRequest.class);
    org.mockito.Mockito.verify(client).getSecretValue(captor.capture());
    assertThat(captor.getValue().secretId()).isEqualTo("camunda/app-config");
  }

  @Test
  void shouldReturnNotFoundForMissingKeyInContainer() {
    // given
    final var store = new AwsSecretsManagerSecretStore(client, "", "app-config");
    when(client.getSecretValue(any(GetSecretValueRequest.class)))
        .thenReturn(GetSecretValueResponse.builder().secretString("{\"OTHER\":\"v\"}").build());

    // when
    final var ref = new AwsSecretsManagerSecretReference("MISSING");
    final var result = store.resolve(Set.of(ref));

    // then
    assertThat(((Failed) result.get(ref)).code()).isEqualTo(SecretErrorCode.NOT_FOUND);
  }

  @Test
  void shouldReturnInvalidRefForNonStringValueInContainer() {
    // given — a nested object value isn't supported
    final var store = new AwsSecretsManagerSecretStore(client, "", "app-config");
    when(client.getSecretValue(any(GetSecretValueRequest.class)))
        .thenReturn(
            GetSecretValueResponse.builder().secretString("{\"NESTED\":{\"a\":1}}").build());

    // when
    final var ref = new AwsSecretsManagerSecretReference("NESTED");
    final var result = store.resolve(Set.of(ref));

    // then
    assertThat(((Failed) result.get(ref)).code()).isEqualTo(SecretErrorCode.INVALID_REF);
  }

  @Test
  void shouldReturnInvalidRefForAllKeysWhenContainerIsNotValidJson() {
    // given
    final var store = new AwsSecretsManagerSecretStore(client, "", "app-config");
    when(client.getSecretValue(any(GetSecretValueRequest.class)))
        .thenReturn(GetSecretValueResponse.builder().secretString("not json").build());

    // when
    final var a = new AwsSecretsManagerSecretReference("A");
    final var b = new AwsSecretsManagerSecretReference("B");
    final var result = store.resolve(Set.of(a, b));

    // then — the shared parse failure applies to every requested key
    assertThat(((Failed) result.get(a)).code()).isEqualTo(SecretErrorCode.INVALID_REF);
    assertThat(((Failed) result.get(b)).code()).isEqualTo(SecretErrorCode.INVALID_REF);
  }

  @Test
  void shouldReturnNotFoundForAllKeysWhenContainerSecretMissing() {
    // given
    final var store = new AwsSecretsManagerSecretStore(client, "", "app-config");
    when(client.getSecretValue(any(GetSecretValueRequest.class)))
        .thenThrow(ResourceNotFoundException.builder().message("missing").build());

    // when
    final var ref = new AwsSecretsManagerSecretReference("K");
    final var result = store.resolve(Set.of(ref));

    // then
    assertThat(((Failed) result.get(ref)).code()).isEqualTo(SecretErrorCode.NOT_FOUND);
  }

  @Test
  void shouldThrowUnavailableWhenContainerFetchHitsConnectivityError() {
    // given
    final var store = new AwsSecretsManagerSecretStore(client, "", "app-config");
    when(client.getSecretValue(any(GetSecretValueRequest.class)))
        .thenThrow(SdkClientException.create("connection refused"));

    // when / then
    assertThatThrownBy(() -> store.resolve(Set.of(new AwsSecretsManagerSecretReference("K"))))
        .isInstanceOf(SecretStoreUnavailableException.class);
  }

  @Test
  void shouldListKeysFromContainer() {
    // given
    final var store = new AwsSecretsManagerSecretStore(client, "", "app-config");
    when(client.getSecretValue(any(GetSecretValueRequest.class)))
        .thenReturn(
            GetSecretValueResponse.builder()
                .secretString("{\"DB_PASSWORD\":\"s3cr3t\",\"API_KEY\":\"k3y\"}")
                .build());

    // when
    final var refs = store.list();

    // then
    assertThat(refs)
        .containsExactlyInAnyOrder(
            new AwsSecretsManagerSecretReference("DB_PASSWORD"),
            new AwsSecretsManagerSecretReference("API_KEY"));
  }

  @Test
  void shouldThrowUnavailableWhenListingInvalidJsonContainer() {
    // given
    final var store = new AwsSecretsManagerSecretStore(client, "", "app-config");
    when(client.getSecretValue(any(GetSecretValueRequest.class)))
        .thenReturn(GetSecretValueResponse.builder().secretString("not json").build());

    // when / then
    assertThatThrownBy(store::list).isInstanceOf(SecretStoreUnavailableException.class);
  }

  @Test
  void shouldNotCallListSecretsWhenContainerModeEnabled() {
    // given — container mode must never fall back to the flat ListSecrets scan
    final var store = new AwsSecretsManagerSecretStore(client, "", "app-config");
    when(client.getSecretValue(any(GetSecretValueRequest.class)))
        .thenReturn(GetSecretValueResponse.builder().secretString("{}").build());

    // when
    store.list();

    // then
    org.mockito.Mockito.verify(client, org.mockito.Mockito.times(0))
        .listSecrets(any(ListSecretsRequest.class));
  }
}
