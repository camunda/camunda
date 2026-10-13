/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.secretstore.azure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.azure.core.exception.HttpResponseException;
import com.azure.core.http.HttpResponse;
import com.azure.core.http.rest.PagedIterable;
import com.azure.security.keyvault.secrets.SecretClient;
import com.azure.security.keyvault.secrets.models.KeyVaultSecret;
import com.azure.security.keyvault.secrets.models.SecretProperties;
import io.camunda.secretstore.SecretErrorCode;
import io.camunda.secretstore.SecretResolutionResult.Failed;
import io.camunda.secretstore.SecretResolutionResult.Resolved;
import io.camunda.secretstore.SecretStoreUnavailableException;
import java.time.Instant;
import java.time.InstantSource;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AzureKeyVaultSecretStoreTest {

  private static final Instant NOW = Instant.parse("2024-01-01T00:00:00Z");
  private static final InstantSource CLOCK = InstantSource.fixed(NOW);

  @Mock private SecretClient client;

  private AzureKeyVaultSecretStore oneByOne(final String prefix) {
    return new AzureKeyVaultSecretStore(client, prefix, null, CLOCK);
  }

  @Test
  void shouldResolveKnownSecret() {
    // given
    final var store = oneByOne("camunda-");
    when(client.getSecret("camunda-db-password"))
        .thenReturn(new KeyVaultSecret("camunda-db-password", "s3cr3t"));

    // when
    final var result = store.resolve(Set.of("db-password"));

    // then
    assertThat(result.get("db-password"))
        .isInstanceOf(Resolved.class)
        .extracting(r -> ((Resolved) r).value())
        .isEqualTo("s3cr3t");
  }

  @Test
  void shouldPrependPathPrefixToSecretName() {
    // given
    final var store = oneByOne("camunda-");
    when(client.getSecret(anyString())).thenReturn(new KeyVaultSecret("camunda-token", "v"));

    // when
    store.resolve(Set.of("token"));

    // then
    final var captor = ArgumentCaptor.forClass(String.class);
    verify(client).getSecret(captor.capture());
    assertThat(captor.getValue()).isEqualTo("camunda-token");
  }

  @Test
  void shouldUseBareNameWhenPrefixIsNull() {
    // given
    final var store = oneByOne(null);
    when(client.getSecret(anyString())).thenReturn(new KeyVaultSecret("token", "v"));

    // when
    store.resolve(Set.of("token"));

    // then
    final var captor = ArgumentCaptor.forClass(String.class);
    verify(client).getSecret(captor.capture());
    assertThat(captor.getValue()).isEqualTo("token");
  }

  @Test
  void shouldResolveOneNamePerCallByDefault() {
    // given a store with no container secret, so it resolves via one getSecret call per reference
    final var store = oneByOne("camunda-");

    // then
    assertThat(store.namesPerCall()).isEqualTo(1);
  }

  @Test
  void shouldRejectReferenceThatCannotFormAValidSecretName() {
    // given a name with characters Azure Key Vault forbids, so it can never exist in the vault
    final var store = oneByOne("camunda-");

    // when
    final var result = store.resolve(Set.of("db_password"));

    // then it is rejected locally without a request
    assertThat(result.get("db_password"))
        .isInstanceOf(Failed.class)
        .extracting(r -> ((Failed) r).code())
        .isEqualTo(SecretErrorCode.INVALID_REF);
    verifyNoInteractions(client);
  }

  @Test
  void shouldReportExpiredSecretAsNotFound() {
    // given a secret that still returns a value but is past its expiry
    final var store = oneByOne("camunda-");
    final var secret = new KeyVaultSecret("camunda-rotated", "old");
    secret
        .getProperties()
        .setExpiresOn(OffsetDateTime.ofInstant(NOW.minusSeconds(1), ZoneOffset.UTC));
    when(client.getSecret("camunda-rotated")).thenReturn(secret);

    // when
    final var result = store.resolve(Set.of("rotated"));

    // then the rotated-out value is never handed back
    assertThat(result.get("rotated"))
        .isInstanceOf(Failed.class)
        .extracting(r -> ((Failed) r).code())
        .isEqualTo(SecretErrorCode.NOT_FOUND);
  }

  @Test
  void shouldReportNotYetActiveSecretAsNotFound() {
    // given a secret whose notBefore is still in the future
    final var store = oneByOne("camunda-");
    final var secret = new KeyVaultSecret("camunda-pending", "future");
    secret
        .getProperties()
        .setNotBefore(OffsetDateTime.ofInstant(NOW.plusSeconds(60), ZoneOffset.UTC));
    when(client.getSecret("camunda-pending")).thenReturn(secret);

    // when
    final var result = store.resolve(Set.of("pending"));

    // then
    assertThat(result.get("pending"))
        .isInstanceOf(Failed.class)
        .extracting(r -> ((Failed) r).code())
        .isEqualTo(SecretErrorCode.NOT_FOUND);
  }

  @Test
  void shouldMapNotFoundStatusToNotFound() {
    // given
    final var store = oneByOne("camunda-");
    when(client.getSecret("camunda-missing")).thenThrow(httpError(404));

    // when
    final var result = store.resolve(Set.of("missing"));

    // then
    assertThat(result.get("missing"))
        .isInstanceOf(Failed.class)
        .extracting(r -> ((Failed) r).code())
        .isEqualTo(SecretErrorCode.NOT_FOUND);
  }

  @Test
  void shouldMapForbiddenStatusToAccessDenied() {
    // given
    final var store = oneByOne("camunda-");
    when(client.getSecret("camunda-secret")).thenThrow(httpError(403));

    // when
    final var result = store.resolve(Set.of("secret"));

    // then
    assertThat(result.get("secret"))
        .isInstanceOf(Failed.class)
        .extracting(r -> ((Failed) r).code())
        .isEqualTo(SecretErrorCode.ACCESS_DENIED);
  }

  @Test
  void shouldSurfaceThrottlingAsStoreUnavailable() {
    // given a 429 is a store-wide condition, not a per-secret one
    final var store = oneByOne("camunda-");
    when(client.getSecret("camunda-secret")).thenThrow(httpError(429));

    // when / then
    assertThatThrownBy(() -> store.resolve(Set.of("secret")))
        .isInstanceOf(SecretStoreUnavailableException.class);
  }

  @Test
  void shouldListSecretsStrippingPrefix() {
    // given
    final var store = oneByOne("camunda-");
    when(client.listPropertiesOfSecrets())
        .thenReturn(pagedProperties("camunda-db-password", "camunda-api-token", "other-app"));

    // when
    final var names = store.list();

    // then only prefixed secrets are returned, with the prefix stripped
    assertThat(names).containsExactlyInAnyOrder("db-password", "api-token");
  }

  @Test
  void shouldResolveFromContainerSecret() {
    // given a container secret holding a JSON object of key/value pairs
    final var store = new AzureKeyVaultSecretStore(client, "camunda-", "app-config", CLOCK);
    when(client.getSecret("camunda-app-config"))
        .thenReturn(
            new KeyVaultSecret(
                "camunda-app-config", "{\"db-password\":\"s3cr3t\",\"token\":\"t\"}"));

    // when
    final var result = store.resolve(Set.of("db-password"));

    // then
    assertThat(result.get("db-password"))
        .isInstanceOf(Resolved.class)
        .extracting(r -> ((Resolved) r).value())
        .isEqualTo("s3cr3t");
    assertThat(store.namesPerCall()).isEqualTo(Integer.MAX_VALUE);
  }

  @Test
  void shouldFailContainerKeyMissingAsNotFound() {
    // given
    final var store = new AzureKeyVaultSecretStore(client, "camunda-", "app-config", CLOCK);
    when(client.getSecret("camunda-app-config"))
        .thenReturn(new KeyVaultSecret("camunda-app-config", "{\"db-password\":\"s3cr3t\"}"));

    // when
    final var result = store.resolve(Set.of("absent"));

    // then
    assertThat(result.get("absent"))
        .isInstanceOf(Failed.class)
        .extracting(r -> ((Failed) r).code())
        .isEqualTo(SecretErrorCode.NOT_FOUND);
  }

  @Test
  void shouldFailAllContainerKeysWhenNotJson() {
    // given a container secret whose value is not valid JSON
    final var store = new AzureKeyVaultSecretStore(client, "camunda-", "app-config", CLOCK);
    when(client.getSecret("camunda-app-config"))
        .thenReturn(new KeyVaultSecret("camunda-app-config", "not-json"));

    // when
    final var result = store.resolve(Set.of("db-password"));

    // then
    assertThat(result.get("db-password"))
        .isInstanceOf(Failed.class)
        .extracting(r -> ((Failed) r).code())
        .isEqualTo(SecretErrorCode.INVALID_REF);
  }

  @Test
  void shouldListContainerKeys() {
    // given
    final var store = new AzureKeyVaultSecretStore(client, "camunda-", "app-config", CLOCK);
    when(client.getSecret("camunda-app-config"))
        .thenReturn(
            new KeyVaultSecret(
                "camunda-app-config", "{\"db-password\":\"s3cr3t\",\"token\":\"t\"}"));

    // when
    final var names = store.list();

    // then
    assertThat(names).containsExactlyInAnyOrder("db-password", "token");
  }

  @Test
  void shouldWarnAndContinueWhenStartupProbeFails() {
    // given a resolver whose connectivity probe throws, mimicking an unreachable vault at boot
    final AzureSecretResolver resolver = mock(AzureSecretResolver.class);
    org.mockito.Mockito.doThrow(new RuntimeException("unreachable"))
        .when(resolver)
        .validateConnectivity();

    // when / then — the store is still built; the error only surfaces on first use
    assertThatCode(() -> AzureKeyVaultSecretStore.validateConnectivity(resolver))
        .doesNotThrowAnyException();
  }

  @Test
  void shouldCloseWithoutError() {
    // given there is no client handle to release for Azure's SecretClient
    final var store = oneByOne("camunda-");

    // when / then
    assertThatCode(store::close).doesNotThrowAnyException();
  }

  private static HttpResponseException httpError(final int statusCode) {
    return new HttpResponseException("status " + statusCode, new TestHttpResponse(statusCode));
  }

  /**
   * A minimal concrete {@link HttpResponse} carrying only a status code. The Azure model classes
   * cannot be stubbed under the repository's Mockito mock maker (their accessors run real code), so
   * error-classification tests build a real response instead of mocking one.
   */
  private static final class TestHttpResponse extends HttpResponse {
    private final int statusCode;

    private TestHttpResponse(final int statusCode) {
      super(null);
      this.statusCode = statusCode;
    }

    @Override
    public int getStatusCode() {
      return statusCode;
    }

    @Override
    public String getHeaderValue(final String name) {
      return null;
    }

    @Override
    public com.azure.core.http.HttpHeaders getHeaders() {
      return new com.azure.core.http.HttpHeaders();
    }

    @Override
    public reactor.core.publisher.Flux<java.nio.ByteBuffer> getBody() {
      return reactor.core.publisher.Flux.empty();
    }

    @Override
    public reactor.core.publisher.Mono<byte[]> getBodyAsByteArray() {
      return reactor.core.publisher.Mono.empty();
    }

    @Override
    public reactor.core.publisher.Mono<String> getBodyAsString() {
      return reactor.core.publisher.Mono.empty();
    }

    @Override
    public reactor.core.publisher.Mono<String> getBodyAsString(
        final java.nio.charset.Charset charset) {
      return reactor.core.publisher.Mono.empty();
    }
  }

  private static PagedIterable<SecretProperties> pagedProperties(final String... names) {
    final List<SecretProperties> props =
        java.util.Arrays.stream(names)
            .map(name -> new KeyVaultSecret(name, "x").getProperties())
            .toList();
    final com.azure.core.http.rest.PagedResponse<SecretProperties> page =
        new com.azure.core.http.rest.PagedResponseBase<>(
            null, 200, new com.azure.core.http.HttpHeaders(), props, null, null);
    return new PagedIterable<>(
        new com.azure.core.http.rest.PagedFlux<>(() -> reactor.core.publisher.Mono.just(page)));
  }
}
