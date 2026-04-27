/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.schema;

import static org.assertj.core.api.Assertions.assertThat;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import io.camunda.client.CamundaClient;
import io.camunda.zeebe.exporter.ElasticsearchExporter;
import io.camunda.zeebe.exporter.opensearch.OpensearchExporter;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.qa.util.actuator.ExportingActuator;
import io.camunda.zeebe.qa.util.actuator.PartitionsActuator;
import io.camunda.zeebe.qa.util.cluster.TestHealthProbe;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.util.VersionUtil;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.slf4j.Logger;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.HttpWaitStrategy;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

public class ExporterMigrationTestHelper {

  private static final int BACKLOG_COUNT = 3;
  private static final String CONTAINER_DATA_PATH = "/usr/local/camunda/data";
  private static final String HTTP_PREFIX = "http://";

  private static final String PREVIOUS_VERSION_MINOR =
      getMinorVersion(VersionUtil.getPreviousVersion());
  private static final String CURRENT_VERSION_MINOR = getMinorVersion(VersionUtil.getVersion());
  private static final String API_URL =
      String.format(
          "https://hub.docker.com/v2/repositories/camunda/camunda/tags?page_size=%d&name=%s",
          100, PREVIOUS_VERSION_MINOR);

  private static final String PROCESS_ID = "migration-test";
  private static final String JOB_TYPE = "test-job";
  private static final BpmnModelInstance PROCESS_MODEL =
      Bpmn.createExecutableProcess(PROCESS_ID)
          .startEvent()
          .serviceTask("task", t -> t.zeebeJobType(JOB_TYPE))
          .endEvent()
          .done();

  private ElasticsearchClient esClient;
  private OpenSearchClient osClient;
  private String networkAlias;
  private Network network;
  private String containerAddress;
  private Path dataDir;
  private Logger log;

  private String engineName;
  private String exporterClassName;
  private Map<String, String> exporterEnvironmentVariables;

  public ExporterMigrationTestHelper(
      final Object dbClient,
      final String networkAlias,
      final Network network,
      final String containerAddress,
      final Path dataDir,
      final Logger log) {
    if (dbClient instanceof ElasticsearchClient) {
      this.osClient = null;
      this.esClient = (ElasticsearchClient) dbClient;
      this.engineName = "elasticsearch";
      this.exporterClassName = ElasticsearchExporter.class.getName();
      this.exporterEnvironmentVariables =
          Map.of(
              "ZEEBE_BROKER_EXPORTERS_ELASTICSEARCH_CLASSNAME",
              "io.camunda.zeebe.exporter.ElasticsearchExporter",
              "ZEEBE_BROKER_EXPORTERS_ELASTICSEARCH_ARGS_URL",
              HTTP_PREFIX + networkAlias + ":9200",
              "ZEEBE_BROKER_EXPORTERS_ELASTICSEARCH_ARGS_BULK_SIZE",
              "1");
    } else if (dbClient instanceof OpenSearchClient) {
      this.osClient = (OpenSearchClient) dbClient;
      this.esClient = null;
      this.engineName = "opensearch";
      this.exporterClassName = OpensearchExporter.class.getName();
      this.exporterEnvironmentVariables =
          Map.of(
              "ZEEBE_BROKER_EXPORTERS_OPENSEARCH_CLASSNAME",
              "io.camunda.zeebe.exporter.opensearch.OpensearchExporter",
              "ZEEBE_BROKER_EXPORTERS_OPENSEARCH_ARGS_URL",
              "http://" + networkAlias + ":9200",
              "ZEEBE_BROKER_EXPORTERS_OPENSEARCH_ARGS_BULK_SIZE",
              "1");
    } else {
      throw new IllegalArgumentException("Unsupported client type: " + dbClient.getClass());
    }

    this.network = network;
    this.networkAlias = networkAlias;
    this.containerAddress = containerAddress;
    this.dataDir = dataDir;
    this.log = log;
  }

  public long countDocuments(String indexPattern) throws IOException {
    if (esClient != null) {
      return esClient.count(c -> c.index(indexPattern)).count();
    }

    return osClient.count(c -> c.index(indexPattern)).count();
  }

