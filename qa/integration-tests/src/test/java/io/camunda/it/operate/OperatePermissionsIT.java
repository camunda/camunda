/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.operate;

import static io.camunda.it.client.QueryTest.deployResource;
import static io.camunda.it.client.QueryTest.startProcessInstance;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.webapp.api.v1.entities.ProcessDefinition;
import io.camunda.operate.webapp.api.v1.entities.ProcessInstance;
import io.camunda.operate.webapp.api.v1.entities.Results;
import io.camunda.operate.webapp.rest.dto.ProcessGroupDto;
import io.camunda.operate.webapp.security.auth.OperateUserDetailsService;
import io.camunda.qa.util.cluster.TestOperateWithExporter;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.response.Process;
import io.camunda.zeebe.client.api.response.ProcessInstanceEvent;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import java.io.IOException;
import java.net.Authenticator;
import java.net.PasswordAuthentication;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

@ZeebeIntegration
public class OperatePermissionsIT {

  private static final String OPERATE_USER = "demo";
  private static final String OPERATE_PASS = "demo";
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private static final List<Process> DEPLOYED_PROCESSES = new ArrayList<>();
  private static final List<ProcessInstanceEvent> PROCESS_INSTANCES = new ArrayList<>();

  private static ZeebeClient zeebeClient;
  private static String operateEndpoint;
  private static String operateCookie;

  @ZeebeIntegration.TestZeebe(initMethod = "initTestOperateWithExporter")
  private static TestOperateWithExporter testOperateWithExporter;

  @SuppressWarnings("unused")
  static void initTestOperateWithExporter() {
    testOperateWithExporter = new TestOperateWithExporter();
  }

  @BeforeAll
  public static void beforeAll() throws Exception {

    waitForOperateUserCreation();
    operateEndpoint = testOperateWithExporter.operateUri().toString();
    operateCookie = loginToOperate(operateEndpoint, OPERATE_USER, OPERATE_USER);

    zeebeClient = testOperateWithExporter.newClientBuilder().build();

    //    zeebeClient
    //        .newUserCreateCommand()
    //        .name("Demo")
    //        .username("demo")
    //        .password("demo")
    //        .email("demo@camunda.com")
    //        .send()
    //        .join();

    final List<String> processes =
        List.of(
            "service_tasks_v1.bpmn",
            "service_tasks_v2.bpmn",
            "incident_process_v1.bpmn",
            "manual_process.bpmn",
            "parent_process_v1.bpmn",
            "child_process_v1.bpmn");
    processes.forEach(
        process ->
            DEPLOYED_PROCESSES.addAll(
                deployResource(zeebeClient, String.format("process/%s", process)).getProcesses()));

    waitForProcessesToBeDeployed(DEPLOYED_PROCESSES.size());

    PROCESS_INSTANCES.add(startProcessInstance(zeebeClient, "service_tasks_v1"));
    PROCESS_INSTANCES.add(startProcessInstance(zeebeClient, "service_tasks_v2", "{\"path\":222}"));
    PROCESS_INSTANCES.add(startProcessInstance(zeebeClient, "manual_process"));
    PROCESS_INSTANCES.add(startProcessInstance(zeebeClient, "incident_process_v1"));
    PROCESS_INSTANCES.add(startProcessInstance(zeebeClient, "parent_process_v1"));

    waitForProcessInstancesToStart(6);
  }

  private static void waitForOperateUserCreation() {
    final var userDetailsService = testOperateWithExporter.bean(OperateUserDetailsService.class);
    userDetailsService.initializeUsers();
    Awaitility.await()
        .pollDelay(5, TimeUnit.SECONDS)
        .atMost(10, TimeUnit.SECONDS)
        .until(() -> true);
  }

  private static void waitForProcessesToBeDeployed(final int expectedProcessDefinitions) {
    Awaitility.await("Should deploy processes and import in Operate")
        .atMost(Duration.ofSeconds(15))
        .ignoreExceptions() // Ignore exceptions and continue retrying
        .untilAsserted(
            () -> {
              final var result = getOperateProcessDefinitions(operateEndpoint, operateCookie);
              assertThat(result.size()).isEqualTo(expectedProcessDefinitions);
            });
  }

