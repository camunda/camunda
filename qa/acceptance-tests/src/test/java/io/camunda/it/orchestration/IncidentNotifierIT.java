/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.orchestration;

import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.client.api.search.response.Incident;
import io.camunda.client.api.search.response.SearchResponse;
import io.camunda.exporter.CamundaExporter;
import io.camunda.exporter.notifier.HttpClientWrapper;
import io.camunda.it.util.HttpRequestBodyTestUtility;
import io.camunda.qa.util.cluster.TestCamundaApplication;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.qa.util.multidb.MultiDbTestApplication;
import io.camunda.security.entity.AuthenticationMethod;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
  private static final HttpClient HTTP_CLIENT = spy(HttpClient.newHttpClient());
  private static final ObjectMapper MAPPER = new ObjectMapper();

  @BeforeAll
  public static void setUp() throws IOException, InterruptedException {
    stubHttpClientResponses();
    final var camundaExporter = CamundaExporter.class.getSimpleName().toLowerCase();
    STANDALONE_CAMUNDA.withBrokerConfig(
        c -> {
          final var newArgs = new HashMap<>(c.getExporters().get(camundaExporter).getArgs());
          newArgs.put(
              "notifier",
              Map.of(
                  "webhook",
                  "http://localhost:123" + WEBHOOK_PATH,
                  "auth0Domain",
                  "camunda.domain"));

          c.getExporters().get(camundaExporter).setArgs(newArgs);
        });
  }

  @AfterAll
  public static void tearDown() {
    HTTP_CLIENT.close();
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
        .untilAsserted(
            () ->
                verify(HTTP_CLIENT)
                    .send(
                        argThat(
                            req ->
                                getIncidentsInRequestPayload(req) != null
                                    && getIncidentsInRequestPayload(req).stream()
                                        .allMatch(
                                            incidents -> incidents.get("processVersion") != null)),
                        any()));
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
        .untilAsserted(
            () ->
                verify(HTTP_CLIENT)
                    .send(
                        argThat(
                            req ->
                                req.uri().toString().endsWith(WEBHOOK_PATH)
                                    && req.method().equalsIgnoreCase("POST")
                                    && incidentsInRequestContainProcessInstanceKey(
                                        req, incident.getProcessInstanceKey())),
                        any()));
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

  private boolean incidentsInRequestContainProcessInstanceKey(
      final HttpRequest httpRequest, final Long processInstanceKey) {
    return getIncidentsInRequestPayload(httpRequest).stream()
        .anyMatch(map -> map.get("processInstanceId").equals(processInstanceKey.toString()));
  }

  private List<Map<String, Object>> getIncidentsInRequestPayload(final HttpRequest httpRequest) {
    try {
      final var incidents =
          MAPPER.readTree(HttpRequestBodyTestUtility.extractBody(httpRequest)).at("/alerts");
      return MAPPER.convertValue(incidents, List.class);
    } catch (final JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }

  private static void stubHttpClientResponses() throws IOException, InterruptedException {
    final var mockTokenResponse = mock(HttpResponse.class);
    when(mockTokenResponse.statusCode()).thenReturn(200);
    when(mockTokenResponse.body())
        .thenReturn(String.format("{\"access_token\": \"%s\"}", JWT_TOKEN));

    final var mockWebhookResponse = mock(HttpResponse.class);
    when(mockWebhookResponse.statusCode()).thenReturn(200);

    doAnswer(
            ctx -> {
              final HttpRequest req = ctx.getArgument(0);
              final var url = req.uri().toString();
              final var method = req.method();

              if (url.endsWith(WEBHOOK_PATH) && method.equalsIgnoreCase("POST")) {
                return mockWebhookResponse;
              } else if (url.endsWith(OAUTH_TOKEN_PATH) && method.equalsIgnoreCase("POST")) {
                return mockTokenResponse;
              }

              return HTTP_CLIENT.send(req, ctx.getArgument(1));
            })
        .when(HTTP_CLIENT)
        .send(any(HttpRequest.class), any());

    HttpClientWrapper.setHttpClient(HTTP_CLIENT);
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
