/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter;

import static io.camunda.exporter.utils.CamundaExporterSchemaUtils.createSchemas;
import static io.camunda.search.test.utils.SearchDBExtension.CUSTOM_PREFIX;
import static io.camunda.search.test.utils.SearchDBExtension.TEST_INTEGRATION_OPENSEARCH_AWS_URL;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.impl.util.VersionUtil;
import io.camunda.exporter.config.ConnectionTypes;
import io.camunda.exporter.config.ExporterConfiguration;
import io.camunda.exporter.utils.CamundaExporterITTemplateExtension;
import io.camunda.search.test.utils.SearchClientAdapter;
import io.camunda.search.test.utils.SearchDBExtension;
import io.camunda.webapps.schema.descriptors.template.FlowNodeInstanceTemplate;
import io.camunda.webapps.schema.descriptors.template.ListViewTemplate;
import io.camunda.webapps.schema.entities.flownode.FlowNodeInstanceEntity;
import io.camunda.webapps.schema.entities.flownode.FlowNodeState;
import io.camunda.webapps.schema.entities.listview.FlowNodeInstanceForListViewEntity;
import io.camunda.webapps.schema.entities.listview.ProcessInstanceForListViewEntity;
import io.camunda.webapps.schema.entities.listview.ProcessInstanceState;
import io.camunda.zeebe.exporter.api.context.Context;
import io.camunda.zeebe.exporter.test.ExporterTestConfiguration;
import io.camunda.zeebe.exporter.test.ExporterTestContext;
import io.camunda.zeebe.exporter.test.ExporterTestController;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ImmutableIncidentRecordValue;
import io.camunda.zeebe.protocol.record.value.ImmutableJobRecordValue;
import io.camunda.zeebe.protocol.record.value.ImmutableProcessInstanceRecordValue;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * Verifies that the final indexed state produced by the CamundaExporter is identical regardless of
 * the bulk flush size.
 */
@TestInstance(Lifecycle.PER_CLASS)
final class ExporterBulkConsistencyIT {

  private static final int INSTANCE_COUNT = 100;
  private static final int PARTITION_ID = 1;
  private static final String TENANT_ID = TenantOwned.DEFAULT_TENANT_IDENTIFIER;
  private static final String ELEMENT_ID = "serviceTask";
  private static final String ERROR_MESSAGE = "an error occurred";

  @RegisterExtension private static final SearchDBExtension searchDB = SearchDBExtension.create();

  @RegisterExtension
  private static final CamundaExporterITTemplateExtension templateExtension =
      new CamundaExporterITTemplateExtension(searchDB);

  private final ProtocolFactory factory = new ProtocolFactory();

  @AfterEach
  void afterEach() throws IOException {
    final var openSearchAwsInstanceUrl =
        Optional.ofNullable(System.getProperty(TEST_INTEGRATION_OPENSEARCH_AWS_URL)).orElse("");
    if (openSearchAwsInstanceUrl.isEmpty()) {
      searchDB.esClient().indices().delete(req -> req.index(CUSTOM_PREFIX + "*"));
    }
    searchDB.osClient().indices().delete(req -> req.index(CUSTOM_PREFIX + "*"));
  }

