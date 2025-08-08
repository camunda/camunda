/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.opensearch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import io.camunda.zeebe.exporter.opensearch.TestClient.ComponentTemplatesDto.ComponentTemplateWrapper;
import io.camunda.zeebe.exporter.opensearch.TestClient.IndexISMPolicyDto;
import io.camunda.zeebe.exporter.opensearch.TestClient.IndexTemplatesDto.IndexTemplateWrapper;
import io.camunda.zeebe.exporter.test.ExporterTestConfiguration;
import io.camunda.zeebe.exporter.test.ExporterTestContext;
import io.camunda.zeebe.exporter.test.ExporterTestController;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.value.ImmutableJobBatchRecordValue;
import io.camunda.zeebe.protocol.record.value.ImmutableJobRecordValue;
import io.camunda.zeebe.protocol.record.value.JobBatchRecordValue;
import io.camunda.zeebe.protocol.record.value.JobRecordValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import io.camunda.zeebe.test.util.testcontainers.TestSearchContainers;
import io.camunda.zeebe.util.VersionUtil;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import org.agrona.CloseHelper;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mockito;
import org.opensearch.client.ResponseException;
import org.opensearch.client.opensearch._types.OpenSearchException;
import org.opensearch.client.opensearch.core.GetResponse;
import org.opensearch.testcontainers.OpensearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Tests here should be purely about verifying changes made to Opensearch with default
 * configuration. Testing flushing behavior, template configuration, etc., isn't necessary here.
 *
 * <p>Similarly, testing against a secured Opensearch, or testing fault tolerance when Opensearch is
 * down, should be done elsewhere (e.g. {@link FaultToleranceIT}
 */
@Testcontainers
final class OpensearchExporterIT {
  @Container
  private static final OpensearchContainer<?> CONTAINER =
      TestSearchContainers.createDefaultOpensearchContainer()
          .withEnv("action.destructive_requires_name", "false");

  private final OpensearchExporterConfiguration config = new OpensearchExporterConfiguration();
  // omit authorizations since they are removed from the records during serialization
  private final ProtocolFactory factory = new ProtocolFactory(b -> b.withAuthorizations(Map.of()));
  private final ExporterTestController controller = new ExporterTestController();
  private final OpensearchExporter exporter = new OpensearchExporter();
  private final RecordIndexRouter indexRouter = new RecordIndexRouter(config.index);

  private TestClient testClient;
  private ExporterTestContext exporterTestContext;

  @BeforeEach
  public void beforeEach() {
    config.url = CONTAINER.getHttpHostAddress();
    config.setIncludeEnabledRecords(true);
    config.index.setNumberOfShards(1);
    config.index.setNumberOfReplicas(1);
    config.index.createTemplate = true;
    config.bulk.size = 1; // force flushing on the first record
    config.retention.setEnabled(true);
    // here; enable all indexes that needed during the tests beforehand as they will be created once
    TestSupport.provideValueTypes()
        .forEach(valueType -> TestSupport.setIndexingForValueType(config.index, valueType, true));

    testClient = new TestClient(config, indexRouter);

    exporterTestContext =
        new ExporterTestContext()
            .setConfiguration(new ExporterTestConfiguration<>("opensearch", config));
    exporter.configure(exporterTestContext);
    exporter.open(controller);
  }

  @AfterEach
  void afterEach() {
    testClient.deleteIndices();
    testClient.deleteIndexTemplates();
    testClient.deleteComponentTemplates();
    CloseHelper.quietCloseAll(testClient);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("io.camunda.zeebe.exporter.opensearch.TestSupport#provideValueTypes")
  void shouldExportRecord(final ValueType valueType) {
    // given
    final var record = generateRecord(valueType);

    // when
    export(record);

    // then
    final var response = testClient.getExportedDocumentFor(record);
    assertThat(response)
        .extracting(GetResponse::index, GetResponse::id, GetResponse::routing, GetResponse::source)
        .containsExactly(
            indexRouter.indexFor(record),
            indexRouter.idFor(record),
            String.valueOf(record.getPartitionId()),
            record);
  }

  // both tests below are regression tests for https://github.com/camunda/camunda/issues/4640
  // one option would be to get rid of these and instead have unit tests on the templates themselves
  // where we can guarantee that this field is not indexed, for example

  // regression test for https://github.com/camunda/camunda/issues/4640
  @Test
  void shouldExportJobRecordWithOverlappingCustomHeaders() {
    // given
    final JobRecordValue value =
        ImmutableJobRecordValue.builder()
            .from(factory.generateObject(JobRecordValue.class))
            .withCustomHeaders(Map.of("x", "1", "x.y", "2"))
            .build();
    final Record<JobRecordValue> record =
        factory.generateRecord(
            ValueType.JOB,
            builder ->
                builder.withValue(value).withBrokerVersion(VersionUtil.getVersionLowerCase()));

    // when
    export(record);

    // then
    final var response = testClient.getExportedDocumentFor(record);
    assertThat(response.source()).isEqualTo(record);
  }

  // regression test for https://github.com/camunda/camunda/issues/4640
  @Test
  void shouldExportJobBatchRecordWithOverlappingCustomHeaders() {
    // given
    final JobRecordValue job =
        ImmutableJobRecordValue.builder()
            .from(factory.generateObject(JobRecordValue.class))
            .withCustomHeaders(Map.of("x", "1", "x.y", "2"))
            .build();
    final JobBatchRecordValue value =
        ImmutableJobBatchRecordValue.builder()
            .from(factory.generateObject(JobBatchRecordValue.class))
            .withJobs(Collections.singleton(job))
            .withJobKeys(Collections.singleton(1L))
            .build();
    final Record<JobBatchRecordValue> record =
        factory.generateRecord(
            ValueType.JOB_BATCH,
            builder ->
                builder.withValue(value).withBrokerVersion(VersionUtil.getVersionLowerCase()));

    // when
    export(record);

    // then
    final var response = testClient.getExportedDocumentFor(record);
    assertThat(response.source()).isEqualTo(record);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("io.camunda.zeebe.exporter.opensearch.TestSupport#provideValueTypes")
  void shouldPutIndexTemplate(final ValueType valueType) {
    // assuming
    Assumptions.assumeTrue(
        config.shouldIndexValueType(valueType),
        "no template is created because the exporter is configured filter out records of this type");

    // given
    final var record = generateRecord(valueType);
    final var expectedIndexTemplateName =
        indexRouter.indexPrefixForValueType(valueType, VersionUtil.getVersionLowerCase());

    // when - export a single record to enforce installing all index templatesWrapper
    export(record);

    // then
    final var template = testClient.getIndexTemplate(valueType, VersionUtil.getVersionLowerCase());
    assertThat(template)
        .as("should have created index template for value type %s", valueType)
        .isPresent()
        .get()
        .extracting(IndexTemplateWrapper::name)
        .isEqualTo(expectedIndexTemplateName);
  }

  @Test
  void shouldPutComponentTemplate() {
    // given
    final var record = generateRecord();

    // when - export a single record to enforce installing all index templatesWrapper
    export(record);

    // then
    final var template = testClient.getComponentTemplate();
    assertThat(template)
        .as("should have created the component template")
        .isPresent()
        .get()
        .extracting(ComponentTemplateWrapper::name)
        .isEqualTo(config.index.prefix + "-" + VersionUtil.getVersionLowerCase());
  }

  @Test
  void shouldCreateIndexStateManagementPolicy() {
    // given
    final var record = generateRecord();

    // when - export a single record to enforce creating the policy
    export(record);

    // then
    final var policy = testClient.getIndexStateManagementPolicy().policy();
    assertThat(policy.description())
        .as("Uses configured description")
        .isEqualTo(config.retention.getPolicyDescription());
    assertThat(policy.defaultState()).as("Starts in initial state").isEqualTo("initial");

    final var initialState = policy.states().getFirst();
    assertThat(initialState.name()).isEqualTo("initial");
    assertThat(initialState.actions()).as("Initial state has no actions").isEmpty();
    assertThat(initialState.transitions()).as("Initial state has 1 transition").hasSize(1);

    final var transition = initialState.transitions().getFirst();
    assertThat(transition.stateName())
        .as("Initial state transitions to delete state")
        .isEqualTo("delete");
    assertThat(transition.conditions().minIndexAge())
        .as("Initial state transitions after configured minimum age")
        .isEqualTo(config.retention.getMinimumAge());

    final var deleteState = policy.states().getLast();
    assertThat(deleteState.name()).isEqualTo("delete");
    assertThat(deleteState.transitions()).as("Delete state has no transitions").isEmpty();
    assertThat(deleteState.actions()).as("Delete state has 1 action").hasSize(1);

    final var deleteAction = deleteState.actions().getFirst();
    assertThat(deleteAction.delete()).as("Delete action deleted index").isNotNull();

    final var ismTemplate = policy.ismTemplate().getFirst();
    assertThat(ismTemplate.indexPatterns())
        .as("Has 1 configured index pattern")
        .containsOnly(config.index.prefix + "*");
    assertThat(ismTemplate.priority()).as("Has low priority").isEqualTo(1);
  }

  @Test
  void shouldUpdateIndexStateManagementPolicy() {
    // given - Make sure we create the policy before the exporter does
    final var initialMinimumAge = "100d";
    assertThat(initialMinimumAge).isNotEqualTo(config.retention.getMinimumAge());
    testClient.putIndexStateManagementPolicy(initialMinimumAge);
    final var record = generateRecord();

    // when - export a single record to enforce creating the policy
    export(record);

    // then
    final var updatedPolicy = testClient.getIndexStateManagementPolicy().policy();
    final String updatedMinimumAge =
        updatedPolicy.states().getFirst().transitions().getFirst().conditions().minIndexAge();
    assertThat(updatedMinimumAge).isEqualTo(config.retention.getMinimumAge());
  }

  @Test
  void shouldDeleteIndexStateManagementPolicy() {
    // given - Make sure we create the policy before the exporter does
    final var initialMinimumAge = "100d";
    assertThat(initialMinimumAge).isNotEqualTo(config.retention.getMinimumAge());
    testClient.putIndexStateManagementPolicy(initialMinimumAge);
    final var record = generateRecord();

    // when - export a single record to enforce deletion the policy
    configureExporter(false);
    export(record);

    // then
    assertThatThrownBy(() -> testClient.getIndexStateManagementPolicy())
        .isInstanceOf(UncheckedIOException.class)
        .hasCauseInstanceOf(ResponseException.class)
        .hasMessageContaining("Policy not found");
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("io.camunda.zeebe.exporter.opensearch.TestSupport#provideValueTypes")
  void shouldExportOnlyRequiredRecords(final ValueType valueType) {
    // given
    config.setIncludeEnabledRecords(false);
    exporter.configure(exporterTestContext);
    exporter.open(controller);

    final var record = generateRecord(valueType);

    // when
    export(record);

    // then
    if (valueType == ValueType.PROCESS_INSTANCE
        || valueType == ValueType.PROCESS
        || valueType == ValueType.VARIABLE
        || valueType == ValueType.INCIDENT
        || valueType == ValueType.USER_TASK
        || valueType == ValueType.DEPLOYMENT
        || valueType == ValueType.JOB) {
      final var response = testClient.getExportedDocumentFor(record);
      assertThat(response)
          .extracting(
              GetResponse::index, GetResponse::id, GetResponse::routing, GetResponse::source)
          .containsExactly(
              indexRouter.indexFor(record),
              indexRouter.idFor(record),
              String.valueOf(record.getPartitionId()),
              record);
    } else {
      assertThatThrownBy(() -> testClient.getExportedDocumentFor(record))
          .isInstanceOf(OpenSearchException.class)
          .hasMessageContaining("no such index [%s]".formatted(indexRouter.indexFor(record)));
    }
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("io.camunda.zeebe.exporter.opensearch.TestSupport#provideValueTypes")
  void shouldExportRecordsOnPreviousVersion(final ValueType valueType) {
    // given
    config.setIncludeEnabledRecords(false);
    exporter.configure(exporterTestContext);
    exporter.open(controller);

    final var record =
        factory.generateRecord(
            valueType, r -> r.withBrokerVersion(VersionUtil.getPreviousVersion().toLowerCase()));

    // when
    export(record);

    // then
    final var response = testClient.getExportedDocumentFor(record);
    assertThat(response)
        .extracting(GetResponse::index, GetResponse::id, GetResponse::routing, GetResponse::source)
        .containsExactly(
            indexRouter.indexFor(record),
            indexRouter.idFor(record),
            String.valueOf(record.getPartitionId()),
            record);
  }

  private boolean export(final Record<?> record) {
    exporter.export(record);
    return true;
  }

  private void configureExporter(final boolean retentionEnabled) {
    configureExporter(config -> config.retention.setEnabled(retentionEnabled));
  }

  private void configureExporter(final Consumer<OpensearchExporterConfiguration> configurator) {
    configurator.accept(config);
    exporter.configure(exporterTestContext);
    exporter.open(controller);
  }

  private <T extends RecordValue> Record<T> generateRecord(final ValueType valueType) {
    return factory.generateRecord(
        valueType, r -> r.withBrokerVersion(VersionUtil.getVersionLowerCase()));
  }

  private <T extends RecordValue> Record<T> generateRecord() {
    return factory.generateRecord(r -> r.withBrokerVersion(VersionUtil.getVersionLowerCase()));
  }

  /**
   * policy change is an asynchronous background process in opensearch, that's why we use awaits
   * before asserts to reduce flaky results
   */
  @Nested
  final class IndexSettingsTest {
    @Test
    void shouldAddIndexLifecycleSettingsToExistingIndicesOnRerunWhenRetentionIsEnabled() {
      // given
      configureExporter(false);
      final var record1 = generateRecord(ValueType.JOB);

      // when
      export(record1);

      // then
      final var index1 = indexRouter.indexFor(record1);
      await()
          .atMost(Duration.ofSeconds(30))
          .untilAsserted(
              () -> {
                final var index1Policy = testClient.explainIndex(index1);
                assertHasNoISMPolicy(index1Policy);
              });

      /* Tests when retention is later enabled all indices should have lifecycle policy */
      // given
      configureExporter(true);
      final var record2 = generateRecord(ValueType.JOB);

      // when
      export(record2);

      // then
      final var index2 = indexRouter.indexFor(record2);
      await()
          .atMost(Duration.ofSeconds(30))
          .untilAsserted(
              () -> {
                final var index2Policy = testClient.explainIndex(index2);
                assertHasISMPolicy(index2Policy);
              });
      await()
          .atMost(Duration.ofSeconds(30))
          .untilAsserted(
              () -> {
                final var index1PolicyNew = testClient.explainIndex(index1);
                assertHasISMPolicy(index1PolicyNew);
              });
    }

    @Test
    void shouldRemoveIndexLifecycleSettingsFromExistingIndicesOnRerunWhenRetentionIsDisabled() {
      // given
      configureExporter(true);
      final var record1 = generateRecord(ValueType.JOB);

      // when
      export(record1);

      // then
      final var index1 = indexRouter.indexFor(record1);
      await()
          .atMost(Duration.ofSeconds(30))
          .untilAsserted(
              () -> {
                final var indexPolicy1 = testClient.explainIndex(index1);
                assertHasISMPolicy(indexPolicy1);
              });

      /* Tests when retention is later disabled all indices should not have a lifecycle policy */
      // given
      configureExporter(false);
      final var record2 = generateRecord(ValueType.JOB);

      // when
      export(record2);

      // then
      final var index2 = indexRouter.indexFor(record2);

      await()
          .atMost(Duration.ofSeconds(30))
          .untilAsserted(
              () -> {
                final var response2 = testClient.explainIndex(index2);
                assertHasNoISMPolicy(response2);
              });
      await()
          .atMost(Duration.ofSeconds(30))
          .untilAsserted(
              () -> {
                final var index1PolicyNew = testClient.explainIndex(index1);
                assertHasNoISMPolicy(index1PolicyNew);
              });
    }

    @Test
    void shouldNotTimeoutWhenUpdatingLifecyclePolicyForExistingIndices() {
      // given
      configureExporter(false);
      final var records = new ArrayList<Record<RecordValue>>();
      // using 490 here as we will export one more record after (1 main shard, 1 replica)
      final int limit = 490;
      for (int i = 0; i < limit; i++) {
        final var record = generateRecord(ValueType.JOB);
        records.add(record);
        export(record);
      }

      // when
      configureExporter(true);
      final var record2 = generateRecord(ValueType.JOB);

      await("New record is exported, and existing indices are updated")
          .atMost(Duration.ofSeconds(30))
          .until(() -> export(record2));

      // then
      final var index2 = indexRouter.indexFor(record2);
      await()
          .atMost(Duration.ofSeconds(30))
          .untilAsserted(
              () -> {
                final var index2Policy = testClient.explainIndex(index2);
                assertHasISMPolicy(index2Policy);
              });

      for (final var record : records) {
        final var index = indexRouter.indexFor(record);
        final var response = testClient.explainIndex(index);

        assertHasISMPolicy(response);
      }
    }

    @Test
    void shouldExportRecordToIndexSpecifiedByRecordBrokerVersion() {
      configureExporter(false);
      final var oldRecord =
          factory.generateRecord(ValueType.JOB, r -> r.withBrokerVersion("8.6.0"));

      try (final var mockVersion =
          Mockito.mockStatic(VersionUtil.class, Mockito.CALLS_REAL_METHODS)) {
        mockVersion.when(VersionUtil::getVersionLowerCase).thenReturn("8.7.0");

        await("New record is exported, and existing indices are updated")
            .atMost(Duration.ofSeconds(30))
            .until(() -> export(oldRecord));
      }

      final var document = testClient.getExportedDocumentFor(oldRecord);
      assertThat(document.index().contains(oldRecord.getBrokerVersion())).isTrue();
    }

    @Test
    void shouldExportToCorrectIndexWithOpensearchNotReachable() throws IOException {

      // given
      final var currentPort = CONTAINER.getFirstMappedPort();
      CONTAINER.stop();
      Awaitility.await().until(() -> !CONTAINER.isRunning());

      final var record = factory.generateRecord(ValueType.JOB, r -> r.withBrokerVersion("8.6.0"));

      try (final var mockVersion =
          Mockito.mockStatic(VersionUtil.class, Mockito.CALLS_REAL_METHODS)) {
        mockVersion.when(VersionUtil::getVersionLowerCase).thenReturn("8.6.0");
        configureExporter(false);

        assertThatThrownBy(() -> export(record));
      }

      CONTAINER
          .withEnv("discovery.type", "single-node")
          .setPortBindings(List.of(currentPort + ":9200"));
      CONTAINER.start();
      Awaitility.await().until(CONTAINER::isRunning);

      // when
      final var record2 = factory.generateRecord(ValueType.JOB, r -> r.withBrokerVersion("8.7.0"));
      try (final var mockVersion =
          Mockito.mockStatic(VersionUtil.class, Mockito.CALLS_REAL_METHODS)) {
        mockVersion.when(VersionUtil::getVersionLowerCase).thenReturn("8.7.0");
        configureExporter(false);

        export(record);
        export(record2);
      }

      // then

      // If the templates are not created then the dynamically created indices will not have an
      // alias versus with a template as the template defines an alias.
      final var firstRecordIndexName = indexRouter.indexFor(record);
      final var firstRecordIndexAliases =
          testClient
              .getOsClient()
              .indices()
              .get(r -> r.index(firstRecordIndexName))
              .result()
              .get(firstRecordIndexName)
              .aliases();
      assertThat(firstRecordIndexAliases.size()).isEqualTo(1);
      assertThat(firstRecordIndexName).contains("8.6.0");

      final var secondRecordIndexName = indexRouter.indexFor(record2);
      final var secondRecordIndexAliases =
          testClient
              .getOsClient()
              .indices()
              .get(r -> r.index(secondRecordIndexName))
              .result()
              .get(secondRecordIndexName)
              .aliases();
      assertThat(secondRecordIndexAliases.size()).isEqualTo(1);
      assertThat(secondRecordIndexName).contains("8.7.0");
    }

    @Test
    void shouldSetIndexTemplatePriorityFromConfiguration() {
      // given
      final int priority = 100;
      configureExporter(config -> config.index.setPriority(priority));
      final var record = generateRecord(ValueType.JOB);

      // when
      export(record);

      // then
      final var template =
          testClient.getIndexTemplate(ValueType.JOB, VersionUtil.getVersionLowerCase());
      assertThat(template)
          .as("should have created index template for value type %s", ValueType.JOB)
          .isPresent()
          .get()
          .extracting(wrapper -> wrapper.template().priority())
          .isEqualTo((long) priority);
    }

    @Test
    void shouldSetIndexTemplateWithDefaultPriorityWhenNotSetInConfiguration() {
      // given
      configureExporter(config -> {});
      final var record = generateRecord(ValueType.JOB);

      // when
      export(record);

      // then
      final var template =
          testClient.getIndexTemplate(ValueType.JOB, VersionUtil.getVersionLowerCase());
      assertThat(template)
          .as("should have created index template for value type %s", ValueType.JOB)
          .isPresent()
          .get()
          .extracting(wrapper -> wrapper.template().priority())
          .isEqualTo(20L); // default priority is 20
    }

    private void assertHasISMPolicy(final Optional<IndexISMPolicyDto> indexSettings) {
      assertThat(indexSettings)
          .as("should have found the index")
          .isPresent()
          .get()
          .extracting(IndexISMPolicyDto::policyId)
          .as("should have lifecycle config")
          .isNotNull()
          .isEqualTo(config.retention.getPolicyName());
    }

    private static void assertHasNoISMPolicy(final Optional<IndexISMPolicyDto> policy) {
      assertThat(policy).as("ISM policy should not be configured").isEmpty();
    }
  }
}
