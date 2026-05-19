/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.optimize;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.config.OptimizeProperties;
import io.camunda.zeebe.optimize.OptimizeApiClient.DetailedPageResult;
import io.camunda.zeebe.optimize.OptimizeApiClient.HomepageResult;
import io.camunda.zeebe.optimize.OptimizeApiClient.OptimizeApiException;
import io.camunda.zeebe.optimize.OptimizeApiClient.OptimizeAuthException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.reactive.MockClientHttpRequest;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

final class OptimizeApiClientTest {

  private static final String OPTIMIZE_BASE = "http://optimize:8090";
  private static final String KEYCLOAK_BASE = "http://keycloak:18080";

  private RecordingExchangeFunction exchange;
  private OptimizeProperties props;
  private OptimizeApiClient client;
  private AtomicLong clockMillis;

  @BeforeEach
  void setUp() {
    exchange = new RecordingExchangeFunction();
    props = new OptimizeProperties();
    props.setBaseUrl(OPTIMIZE_BASE);
    props.setKeycloakUrl(KEYCLOAK_BASE);
    props.setRealm("camunda-platform");
    props.setClientId("optimize");
    props.setClientSecret("secret");
    props.setRequestTimeout(Duration.ofSeconds(2));
    props.setTokenRefreshSkew(Duration.ofSeconds(30));
    clockMillis = new AtomicLong(0L);
    final WebClient.Builder builder = WebClient.builder().exchangeFunction(exchange);
    client = new OptimizeApiClient(props, builder, new ObjectMapper(), clockMillis::get);
  }

  @Test
  void shouldAuthenticateWithClientCredentialsAndCacheToken() {
    // given
    exchange.respond(
        req -> req.url().getHost().equals("keycloak"),
        jsonResponse(HttpStatus.OK, "{\"access_token\":\"tok-1\",\"expires_in\":300}"));
    exchange.respond(
        req -> req.url().getPath().equals("/api/dashboard/management"),
        jsonResponse(HttpStatus.OK, "{\"tiles\":[]}"));
    clockMillis.set(1_000L);

    // when
    client.authenticate();
    client.evaluateHomepage();

    // then - keycloak called once with the client_credentials form body
    final ClientRequest authRequest = exchange.requests().get(0);
    assertThat(authRequest.url().toString())
        .isEqualTo(KEYCLOAK_BASE + "/auth/realms/camunda-platform/protocol/openid-connect/token");
    assertThat(authRequest.headers().getContentType())
        .isEqualTo(MediaType.APPLICATION_FORM_URLENCODED);

    final String formBody = extractBody(authRequest);
    assertThat(formBody)
        .contains("grant_type=client_credentials")
        .contains("client_id=optimize")
        .contains("client_secret=secret")
        .contains("scope=openid")
        .doesNotContain("username=")
        .doesNotContain("password=")
        .doesNotContain("grant_type=password");

    // and - the cached token is propagated as a bearer header on subsequent requests
    final ClientRequest dashboardRequest =
        exchange.requests().stream()
            .filter(r -> r.url().getPath().equals("/api/dashboard/management"))
            .findFirst()
            .orElseThrow();
    assertThat(dashboardRequest.headers().getFirst("Authorization")).isEqualTo("Bearer tok-1");
  }

  @Test
  void shouldRefreshTokenBeforeExpiry() {
    // given - first auth returns tok-1 with TTL 60s; refresh skew is 30s
    exchange.respond(
        req -> req.url().getHost().equals("keycloak"),
        jsonResponse(HttpStatus.OK, "{\"access_token\":\"tok-1\",\"expires_in\":60}"));
    exchange.respond(
        req -> req.url().getPath().equals("/api/dashboard/management"),
        jsonResponse(HttpStatus.OK, "{\"tiles\":[]}"));
    clockMillis.set(0L);
    client.evaluateHomepage();

    // when - re-prime keycloak with a fresh token, advance clock into the skew window
    exchange.respond(
        req -> req.url().getHost().equals("keycloak"),
        jsonResponse(HttpStatus.OK, "{\"access_token\":\"tok-2\",\"expires_in\":60}"));
    clockMillis.set(31_000L);
    client.evaluateHomepage();

    // then - the two dashboard requests carry the pre- and post-refresh tokens
    final List<ClientRequest> dashboardRequests =
        exchange.requests().stream()
            .filter(r -> r.url().getPath().equals("/api/dashboard/management"))
            .toList();
    assertThat(dashboardRequests).hasSize(2);
    assertThat(dashboardRequests.get(0).headers().getFirst("Authorization"))
        .isEqualTo("Bearer tok-1");
    assertThat(dashboardRequests.get(1).headers().getFirst("Authorization"))
        .isEqualTo("Bearer tok-2");
  }

