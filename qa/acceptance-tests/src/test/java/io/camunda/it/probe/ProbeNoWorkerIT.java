/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.probe;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * A probe that no worker collects ends at its deadline in the engine, and the gateway waits past
 * that deadline, so the client gets the engine's {@code 404 NO_WORKER} and not a gateway time-out
 * (AT19, D40, D49).
 */
@ZeebeIntegration
final class ProbeNoWorkerIT {

  private static final long REQUEST_TIMEOUT = 1_000;

  @TestZeebe(purgeAfterEach = false)
  private static final TestStandaloneBroker BROKER =
      new TestStandaloneBroker().withUnauthenticatedAccess();

  @AutoClose private final HttpClient httpClient = HttpClient.newHttpClient();

  @ParameterizedTest
  @ValueSource(strings = {"v2/probes", "physical-tenants/default/v2/probes"})
  void shouldAnswerNoWorkerWhenNoWorkerCollectsTheProbe(final String path) throws Exception {
    // given - a probe ID that no worker polls
    final String probeId = "no-worker-" + path.hashCode();
    final var request =
        HttpRequest.newBuilder(BROKER.restAddress().resolve(path))
            .header("Content-Type", "application/json")
            .header("Accept", "application/json, application/problem+json")
            // at least the request timeout plus one second, as the API documentation asks
            .timeout(Duration.ofMillis(REQUEST_TIMEOUT + 5_000))
            .POST(
                BodyPublishers.ofString(
                    """
                    {"probeId": "%s", "input": "={region: \\"eu-west-1\\"}", "requestTimeout": %d}"""
                        .formatted(probeId, REQUEST_TIMEOUT)))
            .build();

    // when
    final var response = httpClient.send(request, BodyHandlers.ofString());

    // then
    assertThat(response.statusCode()).isEqualTo(404);
    final var problem = new ObjectMapper().readTree(response.body());
    assertThat(problem.get("title").asText()).isEqualTo("NO_WORKER");
    assertThat(problem.get("detail").asText()).contains(probeId);
  }
}