  @TestTemplate
  void shouldProduceConsistentDataRegardlessOfBulkSize(
      final ExporterConfiguration baseConfig, final SearchClientAdapter clientAdapter)
      throws IOException {
    // given — INSTANCE_COUNT independent process instances, each producing 6 records covering
    // all multi-handler entity scenarios. Records from different instances are interleaved in a
    // random order on each run to exercise different flush boundaries, while records within each
    // instance always stay in causal order (ACTIVATING before COMPLETED etc.) to match the
    // invariant the exporter relies on.
    final var recordGroups = new ArrayList<List<Record<?>>>();
    final var flowNodeIds = new ArrayList<String>();
    final var processInstanceIds = new ArrayList<String>();
    for (int i = 0; i < INSTANCE_COUNT; i++) {
      recordGroups.add(buildRecordsForInstance(i));
      flowNodeIds.add(String.valueOf(flowNodeKey(i)));
      processInstanceIds.add(String.valueOf(processInstanceKey(i)));
    }
    Collections.shuffle(recordGroups);

    // interleave: for each step in the lifecycle sequence, take that record from every instance
    // in the shuffled order, then shuffle within the step for extra variety
    final var allRecords = new ArrayList<Record<?>>();
    final int stepsPerInstance = recordGroups.getFirst().size();
    for (int step = 0; step < stepsPerInstance; step++) {
      final var stepRecords = new ArrayList<Record<?>>();
      for (final var group : recordGroups) {
        stepRecords.add(group.get(step));
      }
      Collections.shuffle(stepRecords);
      allRecords.addAll(stepRecords);
    }

    // bulk size 1 forces a flush after every record; the reference bulk size is large enough
    // that all records are buffered and flushed in a single batch
    final int referenceBulkSize = allRecords.size() + 1;

    // when — export at bulk size 1
    final var bulkOneConfig = configWithBulkSize(baseConfig, 1, "bs-1");
    createSchemas(bulkOneConfig);
    runExporter(allRecords, bulkOneConfig);
    clientAdapter.refresh();
    final var bulkOneSnapshot =
        captureSnapshot(clientAdapter, bulkOneConfig, flowNodeIds, processInstanceIds);

    // when — export at reference bulk size (single flush)
    final var referenceConfig = configWithBulkSize(baseConfig, referenceBulkSize, "bs-ref");
    createSchemas(referenceConfig);
    runExporter(allRecords, referenceConfig);
    clientAdapter.refresh();
    final var referenceSnapshot =
        captureSnapshot(clientAdapter, referenceConfig, flowNodeIds, processInstanceIds);

    // then — confirm all handlers contributed their fields to the reference documents
    assertThat(referenceSnapshot.listViewFlowNodes())
        .as("one FlowNodeInstanceForListViewEntity per instance")
        .hasSize(INSTANCE_COUNT);
    assertThat(referenceSnapshot.listViewFlowNodes())
        .as("activityState set by ListViewFlowNodeFromProcessInstanceHandler (COMPLETED intent)")
        .allMatch(e -> FlowNodeState.COMPLETED.equals(e.getActivityState()));
    assertThat(referenceSnapshot.listViewFlowNodes())
        .as("jobFailedWithRetriesLeft set by ListViewFlowNodeFromJobHandler")
        .allMatch(FlowNodeInstanceForListViewEntity::isJobFailedWithRetriesLeft);
    assertThat(referenceSnapshot.listViewFlowNodes())
        .as("errorMessage set by ListViewFlowNodeFromIncidentHandler")
        .allMatch(e -> ERROR_MESSAGE.equals(e.getErrorMessage()));

    assertThat(referenceSnapshot.flowNodeInstances())
        .as("one FlowNodeInstanceEntity per instance")
        .hasSize(INSTANCE_COUNT);
    assertThat(referenceSnapshot.flowNodeInstances())
        .as("state set by FlowNodeInstanceFromProcessInstanceHandler (COMPLETED intent)")
        .allMatch(e -> FlowNodeState.COMPLETED.equals(e.getState()));
    assertThat(referenceSnapshot.flowNodeInstances())
        .as("incidentKey set by FlowNodeInstanceFromIncidentHandler")
        .allMatch(e -> e.getIncidentKey() != null);

    assertThat(referenceSnapshot.listViewProcessInstances())
        .as("one ProcessInstanceForListViewEntity per instance")
        .hasSize(INSTANCE_COUNT);
    assertThat(referenceSnapshot.listViewProcessInstances())
        .as("state set by ListViewProcessInstanceFromProcessInstanceHandler (COMPLETED intent)")
        .allMatch(e -> ProcessInstanceState.COMPLETED.equals(e.getState()));

    // then — all bulk-size-1 documents must be identical to the reference documents
    assertThat(
            sortById(bulkOneSnapshot.listViewFlowNodes(), FlowNodeInstanceForListViewEntity::getId))
        .as("FlowNodeInstanceForListViewEntity: bulk size 1 should match reference")
        .usingRecursiveFieldByFieldElementComparator()
        .isEqualTo(
            sortById(
                referenceSnapshot.listViewFlowNodes(), FlowNodeInstanceForListViewEntity::getId));

    assertThat(sortById(bulkOneSnapshot.flowNodeInstances(), FlowNodeInstanceEntity::getId))
        .as("FlowNodeInstanceEntity: bulk size 1 should match reference")
        .usingRecursiveFieldByFieldElementComparator()
        .isEqualTo(sortById(referenceSnapshot.flowNodeInstances(), FlowNodeInstanceEntity::getId));

    assertThat(
            sortById(
                bulkOneSnapshot.listViewProcessInstances(),
                ProcessInstanceForListViewEntity::getId))
        .as("ProcessInstanceForListViewEntity: bulk size 1 should match reference")
        .usingRecursiveFieldByFieldElementComparator()
        .isEqualTo(
            sortById(
                referenceSnapshot.listViewProcessInstances(),
                ProcessInstanceForListViewEntity::getId));
  }

