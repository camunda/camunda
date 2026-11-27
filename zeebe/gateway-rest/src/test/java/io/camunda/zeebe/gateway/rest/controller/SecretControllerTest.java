/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import static io.camunda.gateway.mapping.http.validator.SecretRequestValidator.MAX_BATCH_SIZE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.camunda.secretstore.SecretStatus;
import io.camunda.security.api.context.CamundaAuthenticationProvider;
import io.camunda.security.spring.CamundaSecurityLibraryProperties;
import io.camunda.service.SecretServices;
import io.camunda.service.SecretServices.ResolvedSecret;
import io.camunda.service.SecretServices.SecretErrorCode;
import io.camunda.service.SecretServices.SecretResolution;
import io.camunda.service.SecretServices.SecretResolutionError;
import io.camunda.service.registry.ServiceRegistry;
import io.camunda.zeebe.gateway.rest.RestControllerTest;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.Timeout.ThreadMode;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;

@ExtendWith(MockitoExtension.class)
@WebMvcTest(value = SecretController.class)
public class SecretControllerTest extends RestControllerTest {

  static final String RESOLVE_URL = "/v2/secrets/resolve";
  static final String LIST_URL = "/v2/secrets/list";

  @Captor ArgumentCaptor<List<String>> referencesCaptor;
  @MockitoBean SecretServices secretServices;
  @MockitoBean CamundaSecurityLibraryProperties cslProperties;
  @MockitoBean CamundaAuthenticationProvider authenticationProvider;
  @MockitoBean ServiceRegistry serviceRegistry;

  @BeforeEach
  void setupServices() {
    lenient().when(serviceRegistry.secretServices(any())).thenReturn(secretServices);
    when(authenticationProvider.getCamundaAuthentication())
        .thenReturn(AUTHENTICATION_WITH_DEFAULT_TENANT);
  }

  @Test
  void shouldReturnMixedOutcomesWithHttp200() {
    // given a batch that resolves one reference and fails two independently
    when(secretServices.resolve(any(), any()))
        .thenReturn(
            CompletableFuture.completedFuture(
                new SecretResolution(
                    List.of(new ResolvedSecret("camunda.secrets.a", "value-a")),
                    List.of(
                        new SecretResolutionError(
                            "camunda.secrets.missing", SecretErrorCode.NOT_FOUND, "not found"),
                        new SecretResolutionError(
                            "camunda.secrets.denied", SecretErrorCode.ACCESS_DENIED, "denied")))));

    final var expectedBody =
        """
        {
          "resolved": [
            { "reference": "camunda.secrets.a", "value": "value-a" }
          ],
          "errors": [
            { "reference": "camunda.secrets.missing", "code": "NOT_FOUND", "message": "not found" },
            { "reference": "camunda.secrets.denied", "code": "ACCESS_DENIED", "message": "denied" }
          ]
        }""";

    // when / then a partial failure is not a 4xx/5xx
    webClient
        .post()
        .uri(RESOLVE_URL)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(
            """
            { "references": ["camunda.secrets.a", "camunda.secrets.missing", "camunda.secrets.denied"] }""")
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .json(expectedBody, JsonCompareMode.STRICT);
  }

  @Test
  void shouldReturnHttp200WhenAllReferencesFail() {
    // given every reference fails
    when(secretServices.resolve(any(), any()))
        .thenReturn(
            CompletableFuture.completedFuture(
                new SecretResolution(
                    List.of(),
                    List.of(
                        new SecretResolutionError(
                            "camunda.secrets.denied", SecretErrorCode.ACCESS_DENIED, "denied")))));

    // when / then still HTTP 200
    webClient
        .post()
        .uri(RESOLVE_URL)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(
            """
            { "references": ["camunda.secrets.denied"] }""")
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isOk();
  }

  @Test
  void shouldForwardReferencesToService() {
    // given
    when(secretServices.resolve(any(), any()))
        .thenReturn(CompletableFuture.completedFuture(new SecretResolution(List.of(), List.of())));

    // when
    webClient
        .post()
        .uri(RESOLVE_URL)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(
            """
            { "references": ["camunda.secrets.a", "camunda.secrets.b"] }""")
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isOk();

    // then the raw references are forwarded (server-side deduplication happens in the service)
    verify(secretServices)
        .resolve(referencesCaptor.capture(), eq(AUTHENTICATION_WITH_DEFAULT_TENANT));
    assertThat(referencesCaptor.getValue())
        .containsExactly("camunda.secrets.a", "camunda.secrets.b");
  }

