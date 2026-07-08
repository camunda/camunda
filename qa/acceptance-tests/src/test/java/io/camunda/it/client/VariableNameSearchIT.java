/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.client;

import static io.camunda.it.util.TestHelper.deployProcessAndWaitForIt;
import static io.camunda.it.util.TestHelper.startProcessInstance;
import static io.camunda.qa.util.multidb.CamundaMultiDBExtension.TIMEOUT_DATA_AVAILABILITY;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.CamundaClient;
import io.camunda.qa.util.multidb.MultiDbTest;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Cross-exporter parity coverage for {@code POST
 * /v2/process-definitions/{processDefinitionKey}/variable-names/search}. The endpoint has no fluent
 * Java client method yet, so requests are issued as raw HTTP calls against the default
 * (unauthenticated) {@link CamundaClient} REST address.
 */
@MultiDbTest
class VariableNameSearchIT {

  private static final ObjectMapper OBJECT_MAPPER =
      new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

  private static CamundaClient camundaClient;
  private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

  private static long processDefinitionKey;
  private static long wildcardProcessDefinitionKey;

  @BeforeAll
  static void beforeAll() throws Exception {
    final var process = deployProcessAndWaitForIt(camundaClient, "process/bpm_variable_test.bpmn");
    processDefinitionKey = process.getProcessDefinitionKey();

    startProcessInstance(
        camundaClient,
        "bpmProcessVariable",
        Map.of("amount", 1, "amendment", 2, "total", 3, "unrelated", 4));

    waitForVariableNames(
        processDefinitionKey,
        null,
        100,
        List.of("amendment", "amount", "process01", "task01", "task02", "total", "unrelated"));

    final var wildcardProcess =
        deployProcessAndWaitForIt(camundaClient, "process/bpm_variable_wildcard_test.bpmn");
    wildcardProcessDefinitionKey = wildcardProcess.getProcessDefinitionKey();

    startProcessInstance(
        camundaClient, "bpmProcessVariableWildcard", Map.of("50*off", 1, "50xoff", 2));

    waitForVariableNames(wildcardProcessDefinitionKey, null, 100, List.of("50*off", "50xoff"));
  }

  @Test
  void shouldReturnNamesOrderedAlphabetically() throws Exception {
    // when
    final var names = searchVariableNames(processDefinitionKey, null, 100);

    // then
    assertThat(names)
        .containsExactly(
            "amendment", "amount", "process01", "task01", "task02", "total", "unrelated");
  }

  @Test
  void shouldNarrowByNamePrefix() throws Exception {
    // when
    final var names = searchVariableNames(processDefinitionKey, "am*", 100);

    // then
    assertThat(names).containsExactly("amendment", "amount");
  }

  @Test
  void shouldCapAtClientSuppliedLimit() throws Exception {
    // when
    final var names = searchVariableNames(processDefinitionKey, null, 2);

    // then
    assertThat(names).containsExactly("amendment", "amount");
  }

  @Test
  void shouldEscapeLiteralWildcardCharactersInPrefix() throws Exception {
    // when: the literal asterisk must be escaped, not treated as the wildcard character
    final var names = searchVariableNames(wildcardProcessDefinitionKey, "50\\\\**", 100);

    // then
    assertThat(names).containsExactly("50*off");
  }

  @Test
  void shouldReturnEmptyForUnknownProcessDefinitionKey() throws Exception {
    // when
    final var names = searchVariableNames(999999999L, null, 100);

    // then
    assertThat(names).isEmpty();
  }

  private static void waitForVariableNames(
      final long processDefinitionKey,
      final String namePrefix,
      final int limit,
      final List<String> expectedNames) {
    Awaitility.await("should receive variable names from secondary storage")
        .atMost(TIMEOUT_DATA_AVAILABILITY)
        .ignoreExceptions()
        .untilAsserted(
            () ->
                assertThat(searchVariableNames(processDefinitionKey, namePrefix, limit))
                    .containsExactlyElementsOf(expectedNames));
  }

  private static List<String> searchVariableNames(
      final long processDefinitionKey, final String namePrefix, final int limit)
      throws IOException, InterruptedException, URISyntaxException {
    final var filter =
        namePrefix == null ? "" : "\"filter\": {\"name\": {\"$like\": \"" + namePrefix + "\"}}, ";
    final var body = "{%s\"page\": {\"limit\": %d}}".formatted(filter, limit);

    final var response = sendSearchRequest(processDefinitionKey, body);
    assertThat(response.statusCode()).isEqualTo(200);
    return readNames(response);
  }

  private static HttpResponse<String> sendSearchRequest(
      final long processDefinitionKey, final String jsonBody)
      throws IOException, InterruptedException, URISyntaxException {
    final var request =
        HttpRequest.newBuilder()
            .uri(
                createUri(
                    camundaClient,
                    "v2/process-definitions/%d/variable-names/search"
                        .formatted(processDefinitionKey)))
            .header("Content-Type", "application/json")
            .POST(BodyPublishers.ofString(jsonBody))
            .build();
    return HTTP_CLIENT.send(request, BodyHandlers.ofString());
  }

  private static List<String> readNames(final HttpResponse<String> response) throws IOException {
    final JsonNode root = OBJECT_MAPPER.readTree(response.body());
    return StreamSupport.stream(root.get("items").spliterator(), false)
        .map(item -> item.get("name").asText())
        .toList();
  }

  private static URI createUri(final CamundaClient client, final String path)
      throws URISyntaxException {
    final String base = client.getConfiguration().getRestAddress().toString();
    final String separator = base.endsWith("/") ? "" : "/";
    return new URI(base + separator + path);
  }
}