  private static long processInstanceKey(final int i) {
    return (i + 1) * 1000L;
  }

  private static long flowNodeKey(final int i) {
    return processInstanceKey(i) + 1;
  }

  private List<Record<?>> buildRecordsForInstance(final int i) {
    final long processInstanceKey = processInstanceKey(i);
    final long flowNodeKey = flowNodeKey(i);
    final long jobKey = processInstanceKey + 2;
    final long incidentKey = processInstanceKey + 3;

    return List.of(
        buildProcessActivatingRecord(processInstanceKey),
        buildFlowNodeActivatingRecord(flowNodeKey, processInstanceKey),
        buildJobFailedRecord(jobKey, flowNodeKey, processInstanceKey),
        buildIncidentCreatedRecord(incidentKey, flowNodeKey, processInstanceKey),
        buildFlowNodeCompletedRecord(flowNodeKey, processInstanceKey),
        buildProcessCompletedRecord(processInstanceKey));
  }

  // PROCESS_INSTANCE ELEMENT_ACTIVATING for the root process.
  // Handled by: ListViewProcessInstanceFromProcessInstanceHandler →
  // ProcessInstanceForListViewEntity
  private Record<?> buildProcessActivatingRecord(final long processInstanceKey) {
    return factory.generateRecord(
        ValueType.PROCESS_INSTANCE,
        r ->
            r.withKey(processInstanceKey)
                .withBrokerVersion(VersionUtil.getVersion())
                .withTimestamp(System.currentTimeMillis())
                .withPartitionId(PARTITION_ID)
                .withIntent(ProcessInstanceIntent.ELEMENT_ACTIVATING)
                .withValue(
                    ImmutableProcessInstanceRecordValue.builder()
                        .from(factory.generateObject(ImmutableProcessInstanceRecordValue.class))
                        .withProcessInstanceKey(processInstanceKey)
                        .withBpmnElementType(BpmnElementType.PROCESS)
                        .withParentProcessInstanceKey(-1L)
                        .withFlowScopeKey(-1L)
                        .withRootProcessInstanceKey(processInstanceKey)
                        .withTenantId(TENANT_ID)
                        .build()));
  }

  // PROCESS_INSTANCE ELEMENT_ACTIVATING for the service task flow node.
  // Handled by: ListViewFlowNodeFromProcessInstanceHandler → FlowNodeInstanceForListViewEntity
  //             FlowNodeInstanceFromProcessInstanceHandler → FlowNodeInstanceEntity
  private Record<?> buildFlowNodeActivatingRecord(
      final long flowNodeKey, final long processInstanceKey) {
    return factory.generateRecord(
        ValueType.PROCESS_INSTANCE,
        r ->
            r.withKey(flowNodeKey)
                .withBrokerVersion(VersionUtil.getVersion())
                .withTimestamp(System.currentTimeMillis())
                .withPartitionId(PARTITION_ID)
                .withIntent(ProcessInstanceIntent.ELEMENT_ACTIVATING)
                .withValue(
                    ImmutableProcessInstanceRecordValue.builder()
                        .from(factory.generateObject(ImmutableProcessInstanceRecordValue.class))
                        .withProcessInstanceKey(processInstanceKey)
                        .withBpmnElementType(BpmnElementType.SERVICE_TASK)
                        .withElementId(ELEMENT_ID)
                        .withFlowScopeKey(processInstanceKey)
                        .withRootProcessInstanceKey(processInstanceKey)
                        .withTenantId(TENANT_ID)
                        .build()));
  }

