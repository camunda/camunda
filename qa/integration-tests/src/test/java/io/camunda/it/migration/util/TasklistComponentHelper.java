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
import io.camunda.search.connect.configuration.DatabaseType;
import io.camunda.webapps.schema.entities.tasklist.TaskEntity.TaskImplementation;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.awaitility.Awaitility;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.HttpWaitStrategy;

public class TasklistComponentHelper extends AbstractComponentHelper<TasklistComponentHelper>
    implements ApiCallable {
  public final Map<TaskImplementation, List<UserTaskArg>> generatedTasks = new HashMap<>();
  private GenericContainer tasklistContainer;
  private final ZeebeComponentHelper zeebe;
  private String cookie;
  private String csrfToken;
  private String tasklistUrl;

  public TasklistComponentHelper(
      final ZeebeComponentHelper zeebeComponentHelper,
      final Network network,
      final String indexPrefix) {
    super(zeebeComponentHelper, network, indexPrefix);
    zeebe = zeebeComponentHelper;
  }

  @Override
  public TasklistComponentHelper initial(
      final DatabaseType type, final Map<String, String> envOverrides) {
    createTasklist(envOverrides, false, type);
    return this;
  }

  @Override
  public TasklistComponentHelper update(
      final DatabaseType type, final Map<String, String> envOverrides) {
    createTasklist(envOverrides, true, type);
    return this;
  }

  @Override
  public void close() {
    if (tasklistContainer != null) {
      tasklistContainer.stop();
      tasklistContainer = null;
    }
  }

  @Override
  public String getCookie() {
    return cookie;
  }

  @Override
  public void setCookie(final String cookie) {
    this.cookie = cookie;
  }

  @Override
  public String getUrl() {
    return tasklistUrl;
  }

  @Override
  public String getCsrfToken() {
    return csrfToken;
  }

  @Override
  public void setCsrfToken(final String csrfToken) {
    this.csrfToken = csrfToken;
  }

  private GenericContainer createTasklist(
      final Map<String, String> envOverrides,
      final boolean newVersion,
      final DatabaseType databaseType) {
    String image = "camunda/tasklist:8.7.0-SNAPSHOT";
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

    final Map<String, String> env =
        databaseType.equals(DatabaseType.ELASTICSEARCH)
            ? tasklistElasticsearchDefaultConfig(zeebe, newVersion)
            : tasklistOpensearchDefaultConfig(zeebe, newVersion);
    if (envOverrides != null) {
      env.putAll(envOverrides);
    }
    env.forEach(tasklistContainer::withEnv);

    tasklistContainer.start();
    tasklistUrl = "http://localhost:" + tasklistContainer.getMappedPort(8080);
    try {
      login();
    } catch (final IOException | InterruptedException e) {
      throw new RuntimeException(e);
    }
    tasklistUrl = "http://localhost:" + tasklistContainer.getMappedPort(8080) + "/v1";
    return tasklistContainer;
  }

  private Map<String, String> tasklistElasticsearchDefaultConfig(
      final ZeebeComponentHelper zeebe, final boolean newVersion) {
    return new HashMap<>() {
      {
        put(
            "CAMUNDA_TASKLIST_ELASTICSEARCH_INDEXPREFIX",
            newVersion ? indexPrefix : indexPrefix + "-tasklist");
        put("CAMUNDA_TASKLIST_ZEEBEELASTICSEARCH_PREFIX", indexPrefix);
        put("CAMUNDA_TASKLIST_ELASTICSEARCH_URL", "http://elasticsearch:9200");
        put("CAMUNDA_DATABASE_URL", "http://elasticsearch:9200");
        put("CAMUNDA_TASKLIST_ZEEBEELASTICSEARCH_URL", "http://elasticsearch:9200");
        put("CAMUNDA_TASKLIST_ZEEBE_GATEWAYADDRESS", zeebe.getZeebeGatewayAddress());
        put("CAMUNDA_TASKLIST_ZEEBE_REST_ADDRESS", zeebe.getZeebeRestAddress());
        put("CAMUNDA_TASKLIST_ZEEBE_COMPATIBILITY_ENABLED", "true");
      }
    };
  }

  private Map<String, String> tasklistOpensearchDefaultConfig(
      final ZeebeComponentHelper zeebe, final boolean newVersion) {
    return new HashMap<>() {
      {
        put(
            "CAMUNDA_TASKLIST_OPENSEARCH_INDEXPREFIX",
            newVersion ? indexPrefix : indexPrefix + "-tasklist");
        put("CAMUNDA_TASKLIST_ZEEBEOPENSEARCH_PREFIX", indexPrefix);
        put("CAMUNDA_TASKLIST_DATABASE", "opensearch");
        put("CAMUNDA_DATABASE_TYPE", "opensearch");
        put("CAMUNDA_TASKLIST_OPENSEARCH_URL", "http://opensearch:9200");
        put("CAMUNDA_DATABASE_URL", "http://opensearch:9200");
        put("CAMUNDA_TASKLIST_ZEEBEOPENSEARCH_URL", "http://opensearch:9200");
        put("CAMUNDA_TASKLIST_ZEEBE_GATEWAYADDRESS", zeebe.getZeebeGatewayAddress());
        put("CAMUNDA_TASKLIST_ZEEBE_REST_ADDRESS", zeebe.getZeebeRestAddress());
        put("CAMUNDA_TASKLIST_ZEEBE_COMPATIBILITY_ENABLED", "true");
      }
    };
  }

  public void waitForTasksToBeImported(final int count) {
    Awaitility.await()
        .ignoreExceptions()
        .atMost(Duration.ofSeconds(15))
        .until(
            () -> {
              final var response =
                  request(
                      b ->
                          b.POST(HttpRequest.BodyPublishers.noBody())
                              .uri(URI.create(getUrl() + "/tasks/search")),
                      HttpResponse.BodyHandlers.ofString());
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
                          .add(new UserTaskArg(task.get("id").getAsLong(), "8.7", apiVersion));
                    });
                return true;
              }
              return false;
            });
  }

  public record UserTaskArg(long key, String version, String apiVersion) {}
}
