/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.migration.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import io.camunda.webapps.schema.entities.tasklist.TaskEntity.TaskImplementation;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.awaitility.Awaitility;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.HttpWaitStrategy;

public class TasklistUtil {
  static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();
  public final Map<TaskImplementation, List<UserTaskArg>> generatedTasks = new HashMap<>();
  private GenericContainer tasklistContainer;
  private String cookie;
  private String csrfToken;
  private final Network network;
  private String tasklistUrl = "http://localhost:8080";

  public TasklistUtil(final Network network) {
    this.network = network;
  }

  public GenericContainer createTasklist(final Map<String, String> env, final boolean newVersion) {
    String image = "camunda/tasklist:8.6.6";
    if (newVersion) {
      image = "camunda/tasklist:SNAPSHOT";
    }
    tasklistContainer =
        new GenericContainer<>(image)
            .withExposedPorts(9600, 8080)
            .withAccessToHost(true)
            .withNetwork(network)
            .waitingFor(
                new HttpWaitStrategy()
                    .forPort(9600)
                    .forPath("/actuator/health")
                    .withReadTimeout(Duration.ofSeconds(120)))
            .withStartupTimeout(Duration.ofSeconds(120));

    env.forEach(tasklistContainer::withEnv);
    tasklistContainer.start();
    try {
      login();
    } catch (final IOException | InterruptedException e) {
      throw new RuntimeException(e);
    }
    tasklistUrl = "http://localhost:" + tasklistContainer.getMappedPort(8080) + "/v1";
    return tasklistContainer;
  }

  public void waitForTasksToBeImported(final int count) {
    final HttpRequest request =
        HttpRequest.newBuilder()
            .POST(HttpRequest.BodyPublishers.noBody())
            .headers(requestHeaders())
            .uri(URI.create(tasklistUrl + "/tasks/search"))
            .build();

    Awaitility.await()
        .ignoreExceptions()
        .atMost(Duration.ofSeconds(15))
        .until(
            () -> {
              final var response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
              final JsonArray jsonArray = JsonParser.parseString(response.body()).getAsJsonArray();
              if (jsonArray.size() == count) {
                jsonArray.forEach(
                    j -> {
                      final var task = j.getAsJsonObject();
                      final var impl =
                          TaskImplementation.valueOf(task.get("implementation").getAsString());
                      final var apiVersion =
                          task.get("name").getAsString().contains("V1") ? "V1" : "V2";
                      generatedTasks.putIfAbsent(impl, new ArrayList<>());
                      generatedTasks
                          .get(impl)
                          .add(new UserTaskArg(task.get("id").getAsLong(), "8.6", apiVersion));
                    });
                return true;
              }
              return false;
            });
  }

  public HttpResponse<String> assign(final long userTaskKey, final String assignee)
      throws InterruptedException, IOException {
    final HttpRequest assignTask =
        HttpRequest.newBuilder()
            .method(
                "PATCH", HttpRequest.BodyPublishers.ofString("{\"assignee\":\"" + assignee + "\"}"))
            .headers(requestHeaders())
            .uri(URI.create(tasklistUrl + "/tasks/" + userTaskKey + "/assign"))
            .build();
    return HTTP_CLIENT.send(assignTask, HttpResponse.BodyHandlers.ofString());
  }

  public HttpResponse<String> unassignTask(final long userTaskKey)
      throws InterruptedException, IOException {
    final HttpRequest unassignTask =
        HttpRequest.newBuilder()
            .method("PATCH", HttpRequest.BodyPublishers.ofString(""))
            .headers(requestHeaders())
            .uri(URI.create(tasklistUrl + "/tasks/" + userTaskKey + "/unassign"))
            .build();
    return HTTP_CLIENT.send(unassignTask, HttpResponse.BodyHandlers.ofString());
  }

  public HttpResponse<String> completeTask(final long userTaskKey)
      throws InterruptedException, IOException {
    final HttpRequest completeTask =
        HttpRequest.newBuilder()
            .method("PATCH", HttpRequest.BodyPublishers.ofString(""))
            .headers(requestHeaders())
            .uri(URI.create(tasklistUrl + "/tasks/" + userTaskKey + "/complete"))
            .build();
    return HTTP_CLIENT.send(completeTask, HttpResponse.BodyHandlers.ofString());
  }

  private String[] requestHeaders() {
    return new String[] {
      "Cookie",
      cookie,
      "X-Csrf-Token",
      csrfToken,
      "Content-Type",
      "application/json",
      "Accept",
      "application/json"
    };
  }

  public void stop() {
    if (tasklistContainer != null) {
      tasklistContainer.stop();
      tasklistContainer = null;
    }
  }

  private void login() throws IOException, InterruptedException {
    final HttpRequest login =
        HttpRequest.newBuilder()
            .POST(HttpRequest.BodyPublishers.noBody())
            .uri(
                URI.create(
                    "http://localhost:"
                        + tasklistContainer.getMappedPort(8080)
                        + "/api/login?username=demo&password=demo"))
            .build();
    final var loginRes = HTTP_CLIENT.send(login, HttpResponse.BodyHandlers.ofString());

    cookie =
        loginRes.headers().allValues("Set-Cookie").stream()
            .map(k -> k.split(";")[0])
            .collect(Collectors.joining("; "));
    csrfToken =
        loginRes
            .headers()
            .firstValue("X-CSRF-TOKEN")
            .orElse(
                loginRes.headers().allValues("Set-Cookie").stream()
                    .filter(c -> c.contains("X-CSRF-TOKEN"))
                    .filter(c -> !c.split("=")[1].isBlank())
                    .map(c -> c.split("=")[0] + "=" + c.split("=")[1])
                    .findFirst()
                    .get());
  }

  public record UserTaskArg(long key, String version, String apiVersion) {}
}
