/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.orchestration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.client.api.search.response.Incident;
import io.camunda.client.api.search.response.SearchResponse;
import io.camunda.exporter.CamundaExporter;
import io.camunda.qa.util.cluster.TestCamundaApplication;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.qa.util.multidb.MultiDbTestApplication;
import io.camunda.security.entity.AuthenticationMethod;
import java.io.IOException;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

@MultiDbTest
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "rdbms")
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "AWS_OS")
public class IncidentNotifierIT {

  @MultiDbTestApplication(managedLifecycle = false)
  private static final TestCamundaApplication STANDALONE_CAMUNDA =
      new TestCamundaApplication()
          .withAuthenticationMethod(AuthenticationMethod.BASIC)
          .withUnauthenticatedAccess();

  private static final String JWT_TOKEN = JWT.create().sign(Algorithm.HMAC256("secretkey"));
  private static final String WEBHOOK_PATH = "/webhook";
  private static final String OAUTH_TOKEN_PATH = "/oauth/token";
  private static final ObjectMapper MAPPER = new ObjectMapper();
  // WireMock server to simulate token and webhook endpoints
  private static WireMockServer wireMockServer;

  @BeforeAll
  public static void setUp() throws IOException, InterruptedException {
    // start WireMock with dynamic port
    wireMockServer = new WireMockServer(WireMockConfiguration.options().dynamicPort());
    wireMockServer.start();

    // stub token endpoint
    wireMockServer.stubFor(
        post(urlEqualTo(OAUTH_TOKEN_PATH))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(String.format("{\"access_token\": \"%s\"}", JWT_TOKEN))));

    // stub webhook endpoint
    wireMockServer.stubFor(post(urlEqualTo(WEBHOOK_PATH)).willReturn(aResponse().withStatus(200)));

    final var camundaExporter = CamundaExporter.class.getSimpleName().toLowerCase();

    // configure exporter to point to WireMock
    STANDALONE_CAMUNDA.withBrokerConfig(
        c -> {
          final var newArgs = new HashMap<>(c.getExporters().get(camundaExporter).getArgs());
          final var baseUrl = wireMockServer.baseUrl();
          newArgs.put(
              "notifier",
              Map.of(
                  "webhook",
                  baseUrl + WEBHOOK_PATH,
                  "auth0Domain",
                  "localhost:" + wireMockServer.port(),
                  "auth0Protocol",
                  "http"));

          c.getExporters().get(camundaExporter).setArgs(newArgs);
        });
  }

  @AfterAll
  public static void tearDown() {
    if (wireMockServer != null && wireMockServer.isRunning()) {
      wireMockServer.stop();
    }
    STANDALONE_CAMUNDA.stop();
  }

  @Test
  public void shouldUseProcessVersionForWebhookPayloadProcessVersionField() {
    if (!STANDALONE_CAMUNDA.isStarted()) {
      STANDALONE_CAMUNDA.start();
      STANDALONE_CAMUNDA.awaitCompleteTopology();
    }

    try (final var camundaClient = STANDALONE_CAMUNDA.newClientBuilder().build()) {
      final var incident = generateIncident(camundaClient);
      waitForIncidentToExist(incident, camundaClient);
    }

    await()
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(
            () -> {
              // get all webhook requests received by WireMock
              final var requests = getRequestsToWebhook();
              assertThat(
                      requests.stream()
                          .anyMatch(
                              req -> {
                                final var incidents =
                                    getIncidentsInRequestPayload(req.getBodyAsString());
                                return incidents != null
                                    && incidents.stream()
                                        .allMatch(map -> map.get("processVersion") != null);
                              }))
                  .isTrue();
            });
  }

  @Test
  public void shouldNotifyWebhookAboutIncident() {
    // when
    if (!STANDALONE_CAMUNDA.isStarted()) {
      STANDALONE_CAMUNDA.start();
      STANDALONE_CAMUNDA.awaitCompleteTopology();
    }
    final ProcessInstanceEvent incident;
    try (final var camundaClient = STANDALONE_CAMUNDA.newClientBuilder().build()) {
      incident = generateIncident(camundaClient);
      // then
      waitForIncidentToExist(incident, camundaClient);
    }

    await()
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(
            () -> {
              final var requests = getRequestsToWebhook();
              assertThat(
                      requests.stream()
                          .anyMatch(
                              req -> {
                                if (!req.getUrl().endsWith(WEBHOOK_PATH)) {
                                  return false;
                                }
                                if (!"POST".equalsIgnoreCase(req.getMethod().getName())) {
                                  return false;
                                }
                                final var incidents =
                                    getIncidentsInRequestPayload(req.getBodyAsString());
                                final String incidentKey =
                                    String.valueOf(incident.getProcessInstanceKey());
                                return incidents.stream()
                                    .anyMatch(
                                        map ->
                                            String.valueOf(map.get("processInstanceId"))
                                                .equals(incidentKey));
                              }))
                  .isTrue();
            });
  }

  private void waitForIncidentToExist(
      final ProcessInstanceEvent incident, final CamundaClient camundaClient) {
    await()
        .atMost(Duration.ofSeconds(30))
        .pollInterval(Duration.ofSeconds(1))
        .until(
            () -> getIncidents(camundaClient),
            res ->
                res.items().stream()
                    .anyMatch(
                        inc ->
                            inc.getProcessInstanceKey().equals(incident.getProcessInstanceKey())));
  }

  private List<LoggedRequest> getRequestsToWebhook() {
    return wireMockServer.getAllServeEvents().stream()
        .map(ServeEvent::getRequest)
        .filter(req -> req.getUrl().endsWith(WEBHOOK_PATH))
        .collect(Collectors.toList());
  }

  private List<Map<String, Object>> getIncidentsInRequestPayload(final String body) {
    try {
      final var incidents = MAPPER.readTree(body).at("/alerts");
      return MAPPER.convertValue(incidents, new TypeReference<>() {});
    } catch (final JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }

  private SearchResponse<Incident> getIncidents(final CamundaClient camundaClient) {
    return camundaClient.newIncidentSearchRequest().send().join();
  }

  private ProcessInstanceEvent generateIncident(final CamundaClient camundaClient) {
    camundaClient
        .newDeployResourceCommand()
        .addResourceFromClasspath("process/incident_process_v1.bpmn")
        .send()
        .join();

    return camundaClient
        .newCreateInstanceCommand()
        .bpmnProcessId("incident_process_v1")
        .latestVersion()
        .send()
        .join();
  }
}
