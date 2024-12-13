/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.migration.util;

import co.elastic.clients.elasticsearch._types.Script;
import co.elastic.clients.elasticsearch.core.reindex.Destination;
import co.elastic.clients.elasticsearch.core.reindex.Source;
import co.elastic.clients.elasticsearch.indices.GetIndexRequest;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import io.camunda.search.connect.configuration.ConnectConfiguration;
import io.camunda.search.connect.es.ElasticsearchConnector;
import io.camunda.webapps.schema.entities.tasklist.TaskEntity.TaskImplementation;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.cluster.TestZeebePort;
import io.camunda.zeebe.test.util.testcontainers.TestSearchContainers;
import io.zeebe.containers.ZeebeContainer;
import io.zeebe.containers.ZeebeVolume;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.opensearch.testcontainers.OpensearchContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.HttpWaitStrategy;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.utility.DockerImageName;

public abstract class MigrationUtil {
  protected static final Network TC_NETWORK = Network.newNetwork();
  protected static final ZeebeVolume ZEEBE_VOLUME = ZeebeVolume.newVolume();
  protected static final String ES_INTERNAL_URL = "http://elasticsearch:9200";
  protected static final ElasticsearchContainer ES_CONTAINER =
      TestSearchContainers.createDefeaultElasticsearchContainer()
          .withNetwork(TC_NETWORK)
          .withNetworkAliases("elasticsearch");

  protected static final OpensearchContainer OS_CONTAINER =
      TestSearchContainers.createDefaultOpensearchContainer()
          .withNetwork(TC_NETWORK)
          .withNetworkAliases("opensearch");

  public static void setup() {
    ES_CONTAINER.start();
    ZeebeUtil.start86Broker();
  }

  @AfterAll
  static void tearDown() throws IOException {
    if (TasklistUtil.tasklistContainer != null && TasklistUtil.tasklistContainer.isRunning()) {
      TasklistUtil.tasklistContainer.close();
    }
    ZeebeUtil.zeebeClient.close();
    ZeebeUtil.zeebeContainer.close();
    ZeebeUtil.broker.close();
    // Delete Zeebe data dir
    Files.walk(Path.of(System.getProperty("user.dir") + "/zeebe-data"))
        .sorted(java.util.Comparator.reverseOrder())
        .forEach(
            p -> {
              try {
                Files.delete(p);
              } catch (final IOException ignored) {
                System.out.println("do nothing");
              }
            });
    ES_CONTAINER.stop();
  }

  static Stream<Boolean> isElasticsearch() {
    return Stream.of(true, false);
  }

  public record TestTarget(long key, String version, String apiVersion) {}

  public class ZeebeUtil {
    public static ZeebeClient zeebeClient;
    public static ZeebeContainer zeebeContainer;
    public static TestStandaloneBroker broker;
    private static final String CAMUNDA_OLD_VERSION = "8.6.6";

    public static String getZeebeGatewayAddress() {
      if (zeebeContainer != null && zeebeContainer.isStarted()) {
        return zeebeContainer.getInternalAddress(TestZeebePort.GATEWAY.port());
      }
      return "host.testcontainers.internal:" + broker.mappedPort(TestZeebePort.GATEWAY);
    }

    public static String getZeebeRestAddress() {
      if (zeebeContainer != null && zeebeContainer.isStarted()) {
        return zeebeContainer.getInternalAddress(TestZeebePort.REST.port());
      }
      return "http://host.testcontainers.internal:" + broker.mappedPort(TestZeebePort.REST);
    }

    public static void start86Broker() {
      zeebeContainer =
          new ZeebeContainer(DockerImageName.parse("camunda/zeebe:" + CAMUNDA_OLD_VERSION))
              .withExposedPorts(26500, 9600, 8080)
              .withNetwork(TC_NETWORK)
              .withNetworkAliases("zeebe")
              .withEnv(
                  "ZEEBE_BROKER_EXPORTERS_ELASTICSEARCH_CLASSNAME",
                  "io.camunda.zeebe.exporter.ElasticsearchExporter")
              .withEnv("ZEEBE_BROKER_EXPORTERS_ELASTICSEARCH_ARGS_URL", ES_INTERNAL_URL)
              .withEnv("ZEEBE_BROKER_EXPORTERS_ELASTICSEARCH_ARGS_BULK_SIZE", "1")
              .withEnv("ZEEBE_BROKER_GATEWAY_ENABLE", "true")
              .withEnv("CAMUNDA_DATABASE_URL", ES_INTERNAL_URL)
              .withEnv("CAMUNDA_REST_QUERY_ENABLED", "true");

      zeebeContainer
          .withCreateContainerCmdModifier(
              cmd -> {
                cmd.withUser("1001:0").getHostConfig().withBinds(ZEEBE_VOLUME.asZeebeBind());
              })
          .withCommand(
              "sh", "-c", "chmod -R 777 /usr/local/zeebe/data && /usr/local/bin/start-zeebe");

      zeebeContainer.start();
      zeebeClient =
          ZeebeClient.newClientBuilder()
              .gatewayAddress(zeebeContainer.getExternalGatewayAddress())
              .restAddress(
                  URI.create(
                      "http://"
                          + zeebeContainer.getExternalHost()
                          + ":"
                          + zeebeContainer.getMappedPort(8080)))
              .usePlaintext()
              .build();
    }