  @Test
  void shouldReturnEmptyResultForEmptyBatch() {
    // given an empty batch is a valid no-op
    when(secretServices.resolve(any(), any()))
        .thenReturn(CompletableFuture.completedFuture(new SecretResolution(List.of(), List.of())));

    // when / then it resolves to HTTP 200 with empty outcomes
    webClient
        .post()
        .uri(RESOLVE_URL)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(
            """
            { "references": [] }""")
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .json(
            """
            { "resolved": [], "errors": [] }""",
            JsonCompareMode.STRICT);
  }

  @Test
  void shouldRejectBatchExceedingMaxItems() {
    // given a batch one larger than the maximum
    final var references = referencesJson(MAX_BATCH_SIZE + 1);

    // when / then the request is rejected before reaching the service
    webClient
        .post()
        .uri(RESOLVE_URL)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{ \"references\": [" + references + "] }")
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isBadRequest();
    verifyNoInteractions(secretServices);
  }

  @Test
  void shouldAcceptBatchAtMaxItems() {
    // given a batch exactly at the maximum
    when(secretServices.resolve(any(), any()))
        .thenReturn(CompletableFuture.completedFuture(new SecretResolution(List.of(), List.of())));
    final var references = referencesJson(MAX_BATCH_SIZE);

    // when / then the boundary is inclusive and the request reaches the service
    webClient
        .post()
        .uri(RESOLVE_URL)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{ \"references\": [" + references + "] }")
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isOk();
    verify(secretServices).resolve(any(), any());
  }

  @Test
  void shouldRejectNullReferences() {
    // given a request whose references are explicitly null (bypasses the model's @NotNull because
    // the controller does not run @Valid)

    // when / then it is a 400 rather than a 500 and never reaches the service
    webClient
        .post()
        .uri(RESOLVE_URL)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{ \"references\": null }")
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isBadRequest();
    verifyNoInteractions(secretServices);
  }

  @Test
  void shouldRejectNullRequestBody() {
    // given a literal JSON null body (deserialized as a null request, bypassing the model @NotNull)

    // when / then it is a 400 rather than a 500 and never reaches the service
    webClient
        .post()
        .uri(RESOLVE_URL)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("null")
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isBadRequest();
    verifyNoInteractions(secretServices);
  }

  @Test
  void shouldRejectReferencesContainingNullEntry() {
    // given a batch with a null entry among valid references

    // when / then the whole request is rejected before reaching the service
    webClient
        .post()
        .uri(RESOLVE_URL)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{ \"references\": [\"camunda.secrets.a\", null] }")
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isBadRequest();
    verifyNoInteractions(secretServices);
  }

  @Test
  void shouldReturnAuthorizedReferences() {
    // given
    when(secretServices.list(any(), any()))
        .thenReturn(CompletableFuture.completedFuture(List.of("camunda.secrets.a")));

    // when / then only reference names are returned; the schema has no value field to leak
    webClient
        .post()
        .uri(LIST_URL)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{}")
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .json(
            """
            { "references": ["camunda.secrets.a"] }""",
            JsonCompareMode.STRICT);
  }

  @Test
  void shouldReturnEmptyReferencesWhenNoneAuthorized() {
    // given
    when(secretServices.list(any(), any()))
        .thenReturn(CompletableFuture.completedFuture(List.of()));

    // when / then
    webClient
        .post()
        .uri(LIST_URL)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{}")
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .json(
            """
            { "references": [] }""",
            JsonCompareMode.STRICT);
  }

  @Test
  void shouldTreatNullListRequestBodyAsNoFilters() {
    // given a literal JSON null body (deserialized as a null request)
    when(secretServices.list(any(), any()))
        .thenReturn(CompletableFuture.completedFuture(List.of()));

    // when / then the body is optional, so a null body applies no filters and still reaches the
    // service rather than being rejected
    webClient
        .post()
        .uri(LIST_URL)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("null")
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isOk();
    verify(secretServices).list(any(), isNull());
  }

