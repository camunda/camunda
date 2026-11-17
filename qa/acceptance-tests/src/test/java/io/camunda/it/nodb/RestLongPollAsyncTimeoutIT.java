/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.nodb;

import static io.camunda.spring.utils.DatabaseTypeUtils.UNIFIED_CONFIG_PROPERTY_CAMUNDA_DATABASE_TYPE;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.util.Strings;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Regression test for #64258: REST long-poll job activation and {@code awaitCompletion} process
 * instance creation must outlive the servlet container's async request timeout, since both rely on
 * a broker-side long-poll/wait that can legitimately take longer than that timeout.
 */
@ZeebeIntegration
public class RestLongPollAsyncTimeoutIT {

  @TestZeebe
  private final TestStandaloneBroker broker =
      new TestStandaloneBroker()
          .withUnauthenticatedAccess()
          .withProperty(UNIFIED_CONFIG_PROPERTY_CAMUNDA_DATABASE_TYPE, "none")
          .withProperty("spring.profiles.active", "broker")
          .withProperty("spring.mvc.async.request-timeout", "2s");

  private CamundaClient client;
  private final HttpClient http = HttpClient.newHttpClient();

  @BeforeEach
  void setUp() {
    client = broker.newClientBuilder().preferRestOverGrpc(false).build();
  }

  @AfterEach
  void tearDown() {
    client.close();
    http.close();
  }

  @Test
  void shouldReturnEmptyActivationAfterRequestTimeoutBeyondAsyncLimit() throws Exception {
    // given - no jobs are available, and requestTimeout exceeds the 2s async limit
    // when - activation waits past the container's async timeout
    final var result = activate(Strings.newRandomValidIdentityId(), 5_000);

    // then - the long poll is not cut off, and returns an empty result after the full wait
    assertThat(result.status).isEqualTo(200);
    assertThat(result.elapsedMs).isGreaterThanOrEqualTo(4_000);
  }

  @Test
  void shouldDeliverJobCreatedAfterAsyncLimitToWaitingRequest() throws Exception {
    // given - a long poll activation request waiting past the async limit
    final var processId = Strings.newRandomValidBpmnId();
    final var jobType = processId + "-type";
    deployServiceTaskProcess(processId, jobType);
    final var pending = activateAsync(jobType, 6_000);

    // when - a job becomes available after the async limit has elapsed
    Awaitility.await().during(Duration.ofSeconds(3)).until(() -> !pending.isDone());
    client.newCreateInstanceCommand().bpmnProcessId(processId).latestVersion().send().join();
    final var result = pending.join();

    // then - the waiting request receives the job instead of losing it to the dead connection
    assertThat(result.status).isEqualTo(200);
    assertThat(result.body).contains("\"jobKey\"");
  }

  @Test
  void shouldReturnGatewayTimeoutForAwaitCompletionBeyondAsyncLimit() throws Exception {
    // given - a process that never completes, and requestTimeout exceeds the async limit
    final var processId = Strings.newRandomValidBpmnId();
    deployServiceTaskProcess(processId, processId + "-type");

    // when - awaitCompletion waits past the container's async timeout
    final var result =
        post(
            "process-instances",
            "{\"processDefinitionId\":\"%s\",\"awaitCompletion\":true,\"requestTimeout\":5000}"
                .formatted(processId));

    // then - the request times out with the broker's 504, not the container's 503
    assertThat(result.status).isEqualTo(504);
    assertThat(result.elapsedMs).isGreaterThanOrEqualTo(4_000);
  }

  @Test
  void shouldKeepBehaviourBelowAsyncLimit() throws Exception {
    // given - a process definition and a worker type with no pending jobs
    final var processId = Strings.newRandomValidBpmnId();
    deployServiceTaskProcess(processId, processId + "-type");

    // when - requestTimeout stays well below the async limit
    final var activation = activate(Strings.newRandomValidIdentityId(), 1_000);
    final var create =
        post(
            "process-instances",
            "{\"processDefinitionId\":\"%s\",\"awaitCompletion\":false}".formatted(processId));

    // then - behaviour is unchanged from before the fix
    assertThat(activation.status).isEqualTo(200);
    assertThat(create.status).isEqualTo(200);
  }

  private void deployServiceTaskProcess(final String processId, final String jobType) {
    client
        .newDeployResourceCommand()
        .addProcessModel(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .serviceTask("task", t -> t.zeebeJobType(jobType))
                .endEvent()
                .done(),
            processId + ".bpmn")
        .send()
        .join();
  }

  private Result activate(final String jobType, final long requestTimeout) throws Exception {
    return activateAsync(jobType, requestTimeout).join();
  }

  private CompletableFuture<Result> activateAsync(final String jobType, final long requestTimeout) {
    return postAsync(
        "jobs/activation",
        "{\"type\":\"%s\",\"timeout\":60000,\"maxJobsToActivate\":1,\"requestTimeout\":%d}"
            .formatted(jobType, requestTimeout));
  }

  private Result post(final String path, final String body) {
    return postAsync(path, body).join();
  }

  private CompletableFuture<Result> postAsync(final String path, final String body) {
    final var base = broker.restAddress().toString().replaceAll("/$", "");
    final var request =
        HttpRequest.newBuilder(URI.create(base + "/v2/" + path))
            .header("Content-Type", "application/json")
            .timeout(Duration.ofSeconds(60))
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();
    final long start = System.nanoTime();
    return http.sendAsync(request, BodyHandlers.ofString())
        .handle(
            (HttpResponse<String> r, Throwable t) ->
                new Result(
                    r == null ? -1 : r.statusCode(),
                    (System.nanoTime() - start) / 1_000_000,
                    r == null ? String.valueOf(t) : r.body()));
  }

  private record Result(int status, long elapsedMs, String body) {}
}
