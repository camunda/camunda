/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.cases;

import static io.camunda.it.util.TestHelper.waitForProcessesToBeDeployed;
import static io.camunda.qa.util.multidb.CamundaMultiDBExtension.TIMEOUT_DATA_AVAILABILITY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.CamundaClient;
import io.camunda.client.api.search.enums.UserTaskState;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.zeebe.model.bpmn.Bpmn;
import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.StreamSupport;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

/**
 * End-to-end smoke test for the case search POC: the engine writes case events, the camunda
 * exporter writes them to the case index, and {@code POST /v2/cases/search} reads them back. The
 * endpoint has no Java client method, so requests are raw HTTP calls against the default
 * (unauthenticated) {@link CamundaClient} REST address.
 */
@MultiDbTest
class CaseSearchPocIT {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

  private static CamundaClient camundaClient;

  private final Set<Long> completedTaskKeys = new HashSet<>();

  @Test
  void shouldSearchCasesWithStageGraphAndStates() throws Exception {
    // given
    final var model = Bpmn.readModelFromFile(new File("../../poc/case-search/case-poc.bpmn"));
    final var processDefinitionKey =
        camundaClient
            .newDeployResourceCommand()
            .addProcessModel(model, "case-poc.bpmn")
            .send()
            .join()
            .getProcesses()
            .getFirst()
            .getProcessDefinitionKey();
    waitForProcessesToBeDeployed(
        camundaClient, f -> f.processDefinitionKey(processDefinitionKey), 1);

    // when: case 1 walks the whole graph, including the loop back to Intake and the withdrawal
    final long firstCase =
        camundaClient
            .newCreateInstanceCommand()
            .processDefinitionKey(processDefinitionKey)
            .variables(Map.of("caseTitle", "Broken washing machine", "escalate", false))
            .businessId("case-1")
            .send()
            .join()
            .getProcessInstanceKey();
    completeTask(firstCase, "Task_Intake", Map.of());
    completeTask(firstCase, "Task_Review", Map.of("approved", false));
    completeTask(firstCase, "Task_Intake", Map.of("escalate", true));
    completeTask(firstCase, "Task_EscalationA", Map.of());
    completeTask(firstCase, "Task_EscalationB", Map.of());
    awaitTask(firstCase, "Task_Resolution");
    camundaClient.newBroadcastSignalCommand().signalName("withdraw").send().join();
    completeTask(firstCase, "Task_Withdrawal", Map.of());

    // and: case 2 is left active in the Review stage
    final long secondCase =
        camundaClient
            .newCreateInstanceCommand()
            .processDefinitionKey(processDefinitionKey)
            .variables(Map.of("caseTitle", "Second case", "escalate", false))
            .businessId("case-2")
            .send()
            .join()
            .getProcessInstanceKey();
    completeTask(secondCase, "Task_Intake", Map.of());
    awaitTask(secondCase, "Task_Review");

    // then
    final var processDefinitionFilter = "\"processDefinitionKey\":\"" + processDefinitionKey + "\"";
    Awaitility.await("should find both cases in their final state")
        .atMost(TIMEOUT_DATA_AVAILABILITY)
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              final var items = search("{\"filter\":{" + processDefinitionFilter + "}}");
              assertThat(items).hasSize(2);
              assertThat(byKey(items, firstCase).get("state").asText()).isEqualTo("COMPLETED");
              assertThat(stageStates(byKey(items, firstCase)).get("Stage_Withdrawal"))
                  .isEqualTo("COMPLETED");
              assertThat(stageStates(byKey(items, secondCase)).get("Stage_Review"))
                  .isEqualTo("ACTIVE");
            });

    final var items = search("{\"filter\":{" + processDefinitionFilter + "}}");
    final var first = byKey(items, firstCase);
    final var second = byKey(items, secondCase);
    System.out.println("CASE 1 RESPONSE:\n" + pretty(first));
    System.out.println("CASE 2 RESPONSE:\n" + pretty(second));

    assertThat(first.get("state").asText()).isEqualTo("COMPLETED");
    assertThat(first.get("endDate").isNull()).isFalse();
    assertThat(first.get("title").asText()).isEqualTo("Broken washing machine");
    assertThat(first.get("businessId").asText()).isEqualTo("case-1");
    assertThat(strings(first.get("startElementIds"))).containsExactly("Stage_Intake");
    assertThat(stageStates(first))
        .containsOnly(
            Map.entry("Stage_Intake", "COMPLETED"),
            Map.entry("Stage_Review", "COMPLETED"),
            Map.entry("Stage_EscalationA", "COMPLETED"),
            Map.entry("Stage_EscalationB", "COMPLETED"),
            Map.entry("Stage_Resolution", "TERMINATED"),
            Map.entry("Stage_Withdrawal", "COMPLETED"));
    assertThat(stages(first))
        .extracting(
            s -> s.get("elementId").asText(),
            s -> s.get("name").isNull() ? null : s.get("name").asText(),
            s -> s.get("alternative").asBoolean(),
            s -> strings(s.get("nextElementIds")))
        .containsExactlyInAnyOrder(
            tuple("Stage_Intake", "Intake", false, List.of("Stage_Review", "Stage_EscalationA")),
            tuple("Stage_Review", "Review", false, List.of("Stage_Resolution", "Stage_Intake")),
            tuple("Stage_EscalationA", "Escalation A", true, List.of("Stage_EscalationB")),
            tuple("Stage_EscalationB", "Escalation B", true, List.of("Stage_Resolution")),
            tuple("Stage_Resolution", "Resolution", false, List.of()),
            tuple("Stage_Withdrawal", "Withdrawal", true, List.of()));

    assertThat(second.get("state").asText()).isEqualTo("ACTIVE");
    assertThat(second.get("endDate").isNull()).isTrue();
    assertThat(second.get("title").asText()).isEqualTo("Second case");
    assertThat(stageStates(second))
        .containsOnly(
            Map.entry("Stage_Intake", "COMPLETED"),
            Map.entry("Stage_Review", "ACTIVE"),
            Map.entry("Stage_EscalationA", "NOT_STARTED"),
            Map.entry("Stage_EscalationB", "NOT_STARTED"),
            Map.entry("Stage_Resolution", "NOT_STARTED"),
            Map.entry("Stage_Withdrawal", "NOT_STARTED"));

    // filter by state returns only the active case of this test
    final var active =
        search("{\"filter\":{" + processDefinitionFilter + ",\"state\":\"ACTIVE\"}}");
    assertThat(active)
        .extracting(c -> c.get("caseKey").asText())
        .containsExactly(String.valueOf(secondCase));

    // sorting is accepted
    final var sorted =
        search(
            "{\"filter\":{"
                + processDefinitionFilter
                + "},\"sort\":[{\"field\":\"startDate\",\"order\":\"DESC\"}]}");
    assertThat(sorted)
        .extracting(c -> c.get("caseKey").asText())
        .containsExactly(String.valueOf(secondCase), String.valueOf(firstCase));
  }

  private void completeTask(
      final long processInstanceKey, final String elementId, final Map<String, Object> variables) {
    final long taskKey = awaitTask(processInstanceKey, elementId);
    camundaClient.newCompleteUserTaskCommand(taskKey).variables(variables).send().join();
    completedTaskKeys.add(taskKey);
  }

  private long awaitTask(final long processInstanceKey, final String elementId) {
    return Awaitility.await("user task " + elementId + " is created")
        .atMost(TIMEOUT_DATA_AVAILABILITY)
        .ignoreExceptions()
        .until(
            () ->
                camundaClient
                    .newUserTaskSearchRequest()
                    .filter(
                        f ->
                            f.processInstanceKey(processInstanceKey)
                                .elementId(elementId)
                                .state(UserTaskState.CREATED))
                    .send()
                    .join()
                    .items()
                    .stream()
                    .map(t -> t.getUserTaskKey())
                    .filter(k -> !completedTaskKeys.contains(k))
                    .findFirst(),
            java.util.Optional::isPresent)
        .orElseThrow();
  }

  private static List<JsonNode> search(final String body) throws Exception {
    final var base = camundaClient.getConfiguration().getRestAddress().toString();
    final var request =
        HttpRequest.newBuilder()
            .uri(new URI(base + (base.endsWith("/") ? "" : "/") + "v2/cases/search"))
            .header("Content-Type", "application/json")
            .POST(BodyPublishers.ofString(body))
            .build();
    final HttpResponse<String> response = HTTP_CLIENT.send(request, BodyHandlers.ofString());
    assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
    return StreamSupport.stream(
            OBJECT_MAPPER.readTree(response.body()).get("items").spliterator(), false)
        .toList();
  }

  private static JsonNode byKey(final List<JsonNode> items, final long caseKey) {
    return items.stream()
        .filter(c -> c.get("caseKey").asText().equals(String.valueOf(caseKey)))
        .findFirst()
        .orElseThrow();
  }

  private static List<JsonNode> stages(final JsonNode caseNode) {
    return StreamSupport.stream(caseNode.get("stages").spliterator(), false).toList();
  }

  private static Map<String, String> stageStates(final JsonNode caseNode) {
    return stages(caseNode).stream()
        .collect(
            java.util.stream.Collectors.toMap(
                s -> s.get("elementId").asText(), s -> s.get("state").asText()));
  }

  private static List<String> strings(final JsonNode array) {
    return StreamSupport.stream(array.spliterator(), false).map(JsonNode::asText).toList();
  }

  private static String pretty(final JsonNode node) throws Exception {
    return OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(node);
  }
}