  // JOB FAILED with retries remaining.
  // Handled by: ListViewFlowNodeFromJobHandler → FlowNodeInstanceForListViewEntity
  private Record<?> buildJobFailedRecord(
      final long jobKey, final long flowNodeKey, final long processInstanceKey) {
    return factory.generateRecord(
        ValueType.JOB,
        r ->
            r.withKey(jobKey)
                .withBrokerVersion(VersionUtil.getVersion())
                .withTimestamp(System.currentTimeMillis())
                .withPartitionId(PARTITION_ID)
                .withIntent(JobIntent.FAILED)
                .withValue(
                    ImmutableJobRecordValue.builder()
                        .from(factory.generateObject(ImmutableJobRecordValue.class))
                        .withElementInstanceKey(flowNodeKey)
                        .withProcessInstanceKey(processInstanceKey)
                        .withRootProcessInstanceKey(processInstanceKey)
                        .withRetries(2)
                        .withDeadline(System.currentTimeMillis() + 60_000L)
                        .withTenantId(TENANT_ID)
                        .withJobToUserTaskMigration(false)
                        .build()));
  }

  // INCIDENT CREATED on the service task flow node.
  // Handled by: ListViewFlowNodeFromIncidentHandler → FlowNodeInstanceForListViewEntity
  //             FlowNodeInstanceFromIncidentHandler → FlowNodeInstanceEntity
  private Record<?> buildIncidentCreatedRecord(
      final long incidentKey, final long flowNodeKey, final long processInstanceKey) {
    return factory.generateRecord(
        ValueType.INCIDENT,
        r ->
            r.withKey(incidentKey)
                .withBrokerVersion(VersionUtil.getVersion())
                .withTimestamp(System.currentTimeMillis())
                .withPartitionId(PARTITION_ID)
                .withIntent(IncidentIntent.CREATED)
                .withValue(
                    ImmutableIncidentRecordValue.builder()
                        .from(factory.generateObject(ImmutableIncidentRecordValue.class))
                        .withElementInstanceKey(flowNodeKey)
                        .withProcessInstanceKey(processInstanceKey)
                        .withElementId(ELEMENT_ID)
                        .withTenantId(TENANT_ID)
                        .withErrorMessage(ERROR_MESSAGE)
                        .build()));
  }

  // PROCESS_INSTANCE ELEMENT_COMPLETED for the service task flow node.
  // Handled by: ListViewFlowNodeFromProcessInstanceHandler → FlowNodeInstanceForListViewEntity
  //             FlowNodeInstanceFromProcessInstanceHandler → FlowNodeInstanceEntity
  private Record<?> buildFlowNodeCompletedRecord(
      final long flowNodeKey, final long processInstanceKey) {
    return factory.generateRecord(
        ValueType.PROCESS_INSTANCE,
        r ->
            r.withKey(flowNodeKey)
                .withBrokerVersion(VersionUtil.getVersion())
                .withTimestamp(System.currentTimeMillis())
                .withPartitionId(PARTITION_ID)
                .withIntent(ProcessInstanceIntent.ELEMENT_COMPLETED)
                .withValue(
                    ImmutableProcessInstanceRecordValue.builder()
                        .from(factory.generateObject(ImmutableProcessInstanceRecordValue.class))
                        .withProcessInstanceKey(processInstanceKey)
                        .withBpmnElementType(BpmnElementType.SERVICE_TASK)
                        .withElementId(ELEMENT_ID)
                        .withFlowScopeKey(processInstanceKey)
                        .withRootProcessInstanceKey(processInstanceKey)
                        .withTenantId(TENANT_ID)
                        .build()));
  }

