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
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import io.camunda.client.CamundaClient;
import io.camunda.webapps.schema.SupportedVersions;
import io.camunda.zeebe.exporter.ElasticsearchExporter;
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
import org.apache.http.HttpHost;
import org.awaitility.Awaitility;
import org.elasticsearch.client.RestClient;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.HttpWaitStrategy;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Full lifecycle upgrade test that validates the ES exporter works correctly after upgrading from
 * Camunda previous to current version with an export backlog. If strict mapping or other export
 * issues exist, this test will fail because backlog records won't be exported and jobs won't
 * complete.
 *
 * <p>Data is shared between the previous Docker container and the current in-JVM broker via a
 * Docker volume ({@link CamundaVolume}), which is extracted to the host filesystem using tar. This
 * approach works reliably in Docker-in-Docker (CI) environments.
 */
class ElasticsearchExporterMigrationIT {

  private static final Logger LOG = LoggerFactory.getLogger(ElasticsearchExporterMigrationIT.class);

  private static final String ES_NETWORK_ALIAS = "test-elasticsearch";
  private static final String PROCESS_ID = "migration-test";
  private static final String JOB_TYPE = "test-job";
  private static final String CONTAINER_DATA_PATH = "/usr/local/camunda/data";
  private static final int BACKLOG_COUNT = 3;
  private static final String CURRENT_VERSION = getMinorVersion(VersionUtil.getVersion());
  private static final String BASE_VERSION = getMinorVersion(VersionUtil.getPreviousVersion());
  private static final String API_URL =
      String.format(
          "https://hub.docker.com/v2/repositories/camunda/camunda/tags?page_size=%d&name=%s",
          100, BASE_VERSION);

  private static final BpmnModelInstance PROCESS_MODEL =
      Bpmn.createExecutableProcess(PROCESS_ID)
          .startEvent()
          .serviceTask("task", t -> t.zeebeJobType(JOB_TYPE))
          .endEvent()
          .done();

  private static Network network;
  private static ElasticsearchContainer esContainer;
  private static ElasticsearchClient esClient;
  private static RestClient restClient;

  @TempDir private static Path dataDir;

  @BeforeAll
  static void setUp() {
    network = Network.newNetwork();

    esContainer =
        new ElasticsearchContainer(
                DockerImageName.parse("docker.elastic.co/elasticsearch/elasticsearch")
                    .withTag(SupportedVersions.SUPPORTED_ELASTICSEARCH_VERSION))
            .withNetwork(network)
            .withNetworkAliases(ES_NETWORK_ALIAS)
            .withStartupTimeout(Duration.ofMinutes(5))
            .withEnv("xpack.security.enabled", "false")
            .withEnv("xpack.watcher.enabled", "false")
            .withEnv("xpack.ml.enabled", "false")
            .withEnv("action.auto_create_index", "true")
            .withEnv("action.destructive_requires_name", "false");
    esContainer.start();

    restClient =
        RestClient.builder(HttpHost.create("http://" + esContainer.getHttpHostAddress())).build();
    final var transport = new RestClientTransport(restClient, new JacksonJsonpMapper());
    esClient = new ElasticsearchClient(transport);
  }

  @AfterAll
  static void tearDown() throws Exception {
    if (restClient != null) {
      restClient.close();
    }
    if (esContainer != null) {
      esContainer.stop();
    }
    if (network != null) {
      network.close();
    }
  }

  @ParameterizedTest(name = "Migrate from {0} to current version")
  @MethodSource("fetchLatestPatchFromPreviousMinor")
  @Timeout(value = 10, unit = TimeUnit.MINUTES)
  void shouldCompleteUpgradeWithBacklogAndExportAllRecordsAgainstLatestReleasePatch(
      final String param) throws Exception {
    shouldCompleteUpgradeWithBacklogAndExportAllRecords(param);
  }

  @ParameterizedTest(name = "Migrate from {0} to current version")
  @MethodSource("fetchAllPatchesFromPreviousMinor")
  @Tag("nightly")
  @Timeout(value = 10, unit = TimeUnit.MINUTES)
  void shouldCompleteUpgradeWithBacklogAndExportAllRecordsAgainstReleasePatches(final String param)
      throws Exception {
    shouldCompleteUpgradeWithBacklogAndExportAllRecords(param);
  }