  void shouldCompleteUpgradeWithBacklogAndExportAllRecords(final String version) throws Exception {
    final List<Long> instanceKeys = new ArrayList<>();

    // Create a Docker volume for sharing data between the previous version and the current version
    // in-JVM broker
    final var volume = CamundaVolume.newCamundaVolume();

    // ---- Phase 1: Start previous version, deploy process, create & complete instance #1 ----
    log.info("Phase 1: Starting Camunda " + version + " in Docker...");
    final GenericContainer<?> camundaPrevious =
        new GenericContainer<>("camunda/camunda:" + version)
            .withNetwork(network)
            .withExposedPorts(26500, 8080, 9600)
            .withCreateContainerCmdModifier(
                cmd -> cmd.withBinds(volume.asBind(CONTAINER_DATA_PATH)))
            .withEnv("SPRING_PROFILES_ACTIVE", "broker,standalone")
            .withEnv("ZEEBE_BROKER_DATA_SNAPSHOTPERIOD", "1m")
            .withEnv("ZEEBE_BROKER_DATA_LOGINDEXDENSITY", "1")
            .withEnv("CAMUNDA_REST_ENABLED", "false")
            .withEnv("CAMUNDA_DATABASE_TYPE", "NONE")
            .withEnv("CAMUNDA_DATA_SECONDARYSTORAGE_TYPE", "NONE")
            .withEnv("CAMUNDA_SECURITY_AUTHENTICATION_UNPROTECTEDAPI", "true")
            .withEnv("CAMUNDA_SECURITY_AUTHORIZATIONS_ENABLED", "false")
            .withStartupTimeout(Duration.ofMinutes(5))
            .waitingFor(
                new HttpWaitStrategy()
                    .forPort(9600)
                    .forPath("/actuator/health")
                    .withReadTimeout(Duration.ofSeconds(120)))
            .withLogConsumer(new Slf4jLogConsumer(log).withPrefix("camunda-" + version));

    exporterEnvironmentVariables.forEach(
        (key, value) -> {
          camundaPrevious.withEnv(key, value);
        });

    camundaPrevious.start();

    try {
      final var grpcPort = camundaPrevious.getMappedPort(26500);
      final var monitoringPort = camundaPrevious.getMappedPort(9600);

      try (final var clientPrevious =
          CamundaClient.newClientBuilder()
              .grpcAddress(URI.create("http://localhost:" + grpcPort))
              .preferRestOverGrpc(false)
              .build()) {

        // Deploy process
        clientPrevious
            .newDeployResourceCommand()
            .addProcessModel(PROCESS_MODEL, "migration-test.bpmn")
            .send()
            .join(30, TimeUnit.SECONDS);
        log.info("Deployed process on version " + version);

        // Create instance #1 and complete its job
        final var instance1 =
            clientPrevious
                .newCreateInstanceCommand()
                .bpmnProcessId(PROCESS_ID)
                .latestVersion()
                .send()
                .join(30, TimeUnit.SECONDS);
        instanceKeys.add(instance1.getProcessInstanceKey());
        log.info("Created instance #1: {}", instance1.getProcessInstanceKey());

        completeJobs(clientPrevious, 1);
        log.info("Completed job for instance #1");

        // Wait for instance #1 to be exported
        Awaitility.await("instance #1 exported to " + engineName)
            .atMost(Duration.ofSeconds(60))
            .pollInterval(Duration.ofSeconds(2))
            .ignoreExceptions()
            .untilAsserted(
                () -> {
                  final long count = countDocuments("zeebe-record_process-instance_*");
                  assertThat(count)
                      .as("process instance records should be exported to " + engineName)
                      .isGreaterThan(0);
                });

        // ---- Phase 2: Pause exporter, create backlog instances #2-4 ----
        log.info("Phase 2: Pausing exporter, creating backlog");
        final var exportingActuator =
            ExportingActuator.of("http://localhost:" + monitoringPort + "/actuator/exporting");
        exportingActuator.pause();

        for (int i = 0; i < BACKLOG_COUNT; i++) {
          final var instance =
              clientPrevious
                  .newCreateInstanceCommand()
                  .bpmnProcessId(PROCESS_ID)
                  .latestVersion()
                  .send()
                  .join(30, TimeUnit.SECONDS);
          instanceKeys.add(instance.getProcessInstanceKey());
          log.info("Created backlog instance #{}: {}", i + 2, instance.getProcessInstanceKey());
        }
      }

      // ---- Phase 3: Stop previous version, extract volume to host ----
      log.info("Phase 3: Stopping Camunda " + PREVIOUS_VERSION_MINOR + " and extracting volume");
      camundaPrevious.stop();

    } catch (final Exception e) {
      camundaPrevious.stop();
      throw e;
    }

    // Extract the Docker volume to host filesystem
    final var extractedPath = dataDir.resolve(volume.getName());
    volume.extract(extractedPath);

    // The extracted tar preserves the container's path structure.
    // ZeebeDefaults.getDefaultDataPath() determines the path inside the TinyContainer.
    // The working directory for TestStandaloneBroker is the parent of "data/".
    // Find the data directory in the extracted contents.
    final Path workingDir;
    if (Files.exists(extractedPath.resolve("data"))) {
      workingDir = extractedPath;
    } else if (Files.exists(extractedPath.resolve("usr/local/zeebe/data"))) {
      workingDir = extractedPath.resolve("usr/local/zeebe");
    } else if (Files.exists(extractedPath.resolve("usr/local/camunda/data"))) {
      workingDir = extractedPath.resolve("usr/local/camunda");
    } else {
      // Log what we have for debugging
      try (final var files = Files.walk(extractedPath, 4)) {
        files.forEach(p -> log.info("  extracted: {}", extractedPath.relativize(p)));
      }
      throw new IllegalStateException(
          "Could not find data directory in extracted volume: " + extractedPath);
    }

    // ---- Phase 4: Start current version in-JVM, resume exporter, create instance #5 ----
    log.info(
        "Phase 4: Starting Camunda {} (in-JVM) with working dir: {}",
        CURRENT_VERSION_MINOR,
        workingDir);
    assertThat(Files.exists(workingDir.resolve("data")))
        .as("data directory should exist after extraction")
        .isTrue();

    final var brokerCurrent =
        new TestStandaloneBroker()
            .withWorkingDirectory(workingDir)
            .withUnifiedConfig(cfg -> cfg.getSystem().getUpgrade().setEnableVersionCheck(false))
            .withExporter(
                engineName,
                cfg -> {
                  cfg.setClassName(exporterClassName);
                  cfg.setArgs(
                      Map.of(
                          "url", containerAddress,
                          "bulk", Map.of("size", 1),
                          "index", Map.of("createTemplate", false)));
                })
            .withCreateSchema(false)
            .withProperty("camunda.database.type", "NONE")
            .withProperty("camunda.data.secondary-storage.type", "NONE")
            .withProperty("camunda.rest.enabled", "false");

    try {
      brokerCurrent.start().await(TestHealthProbe.READY, Duration.ofMinutes(3));
      log.info("Camunda " + CURRENT_VERSION_MINOR + " broker started");

      // Resume exporter
      ExportingActuator.of(brokerCurrent).resume();
      log.info("Resumed exporter on " + CURRENT_VERSION_MINOR);

      try (final var clientCurrent =
          brokerCurrent.newClientBuilder().preferRestOverGrpc(false).build()) {
        // Create instance #5 on current version — retry because the process definition from
        // previous version
        // may not be immediately available after log replay
        final var instanceKey5 = new long[1];
        Awaitility.await("create instance #5 on " + CURRENT_VERSION_MINOR)
            .atMost(Duration.ofSeconds(60))
            .pollInterval(Duration.ofSeconds(2))
            .ignoreExceptions()
            .until(
                () -> {
                  final var result =
                      clientCurrent
                          .newCreateInstanceCommand()
                          .bpmnProcessId(PROCESS_ID)
                          .latestVersion()
                          .send()
                          .join(30, TimeUnit.SECONDS);
                  instanceKey5[0] = result.getProcessInstanceKey();
                  return true;
                });
        instanceKeys.add(instanceKey5[0]);
        log.info("Created instance #5 on {}: {}", CURRENT_VERSION_MINOR, instanceKey5[0]);

        // ---- Phase 5: Complete all remaining jobs (#2-5) ----
        log.info("Phase 5: Completing remaining jobs");
        completeJobs(clientCurrent, BACKLOG_COUNT + 1);
        log.info("Completed all remaining jobs");

        // ---- Phase 6: Verify everything exported and completed ----
        log.info("Phase 6: Verifying all records exported");
        final var partitionsActuator = PartitionsActuator.of(brokerCurrent);

        // Wait for exporter to catch up (exportedPosition == processedPosition)
        Awaitility.await("exporter should catch up")
            .atMost(Duration.ofMinutes(2))
            .pollInterval(Duration.ofSeconds(5))
            .untilAsserted(
                () -> {
                  final var status = partitionsActuator.query();
                  final var partition = status.values().stream().findFirst().orElseThrow();
                  log.info(
                      "Partition: processed={}, exported={}, phase={}",
                      partition.processedPosition(),
                      partition.exportedPosition(),
                      partition.exporterPhase());
                  assertThat(partition.exportedPosition())
                      .as("exporter should have caught up to processed position")
                      .isGreaterThanOrEqualTo(partition.processedPosition());
                });

        // Verify all process instance records are in ES/OS
        final var piCount = countDocuments("zeebe-record_process-instance_*");
        log.info("Total process-instance records in " + engineName + ": {}", piCount);
        assertThat(piCount)
            .as("all process instance records should be exported")
            .isGreaterThanOrEqualTo(5);

        // Verify job records are in ES
        final var jobCount = countDocuments("zeebe-record_job_*");
        log.info("Total job records in " + engineName + ": {}", jobCount);
        assertThat(jobCount)
            .as("job records should be exported for all 5 instances")
            .isGreaterThanOrEqualTo(5);
      }
    } finally {
      brokerCurrent.close();
    }

    log.info(
        "Upgrade test passed: all {} instances created, completed, and exported",
        instanceKeys.size());
  }