  @Test
  void shouldNotRefreshTokenWhenStillValid() {
    // given
    exchange.respond(
        req -> req.url().getHost().equals("keycloak"),
        jsonResponse(HttpStatus.OK, "{\"access_token\":\"tok-1\",\"expires_in\":300}"));
    clockMillis.set(0L);
    client.ensureValidToken();
    final int initialCallCount = exchange.requests().size();

    // when
    clockMillis.set(1_000L);
    client.ensureValidToken();

    // then
    assertThat(exchange.requests()).hasSize(initialCallCount);
  }

  @Test
  void shouldThrowOnClientCredentialsNon200() {
    // given
    exchange.respond(
        req -> req.url().getHost().equals("keycloak"),
        jsonResponse(HttpStatus.UNAUTHORIZED, "{\"error\":\"invalid_client\"}"));

    // when / then
    assertThatThrownBy(client::authenticate)
        .isInstanceOf(OptimizeAuthException.class)
        .hasMessageContaining("401");
  }

  @Test
  void shouldExtractReportIdsFromDashboardJson() {
    final String body =
        "{\"tiles\":[{\"id\":\"r1\"},{\"id\":\"r2\"},{\"id\":\"\"},{\"foo\":\"bar\"}]}";
    final List<String> reportIds = client.extractReportIdsFromDashboard(body);
    assertThat(reportIds).containsExactly("r1", "r2");
  }

  @Test
  void shouldEvaluateHomepageAndCollectPerReportResults() {
    // given
    primeAuth("tok-evalhome");
    exchange.respond(
        req -> req.url().getPath().equals("/api/dashboard/management"),
        jsonResponse(HttpStatus.OK, "{\"tiles\":[{\"id\":\"rA\"},{\"id\":\"rB\"}]}"));
    exchange.respond(
        req -> req.url().getPath().equals("/api/report/rA/evaluate"),
        jsonResponse(HttpStatus.OK, "{\"reportId\":\"rA\"}"));
    exchange.respond(
        req -> req.url().getPath().equals("/api/report/rB/evaluate"),
        jsonResponse(HttpStatus.INTERNAL_SERVER_ERROR, "{\"err\":\"boom\"}"));

    // when
    final HomepageResult result = client.evaluateHomepage();

    // then
    assertThat(result.dashboardStatusCode()).isEqualTo(200);
    assertThat(result.reportResults()).hasSize(2);
    assertThat(result.reportResults().get(0).reportId()).isEqualTo("rA");
    assertThat(result.reportResults().get(0).success()).isTrue();
    assertThat(result.reportResults().get(1).reportId()).isEqualTo("rB");
    assertThat(result.reportResults().get(1).success()).isFalse();

    final List<ClientRequest> reportCalls =
        exchange.requests().stream()
            .filter(r -> r.url().getPath().startsWith("/api/report/"))
            .toList();
    assertThat(reportCalls).isNotEmpty();
    for (final ClientRequest r : reportCalls) {
      assertThat(r.headers().getFirst("Authorization")).isEqualTo("Bearer tok-evalhome");
    }
  }

  @Test
  void shouldThrowOnHomepageDashboardNon2xx() {
    primeAuth("tok");
    exchange.respond(
        req -> req.url().getPath().equals("/api/dashboard/management"),
        jsonResponse(HttpStatus.INTERNAL_SERVER_ERROR, "{}"));

    assertThatThrownBy(client::evaluateHomepage).isInstanceOf(OptimizeApiException.class);
  }

