/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.secretstore.azure;

import static org.assertj.core.api.Assertions.assertThat;

import com.azure.core.exception.HttpResponseException;
import com.azure.core.http.HttpHeaders;
import com.azure.core.http.HttpMethod;
import com.azure.core.http.HttpRequest;
import com.azure.core.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.Map;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class AzureKeyVaultErrorsTest {

  @Test
  void shouldTellADisabledSecretFromAMissingPermission() {
    // given
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
    final var missingPermission =
        new HttpResponseException(
            "Status code 403",
            response(403),
            Map.of("error", Map.of("code", "Forbidden", "message", "Caller is not authorized")));
    final var forbiddenByRbac =
        new HttpResponseException(
            "Status code 403",
            response(403),
            Map.of(
                "error",
                Map.of("code", "Forbidden", "innererror", Map.of("code", "ForbiddenByRbac"))));

    // then
    assertThat(AzureKeyVaultErrors.isSecretDisabled(disabled)).isTrue();
    assertThat(AzureKeyVaultErrors.isSecretDisabled(missingPermission)).isFalse();
    assertThat(AzureKeyVaultErrors.isSecretDisabled(forbiddenByRbac)).isFalse();
  }

  @Test
  void shouldRecogniseNestedAndUnnormalizedDisabledErrorCodes() {
    // given — Key Vault's OData Error model nests innererror recursively from general to specific
    final var nested =
        new HttpResponseException(
            "Status code 403",
            response(403),
            Map.of(
                "error",
                Map.of(
                    "code",
                    "Forbidden",
                    "innererror",
                    Map.of(
                        "code",
                        "ForbiddenByPolicy",
                        "innererror",
                        Map.of("code", " secretdisabled ")))));

    // then
    assertThat(AzureKeyVaultErrors.isSecretDisabled(nested)).isTrue();
  }

  @Test
  void shouldRejectNon403OrMalformedErrorBodies() {
    // given
    final var disabledBody =
        Map.of(
            "error", Map.of("code", "Forbidden", "innererror", Map.of("code", "SecretDisabled")));

    // then
    assertThat(
            AzureKeyVaultErrors.isSecretDisabled(
                new HttpResponseException("Status code 400", response(400), disabledBody)))
        .isFalse();
    assertThat(
            AzureKeyVaultErrors.isSecretDisabled(
                new HttpResponseException("Status code 404", response(404), disabledBody)))
        .isFalse();
    assertThat(
            AzureKeyVaultErrors.isSecretDisabled(
                new HttpResponseException("no response", null, disabledBody)))
        .isFalse();
    assertThat(
            AzureKeyVaultErrors.isSecretDisabled(
                new HttpResponseException("no body", response(403), null)))
        .isFalse();
    assertThat(
            AzureKeyVaultErrors.isSecretDisabled(
                new HttpResponseException("plain text", response(403), "Forbidden")))
        .isFalse();
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
