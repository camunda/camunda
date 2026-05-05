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
import io.camunda.webapps.schema.descriptors.template.IncidentTemplate;
import io.camunda.webapps.schema.descriptors.template.JobTemplate;
import io.camunda.webapps.schema.descriptors.template.ListViewTemplate;
import io.camunda.webapps.schema.descriptors.template.PostImporterQueueTemplate;
import io.camunda.webapps.schema.entities.JobEntity;
import io.camunda.webapps.schema.entities.flownode.FlowNodeInstanceEntity;
import io.camunda.webapps.schema.entities.flownode.FlowNodeState;
import io.camunda.webapps.schema.entities.incident.IncidentEntity;
import io.camunda.webapps.schema.entities.listview.FlowNodeInstanceForListViewEntity;
import io.camunda.webapps.schema.entities.listview.ProcessInstanceForListViewEntity;
import io.camunda.webapps.schema.entities.listview.ProcessInstanceState;
import io.camunda.webapps.schema.entities.post.PostImporterQueueEntity;
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
 * the bulk flush size. Records are interleaved in a deterministic step-round-robin order so that
 * specific flush boundaries (exact step boundary, one-past, intra-step splits) are exercised
 * predictably on every run.
 */
@TestInstance(Lifecycle.PER_CLASS)
final class ExporterBulkConsistencyIT {

  private static final int INSTANCE_COUNT = 20;
  private static final int PARTITION_ID = 1;
  private static final String TENANT_ID = TenantOwned.DEFAULT_TENANT_IDENTIFIER;
  private static final String ELEMENT_ID = "serviceTask";
  private static final String ERROR_MESSAGE = "an error occurred";

  @RegisterExtension
  private static final SearchDBExtension SEARCH_DB = SearchDBExtension.create();

  @RegisterExtension
  private static final CamundaExporterITTemplateExtension TEMPLATE_EXTENSION =
      new CamundaExporterITTemplateExtension(SEARCH_DB);

  private final ProtocolFactory factory = new ProtocolFactory();

  @AfterEach
  void afterEach() throws IOException {
    final var openSearchAwsInstanceUrl =
        Optional.ofNullable(System.getProperty(TEST_INTEGRATION_OPENSEARCH_AWS_URL)).orElse("");
    if (openSearchAwsInstanceUrl.isEmpty()) {
      SEARCH_DB.esClient().indices().delete(req -> req.index(CUSTOM_PREFIX + "*"));
    }
    SEARCH_DB.osClient().indices().delete(req -> req.index(CUSTOM_PREFIX + "*"));
  }

