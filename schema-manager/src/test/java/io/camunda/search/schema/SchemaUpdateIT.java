/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.schema;

import static io.camunda.search.schema.SchemaManager.PI_ARCHIVING_BLOCKED_META_KEY;
import static io.camunda.search.schema.utils.SchemaManagerITInvocationProvider.ELASTICSEARCH_NETWORK_ALIAS;
import static io.camunda.search.schema.utils.SchemaManagerITInvocationProvider.OPENSEARCH_NETWORK_ALIAS;
import static io.camunda.search.schema.utils.SchemaTestUtil.assertMappingsMatch;
import static io.camunda.search.schema.utils.SchemaTestUtil.createSchemaManager;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.Maps;
import io.camunda.search.schema.config.SearchEngineConfiguration;
import io.camunda.search.schema.utils.SchemaManagerITInvocationProvider;
import io.camunda.search.test.utils.SearchClientAdapter;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;
import io.camunda.webapps.schema.descriptors.IndexDescriptors;
import io.camunda.webapps.schema.descriptors.IndexTemplateDescriptor;
import io.camunda.webapps.schema.descriptors.index.TasklistImportPositionIndex;
import io.camunda.zeebe.util.VersionUtil;
import java.io.IOException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.HttpWaitStrategy;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@ExtendWith(SchemaManagerITInvocationProvider.class)
class SchemaUpdateIT {

  private static final Logger LOG = LoggerFactory.getLogger(SchemaUpdateIT.class);

  private static String currentMinorVersion; // e.g. "8.8"

  private static String previousMinorSnapshotVersion; // e.g. "8.7-SNAPSHOT"

  @BeforeAll
  static void beforeAll() {
    final var semanticVersion = VersionUtil.getSemanticVersion().get();
    currentMinorVersion = "%d.%d".formatted(semanticVersion.major(), semanticVersion.minor());
    previousMinorSnapshotVersion =
        "%d.%d-SNAPSHOT".formatted(semanticVersion.major(), semanticVersion.minor() - 1);
  }

  @BeforeEach
  void setup(final SearchEngineConfiguration config, final SearchClientAdapter ignored) {
    // startupWithPreviousVersionSchema
    final var databaseType = config.connect().getTypeEnum();
    final var indexPrefix = config.connect().getIndexPrefix();
    final var url =
        "http://%s:%d"
            .formatted(
                databaseType.isElasticSearch()
                    ? ELASTICSEARCH_NETWORK_ALIAS
                    : OPENSEARCH_NETWORK_ALIAS,
                9200);
    try (final var previousVersionContainer =
        new GenericContainer<>("camunda/camunda:%s".formatted(previousMinorSnapshotVersion))
            .withNetwork(Network.SHARED)
            .withExposedPorts(9600)
            .waitingFor(
                new HttpWaitStrategy()
                    .forPort(9600)
                    .forPath("/actuator/health")
                    .withReadTimeout(Duration.ofSeconds(120)))
            .withEnv("CAMUNDA_OPERATE_DATABASE", databaseType.toString())
            .withEnv("CAMUNDA_OPERATE_%s_URL".formatted(databaseType.name()), url)
            .withEnv("CAMUNDA_OPERATE_%s_NUMBEROFREPLICAS".formatted(databaseType.name()), "1")
            .withEnv(
                "CAMUNDA_OPERATE_%s_INDEXPREFIX".formatted(databaseType.name()),
                "%s-operate".formatted(indexPrefix))
            .withEnv("CAMUNDA_OPERATE_ZEEBE%s_URL".formatted(databaseType.name()), url)
            .withEnv("CAMUNDA_OPERATE_ARCHIVER_ILMENABLED", "true")
            .withEnv("CAMUNDA_TASKLIST_DATABASE", databaseType.toString())
            .withEnv("CAMUNDA_TASKLIST_%s_URL".formatted(databaseType.name()), url)
            .withEnv("CAMUNDA_TASKLIST_%s_NUMBEROFREPLICAS".formatted(databaseType.name()), "1")
            .withEnv(
                "CAMUNDA_TASKLIST_%s_INDEXPREFIX".formatted(databaseType.name()),
                "%s-tasklist".formatted(indexPrefix))
            .withEnv("CAMUNDA_TASKLIST_ZEEBE%s_URL".formatted(databaseType.name()), url)
            .withEnv("CAMUNDA_TASKLIST_ARCHIVER_ILMENABLED", "true")) {
      previousVersionContainer.start();
      previousVersionContainer.followOutput(new Slf4jLogConsumer(LOG));
    }
  }