    public static void extractVolume() throws IOException {
      ZEEBE_VOLUME.extract(Path.of(System.getProperty("user.dir") + "/zeebe-data"));
      Files.delete(
          Path.of(
              System.getProperty("user.dir") + "/zeebe-data/usr/local/zeebe/data/.topology.meta"));
    }

    public static void start87Broker() throws IOException {
      if (zeebeContainer != null) {
        extractVolume();
      }
      broker =
          new TestStandaloneBroker()
              .withRecordingExporter(true)
              .withBrokerConfig(cfg -> cfg.getGateway().setEnable(true))
              .withExporter(
                  "CamundaExporter",
                  cfg -> {
                    cfg.setClassName("io.camunda.exporter.CamundaExporter");
                    cfg.setArgs(
                        Map.of(
                            "connect",
                            Map.of("url", "http://" + ES_CONTAINER.getHttpHostAddress()),
                            "bulk",
                            Map.of("size", 10, "delay", 1),
                            "index",
                            Map.of("shouldWaitForImporters", false)));
                  })
              .withWorkingDirectory(
                  Path.of(System.getProperty("user.dir") + "/zeebe-data/usr/local/zeebe"))
              .withProperty("camunda.rest.query.enabled", true)
              .withProperty("camunda.database.url", "http://" + ES_CONTAINER.getHttpHostAddress())
              .start();
      broker.awaitCompleteTopology();
      org.testcontainers.Testcontainers.exposeHostPorts(
          broker.mappedPort(TestZeebePort.GATEWAY), broker.mappedPort(TestZeebePort.REST));

      zeebeClient = broker.newClientBuilder().build();
      zeebeClient
          .newUserCreateCommand()
          .name("demo")
          .username("demo")
          .email("dem@demo.com")
          .password("demo")
          .send()
          .join();
      if (TasklistUtil.tasklistContainer != null) {
        migrateProcesses();
      }
    }

    private static void migrateProcesses() throws IOException {

      final var cfg = new ConnectConfiguration();
      cfg.setUrl("http://" + ES_CONTAINER.getHttpHostAddress());
      cfg.setType("elasticsearch");
      final var connector = new ElasticsearchConnector(cfg);
      final var esClient = connector.createClient();

      Awaitility.await()
          .until(
              () ->
                  esClient
                          .indices()
                          .get(GetIndexRequest.of(req -> req.index("*")))
                          .get("operate-process-8.3.0_")
                      != null);

      // Copy previous tasklist-process to operate-process, required for V2 APIs
      esClient.reindex(
          r ->
              r.source(Source.of(s -> s.index("tasklist-process-8.4.0_")))
                  .dest(Destination.of(d -> d.index("operate-process-8.3.0_")))
                  .script(
                      Script.of(
                          s ->
                              s.inline(
                                  i ->
                                      i.source(
                                              "ctx._source.isPublic = ctx._source.remove('startedByForm')")
                                          .lang("painless")))));
    }
  }

  public class TasklistUtil {

    public static GenericContainer tasklistContainer;
    public static final Map<TaskImplementation, List<TestTarget>> TASKS = new HashMap<>();
    public static String cookie;
    public static String csrfToken;
    static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