  private static void waitForProcessInstancesToStart(final int expectedProcessInstances) {
    Awaitility.await("should start process instances and import in Operate")
        .atMost(Duration.ofSeconds(60))
        .ignoreExceptions() // Ignore exceptions and continue retrying
        .untilAsserted(
            () -> {
              final var result = getOperateProcessInstances(operateEndpoint, operateCookie);
              assertThat(result.size()).isEqualTo(expectedProcessInstances);
            });
  }

  @AfterAll
  static void afterAll() {
    DEPLOYED_PROCESSES.clear();
    PROCESS_INSTANCES.clear();
  }

  @Test
  public void shouldReturnProcessesGrouped() throws Exception {

    try (final HttpClient httpClient = buildHttpClient()) {
      // given
      final HttpRequest request =
          HttpRequest.newBuilder()
              .uri(new URI(String.format("%sapi/processes/grouped", operateEndpoint)))
              .header("Content-Type", "application/json")
              .header("Cookie", operateCookie)
              .GET()
              .build();

      // when
      final HttpResponse<String> response =
          httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      final String responseBody = response.body();
      final List<ProcessGroupDto> processGroupDtos =
          OBJECT_MAPPER.readValue(responseBody, new TypeReference<>() {});

      // then
      assertThat(processGroupDtos).isEmpty();
    }
  }

  private static String loginToOperate(
      String operateEndpoint, String operateUser, String operatePass) throws Exception {

    try (final HttpClient httpClient = buildHttpClient(operateUser, operatePass)) {
      final String requestBody = String.format("username=%s&password=%s", operateUser, operatePass);
      final HttpRequest request =
          HttpRequest.newBuilder()
              .uri(new URI(String.format("%sapi/login", operateEndpoint)))
              .header("Content-Type", "application/x-www-form-urlencoded")
              .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
              .build();

      final HttpResponse<String> response =
          httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      final String cookie =
          response
              .headers()
              .firstValue("set-cookie")
              .orElseThrow(
                  (Supplier<IOException>)
                      () -> new IOException("Expected cookie from Operate server not found."));
      final String responseBody = response.body();

      System.out.println("### COOKIE: " + cookie);
      return cookie;
    }
  }

  private static List<ProcessDefinition> getOperateProcessDefinitions(
      String operateEndpoint, String operateCookie) throws Exception {
    final String requestBody = "{}";
    final HttpRequest request =
        HttpRequest.newBuilder()
            .uri(new URI(String.format("%sv1/process-definitions/search", operateEndpoint)))
            .header("Content-Type", "application/json")
            .header("Cookie", operateCookie)
            .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
            .build();

    try (HttpClient httpClient = buildHttpClient()) {
      final HttpResponse<String> response =
          httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      final String responseBody = response.body();
      final Results<ProcessDefinition> processDefinitions =
          OBJECT_MAPPER.readValue(responseBody, new TypeReference<>() {});

      return processDefinitions.getItems();
    }
  }

  private static List<ProcessInstance> getOperateProcessInstances(
      String operateEndpoint, String operateCookie) throws Exception {
    final String requestBody = "{}";
    final HttpRequest request =
        HttpRequest.newBuilder()
            .uri(new URI(String.format("%sv1/process-instances/search", operateEndpoint)))
            .header("Content-Type", "application/json")
            .header("Cookie", operateCookie)
            .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
            .build();

    try (HttpClient httpClient = buildHttpClient()) {
      final HttpResponse<String> response =
          httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      final String responseBody = response.body();
      final Results<ProcessInstance> processInstances =
          OBJECT_MAPPER.readValue(responseBody, new TypeReference<>() {});

      return processInstances.getItems();
    }
  }

  private static HttpClient buildHttpClient() {
    final HttpClient httpClient = HttpClient.newBuilder().build();

    return httpClient;
  }

  private static HttpClient buildHttpClient(String user, String password) {
    final HttpClient httpClient =
        HttpClient.newBuilder()
            .authenticator(
                new Authenticator() {
                  @Override
                  protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(user, password.toCharArray());
                  }
                })
            .build();

    return httpClient;
  }
}
