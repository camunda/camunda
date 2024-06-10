/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.camunda.operate.webapp.api.v1.entities.ProcessInstance;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneCamunda;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.List;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.ObjectMapper;

@ZeebeIntegration
public class StandaloneCamundaTest {

  @TestZeebe final TestStandaloneCamunda testStandaloneCamunda = new TestStandaloneCamunda();

  @Test
  public void shouldCreateAndRetrieveInstance() {
    // givne
    final var zeebeClient = testStandaloneCamunda.newClientBuilder().build();

    // when
    zeebeClient
        .newDeployResourceCommand()
        .addProcessModel(
            Bpmn.createExecutableProcess("process")
                .startEvent()
                .serviceTask("test")
                .zeebeJobType("type")
                .endEvent()
                .done(),
            "simple.bpmn")
        .send()
        .join();

    final var processInstanceEvent =
        zeebeClient
            .newCreateInstanceCommand()
            .bpmnProcessId("process")
            .latestVersion()
            .send()
            .join();

    // then
    // Query REST API
    final URI restAddress = testStandaloneCamunda.restAddress();
    final HttpClient httpClient = HttpClient.newHttpClient();

    Awaitility.await("should receive data from ES")
        .timeout(Duration.ofMinutes(1))
        .untilAsserted(
            () -> {
              final HttpRequest request =
                  HttpRequest.newBuilder()
                      .uri(new URI(String.format("%sv1/process-instances/search", restAddress)))
                      .header("content-type", "application/json")
                      .POST(
                          HttpRequest.BodyPublishers.ofString(
                              String.format(
                                  "{\"filter\":{\"key\":%d},\"sort\":[{\"field\":\"endDate\",\"order\":\"ASC\"}],\"size\":20}",
                                  processInstanceEvent.getProcessInstanceKey())))
                      .build();
              final HttpResponse<String> response =
                  httpClient.send(request, BodyHandlers.ofString());

              assertThat(response.statusCode())
                  .withFailMessage(
                      () ->
                          "Expect success response, got "
                              + response.body()
                              + " with code: "
                              + response.statusCode())
                  .isEqualTo(200);

              final ObjectMapper objectMapper = new ObjectMapper();
              final ProcessInstanceResult processInstanceResult =
                  objectMapper.readValue(response.body(), ProcessInstanceResult.class);
              assertThat(processInstanceResult.total)
                  .withFailMessage("Expect to read a process instance from ES")
                  .isGreaterThan(0);

              assertThat(processInstanceResult.processInstances.getFirst().getKey())
                  .withFailMessage("Expect to read the expected process instance from ES")
                  .isEqualTo(processInstanceEvent.getProcessInstanceKey());
            });
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record ProcessInstanceResult(
      @JsonProperty("items") List<ProcessInstance> processInstances,
      @JsonProperty("total") long total) {}
}
