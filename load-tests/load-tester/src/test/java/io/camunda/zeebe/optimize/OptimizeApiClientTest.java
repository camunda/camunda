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
import io.camunda.client.CredentialsProvider;
import io.camunda.client.CredentialsProvider.CredentialsApplier;
import io.camunda.client.CredentialsProvider.StatusCode;
import io.camunda.zeebe.config.OptimizeProperties;
import io.camunda.zeebe.optimize.OptimizeApiClient.OptimizeApiException;
import io.camunda.zeebe.optimize.OptimizeApiClient.PageEvaluationResult;
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
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

final class OptimizeApiClientTest {

  private static final String OPTIMIZE_BASE = "http://optimize:8090";
  // A stub provider that stamps a fixed bearer token; real OAuth is the provider's concern.
  private static final CredentialsProvider FAKE_CREDENTIALS =
      new CredentialsProvider() {
        @Override
        public void applyCredentials(final CredentialsApplier applier) {
          applier.put("Authorization", "Bearer test-token");
        }

        @Override
        public boolean shouldRetryRequest(final StatusCode statusCode) {
          return false;
        }
      };

  private RecordingExchangeFunction exchange;
  private OptimizeProperties props;
  private OptimizeApiClient client;
  private AtomicLong clockMillis;

  @BeforeEach
  void setUp() {
    exchange = new RecordingExchangeFunction();
    props = new OptimizeProperties();
    props.setBaseUrl(OPTIMIZE_BASE);
    props.setRequestTimeout(Duration.ofSeconds(2));
    clockMillis = new AtomicLong(0L);
    final WebClient.Builder builder = WebClient.builder().exchangeFunction(exchange);
    client =
        new OptimizeApiClient(
            props, builder, new ObjectMapper(), FAKE_CREDENTIALS, clockMillis::get);
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
    final PageEvaluationResult result = client.evaluateHomepage();

    // then
    assertThat(result.dashboardStatusCode()).isEqualTo(200);
    assertThat(result.reportResults()).hasSize(2);
    assertThat(result.reportResults().get(0).reportId()).isEqualTo("rA");
    assertThat(result.reportResults().get(0).success()).isTrue();
    assertThat(result.reportResults().get(1).reportId()).isEqualTo("rB");
    assertThat(result.reportResults().get(1).success()).isFalse();

    // and - every request carries the provider's bearer token
    assertThat(exchange.requests())
        .isNotEmpty()
        .allSatisfy(
            r -> assertThat(r.headers().getFirst("Authorization")).isEqualTo("Bearer test-token"));
  }

  @Test
  void shouldThrowOnHomepageDashboardNon2xx() {
    exchange.respond(
        req -> req.url().getPath().equals("/api/dashboard/management"),
        jsonResponse(HttpStatus.INTERNAL_SERVER_ERROR, "{}"));

    assertThatThrownBy(client::evaluateHomepage).isInstanceOf(OptimizeApiException.class);
  }

  @Test
  void shouldEvaluateDetailedPagePerTile() {
    // given
    exchange.respond(
        req -> req.url().getPath().equals("/api/dashboard/instant/key-123"),
        jsonResponse(HttpStatus.OK, "{\"tiles\":[{\"id\":\"rD\"}]}"));
    exchange.respond(
        req -> req.url().getPath().equals("/api/report/rD/evaluate"),
        jsonResponse(HttpStatus.OK, "{\"reportId\":\"rD\"}"));

    // when
    final PageEvaluationResult result = client.evaluateDetailedPage("key-123");

    // then
    assertThat(result.dashboardStatusCode()).isEqualTo(200);
    assertThat(result.reportResults()).hasSize(1);
    assertThat(result.reportResults().get(0).reportId()).isEqualTo("rD");
  }

  @Test
  void shouldFetchFirstProcessDefinitionKey() {
    exchange.respond(
        req -> req.url().getPath().equals("/api/process/overview"),
        jsonResponse(HttpStatus.OK, "[{\"processDefinitionKey\":\"pd-xyz\"}]"));

    assertThat(client.fetchFirstProcessDefinitionKey()).isEqualTo("pd-xyz");
  }

  @Test
  void shouldThrowOnEmptyProcessOverview() {
    exchange.respond(
        req -> req.url().getPath().equals("/api/process/overview"),
        jsonResponse(HttpStatus.OK, "[]"));

    assertThatThrownBy(() -> client.fetchFirstProcessDefinitionKey())
        .isInstanceOf(OptimizeApiException.class);
  }

  @Test
  void shouldReportTimingInMilliseconds() {
    // given - the response advances the clock by 45ms
    clockMillis.set(10_000L);
    exchange.respond(
        req -> req.url().getPath().equals("/api/dashboard/management"),
        () -> {
          clockMillis.addAndGet(45L);
          return jsonResponse(HttpStatus.OK, "{\"tiles\":[]}").get();
        });

    // when
    final PageEvaluationResult result = client.evaluateHomepage();

    // then
    assertThat(result.dashboardResponseTimeMs()).isEqualTo(45L);
  }

  private static Supplier<ClientResponse> jsonResponse(final HttpStatus status, final String body) {
    return () ->
        ClientResponse.create(status)
            .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
            .body(body)
            .build();
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