  // PROCESS_INSTANCE ELEMENT_COMPLETED for the root process.
  // Handled by: ListViewProcessInstanceFromProcessInstanceHandler →
  // ProcessInstanceForListViewEntity
  private Record<?> buildProcessCompletedRecord(final long processInstanceKey) {
    return factory.generateRecord(
        ValueType.PROCESS_INSTANCE,
        r ->
            r.withKey(processInstanceKey)
                .withBrokerVersion(VersionUtil.getVersion())
                .withTimestamp(System.currentTimeMillis())
                .withPartitionId(PARTITION_ID)
                .withIntent(ProcessInstanceIntent.ELEMENT_COMPLETED)
                .withValue(
                    ImmutableProcessInstanceRecordValue.builder()
                        .from(factory.generateObject(ImmutableProcessInstanceRecordValue.class))
                        .withProcessInstanceKey(processInstanceKey)
                        .withBpmnElementType(BpmnElementType.PROCESS)
                        .withParentProcessInstanceKey(-1L)
                        .withFlowScopeKey(-1L)
                        .withRootProcessInstanceKey(processInstanceKey)
                        .withTenantId(TENANT_ID)
                        .build()));
  }

  private void runExporter(final List<Record<?>> records, final ExporterConfiguration config)
      throws IOException {
    final var exporter = new CamundaExporter();
    exporter.configure(getContextFromConfig(config));
    exporter.open(new ExporterTestController());
    records.forEach(exporter::export);
    exporter.close();
  }

  private IndexSnapshot captureSnapshot(
      final SearchClientAdapter clientAdapter,
      final ExporterConfiguration config,
      final List<String> flowNodeIds,
      final List<String> processInstanceIds)
      throws IOException {
    final boolean isElasticsearch = ConnectionTypes.isElasticSearch(config.getConnect().getType());
    final String prefix = config.getConnect().getIndexPrefix();

    final var listViewIndex = new ListViewTemplate(prefix, isElasticsearch).getFullQualifiedName();
    final var flowNodeInstanceIndex =
        new FlowNodeInstanceTemplate(prefix, isElasticsearch).getFullQualifiedName();

    // list-view hosts both FlowNodeInstanceForListViewEntity and ProcessInstanceForListViewEntity,
    // so we filter by expected IDs to avoid deserializing the wrong entity type
    final var listViewFlowNodes =
        clientAdapter.searchByIds(
            listViewIndex, flowNodeIds, FlowNodeInstanceForListViewEntity.class);
    final var flowNodeInstances =
        clientAdapter.searchAll(flowNodeInstanceIndex, FlowNodeInstanceEntity.class);
    final var listViewProcessInstances =
        clientAdapter.searchByIds(
            listViewIndex, processInstanceIds, ProcessInstanceForListViewEntity.class);

    return new IndexSnapshot(listViewFlowNodes, flowNodeInstances, listViewProcessInstances);
  }

  private ExporterConfiguration configWithBulkSize(
      final ExporterConfiguration base, final int bulkSize, final String suffix) {
    final var config = new ExporterConfiguration();
    config.getAuditLog().setEnabled(false);
    config.getConnect().setUrl(base.getConnect().getUrl());
    config.getConnect().setType(base.getConnect().getType());
    config.getConnect().setClusterName(base.getConnect().getClusterName());
    config.getConnect().setAwsEnabled(base.getConnect().isAwsEnabled());
    config.getConnect().setIndexPrefix(CUSTOM_PREFIX + "-" + suffix);
    config.getBulk().setSize(bulkSize);
    return config;
  }

  private Context getContextFromConfig(final ExporterConfiguration config) {
    return new ExporterTestContext()
        .setConfiguration(new ExporterTestConfiguration<>(config.getConnect().getType(), config))
        .setPartitionId(1);
  }

  private static <T> List<T> sortById(
      final List<T> entities, final java.util.function.Function<T, String> idExtractor) {
    return entities.stream().sorted(Comparator.comparing(idExtractor)).toList();
  }

  private record IndexSnapshot(
      List<FlowNodeInstanceForListViewEntity> listViewFlowNodes,
      List<FlowNodeInstanceEntity> flowNodeInstances,
      List<ProcessInstanceForListViewEntity> listViewProcessInstances) {}
}