  @TestTemplate
  void shouldProduceConsistentDataRegardlessOfBulkSize(
      final ExporterConfiguration baseConfig, final SearchClientAdapter clientAdapter)
      throws IOException {
    // given — INSTANCE_COUNT independent process instances, each producing 6 records.
    // Deterministic interleaving: all step-N records precede all step-(N+1) records, with
    // instances in fixed construction order within each step. This exposes flush-boundary
    // bugs predictably: at bulk size INSTANCE_COUNT each flush holds exactly one step, at
    // INSTANCE_COUNT+1 the first record of the next step spills into the same flush, etc.
    final var recordGroups = new ArrayList<List<Record<?>>>();
    final var flowNodeIds = new ArrayList<String>();
    final var processInstanceIds = new ArrayList<String>();
    final var jobIds = new ArrayList<String>();
    final var incidentIds = new ArrayList<String>();
    final var postImporterQueueIds = new ArrayList<String>();
    for (int i = 0; i < INSTANCE_COUNT; i++) {
      recordGroups.add(buildRecordsForInstance(i));
      flowNodeIds.add(String.valueOf(flowNodeKey(i)));
      processInstanceIds.add(String.valueOf(processInstanceKey(i)));
      jobIds.add(String.valueOf(jobKey(i)));
      incidentIds.add(String.valueOf(incidentKey(i)));
      postImporterQueueIds.add(String.format("%d-CREATED", incidentKey(i)));
    }

    final var allRecords = new ArrayList<Record<?>>();
    final int stepsPerInstance = recordGroups.getFirst().size();
    for (int step = 0; step < stepsPerInstance; step++) {
      for (final var group : recordGroups) {
        allRecords.add(group.get(step));
      }
    }

    // reference: single flush containing all records
    final int referenceBulkSize = allRecords.size() + 1;

    // test sizes hit: every-record, intra-step splits, exact step boundary (INSTANCE_COUNT),
    // one-past-step boundary (INSTANCE_COUNT+1), and multi-step flushes
    final var testBulkSizes = List.of(1, 3, 7, INSTANCE_COUNT, INSTANCE_COUNT + 1, INSTANCE_COUNT * 2);

    // when — export at reference bulk size (single flush)
    final var referenceConfig = configWithBulkSize(baseConfig, referenceBulkSize, "bs-ref");
    createSchemas(referenceConfig);
    runExporter(allRecords, referenceConfig);
    clientAdapter.refresh();
    final var referenceSnapshot =
        captureSnapshot(
            clientAdapter,
            referenceConfig,
            flowNodeIds,
            processInstanceIds,
            jobIds,
            incidentIds,
            postImporterQueueIds);

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

    assertThat(referenceSnapshot.jobs())
        .as("one JobEntity per instance (JobHandler)")
        .hasSize(INSTANCE_COUNT);
    assertThat(referenceSnapshot.jobs())
        .as("processInstanceKey set by JobHandler")
        .allMatch(e -> e.getProcessInstanceKey() != null);

    assertThat(referenceSnapshot.incidents())
        .as("one IncidentEntity per instance (IncidentHandler)")
        .hasSize(INSTANCE_COUNT);
    assertThat(referenceSnapshot.incidents())
        .as("errorMessage set by IncidentHandler")
        .allMatch(e -> ERROR_MESSAGE.equals(e.getErrorMessage()));

    assertThat(referenceSnapshot.postImporterQueue())
        .as("one PostImporterQueueEntity per instance (PostImporterQueueFromIncidentHandler)")
        .hasSize(INSTANCE_COUNT);
    assertThat(referenceSnapshot.postImporterQueue())
        .as("processInstanceKey set by PostImporterQueueFromIncidentHandler")
        .allMatch(e -> e.getProcessInstanceKey() != null);

    // then — each test bulk size must produce documents identical to the reference
    for (final int bulkSize : testBulkSizes) {
      final var config = configWithBulkSize(baseConfig, bulkSize, "bs-" + bulkSize);
      createSchemas(config);
      runExporter(allRecords, config);
      clientAdapter.refresh();
      final var snapshot =
          captureSnapshot(
              clientAdapter, config, flowNodeIds, processInstanceIds, jobIds, incidentIds,
              postImporterQueueIds);

      assertThat(sortById(snapshot.listViewFlowNodes(), FlowNodeInstanceForListViewEntity::getId))
          .as("FlowNodeInstanceForListViewEntity: bulk size %d should match reference", bulkSize)
          .usingRecursiveFieldByFieldElementComparator()
          .isEqualTo(
              sortById(
                  referenceSnapshot.listViewFlowNodes(), FlowNodeInstanceForListViewEntity::getId));

      assertThat(sortById(snapshot.flowNodeInstances(), FlowNodeInstanceEntity::getId))
          .as("FlowNodeInstanceEntity: bulk size %d should match reference", bulkSize)
          .usingRecursiveFieldByFieldElementComparator()
          .isEqualTo(
              sortById(referenceSnapshot.flowNodeInstances(), FlowNodeInstanceEntity::getId));

      assertThat(
              sortById(
                  snapshot.listViewProcessInstances(), ProcessInstanceForListViewEntity::getId))
          .as("ProcessInstanceForListViewEntity: bulk size %d should match reference", bulkSize)
          .usingRecursiveFieldByFieldElementComparator()
          .isEqualTo(
              sortById(
                  referenceSnapshot.listViewProcessInstances(),
                  ProcessInstanceForListViewEntity::getId));

      assertThat(sortById(snapshot.jobs(), e -> String.valueOf(e.getKey())))
          .as("JobEntity: bulk size %d should match reference", bulkSize)
          .usingRecursiveFieldByFieldElementComparator()
          .isEqualTo(sortById(referenceSnapshot.jobs(), e -> String.valueOf(e.getKey())));

      assertThat(sortById(snapshot.incidents(), e -> String.valueOf(e.getKey())))
          .as("IncidentEntity: bulk size %d should match reference", bulkSize)
          .usingRecursiveFieldByFieldElementComparator()
          .isEqualTo(sortById(referenceSnapshot.incidents(), e -> String.valueOf(e.getKey())));

      assertThat(sortById(snapshot.postImporterQueue(), PostImporterQueueEntity::getId))
          .as("PostImporterQueueEntity: bulk size %d should match reference", bulkSize)
          .usingRecursiveFieldByFieldElementComparatorIgnoringFields("creationTime")
          .isEqualTo(
              sortById(referenceSnapshot.postImporterQueue(), PostImporterQueueEntity::getId));
    }
  }

