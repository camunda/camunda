/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it;

import static io.camunda.it.util.TestHelper.deployProcessAndWaitForIt;
import static io.camunda.it.util.TestHelper.startProcessInstance;
import static io.camunda.it.util.TestHelper.waitForJobs;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.CamundaClient;
import io.camunda.client.api.search.enums.JobKind;
import io.camunda.client.api.worker.JobHandler;
import io.camunda.client.api.worker.JobWorker;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.zeebe.model.bpmn.Bpmn;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Asks a plain job worker a question through the standalone job REST endpoint, end to end: the
 * worker activates the job like any other one and its answer comes back in the HTTP response.
 */
@MultiDbTest
public class StandaloneJobIT {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

  private static CamundaClient client;

  private final String jobType = "standalone-" + UUID.randomUUID();
  private final List<JobWorker> workers = new CopyOnWriteArrayList<>();

  @AfterEach
  void closeWorkers() {
    workers.forEach(JobWorker::close);
  }

  @Test
  void shouldAnswerWithTheWorkersCompletion() throws Exception {
    // given
    final var activatedKinds = new CopyOnWriteArrayList<JobKind>();
    openWorker(
        (jobClient, job) -> {
          activatedKinds.add(job.getKind());
          jobClient
              .newCompleteCommand(job)
              .variables(Map.of("valid", true, "channel", job.getVariablesAsMap().get("channel")))
              .send()
              .join();
        });

    // when
    final var response =
        createStandaloneJob(
            Map.of(
                "type",
                jobType,
                "inputExpression",
                "={channel: \"general\"}",
                "customHeaders",
                Map.of("camunda.query", "validateCredentials")));

    // then
    assertThat(response.status()).isEqualTo(200);
    assertThat(response.body().get("outcome").asText()).isEqualTo("COMPLETED");
    assertThat(response.body().get("variables").get("valid").asBoolean()).isTrue();
    assertThat(response.body().get("variables").get("channel").asText()).isEqualTo("general");
    assertThat(activatedKinds).containsExactly(JobKind.STANDALONE);
  }

  @Test
  void shouldAnswerWithTheErrorTheWorkerThrows() throws Exception {
    // given
    openWorker(
        (jobClient, job) ->
            jobClient
                .newThrowErrorCommand(job)
                .errorCode("INVALID_CREDENTIALS")
                .errorMessage("the token was revoked")
                .send()
                .join());

    // when
    final var response = createStandaloneJob(Map.of("type", jobType));

    // then
    assertThat(response.status()).isEqualTo(200);
    assertThat(response.body().get("outcome").asText()).isEqualTo("ERROR_THROWN");
    assertThat(response.body().get("errorCode").asText()).isEqualTo("INVALID_CREDENTIALS");
    assertThat(response.body().get("errorMessage").asText()).isEqualTo("the token was revoked");
  }

  @Test
  void shouldTellThatNoWorkerActivatedTheJob() throws Exception {
    // when
    final var response = createStandaloneJob(Map.of("type", jobType, "requestTimeout", 5000));

    // then
    assertThat(response.status()).isEqualTo(504);
    assertThat(response.body().get("title").asText()).isEqualTo("NO_WORKER_ACTIVATED");
  }

  @Test
  void shouldNotShowStandaloneJobInJobSearch() throws Exception {
    // given
    openWorker((jobClient, job) -> jobClient.newCompleteCommand(job).send().join());
    assertThat(createStandaloneJob(Map.of("type", jobType)).status()).isEqualTo(200);

    // when - a job created after the standalone one is visible, so the exporter got past it
    final String processId = "standalone-barrier-" + UUID.randomUUID();
    deployProcessAndWaitForIt(
        client,
        Bpmn.createExecutableProcess(processId)
            .startEvent()
            .serviceTask("task", t -> t.zeebeJobType(processId))
            .endEvent()
            .done(),
        processId + ".bpmn");
    final long processInstanceKey = startProcessInstance(client, processId).getProcessInstanceKey();
    waitForJobs(client, List.of(processInstanceKey));

    // then
    assertThat(client.newJobSearchRequest().filter(f -> f.type(jobType)).send().join().items())
        .isEmpty();
  }

  private void openWorker(final JobHandler handler) {
    workers.add(client.newWorker().jobType(jobType).handler(handler).open());
  }

  private Response createStandaloneJob(final Map<String, Object> body) throws Exception {
    final var base = client.getConfiguration().getRestAddress().toString();
    final var separator = base.endsWith("/") ? "" : "/";
    final var request =
        HttpRequest.newBuilder()
            .uri(new URI(base + separator + "v2/jobs/standalone"))
            .header("Content-Type", "application/json")
            .POST(BodyPublishers.ofString(OBJECT_MAPPER.writeValueAsString(body)))
            .build();
    final var response = HTTP_CLIENT.send(request, BodyHandlers.ofString());
    return new Response(response.statusCode(), OBJECT_MAPPER.readTree(response.body()));
  }

  private record Response(int status, JsonNode body) {}
}