  @Test
  void shouldTreatMissingListRequestBodyAsNoFilters() {
    // given no request body at all
    when(secretServices.list(any(), any()))
        .thenReturn(CompletableFuture.completedFuture(List.of()));

    // when / then the body is optional, so omitting it applies no filters and still reaches the
    // service — clients need not send an empty object
    webClient
        .post()
        .uri(LIST_URL)
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isOk();
    verify(secretServices).list(any(), isNull());
  }

  @Test
  void shouldForwardAuthenticationToServiceOnList() {
    // given
    when(secretServices.list(any(), any()))
        .thenReturn(CompletableFuture.completedFuture(List.of()));

    // when
    webClient
        .post()
        .uri(LIST_URL)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{}")
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isOk();

    // then
    verify(secretServices).list(eq(AUTHENTICATION_WITH_DEFAULT_TENANT), isNull());
  }

  @ParameterizedTest
  @EnumSource(SecretStatus.class)
  void shouldListSecretsInRequestedStatus(final SecretStatus status) {
    // given
    when(secretServices.list(any(), any()))
        .thenReturn(CompletableFuture.completedFuture(List.of("camunda.secrets.a")));

    // when / then the response keeps the shape of an unfiltered listing
    webClient
        .post()
        .uri(LIST_URL)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{ \"filter\": { \"status\": \"%s\" } }".formatted(status.name()))
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .json(
            """
            { "references": ["camunda.secrets.a"] }""",
            JsonCompareMode.STRICT);

    // then the requested status reaches the service
    verify(secretServices).list(eq(AUTHENTICATION_WITH_DEFAULT_TENANT), eq(Set.of(status)));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("advancedStatusFilters")
  void shouldListSecretsMatchingAdvancedStatusFilter(
      final String statusFilter, final Set<SecretStatus> expected) {
    // given
    when(secretServices.list(any(), any()))
        .thenReturn(CompletableFuture.completedFuture(List.of()));

    // when
    listSecrets("{ \"filter\": { \"status\": %s } }".formatted(statusFilter));

    // then the parsed filter resolves to the statuses passed to the service
    verify(secretServices).list(eq(AUTHENTICATION_WITH_DEFAULT_TENANT), eq(expected));
  }

  static Stream<Arguments> advancedStatusFilters() {
    final var all = EnumSet.allOf(SecretStatus.class);
    return Stream.of(
        Arguments.of("{ \"$eq\": \"EXPIRED\" }", EnumSet.of(SecretStatus.EXPIRED)),
        Arguments.of(
            "{ \"$neq\": \"ACTIVE\" }", EnumSet.complementOf(EnumSet.of(SecretStatus.ACTIVE))),
        Arguments.of(
            "{ \"$in\": [\"DISABLED\", \"EXPIRED\"] }",
            EnumSet.of(SecretStatus.DISABLED, SecretStatus.EXPIRED)),
        Arguments.of(
            "{ \"$notIn\": [\"ACTIVE\", \"DISABLED\"] }",
            EnumSet.of(SecretStatus.EXPIRED, SecretStatus.NOT_YET_ACTIVE)),
        Arguments.of("{ \"$exists\": true }", all),
        Arguments.of("{ \"$exists\": false }", EnumSet.noneOf(SecretStatus.class)),
        Arguments.of(
            "{ \"$like\": \"*ACTIVE\" }",
            EnumSet.of(SecretStatus.ACTIVE, SecretStatus.NOT_YET_ACTIVE)),
        Arguments.of("{ \"$in\": [] }", all),
        Arguments.of("{}", all),
        // status names are parsed case-insensitively, unlike $like patterns
        Arguments.of("\"active\"", EnumSet.of(SecretStatus.ACTIVE)),
        Arguments.of("{ \"$eq\": \"active\" }", EnumSet.of(SecretStatus.ACTIVE)),
        Arguments.of("{ \"$like\": \"active\" }", EnumSet.noneOf(SecretStatus.class)),
        // an explicit null operator is parsed like an absent one
        Arguments.of("{ \"$eq\": null }", all),
        Arguments.of("{ \"$like\": null }", all),
        Arguments.of("{ \"$exists\": null }", all));
  }

  @Test
  // a $like value is caller input, so a hostile one must not tie up the request thread
  @Timeout(value = 5, threadMode = ThreadMode.SEPARATE_THREAD)
  void shouldListSecretsQuicklyForHostileLikeStatusFilter() {
    // given a run of stars before a character no status has, which backtracks exponentially
    when(secretServices.list(any(), any()))
        .thenReturn(CompletableFuture.completedFuture(List.of()));
    final var like = "*".repeat(40) + "Z";

    // when
    listSecrets("{ \"filter\": { \"status\": { \"$like\": \"%s\" } } }".formatted(like));

    // then
    verify(secretServices)
        .list(eq(AUTHENTICATION_WITH_DEFAULT_TENANT), eq(EnumSet.noneOf(SecretStatus.class)));
  }

  @ParameterizedTest(name = "{0}")
  @ValueSource(
      strings = {
        "{ \"$eq\": \"BROKEN\" }",
        "{ \"$neq\": \"BROKEN\" }",
        "{ \"$in\": [\"BROKEN\"] }",
        "{ \"$notIn\": [\"BROKEN\"] }",
        "{ \"$like\": [\"ACTIVE\"] }",
        "{ \"$like\": { \"$eq\": \"ACTIVE\" } }",
        "{ \"$exists\": \"maybe\" }",
        "{ \"$in\": \"ACTIVE\" }",
        "{ \"$regex\": \"ACTIVE\" }"
      })
  void shouldRejectMalformedStatusFilter(final String statusFilter) {
    // when / then the request fails before the service is asked
    webClient
        .post()
        .uri(LIST_URL)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{ \"filter\": { \"status\": %s } }".formatted(statusFilter))
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isBadRequest();
    verifyNoInteractions(secretServices);
  }

  @ParameterizedTest
  @ValueSource(strings = {"$in", "$notIn"})
  void shouldRejectNullEntryInAdvancedStatusFilter(final String operator) {
    // when / then the 400 names the offending list, and the service is never asked
    webClient
        .post()
        .uri(LIST_URL)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(
            "{ \"filter\": { \"status\": { \"%s\": [\"ACTIVE\", null] } } }".formatted(operator))
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectBody()
        .jsonPath("$.detail")
        .isEqualTo("The filter.status.%s list must not contain null entries.".formatted(operator));
    verifyNoInteractions(secretServices);
  }

  @ParameterizedTest(name = "{0}")
  @ValueSource(
      strings = {
        "{}",
        "{ \"filter\": null }",
        "{ \"filter\": {} }",
        "{ \"filter\": { \"status\": null } }"
      })
  void shouldListSecretsInEveryStatusWhenFilterIsEmpty(final String body) {
    // given
    when(secretServices.list(any(), any()))
        .thenReturn(CompletableFuture.completedFuture(List.of()));

    // when
    listSecrets(body);

    // then a filter without a status narrows nothing
    verify(secretServices).list(eq(AUTHENTICATION_WITH_DEFAULT_TENANT), isNull());
  }

  @Test
  void shouldRejectUnknownStatus() {
    // when / then the 400 names the allowed values, and the service is never asked
    webClient
        .post()
        .uri(LIST_URL)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{ \"filter\": { \"status\": \"BROKEN\" } }")
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .json(
            """
            {
              "type": "about:blank",
              "title": "Bad Request",
              "status": 400,
              "detail": "Unexpected value 'BROKEN' for enum field 'status'. Use any of the following values: [ACTIVE, DISABLED, EXPIRED, NOT_YET_ACTIVE]",
              "instance": "%s"
            }"""
                .formatted(LIST_URL),
            JsonCompareMode.STRICT);
    verifyNoInteractions(secretServices);
  }

  @Test
  void shouldRejectUnknownFilterProperty() {
    // when / then the filter takes only the properties the spec declares
    webClient
        .post()
        .uri(LIST_URL)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{ \"filter\": { \"name\": \"camunda.secrets.a\" } }")
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isBadRequest();
    verifyNoInteractions(secretServices);
  }

  private void listSecrets(final String body) {
    webClient
        .post()
        .uri(LIST_URL)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(body)
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isOk();
  }

  private static String referencesJson(final int count) {
    return IntStream.rangeClosed(1, count)
        .mapToObj(i -> "\"camunda.secrets.s" + i + "\"")
        .collect(Collectors.joining(","));
  }
}
