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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.camunda.secretstore.SecretErrorCode;
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
import software.amazon.awssdk.services.secretsmanager.model.SecretsManagerException;

@ExtendWith(MockitoExtension.class)
class ContainerSecretResolverTest {

  @Mock private SecretsManagerClient client;

  @Test
  void shouldResolveEmptyMapWithoutFetchingContainerSecretForEmptyNames() {
    // given
    final var resolver = new ContainerSecretResolver(client, "camunda/", "app-config");

    // when
    final var result = resolver.resolve(Set.of());

    // then
    assertThat(result).isEmpty();
    verifyNoInteractions(client);
  }

  @Test
  void shouldResolveKeyFromContainerJson() {
    // given
    final var resolver = new ContainerSecretResolver(client, "camunda/", "app-config");
    when(client.getSecretValue(any(GetSecretValueRequest.class)))
        .thenReturn(
            GetSecretValueResponse.builder()
                .secretString("{\"db-password\":\"s3cr3t\",\"api-token\":\"tok3n\"}")
                .build());

    // when
    final var result = resolver.resolve(Set.of("db-password", "api-token"));

    // then
    assertThat(result.get("db-password"))
        .isInstanceOf(Resolved.class)
        .extracting(r -> ((Resolved) r).value())
        .isEqualTo("s3cr3t");
    assertThat(result.get("api-token"))
        .isInstanceOf(Resolved.class)
        .extracting(r -> ((Resolved) r).value())
        .isEqualTo("tok3n");
  }

  @Test
  void shouldReturnNotFoundForMissingKey() {
    // given
    final var resolver = new ContainerSecretResolver(client, "camunda/", "app-config");
    when(client.getSecretValue(any(GetSecretValueRequest.class)))
        .thenReturn(GetSecretValueResponse.builder().secretString("{\"other\":\"v\"}").build());

    // when
    final var result = resolver.resolve(Set.of("missing"));

    // then
    assertThat(((Failed) result.get("missing")).code()).isEqualTo(SecretErrorCode.NOT_FOUND);
  }

  @Test
  void shouldReturnInvalidRefForNonStringValue() {
    // given
    final var resolver = new ContainerSecretResolver(client, "camunda/", "app-config");
    when(client.getSecretValue(any(GetSecretValueRequest.class)))
        .thenReturn(GetSecretValueResponse.builder().secretString("{\"key\":42}").build());

    // when
    final var result = resolver.resolve(Set.of("key"));

    // then
    assertThat(((Failed) result.get("key")).code()).isEqualTo(SecretErrorCode.INVALID_REF);
  }

  @Test
  void shouldReturnInvalidRefForMalformedJson() {
    // given
    final var resolver = new ContainerSecretResolver(client, "camunda/", "app-config");
    when(client.getSecretValue(any(GetSecretValueRequest.class)))
        .thenReturn(GetSecretValueResponse.builder().secretString("not-json").build());

    // when
    final var result = resolver.resolve(Set.of("key"));

    // then
    assertThat(((Failed) result.get("key")).code()).isEqualTo(SecretErrorCode.INVALID_REF);
  }

  @Test
  void shouldReturnInvalidRefForAllKeysWhenContainerSecretIsJsonArrayNotObject() {
    // given — well-formed JSON, but not the expected object-of-key-value-pairs shape
    final var resolver = new ContainerSecretResolver(client, "camunda/", "app-config");
    when(client.getSecretValue(any(GetSecretValueRequest.class)))
        .thenReturn(
            GetSecretValueResponse.builder()
                .secretString("[\"db-password\",\"api-token\"]")
                .build());

    // when
    final var result = resolver.resolve(Set.of("db-password", "api-token"));

    // then
    assertThat(((Failed) result.get("db-password")).code()).isEqualTo(SecretErrorCode.INVALID_REF);
    assertThat(((Failed) result.get("api-token")).code()).isEqualTo(SecretErrorCode.INVALID_REF);
  }

  @Test
  void shouldReturnInvalidRefForAllKeysWhenContainerSecretIsJsonScalarNotObject() {
    // given — well-formed JSON, but a bare string rather than an object
    final var resolver = new ContainerSecretResolver(client, "camunda/", "app-config");
    when(client.getSecretValue(any(GetSecretValueRequest.class)))
        .thenReturn(GetSecretValueResponse.builder().secretString("\"just-a-string\"").build());

    // when
    final var result = resolver.resolve(Set.of("key"));

    // then
    assertThat(((Failed) result.get("key")).code()).isEqualTo(SecretErrorCode.INVALID_REF);
  }

  @Test
  void shouldReturnInvalidRefForBinaryOnlyContainerSecret() {
    // given — a secret with no string value (binary secret)
    final var resolver = new ContainerSecretResolver(client, "camunda/", "app-config");
    when(client.getSecretValue(any(GetSecretValueRequest.class)))
        .thenReturn(GetSecretValueResponse.builder().build());

    // when
    final var result = resolver.resolve(Set.of("key"));

    // then
    assertThat(((Failed) result.get("key")).code()).isEqualTo(SecretErrorCode.INVALID_REF);
  }

