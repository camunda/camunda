/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.exporter.opensearch;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.exporter.opensearch.TestClient.ComponentTemplatesDto.ComponentTemplateWrapper;
import io.camunda.zeebe.exporter.opensearch.TestClient.IndexTemplatesDto.IndexTemplateWrapper;
import io.camunda.zeebe.exporter.test.ExporterTestConfiguration;
import io.camunda.zeebe.exporter.test.ExporterTestContext;
import io.camunda.zeebe.exporter.test.ExporterTestController;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.value.ImmutableJobBatchRecordValue;
import io.camunda.zeebe.protocol.record.value.ImmutableJobRecordValue;
import io.camunda.zeebe.protocol.record.value.JobBatchRecordValue;
import io.camunda.zeebe.protocol.record.value.JobRecordValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import java.util.Collections;
import java.util.Map;
import org.agrona.CloseHelper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
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
@TestInstance(Lifecycle.PER_CLASS)
final class OpensearchExporterIT {
  @Container
  private static final OpensearchContainer CONTAINER = TestSupport.createDefaultContainer();

  private final OpensearchExporterConfiguration config = new OpensearchExporterConfiguration();
  private final ProtocolFactory factory = new ProtocolFactory();
  private final ExporterTestController controller = new ExporterTestController();
  private final OpensearchExporter exporter = new OpensearchExporter();
  private final RecordIndexRouter indexRouter = new RecordIndexRouter(config.index);

  private TestClient testClient;

  @BeforeAll
  public void beforeAll() {
    config.url = CONTAINER.getHttpHostAddress();
    config.index.setNumberOfShards(1);
    config.index.setNumberOfReplicas(1);
    config.index.createTemplate = true;
    config.bulk.size = 1; // force flushing on the first record
    config.retention.setEnabled(true);
    // here; enable all indexes that needed during the tests beforehand as they will be created once
    TestSupport.provideValueTypes()
        .forEach(valueType -> TestSupport.setIndexingForValueType(config.index, valueType, true));

    testClient = new TestClient(config, indexRouter);

    exporter.configure(
        new ExporterTestContext()
            .setConfiguration(new ExporterTestConfiguration<>("opensearch", config)));
    exporter.open(controller);
  }

  @AfterAll
  void afterAll() {
    CloseHelper.quietCloseAll(testClient);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("io.camunda.zeebe.exporter.opensearch.TestSupport#provideValueTypes")
  void shouldExportRecord(final ValueType valueType) {
    // given
    final var record = factory.generateRecord(valueType);

    // when
    exporter.export(record);

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

  // both tests below are regression tests for https://github.com/camunda/zeebe/issues/4640
  // one option would be to get rid of these and instead have unit tests on the templates themselves
  // where we can guarantee that this field is not indexed, for example

  // regression test for https://github.com/camunda/zeebe/issues/4640
  @Test
  void shouldExportJobRecordWithOverlappingCustomHeaders() {
    // given
    final JobRecordValue value =
        ImmutableJobRecordValue.builder()
            .from(factory.generateObject(JobRecordValue.class))
            .withCustomHeaders(Map.of("x", "1", "x.y", "2"))
            .build();
    final Record<JobRecordValue> record =
        factory.generateRecord(ValueType.JOB, builder -> builder.withValue(value));

    // when
    exporter.export(record);

    // then
    final var response = testClient.getExportedDocumentFor(record);
    assertThat(response.source()).isEqualTo(record);
  }

  // regression test for https://github.com/camunda/zeebe/issues/4640
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
        factory.generateRecord(ValueType.JOB_BATCH, builder -> builder.withValue(value));

    // when
    exporter.export(record);

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
    final var record = factory.generateRecord(valueType);
    final var expectedIndexTemplateName = indexRouter.indexPrefixForValueType(valueType);

    // when - export a single record to enforce installing all index templatesWrapper
    exporter.export(record);

    // then
    final var template = testClient.getIndexTemplate(valueType);
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
    final var record = factory.generateRecord();

    // when - export a single record to enforce installing all index templatesWrapper
    exporter.export(record);

    // then
    final var template = testClient.getComponentTemplate();
    assertThat(template)
        .as("should have created the component template")
        .isPresent()
        .get()
        .extracting(ComponentTemplateWrapper::name)
        .isEqualTo(config.index.prefix);
  }

  @Test
  void shouldCreateIndexStateManagementPolicy() {
    // given
    final var record = factory.generateRecord();

    // when - export a single record to enforce creating the policy
    exporter.export(record);

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
    final var record = factory.generateRecord();

    // when - export a single record to enforce creating the policy
    exporter.export(record);

    // then
    final var updatedPolicy = testClient.getIndexStateManagementPolicy().policy();
    final String updatedMinimumAge =
        updatedPolicy.states().getFirst().transitions().getFirst().conditions().minIndexAge();
    assertThat(updatedMinimumAge).isEqualTo(config.retention.getMinimumAge());
  }
}