  @TestTemplate
  void shouldRunConcurrentUpdates(
      final SearchEngineConfiguration config, final SearchClientAdapter searchClientAdapter)
      throws InterruptedException, IOException {
    // given
    final int numberOfThreads = 12;
    // enable retention policy for the test and set replicas to 1
    config.retention().setEnabled(true);
    config.index().setNumberOfReplicas(1);
    if (config.connect().getTypeEnum().isOpenSearch()) {
      // Opensearch uses optimistic lock on ISM policies update, so we need to increase the retries
      config.schemaManager().getRetry().setMaxRetries(numberOfThreads);
      config.schemaManager().getRetry().setMaxRetryDelay(Duration.ofSeconds(1));
    }
    config
        .schemaManager()
        .setVersionCheckRestrictionEnabled(
            false); // skip version check for the test to allow 8.7.0-SNAPSHOT to 8.8.0-SNAPSHOT
    final var indexDescriptors =
        new IndexDescriptors(
            config.connect().getIndexPrefix(), config.connect().getTypeEnum().isElasticSearch());
    // create dated indices for all templates that are from the previous versions
    final int numberOfDatedIndices =
        createDatedIndices(
            config,
            searchClientAdapter,
            indexDescriptors.templates().stream()
                .filter(template -> !template.getVersion().startsWith(currentMinorVersion))
                .toList());
    final SchemaManager schemaManager =
        createSchemaManager(indexDescriptors.indices(), indexDescriptors.templates(), config);
    final var exceptions = new ConcurrentLinkedQueue<Throwable>();

    // when
    final var threads =
        IntStream.range(0, numberOfThreads)
            .mapToObj(
                i ->
                    Thread.ofVirtual()
                        .start(
                            () -> {
                              try {
                                schemaManager.startup();
                              } catch (final Throwable e) {
                                exceptions.add(e);
                              }
                            }))
            .toList();
    for (final var thread : threads) {
      thread.join(Duration.ofSeconds(10));
    }

    // then
    assertThat(exceptions).isEmpty();
    assertThat(schemaManager.isSchemaReadyForUse()).isTrue();

    // validate "camunda-history-retention-policy" retention policy is created
    assertThat(
            searchClientAdapter.getPolicyAsNode(config.retention().getPolicyName()).get("policy"))
        .isNotNull();
    final var allIndices =
        Maps.filterKeys( // filter out legacy tasklist and operate indices
            searchClientAdapter.getAllIndicesAsNode(config.connect().getIndexPrefix()),
            indexName -> getMatchingIndexDescriptor(indexName, indexDescriptors).isPresent());

    assertThat(allIndices).hasSize(indexDescriptors.all().size() + numberOfDatedIndices);

    allIndices.forEach(
        (indexName, index) -> {
          final var matchingIndexDescriptor =
              getMatchingIndexDescriptor(indexName, indexDescriptors)
                  .orElseThrow(
                      () ->
                          new IllegalStateException(
                              "No matching index descriptor found for index: " + indexName));

          // validate index settings
          final var settings = index.get("settings").get("index");
          assertThat(settings.get("number_of_shards").asText()).isEqualTo("1");
          assertThat(settings.get("number_of_replicas").asText()).isEqualTo("1");

          // validate index mappings
          assertMappingsMatch(index.get("mappings"), matchingIndexDescriptor);

          // validate index aliases
          assertThat(index.get("aliases").fieldNames().next())
              .isEqualTo(matchingIndexDescriptor.getAlias());

          if (matchingIndexDescriptor instanceof TasklistImportPositionIndex) {
            final var meta = index.get("mappings").get("_meta");
            // We only include the meta property on the runtime index on update
            if (indexName.equals(matchingIndexDescriptor.getFullQualifiedName())) {
              assertThat(meta).isNotNull();
              assertThat(meta.get(PI_ARCHIVING_BLOCKED_META_KEY)).isNotNull();
              assertThat(meta.get(PI_ARCHIVING_BLOCKED_META_KEY).asBoolean()).isTrue();
            } else {
              assertThat(meta).isNull();
            }
          }
        });
  }

  private static Optional<IndexDescriptor> getMatchingIndexDescriptor(
      final String indexName, final IndexDescriptors indexDescriptors) {
    return indexDescriptors.all().stream()
        .filter(descriptor -> indexName.startsWith(descriptor.getFullQualifiedName()))
        .findFirst();
  }

  private static int createDatedIndices(
      final SearchEngineConfiguration config,
      final SearchClientAdapter searchClientAdapter,
      final List<IndexTemplateDescriptor> indexTemplateDescriptors) {
    final int archivePeriodInDays = 20;
    final LocalDate today = LocalDate.now();
    for (final var indexTemplate : indexTemplateDescriptors) {
      IntStream.range(0, archivePeriodInDays)
          .mapToObj(i -> today.minusDays(i).format(DateTimeFormatter.ISO_DATE))
          .map(date -> indexTemplate.getIndexPattern().replace("*", date))
          .forEach(
              indexName -> {
                try {
                  searchClientAdapter.createIndex(indexName, config.index().getNumberOfReplicas());
                } catch (final IOException e) {
                  throw new RuntimeException(e);
                }
              });
    }
    return archivePeriodInDays * indexTemplateDescriptors.size();
  }

  @AfterEach
  void tearDown(
      final SearchEngineConfiguration config, final SearchClientAdapter searchClientAdapter)
      throws IOException {
    if (config.connect().getTypeEnum().isElasticSearch()) {
      // Delete the data streams to allow removal of associated index templates.
      // Note: These stream is auto-created by Elasticsearch with the use of deprecated APIs in 8.7
      // (ES client 7.x).
      searchClientAdapter
          .getElsClient()
          .indices()
          .deleteDataStream(
              req -> req.name(".logs-deprecation.elasticsearch-default", "ilm-history-7"));
    }
  }
}