  @Test
  void shouldFailAllKeysWhenContainerSecretNotFound() {
    // given
    final var resolver = new ContainerSecretResolver(client, "camunda/", "app-config");
    when(client.getSecretValue(any(GetSecretValueRequest.class)))
        .thenThrow(
            (SecretsManagerException)
                SecretsManagerException.builder()
                    .awsErrorDetails(
                        AwsErrorDetails.builder().errorCode("ResourceNotFoundException").build())
                    .message("missing")
                    .build());

    // when
    final var result = resolver.resolve(Set.of("a", "b"));

    // then
    assertThat(result.get("a")).isInstanceOf(Failed.class);
    assertThat(((Failed) result.get("a")).code()).isEqualTo(SecretErrorCode.NOT_FOUND);
    assertThat(result.get("b")).isInstanceOf(Failed.class);
    assertThat(((Failed) result.get("b")).code()).isEqualTo(SecretErrorCode.NOT_FOUND);
  }

  @Test
  void shouldThrowUnavailableOnConnectivityError() {
    // given
    final var resolver = new ContainerSecretResolver(client, "camunda/", "app-config");
    when(client.getSecretValue(any(GetSecretValueRequest.class)))
        .thenThrow(SdkClientException.create("connection refused"));

    // when / then
    assertThatThrownBy(() -> resolver.resolve(Set.of("key")))
        .isInstanceOf(SecretStoreUnavailableException.class);
  }

  @Test
  void shouldListKeysInContainerJson() {
    // given
    final var resolver = new ContainerSecretResolver(client, "camunda/", "app-config");
    when(client.getSecretValue(any(GetSecretValueRequest.class)))
        .thenReturn(
            GetSecretValueResponse.builder()
                .secretString("{\"db-password\":\"s3cr3t\",\"api-token\":\"tok3n\"}")
                .build());

    // when
    final var keys = resolver.list();

    // then
    assertThat(keys).containsExactlyInAnyOrder("db-password", "api-token");
  }

  @Test
  void shouldReturnEmptyListForBinaryOnlyContainerSecret() {
    // given
    final var resolver = new ContainerSecretResolver(client, "camunda/", "app-config");
    when(client.getSecretValue(any(GetSecretValueRequest.class)))
        .thenReturn(GetSecretValueResponse.builder().build());

    // when
    final var keys = resolver.list();

    // then
    assertThat(keys).isEmpty();
  }

  @Test
  void shouldThrowUnavailableForMalformedJsonInList() {
    // given
    final var resolver = new ContainerSecretResolver(client, "camunda/", "app-config");
    when(client.getSecretValue(any(GetSecretValueRequest.class)))
        .thenReturn(GetSecretValueResponse.builder().secretString("not-json").build());

    // when / then
    assertThatThrownBy(resolver::list)
        .isInstanceOf(SecretStoreUnavailableException.class)
        .hasMessageContaining("not valid JSON");
  }

  @Test
  void shouldThrowUnavailableWhenContainerSecretIsJsonArrayNotObject() {
    // given — well-formed JSON, but not the expected object-of-key-value-pairs shape
    final var resolver = new ContainerSecretResolver(client, "camunda/", "app-config");
    when(client.getSecretValue(any(GetSecretValueRequest.class)))
        .thenReturn(
            GetSecretValueResponse.builder()
                .secretString("[\"db-password\",\"api-token\"]")
                .build());

    // when / then
    assertThatThrownBy(resolver::list)
        .isInstanceOf(SecretStoreUnavailableException.class)
        .hasMessageContaining("is not a JSON object");
  }

  @Test
  void shouldThrowUnavailableWhenContainerSecretIsJsonScalarNotObject() {
    // given — well-formed JSON, but a bare string rather than an object
    final var resolver = new ContainerSecretResolver(client, "camunda/", "app-config");
    when(client.getSecretValue(any(GetSecretValueRequest.class)))
        .thenReturn(GetSecretValueResponse.builder().secretString("\"just-a-string\"").build());

    // when / then
    assertThatThrownBy(resolver::list)
        .isInstanceOf(SecretStoreUnavailableException.class)
        .hasMessageContaining("is not a JSON object");
  }

  @Test
  void shouldPrependPathPrefixToContainerSecretId() {
    // given
    final var resolver = new ContainerSecretResolver(client, "camunda/", "app-config");
    when(client.getSecretValue(any(GetSecretValueRequest.class)))
        .thenReturn(GetSecretValueResponse.builder().secretString("{}").build());

    // when
    resolver.list();

    // then
    verify(client)
        .getSecretValue(GetSecretValueRequest.builder().secretId("camunda/app-config").build());
  }
}