  @Test
  void shouldEvaluateDetailedPagePerTile() {
    // given
    primeAuth("tok-detail");
    exchange.respond(
        req -> req.url().getPath().equals("/api/dashboard/instant/key-123"),
        jsonResponse(HttpStatus.OK, "{\"tiles\":[{\"id\":\"rD\"}]}"));
    exchange.respond(
        req -> req.url().getPath().equals("/api/report/rD/evaluate"),
        jsonResponse(HttpStatus.OK, "{\"reportId\":\"rD\"}"));

    // when
    final DetailedPageResult result = client.evaluateDetailedPage("key-123");

    // then
    assertThat(result.dashboardStatusCode()).isEqualTo(200);
    assertThat(result.reportEvaluationResults()).hasSize(1);
    assertThat(result.reportEvaluationResults().get(0).reportId()).isEqualTo("rD");
    // No /api/report/evaluate (raw-data) call should be made.
    assertThat(
            exchange.requests().stream()
                .filter(r -> r.url().getPath().equals("/api/report/evaluate"))
                .toList())
        .isEmpty();
  }

  @Test
  void shouldFetchFirstProcessDefinitionKey() {
    primeAuth("tok-pdkey");
    exchange.respond(
        req -> req.url().getPath().equals("/api/process/overview"),
        jsonResponse(HttpStatus.OK, "[{\"processDefinitionKey\":\"pd-xyz\"}]"));

    assertThat(client.fetchFirstProcessDefinitionKey()).isEqualTo("pd-xyz");
  }

  @Test
  void shouldThrowOnEmptyProcessOverview() {
    primeAuth("tok");
    exchange.respond(
        req -> req.url().getPath().equals("/api/process/overview"),
        jsonResponse(HttpStatus.OK, "[]"));

    assertThatThrownBy(() -> client.fetchFirstProcessDefinitionKey())
        .isInstanceOf(OptimizeApiException.class);
  }

  @Test
  void shouldReportTimingInMilliseconds() {
    // given
    primeAuth("tok-timing");
    clockMillis.set(10_000L);
    exchange.respond(
        req -> req.url().getPath().equals("/api/dashboard/management"),
        () -> {
          clockMillis.addAndGet(45L);
          return jsonResponse(HttpStatus.OK, "{\"tiles\":[]}").get();
        });

    // when
    final HomepageResult result = client.evaluateHomepage();

    // then
    assertThat(result.dashboardResponseTimeMs()).isEqualTo(45L);
  }

  private void primeAuth(final String token) {
    exchange.respond(
        req -> req.url().getHost().equals("keycloak"),
        jsonResponse(HttpStatus.OK, "{\"access_token\":\"" + token + "\",\"expires_in\":3600}"));
    clockMillis.set(0L);
    client.ensureValidToken();
  }

  private static Supplier<ClientResponse> jsonResponse(final HttpStatus status, final String body) {
    return () ->
        ClientResponse.create(status)
            .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
            .body(body)
            .build();
  }

  private static String extractBody(final ClientRequest request) {
    final MockClientHttpRequest mockRequest =
        new MockClientHttpRequest(request.method(), request.url());
    request.writeTo(mockRequest, ExchangeStrategies.withDefaults()).block();
    final String body = mockRequest.getBodyAsString().block();
    return body == null ? "" : body;
  }

  private static final class RecordingExchangeFunction implements ExchangeFunction {
    private final List<Responder> responders = new ArrayList<>();
    private final List<ClientRequest> requests = new ArrayList<>();

    void respond(final Predicate<ClientRequest> matcher, final Supplier<ClientResponse> response) {
      responders.add(new Responder(matcher, response));
    }

    List<ClientRequest> requests() {
      return requests;
    }

    @Override
    public Mono<ClientResponse> exchange(final ClientRequest request) {
      requests.add(request);
      for (int i = responders.size() - 1; i >= 0; i--) {
        final Responder responder = responders.get(i);
        if (responder.matcher.test(request)) {
          return Mono.just(responder.response.get());
        }
      }
      return Mono.error(new AssertionError("Unexpected request: " + request.url()));
    }

    private record Responder(Predicate<ClientRequest> matcher, Supplier<ClientResponse> response) {}
  }
}