    public static void login() throws IOException, InterruptedException {
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

      TasklistUtil.cookie =
          loginRes.headers().allValues("Set-Cookie").stream()
              .map(k -> k.split(";")[0])
              .collect(Collectors.joining("; "));
      TasklistUtil.csrfToken =
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

    public static void waitForTasksToBeImported(final int count) {
      final HttpRequest request =
          HttpRequest.newBuilder()
              .POST(HttpRequest.BodyPublishers.noBody())
              .headers(requestHeaders())
              .uri(
                  URI.create(
                      "http://localhost:"
                          + tasklistContainer.getMappedPort(8080)
                          + "/v1/tasks/search"))
              .build();

      Awaitility.await()
          .ignoreExceptions()
          .atMost(Duration.ofSeconds(15))
          .until(
              () -> {
                final var response =
                    HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
                final JsonArray jsonArray =
                    JsonParser.parseString(response.body()).getAsJsonArray();
                if (jsonArray.size() == count) {
                  jsonArray.forEach(
                      j -> {
                        final var task = j.getAsJsonObject();
                        final var impl =
                            TaskImplementation.valueOf(task.get("implementation").getAsString());
                        final var apiVersion =
                            task.get("name").getAsString().contains("V1") ? "V1" : "V2";
                        TASKS.putIfAbsent(impl, new ArrayList<>());
                        TASKS
                            .get(impl)
                            .add(new TestTarget(task.get("id").getAsLong(), "8.6", apiVersion));
                      });
                  return true;
                }
                return false;
              });
    }

    public static void startTasklist() throws IOException, InterruptedException {
      String image = "camunda/tasklist:8.6.6";
      if (ZeebeUtil.broker != null) {
        image = "camunda/tasklist:SNAPSHOT";
      }
      tasklistContainer =
          new GenericContainer<>(image)
              .withExposedPorts(9600, 8080)
              .withAccessToHost(true)
              .withNetwork(TC_NETWORK)
              .waitingFor(
                  new HttpWaitStrategy()
                      .forPort(9600)
                      .forPath("/actuator/health")
                      .withReadTimeout(Duration.ofSeconds(120)))
              .withStartupTimeout(Duration.ofSeconds(120))
              .withEnv("CAMUNDA_TASKLIST_ELASTICSEARCH_URL", ES_INTERNAL_URL)
              .withEnv("CAMUNDA_TASKLIST_ELASTICSEARCH_HOST", "elasticsearch")
              .withEnv("CAMUNDA_TASKLIST_ELASTICSEARCH_PORT", "9200")
              .withEnv("CAMUNDA_TASKLIST_ZEEBEELASTICSEARCH_URL", ES_INTERNAL_URL)
              .withEnv("CAMUNDA_TASKLIST_ZEEBE_GATEWAYADDRESS", ZeebeUtil.getZeebeGatewayAddress())
              .withEnv("CAMUNDA_TASKLIST_ZEEBE_REST_ADDRESS", ZeebeUtil.getZeebeRestAddress());

      /*      if (ZeebeUtil.broker != null) {
        tasklistContainer.withEnv("SPRING_PROFILES_ACTIVE", "standalone, tasklist, auth-basic");
      }*/

      tasklistContainer.start();
      login();
    }

    public static HttpResponse<String> assign(final long userTaskKey)
        throws InterruptedException, IOException {
      final HttpRequest assignTask =
          HttpRequest.newBuilder()
              .method("PATCH", HttpRequest.BodyPublishers.ofString("{\"assignee\":\"demo\"}"))
              .headers(requestHeaders())
              .uri(
                  URI.create(
                      "http://localhost:"
                          + tasklistContainer.getMappedPort(8080)
                          + "/v1/tasks/"
                          + userTaskKey
                          + "/assign"))
              .build();
      return HTTP_CLIENT.send(assignTask, HttpResponse.BodyHandlers.ofString());
    }

    public static HttpResponse<String> unassignTask(final long userTaskKey)
        throws InterruptedException, IOException {
      final HttpRequest unassignTask =
          HttpRequest.newBuilder()
              .method("PATCH", HttpRequest.BodyPublishers.ofString(""))
              .headers(requestHeaders())
              .uri(
                  URI.create(
                      "http://localhost:"
                          + tasklistContainer.getMappedPort(8080)
                          + "/v1/tasks/"
                          + userTaskKey
                          + "/unassign"))
              .build();
      return HTTP_CLIENT.send(unassignTask, HttpResponse.BodyHandlers.ofString());
    }

    public static HttpResponse<String> completeTask(final long userTaskKey)
        throws InterruptedException, IOException {
      final HttpRequest completeTask =
          HttpRequest.newBuilder()
              .method("PATCH", HttpRequest.BodyPublishers.ofString(""))
              .headers(requestHeaders())
              .uri(
                  URI.create(
                      "http://localhost:"
                          + tasklistContainer.getMappedPort(8080)
                          + "/v1/tasks/"
                          + userTaskKey
                          + "/complete"))
              .build();
      return HTTP_CLIENT.send(completeTask, HttpResponse.BodyHandlers.ofString());
    }

    private static String[] requestHeaders() {
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
  }
}