  void shouldCompleteUpgradeWithBacklogAndExportAllRecords(final String version) throws Exception {
    final var esInternalUrl = "http://" + ES_NETWORK_ALIAS + ":9200";
    final var esExternalUrl = "http://" + esContainer.getHttpHostAddress();
    final List<Long> instanceKeys = new ArrayList<>();

    // Create a Docker volume for sharing data between the previous version and the current version
    // in-JVM broker
    final var volume = CamundaVolume.newCamundaVolume();

    // ---- Phase 1: Start previous version, deploy process, create & complete instance #1 ----
    LOG.info("Phase 1: Starting Camunda " + version + " in Docker...");
    final GenericContainer<?> camundaPrevious =
        new GenericContainer<>("camunda/camunda:" + version)
            .withNetwork(network)
            .withExposedPorts(26500, 8080, 9600)
            .withCreateContainerCmdModifier(
                cmd -> cmd.withBinds(volume.asBind(CONTAINER_DATA_PATH)))
            .withEnv("SPRING_PROFILES_ACTIVE", "broker,standalone")
            .withEnv(
                "ZEEBE_BROKER_EXPORTERS_ELASTICSEARCH_CLASSNAME",
                "io.camunda.zeebe.exporter.ElasticsearchExporter")
            .withEnv("ZEEBE_BROKER_EXPORTERS_ELASTICSEARCH_ARGS_URL", esInternalUrl)
            .withEnv("ZEEBE_BROKER_EXPORTERS_ELASTICSEARCH_ARGS_BULK_SIZE", "1")
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
            .withLogConsumer(new Slf4jLogConsumer(LOG).withPrefix("camunda-" + BASE_VERSION));

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
        LOG.info("Deployed process on version " + version);

        // Create instance #1 and complete its job
        final var instance1 =
            clientPrevious
                .newCreateInstanceCommand()
                .bpmnProcessId(PROCESS_ID)
                .latestVersion()
                .send()
                .join(30, TimeUnit.SECONDS);
        instanceKeys.add(instance1.getProcessInstanceKey());
        LOG.info("Created instance #1: {}", instance1.getProcessInstanceKey());

        completeJobs(clientPrevious, 1);
        LOG.info("Completed job for instance #1");

        // Wait for instance #1 to be exported to ES
        Awaitility.await("instance #1 exported to ES")
            .atMost(Duration.ofSeconds(60))
            .pollInterval(Duration.ofSeconds(2))
            .ignoreExceptions()
            .untilAsserted(
                () -> {
                  final var count =
                      esClient.count(c -> c.index("zeebe-record_process-instance_*")).count();
                  assertThat(count)
                      .as("process instance records should be exported to ES")
                      .isGreaterThan(0);
                });

        // ---- Phase 2: Pause exporter, create backlog instances #2-4 ----
        LOG.info("Phase 2: Pausing exporter, creating backlog");
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
          LOG.info("Created backlog instance #{}: {}", i + 2, instance.getProcessInstanceKey());
        }
      }

      // ---- Phase 3: Stop previous version, extract volume to host ----
      LOG.info("Phase 3: Stopping Camunda " + BASE_VERSION + " and extracting volume");
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
        files.forEach(p -> LOG.info("  extracted: {}", extractedPath.relativize(p)));
      }
      throw new IllegalStateException(
          "Could not find data directory in extracted volume: " + extractedPath);
    }

    // ---- Phase 4: Start current version in-JVM, resume exporter, create instance #5 ----
    LOG.info(
        "Phase 4: Starting Camunda {} (in-JVM) with working dir: {}", CURRENT_VERSION, workingDir);
    assertThat(Files.exists(workingDir.resolve("data")))
        .as("data directory should exist after extraction")
        .isTrue();

    final var brokerCurrent =
        new TestStandaloneBroker()
            .withWorkingDirectory(workingDir)
            .withUnifiedConfig(cfg -> cfg.getSystem().getUpgrade().setEnableVersionCheck(false))
            .withExporter(
                "elasticsearch",
                cfg -> {
                  cfg.setClassName(ElasticsearchExporter.class.getName());
                  cfg.setArgs(
                      Map.of(
                          "url", esExternalUrl,
                          "bulk", Map.of("size", 1),
                          "index", Map.of("createTemplate", false)));
                })
            .withCreateSchema(false)
            .withProperty("camunda.database.type", "NONE")
            .withProperty("camunda.data.secondary-storage.type", "NONE")
            .withProperty("camunda.rest.enabled", "false");

    try {
      brokerCurrent.start().await(TestHealthProbe.READY, Duration.ofMinutes(3));
      LOG.info("Camunda " + CURRENT_VERSION + " broker started");

      // Resume exporter
      ExportingActuator.of(brokerCurrent).resume();
      LOG.info("Resumed exporter on " + CURRENT_VERSION);

      try (final var clientCurrent =
          brokerCurrent.newClientBuilder().preferRestOverGrpc(false).build()) {
        // Create instance #5 on current version — retry because the process definition from
        // previous version
        // may not be immediately available after log replay
        final var instanceKey5 = new long[1];
        Awaitility.await("create instance #5 on " + CURRENT_VERSION)
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
        LOG.info("Created instance #5 on {}: {}", CURRENT_VERSION, instanceKey5[0]);

        // ---- Phase 5: Complete all remaining jobs (#2-5) ----
        LOG.info("Phase 5: Completing remaining jobs");
        completeJobs(clientCurrent, BACKLOG_COUNT + 1);
        LOG.info("Completed all remaining jobs");

        // ---- Phase 6: Verify everything exported and completed ----
        LOG.info("Phase 6: Verifying all records exported");
        final var partitionsActuator = PartitionsActuator.of(brokerCurrent);

        // Wait for exporter to catch up (exportedPosition == processedPosition)
        Awaitility.await("exporter should catch up")
            .atMost(Duration.ofMinutes(2))
            .pollInterval(Duration.ofSeconds(5))
            .untilAsserted(
                () -> {
                  final var status = partitionsActuator.query();
                  final var partition = status.values().stream().findFirst().orElseThrow();
                  LOG.info(
                      "Partition: processed={}, exported={}, phase={}",
                      partition.processedPosition(),
                      partition.exportedPosition(),
                      partition.exporterPhase());
                  assertThat(partition.exportedPosition())
                      .as("exporter should have caught up to processed position")
                      .isGreaterThanOrEqualTo(partition.processedPosition());
                });

        // Verify all process instance records are in ES
        final var piCount = esClient.count(c -> c.index("zeebe-record_process-instance_*")).count();
        LOG.info("Total process-instance records in ES: {}", piCount);
        assertThat(piCount)
            .as("all process instance records should be exported")
            .isGreaterThanOrEqualTo(5);

        // Verify job records are in ES
        final var jobCount = esClient.count(c -> c.index("zeebe-record_job_*")).count();
        LOG.info("Total job records in ES: {}", jobCount);
        assertThat(jobCount)
            .as("job records should be exported for all 5 instances")
            .isGreaterThanOrEqualTo(5);
      }
    } finally {
      brokerCurrent.close();
    }

    LOG.info(
        "Upgrade test passed: all {} instances created, completed, and exported",
        instanceKeys.size());
  }

  private static void completeJobs(final CamundaClient client, final int expectedCount) {
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
                LOG.info("Completed job {}", job.getKey());
              }
              return completed.size() >= expectedCount;
            });
  }

  private static List<String> fetchLatestPatchFromPreviousMinor()
      throws IOException, InterruptedException {
    final List<String> allVersions = fetchAllPatchesFromPreviousMinor();
    final int len = allVersions.size();
    return List.of(allVersions.get(len - 2)); // skip SNAPSHOT from the end of the list
  }

  private static List<String> fetchAllPatchesFromPreviousMinor()
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
      if (!tag.startsWith(BASE_VERSION)) {
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
      throw new NoSuchElementException("No release images found for " + BASE_VERSION);
    }

    allVersions.sort(ElasticsearchExporterMigrationIT::comparePatches);

    final String snapshotVersion = BASE_VERSION + "-SNAPSHOT";
    if (allTags.contains(snapshotVersion)) {
      allVersions.add(snapshotVersion);
    }

    return allVersions;
  }

  private static int comparePatches(final String v1, final String v2) {
    final String[] components1 = v1.split("\\.");
    final int patch1 = Integer.parseInt(components1[2]);

    final String[] components2 = v2.split("\\.");
    final int patch2 = Integer.parseInt(components2[2]);

    return Integer.compare(patch1, patch2);
  }

  private static String getMinorVersion(final String version) {
    final String[] components = version.split("\\.");
    return components[0] + "." + components[1];
  }
}