  private static long processInstanceKey(final int i) {
    return (i + 1) * 1000L;
  }

  private static long flowNodeKey(final int i) {
    return processInstanceKey(i) + 1;
  }

  private static long jobKey(final int i) {
    return processInstanceKey(i) + 2;
  }

  private static long incidentKey(final int i) {
    return processInstanceKey(i) + 3;
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
  // Handled by: ListViewFlowNodeFromJobHandler → FlowNodeInstanceForListViewEntity (multi-handler)
  //             JobHandler                     → JobEntity (single-handler)
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
  // Handled by: ListViewFlowNodeFromIncidentHandler    → FlowNodeInstanceForListViewEntity
  // (multi-handler)
  //             FlowNodeInstanceFromIncidentHandler    → FlowNodeInstanceEntity (multi-handler)
  //             IncidentHandler                       → IncidentEntity (single-handler)
  //             PostImporterQueueFromIncidentHandler  → PostImporterQueueEntity (single-handler)
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
      final List<String> processInstanceIds,
      final List<String> jobIds,
      final List<String> incidentIds,
      final List<String> postImporterQueueIds)
      throws IOException {
    final boolean isElasticsearch = ConnectionTypes.isElasticSearch(config.getConnect().getType());
    final String prefix = config.getConnect().getIndexPrefix();

    final var listViewIndex = new ListViewTemplate(prefix, isElasticsearch).getFullQualifiedName();
    final var flowNodeInstanceIndex =
        new FlowNodeInstanceTemplate(prefix, isElasticsearch).getFullQualifiedName();
    final var jobIndex = new JobTemplate(prefix, isElasticsearch).getFullQualifiedName();
    final var incidentIndex = new IncidentTemplate(prefix, isElasticsearch).getFullQualifiedName();
    final var postImporterQueueIndex =
        new PostImporterQueueTemplate(prefix, isElasticsearch).getFullQualifiedName();

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
    final var jobs = clientAdapter.searchByIds(jobIndex, jobIds, JobEntity.class);
    final var incidents =
        clientAdapter.searchByIds(incidentIndex, incidentIds, IncidentEntity.class);
    final var postImporterQueue =
        clientAdapter.searchByIds(
            postImporterQueueIndex, postImporterQueueIds, PostImporterQueueEntity.class);

    return new IndexSnapshot(
        listViewFlowNodes,
        flowNodeInstances,
        listViewProcessInstances,
        jobs,
        incidents,
        postImporterQueue);
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
      List<ProcessInstanceForListViewEntity> listViewProcessInstances,
      List<JobEntity> jobs,
      List<IncidentEntity> incidents,
      List<PostImporterQueueEntity> postImporterQueue) {}
}
