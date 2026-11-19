/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.secretstore.azure;

import static io.camunda.secretstore.SecretErrorCode.ACCESS_DENIED;
import static io.camunda.secretstore.SecretErrorCode.INVALID_REF;
import static io.camunda.secretstore.SecretErrorCode.NOT_FOUND;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.azure.core.exception.ClientAuthenticationException;
import com.azure.core.exception.HttpResponseException;
import com.azure.core.http.HttpHeaders;
import com.azure.core.http.HttpMethod;
import com.azure.core.http.HttpRequest;
import com.azure.core.http.HttpResponse;
import io.camunda.secretstore.SecretStoreUnavailableException;
import java.io.UncheckedIOException;
import java.net.ConnectException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.Map;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class AzureKeyVaultErrorsTest {

  @Test
  void shouldMapPerSecretAnswersToTheirErrorCodes() {
    assertThat(AzureKeyVaultErrors.classify(answer(404, "SecretNotFound"))).isEqualTo(NOT_FOUND);
    assertThat(AzureKeyVaultErrors.classify(answer(403, "Forbidden"))).isEqualTo(ACCESS_DENIED);
    assertThat(AzureKeyVaultErrors.classify(answer(400, "BadParameter"))).isEqualTo(INVALID_REF);
  }

  @Test
  void shouldReportTheStoreUnavailableForEveryOtherStatus() {
    for (final int status : new int[] {401, 408, 429, 500, 501, 502, 503, 504, 409}) {
      assertThatThrownBy(() -> AzureKeyVaultErrors.classify(answer(status, "Error")))
          .as("HTTP %d", status)
          .isInstanceOf(SecretStoreUnavailableException.class)
          .hasMessage(
              "Azure Key Vault is unavailable: Key Vault answered HTTP %d Error: it failed",
              status);
    }
  }

  @Test
  void shouldReportTheStoreUnavailableForAFailedSignInEvenWithA400() {
    // Entra ID answers a rejected client secret with 400, which must not read as a bad name
    final var withAnswer =
        new ClientAuthenticationException("AADSTS7000215: Invalid client secret", response(400));
    final var withoutAnswer = new ClientAuthenticationException("no token", null);

    assertThatThrownBy(() -> AzureKeyVaultErrors.classify(withAnswer))
        .isInstanceOf(SecretStoreUnavailableException.class)
        .hasMessage(
            "Azure Key Vault is unavailable: ClientAuthenticationException: AADSTS7000215:"
                + " Invalid client secret");
    assertThatThrownBy(() -> AzureKeyVaultErrors.classify(withoutAnswer))
        .isInstanceOf(SecretStoreUnavailableException.class);
  }

  @Test
  void shouldReportTheStoreUnavailableWithoutAnAnswer() {
    final var noResponse = new HttpResponseException("connection reset", null);
    final var network = new UncheckedIOException(new ConnectException("Connection refused"));

    assertThatThrownBy(() -> AzureKeyVaultErrors.classify(noResponse))
        .isInstanceOf(SecretStoreUnavailableException.class)
        .hasMessage("Azure Key Vault is unavailable: HttpResponseException: connection reset");
    assertThatThrownBy(() -> AzureKeyVaultErrors.classify(network))
        .isInstanceOf(SecretStoreUnavailableException.class)
        .hasMessageContaining("Connection refused")
        .hasCause(network);
  }

  @Test
  void shouldEndAMessageWithKeyVaultsStatusCodeAndMessage() {
    final var message =
        AzureKeyVaultErrors.messageFor(
            ACCESS_DENIED, "secret", "camunda-db-password", answer(403, "Forbidden"));

    assertThat(message)
        .isEqualTo(
            "Access denied for secret: camunda-db-password"
                + " (Key Vault answered HTTP 403 Forbidden: it failed)");
  }

  @Test
  void shouldTellADisabledSecretFromAMissingPermission() {
    final var disabled =
        new HttpResponseException(
            "Status code 403",
            response(403),
            Map.of(
                "error",
                Map.of(
                    "code",
                    "Forbidden",
                    "message",
                    "Operation get is not allowed on a disabled secret.",
                    "innererror",
                    Map.of("code", "SecretDisabled"))));

    assertThat(AzureKeyVaultErrors.isSecretDisabled(disabled)).isTrue();
    assertThat(AzureKeyVaultErrors.isSecretDisabled(answer(403, "Forbidden"))).isFalse();
  }

  private static HttpResponseException answer(final int status, final String code) {
    return new HttpResponseException(
        "Status code " + status + ", \"{...}\"\nmore",
        response(status),
        Map.of("error", Map.of("code", code, "message", "it failed")));
  }

  private static HttpResponse response(final int status) {
    return new HttpResponse(new HttpRequest(HttpMethod.GET, "https://vault.example/secrets/a")) {
      @Override
      public int getStatusCode() {
        return status;
      }

      @Deprecated
      @Override
      public String getHeaderValue(final String name) {
        return null;
      }

      @Override
      public HttpHeaders getHeaders() {
        return new HttpHeaders();
      }

      @Override
      public Flux<ByteBuffer> getBody() {
        return Flux.empty();
      }

      @Override
      public Mono<byte[]> getBodyAsByteArray() {
        return Mono.empty();
      }

      @Override
      public Mono<String> getBodyAsString() {
        return Mono.empty();
      }

      @Override
      public Mono<String> getBodyAsString(final Charset charset) {
        return Mono.empty();
      }
    };
  }
}