  private void completeJobs(final CamundaClient client, final int expectedCount) {
    final var completed = new ArrayList<Long>();
    Awaitility.await("complete " + expectedCount + " jobs")
        .atMost(Duration.ofSeconds(60))
        .pollInterval(Duration.ofSeconds(1))
        .until(
            () -> {
              final var response =
                  client
                      .newActivateJobsCommand()
                      .jobType(JOB_TYPE)
                      .maxJobsToActivate(expectedCount - completed.size())
                      .timeout(Duration.ofSeconds(10))
                      .send()
                      .join(30, TimeUnit.SECONDS);
              for (final var job : response.getJobs()) {
                client.newCompleteCommand(job).send().join(10, TimeUnit.SECONDS);
                completed.add(job.getKey());
                log.info("Completed job {}", job.getKey());
              }
              return completed.size() >= expectedCount;
            });
  }

  public static int comparePatches(final String v1, final String v2) {
    final String[] components1 = v1.split("\\.");
    final int patch1 = Integer.parseInt(components1[2]);

    final String[] components2 = v2.split("\\.");
    final int patch2 = Integer.parseInt(components2[2]);

    return Integer.compare(patch1, patch2);
  }

  public static String getMinorVersion(final String version) {
    final String[] components = version.split("\\.");
    return components[0] + "." + components[1];
  }

