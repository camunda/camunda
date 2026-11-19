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
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ConnectException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class AzureKeyVaultErrorsTest {

  @Test
  void shouldMapPerSecretAnswersToTheirErrorCodes() {
    assertThat(AzureKeyVaultErrors.classify("a", answer(404, "SecretNotFound")))
        .isEqualTo(NOT_FOUND);
    assertThat(AzureKeyVaultErrors.classify("a", answer(403, "Forbidden")))
        .isEqualTo(ACCESS_DENIED);
    assertThat(AzureKeyVaultErrors.classify("a", answer(400, "BadParameter")))
        .isEqualTo(INVALID_REF);
  }

  @Test
  void shouldReportTheStoreUnavailableForEveryOtherStatus() {
    for (final int status : new int[] {401, 408, 429, 500, 501, 502, 503, 504, 409}) {
      assertThatThrownBy(() -> AzureKeyVaultErrors.classify("a", answer(status, "Error")))
          .as("HTTP %d", status)
          .isInstanceOf(SecretStoreUnavailableException.class)
          .hasMessage(
              "Azure Key Vault is unavailable to read secret a: Key Vault answered HTTP %d Error: it failed",
              status);
    }
  }

  @Test
  void shouldReportTheStoreUnavailableForAFailedSignInEvenWithA400() {
    // Entra ID answers a rejected client secret with 400, which must not read as a bad name
    final var withAnswer =
        new ClientAuthenticationException("AADSTS7000215: Invalid client secret", response(400));
    final var withoutAnswer = new ClientAuthenticationException("no token", null);

    assertThatThrownBy(() -> AzureKeyVaultErrors.classify("a", withAnswer))
        .isInstanceOf(SecretStoreUnavailableException.class)
        .hasMessage(
            "Azure Key Vault is unavailable to read secret a: ClientAuthenticationException: AADSTS7000215:"
                + " Invalid client secret");
    assertThatThrownBy(() -> AzureKeyVaultErrors.classify("a", withoutAnswer))
        .isInstanceOf(SecretStoreUnavailableException.class);
  }

  @Test
  void shouldReportTheStoreUnavailableWithoutAnAnswer() {
    final var noResponse = new HttpResponseException("connection reset", null);
    final var network = new UncheckedIOException(new ConnectException("Connection refused"));

    assertThatThrownBy(() -> AzureKeyVaultErrors.classify("a", noResponse))
        .isInstanceOf(SecretStoreUnavailableException.class)
        .hasMessage(
            "Azure Key Vault is unavailable to read secret a: HttpResponseException: connection reset");
    assertThatThrownBy(() -> AzureKeyVaultErrors.classify("a", network))
        .isInstanceOf(SecretStoreUnavailableException.class)
        .hasMessageContaining("Connection refused")
        .hasCause(network);
  }

  @Test
  void shouldReportTheStoreUnavailableWhenTheVaultsNetworkRulesRefuseTheCaller() {
    for (final var innerCode : new String[] {"ForbiddenByFirewall", "ForbiddenByConnection"}) {
      // given
      final var refused =
          new HttpResponseException(
              "Status code 403",
              response(403),
              Map.of(
                  "error",
                  Map.of(
                      "code",
                      "Forbidden",
                      "message",
                      "Client address is not authorized",
                      "innererror",
                      Map.of("code", innerCode))));

      // then — every secret fails alike, so it is not one secret's denial
      assertThatThrownBy(() -> AzureKeyVaultErrors.classify("a", refused))
          .as(innerCode)
          .isInstanceOf(SecretStoreUnavailableException.class)
          .hasMessage(
              "Azure Key Vault is unavailable to read secret a: Key Vault answered HTTP 403 Forbidden:"
                  + " Client address is not authorized")
          .hasNoCause();
    }
  }

  @Test
  void shouldDescribeTheVaultsOwn401WithItsAnswer() {
    // given — the SDK reports the vault's 401 with the type of a failed sign-in
    final var rejected =
        new ClientAuthenticationException(
            "Status code 401",
            response(401),
            Map.of("error", Map.of("code", "Unauthorized", "message", "AKV10032: Invalid issuer")));

    // then
    assertThatThrownBy(() -> AzureKeyVaultErrors.classify("a", rejected))
        .isInstanceOf(SecretStoreUnavailableException.class)
        .hasMessage(
            "Azure Key Vault is unavailable to read secret a: Key Vault answered HTTP 401 Unauthorized:"
                + " AKV10032: Invalid issuer")
        .hasNoCause();
  }

  @Test
  void shouldNotDescribeATokenThatASignInFailureCopiedFromAParseError() {
    // given the SDK's sync client wrapping a failed sign-in as it does, copying the message of the
    // ExecutionException, which itself quotes the parse error's toString()
    final var token = "eyJ0eXAiOiJKV1QiLCJhbGciOiJSUzI1NiJ9";
    final var execution =
        new ExecutionException(new NumberFormatException("For input string: \"" + token + "\""));
    final var signInFailure =
        new ClientAuthenticationException(execution.getMessage(), null, execution);

    // then
    assertThatThrownBy(() -> AzureKeyVaultErrors.classify("a", signInFailure))
        .isInstanceOf(SecretStoreUnavailableException.class)
        .hasMessageContaining("could not be parsed")
        .hasMessageNotContaining(token)
        .hasNoCause();
  }

  @Test
  void shouldDescribeAMultiLineKeyVaultMessageOnOneLine() {
    // given Key Vault's 403 message, which lists the caller, action and resource on lines of their
    // own
    final var denied =
        new HttpResponseException(
            "Status code 403",
            response(403),
            Map.of(
                "error",
                Map.of(
                    "code",
                    "Forbidden",
                    "message",
                    "Caller is not authorized to perform action on resource.\r\n"
                        + "Caller: appid=1;oid=2\r\nAction: 'getSecret'\r\n")));

    // then
    assertThat(AzureKeyVaultErrors.describe(denied))
        .isEqualTo(
            "Key Vault answered HTTP 403 Forbidden: Caller is not authorized to perform action on"
                + " resource. Caller: appid=1;oid=2 Action: 'getSecret'");
  }

  @Test
  void shouldWithholdTheMessageAndCauseOfAFailureThatMayQuoteTheResponse() {
    // given a response that could not be parsed, quoting what it failed to parse
    final var unparsable =
        new UncheckedIOException(new IOException("Unexpected character 's3cr3t' at offset 9"));
    final var wrappingAnAnswer = new IllegalStateException("boom", answer(403, "Forbidden"));
    final var withAnsweredAttempt = new UncheckedIOException(new ConnectException("refused"));
    withAnsweredAttempt.addSuppressed(answer(503, "Error"));

    // then
    assertThat(AzureKeyVaultErrors.describe(unparsable)).isEqualTo("UncheckedIOException");
    assertThat(AzureKeyVaultErrors.causeToKeep(unparsable)).isNull();
    assertThat(AzureKeyVaultErrors.causeToKeep(wrappingAnAnswer)).isNull();
    assertThat(AzureKeyVaultErrors.causeToKeep(withAnsweredAttempt)).isNull();
  }

  @Test
  void shouldEndAMessageWithKeyVaultsStatusCodeAndMessage() {
    final var message =
        AzureKeyVaultErrors.messageFor(
            ACCESS_DENIED, "camunda-db-password", answer(403, "Forbidden"));

    assertThat(message)
        .isEqualTo(
            "Access denied for secret: camunda-db-password"
                + " (Key Vault answered HTTP 403 Forbidden: it failed)");
  }

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
    final var forbiddenByRbac =
        new HttpResponseException(
            "Status code 403",
            response(403),
            Map.of(
                "error",
                Map.of("code", "Forbidden", "innererror", Map.of("code", "ForbiddenByRbac"))));

    // then
    assertThat(AzureKeyVaultErrors.isSecretDisabled(disabled)).isTrue();
    assertThat(AzureKeyVaultErrors.isSecretDisabled(answer(403, "Forbidden"))).isFalse();
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
    assertThat(AzureKeyVaultErrors.isSecretDisabled(new IllegalStateException("boom"))).isFalse();
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
