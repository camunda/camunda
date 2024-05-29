/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.response.ProcessInstanceEvent;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneCamunda;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

@ZeebeIntegration
public class RESTApiTest {

  @TestZeebe final TestStandaloneCamunda testStandaloneCamunda = new TestStandaloneCamunda();

  @Test
  public void shouldAcceptRequest() {
    // given
    final ZeebeClient zeebeClient = testStandaloneCamunda.newClientBuilder().build();

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

    final ProcessInstanceEvent processInstanceEvent =
        zeebeClient
            .newCreateInstanceCommand()
            .bpmnProcessId("process")
            .latestVersion()
            .send()
            .join();

    // when
    // Query REST API
    final URI restAddress = testStandaloneCamunda.restAddress();
    final HttpClient httpClient = HttpClient.newHttpClient();

    // then
    Awaitility.await("should receive data from ES")
        .untilAsserted(
            () -> {
              final HttpRequest request =
                  HttpRequest.newBuilder()
                      .uri(new URI(String.format("%sv1/process-instances/search1", restAddress)))
                      .header("content-type", "application/json")
                      .POST(
                          HttpRequest.BodyPublishers.ofString(
                              String.format(
                                  "{\"filter\":{\"key\":%d}, \"sort\":{}}",
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
            });
  }
}