  public static List<String> fetchLatestPatchFromPreviousMinor()
      throws IOException, InterruptedException {
    final List<String> allVersions = fetchAllPatchesFromPreviousMinor();
    final int len = allVersions.size();
    return List.of(allVersions.get(len - 2)); // skip SNAPSHOT from the end of the list
  }

  public static List<String> fetchAllPatchesFromPreviousMinor()
      throws IOException, InterruptedException {
    final HttpClient client = HttpClient.newHttpClient();
    final ObjectMapper mapper = new ObjectMapper();

    final List<String> allTags = new ArrayList<>();
    String url = API_URL;

    while (true) {
      final HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
      final HttpResponse<String> response =
          client.send(request, HttpResponse.BodyHandlers.ofString());
      final JsonNode root = mapper.readTree(response.body());

      if (response.statusCode() < 200 || response.statusCode() >= 300) {
        throw new IOException(
            String.format(
                "Failed to fetch Docker Hub tags from %s: HTTP %d, body: %s",
                url, response.statusCode(), response.body()));
      }

      for (final JsonNode tag : root.get("results")) {
        allTags.add(tag.get("name").asString());
      }

      // pagination
      final JsonNode next = root.get("next");
      if (next == null || next.isNull()) { // NOTE: edge case
        break;
      }

      url = next.asString();
    }

    final List<String> allVersions = new ArrayList<>();
    for (final String tag : allTags) {
      if (!tag.startsWith(PREVIOUS_VERSION_MINOR)) {
        continue;
      }

      final String[] components = tag.split("\\.");
      if (components.length != 3) {
        continue;
      }

      try {
        Integer.parseInt(components[2]);
      } catch (final NumberFormatException ignored) {
        continue;
      }

      allVersions.add(tag);
    }

    if (allVersions.isEmpty()) {
      throw new NoSuchElementException("No release images found for " + PREVIOUS_VERSION_MINOR);
    }

    allVersions.sort(ExporterMigrationTestHelper::comparePatches);

    final String snapshotVersion = PREVIOUS_VERSION_MINOR + "-SNAPSHOT";
    if (allTags.contains(snapshotVersion)) {
      allVersions.add(snapshotVersion);
    }

    return allVersions;
  }
}
