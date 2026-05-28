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
import io.camunda.webapps.schema.descriptors.template.TaskTemplate;
import io.camunda.webapps.schema.descriptors.template.VariableTemplate;
import io.camunda.webapps.schema.entities.JobEntity;
import io.camunda.webapps.schema.entities.VariableEntity;
import io.camunda.webapps.schema.entities.flownode.FlowNodeInstanceEntity;
import io.camunda.webapps.schema.entities.flownode.FlowNodeState;
import io.camunda.webapps.schema.entities.incident.IncidentEntity;
import io.camunda.webapps.schema.entities.listview.FlowNodeInstanceForListViewEntity;
import io.camunda.webapps.schema.entities.listview.ProcessInstanceForListViewEntity;
import io.camunda.webapps.schema.entities.listview.ProcessInstanceState;
import io.camunda.webapps.schema.entities.post.PostImporterQueueEntity;
import io.camunda.webapps.schema.entities.usertask.TaskEntity;
import io.camunda.webapps.schema.entities.usertask.TaskEntity.TaskImplementation;
import io.camunda.webapps.schema.entities.usertask.TaskState;
import io.camunda.zeebe.exporter.api.context.Context;
import io.camunda.zeebe.exporter.test.ExporterTestConfiguration;
import io.camunda.zeebe.exporter.test.ExporterTestContext;
import io.camunda.zeebe.exporter.test.ExporterTestController;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import io.camunda.zeebe.protocol.record.intent.VariableIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ImmutableIncidentRecordValue;
import io.camunda.zeebe.protocol.record.value.ImmutableJobRecordValue;
import io.camunda.zeebe.protocol.record.value.ImmutableProcessInstanceRecordValue;
import io.camunda.zeebe.protocol.record.value.ImmutableUserTaskRecordValue;
import io.camunda.zeebe.protocol.record.value.ImmutableVariableRecordValue;
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
 * the bulk flush size.
 */
@TestInstance(Lifecycle.PER_CLASS)
final class ExporterBulkConsistencyIT {

  private static final int INSTANCE_COUNT = 20;
  private static final int PARTITION_ID = 1;
  private static final String TENANT_ID = TenantOwned.DEFAULT_TENANT_IDENTIFIER;
  private static final String ELEMENT_ID = "serviceTask";
  private static final String ERROR_MESSAGE = "an error occurred";
  private static final String VARIABLE_NAME = "myVar";
  private static final String ORIGINAL_BPMN_PROCESS_ID = "originalProcess";
  private static final String MIGRATED_BPMN_PROCESS_ID = "migratedProcess";

  @RegisterExtension private static final SearchDBExtension SEARCH_DB = SearchDBExtension.create();

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

  /**
   * Focused on multi-handler entities — entity types written by more than one handler sharing the
   * same (index, id) pair. When a flush boundary falls between two such handlers, the later handler
   * may overwrite fields the earlier handler wrote. See
   * docs/data-layer/working-with-secondary-storage.md §5.1 for guidance on when to extend this
   * test.
   */
  @TestTemplate
  void shouldProduceConsistentDataRegardlessOfBulkSize(
      final ExporterConfiguration baseConfig, final SearchClientAdapter clientAdapter)
      throws IOException {
    // given
    final var allRecords = new ArrayList<Record<?>>();
    final var flowNodeIds = new ArrayList<String>();
    final var processInstanceIds = new ArrayList<String>();
    final var jobIds = new ArrayList<String>();
    final var incidentIds = new ArrayList<String>();
    final var postImporterQueueIds = new ArrayList<String>();
    final var taskIds = new ArrayList<String>();
    for (int i = 0; i < INSTANCE_COUNT; i++) {
      allRecords.addAll(buildRecordsForInstance(i));
      flowNodeIds.add(String.valueOf(flowNodeKey(i)));
      processInstanceIds.add(String.valueOf(processInstanceKey(i)));
      jobIds.add(String.valueOf(jobKey(i)));
      incidentIds.add(String.valueOf(incidentKey(i)));
      postImporterQueueIds.add(String.format("%d-CREATED", incidentKey(i)));
      taskIds.add(String.valueOf(userTaskElementKey(i)));
    }

    final var testBulkSizes = List.of(1, 2, 5, 20);

    // when — export at bulk size 500 (exceeds total record count, so always a single flush)
    final var referenceConfig = configWithBulkSize(baseConfig, 500, "ref");
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
            postImporterQueueIds,
            taskIds);

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

    assertThat(referenceSnapshot.variables())
        .as("one VariableEntity per instance (VariableHandler)")
        .hasSize(INSTANCE_COUNT);
    assertThat(referenceSnapshot.variables())
        .as("name set by VariableHandler")
        .allMatch(e -> VARIABLE_NAME.equals(e.getName()));
    assertThat(referenceSnapshot.variables())
        .as("bpmnProcessId updated to migrated value by MigratedVariableHandler")
        .allMatch(e -> MIGRATED_BPMN_PROCESS_ID.equals(e.getBpmnProcessId()));

    assertThat(referenceSnapshot.tasks())
        .as("one TaskEntity per instance (UserTaskCreatingHandler + UserTaskHandler)")
        .hasSize(INSTANCE_COUNT);
    assertThat(referenceSnapshot.tasks())
        .as("implementation set by UserTaskCreatingHandler")
        .allMatch(e -> TaskImplementation.ZEEBE_USER_TASK.equals(e.getImplementation()));
    assertThat(referenceSnapshot.tasks())
        .as("state updated to CREATED by UserTaskHandler")
        .allMatch(e -> TaskState.CREATED.equals(e.getState()));

    // then — each test bulk size must produce documents identical to the reference;
    // indices are created once and reused across all test bulk sizes
    createSchemas(configWithBulkSize(baseConfig, 500, "work"));
    for (final int bulkSize : testBulkSizes) {
      final var config = configWithBulkSize(baseConfig, bulkSize, "work");
      clientAdapter.deleteAllDocuments(config.getConnect().getIndexPrefix());
      clientAdapter.refresh(); // make deletions visible before the next exporter opens
      runExporter(allRecords, config);
      clientAdapter.refresh();
      final var snapshot =
          captureSnapshot(
              clientAdapter,
              config,
              flowNodeIds,
              processInstanceIds,
              jobIds,
              incidentIds,
              postImporterQueueIds,
              taskIds);

      assertMatchesReference(
          "FlowNodeInstanceForListViewEntity",
          bulkSize,
          snapshot.listViewFlowNodes(),
          referenceSnapshot.listViewFlowNodes(),
          FlowNodeInstanceForListViewEntity::getId);

      assertMatchesReference(
          "FlowNodeInstanceEntity",
          bulkSize,
          snapshot.flowNodeInstances(),
          referenceSnapshot.flowNodeInstances(),
          FlowNodeInstanceEntity::getId);

      assertMatchesReference(
          "ProcessInstanceForListViewEntity",
          bulkSize,
          snapshot.listViewProcessInstances(),
          referenceSnapshot.listViewProcessInstances(),
          ProcessInstanceForListViewEntity::getId);

      assertMatchesReference(
          "JobEntity",
          bulkSize,
          snapshot.jobs(),
          referenceSnapshot.jobs(),
          e -> String.valueOf(e.getKey()));

      assertMatchesReference(
          "IncidentEntity",
          bulkSize,
          snapshot.incidents(),
          referenceSnapshot.incidents(),
          e -> String.valueOf(e.getKey()));

      assertMatchesReference(
          "PostImporterQueueEntity",
          bulkSize,
          snapshot.postImporterQueue(),
          referenceSnapshot.postImporterQueue(),
          PostImporterQueueEntity::getId,
          "creationTime");

      assertMatchesReference(
          "VariableEntity",
          bulkSize,
          snapshot.variables(),
          referenceSnapshot.variables(),
          VariableEntity::getId);

      assertMatchesReference(
          "TaskEntity",
          bulkSize,
          snapshot.tasks(),
          referenceSnapshot.tasks(),
          TaskEntity::getId,
          "creationTime");
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

  private static long userTaskElementKey(final int i) {
    return processInstanceKey(i) + 4;
  }

  private List<Record<?>> buildRecordsForInstance(final int i) {
    final long processInstanceKey = processInstanceKey(i);
    final long flowNodeKey = flowNodeKey(i);
    final long jobKey = jobKey(i);
    final long incidentKey = incidentKey(i);

    return List.of(
        buildProcessActivatingRecord(processInstanceKey),
        buildFlowNodeActivatingRecord(flowNodeKey, processInstanceKey),
        buildJobFailedRecord(jobKey, flowNodeKey, processInstanceKey),
        buildIncidentCreatedRecord(incidentKey, flowNodeKey, processInstanceKey),
        buildFlowNodeCompletedRecord(flowNodeKey, processInstanceKey),
        buildProcessCompletedRecord(processInstanceKey),
        buildVariableCreatedRecord(processInstanceKey),
        buildVariableMigratedRecord(processInstanceKey),
        buildUserTaskCreatingRecord(userTaskElementKey(i), processInstanceKey),
        buildUserTaskCreatedRecord(userTaskElementKey(i), processInstanceKey));
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
                        .withTenantId(TENANT_ID)
                        .build()));
  }

  // VARIABLE CREATED.
  // Handled by: VariableHandler → VariableEntity
  private Record<?> buildVariableCreatedRecord(final long processInstanceKey) {
    return factory.generateRecord(
        ValueType.VARIABLE,
        r ->
            r.withKey(processInstanceKey + 10)
                .withBrokerVersion(VersionUtil.getVersion())
                .withTimestamp(System.currentTimeMillis())
                .withPartitionId(PARTITION_ID)
                .withIntent(VariableIntent.CREATED)
                .withValue(
                    ImmutableVariableRecordValue.builder()
                        .from(factory.generateObject(ImmutableVariableRecordValue.class))
                        .withScopeKey(processInstanceKey)
                        .withProcessInstanceKey(processInstanceKey)
                        .withName(VARIABLE_NAME)
                        .withValue("\"hello\"")
                        .withBpmnProcessId(ORIGINAL_BPMN_PROCESS_ID)
                        .withTenantId(TENANT_ID)
                        .build()));
  }

  // VARIABLE MIGRATED.
  // Handled by: MigratedVariableHandler → VariableEntity (multi-handler)
  private Record<?> buildVariableMigratedRecord(final long processInstanceKey) {
    return factory.generateRecord(
        ValueType.VARIABLE,
        r ->
            r.withKey(processInstanceKey + 11)
                .withBrokerVersion(VersionUtil.getVersion())
                .withTimestamp(System.currentTimeMillis())
                .withPartitionId(PARTITION_ID)
                .withIntent(VariableIntent.MIGRATED)
                .withValue(
                    ImmutableVariableRecordValue.builder()
                        .from(factory.generateObject(ImmutableVariableRecordValue.class))
                        .withScopeKey(processInstanceKey)
                        .withProcessInstanceKey(processInstanceKey)
                        .withName(VARIABLE_NAME)
                        .withValue("\"hello\"")
                        .withBpmnProcessId(MIGRATED_BPMN_PROCESS_ID)
                        .withTenantId(TENANT_ID)
                        .build()));
  }

  // USER_TASK CREATING.
  // Handled by: UserTaskCreatingHandler → TaskEntity
  private Record<?> buildUserTaskCreatingRecord(
      final long elementInstanceKey, final long processInstanceKey) {
    return factory.generateRecord(
        ValueType.USER_TASK,
        r ->
            r.withKey(elementInstanceKey)
                .withBrokerVersion(VersionUtil.getVersion())
                .withTimestamp(System.currentTimeMillis())
                .withPartitionId(PARTITION_ID)
                .withIntent(UserTaskIntent.CREATING)
                .withValue(
                    ImmutableUserTaskRecordValue.builder()
                        .from(factory.generateObject(ImmutableUserTaskRecordValue.class))
                        .withElementInstanceKey(elementInstanceKey)
                        .withProcessInstanceKey(processInstanceKey)
                        .withTenantId(TENANT_ID)
                        .build()));
  }

  // USER_TASK CREATED.
  // Handled by: UserTaskHandler → TaskEntity (multi-handler)
  private Record<?> buildUserTaskCreatedRecord(
      final long elementInstanceKey, final long processInstanceKey) {
    return factory.generateRecord(
        ValueType.USER_TASK,
        r ->
            r.withKey(elementInstanceKey)
                .withBrokerVersion(VersionUtil.getVersion())
                .withTimestamp(System.currentTimeMillis())
                .withPartitionId(PARTITION_ID)
                .withIntent(UserTaskIntent.CREATED)
                .withValue(
                    ImmutableUserTaskRecordValue.builder()
                        .from(factory.generateObject(ImmutableUserTaskRecordValue.class))
                        .withElementInstanceKey(elementInstanceKey)
                        .withProcessInstanceKey(processInstanceKey)
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

  private static <T> void assertMatchesReference(
      final String entityTypeName,
      final int bulkSize,
      final List<T> actual,
      final List<T> expected,
      final java.util.function.Function<T, String> idExtractor,
      final String... ignoredFields) {
    final var sorted = sortById(actual, idExtractor);
    final var sortedRef = sortById(expected, idExtractor);
    assertThat(sorted)
        .as("%s count: bulk size %d should match reference", entityTypeName, bulkSize)
        .hasSameSizeAs(sortedRef);
    for (int j = 0; j < sorted.size(); j++) {
      final var actualEntity = sorted.get(j);
      final var expectedEntity = sortedRef.get(j);
      assertThat(actualEntity)
          .as(
              "%s id=%s: bulk size %d should match reference",
              entityTypeName, idExtractor.apply(actualEntity), bulkSize)
          .usingRecursiveComparison()
          .ignoringFields(ignoredFields)
          .isEqualTo(expectedEntity);
    }
  }

  private IndexSnapshot captureSnapshot(
      final SearchClientAdapter clientAdapter,
      final ExporterConfiguration config,
      final List<String> flowNodeIds,
      final List<String> processInstanceIds,
      final List<String> jobIds,
      final List<String> incidentIds,
      final List<String> postImporterQueueIds,
      final List<String> taskIds)
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
    final var variableIndex = new VariableTemplate(prefix, isElasticsearch).getFullQualifiedName();
    final var taskIndex = new TaskTemplate(prefix, isElasticsearch).getFullQualifiedName();

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
    final var variables = clientAdapter.searchAll(variableIndex, VariableEntity.class);
    final var tasks = clientAdapter.searchByIds(taskIndex, taskIds, TaskEntity.class);

    return new IndexSnapshot(
        listViewFlowNodes,
        flowNodeInstances,
        listViewProcessInstances,
        jobs,
        incidents,
        postImporterQueue,
        variables,
        tasks);
  }

  private ExporterConfiguration configWithBulkSize(
      final ExporterConfiguration base, final int bulkSize, final String suffix) {
    final var config = new ExporterConfiguration();
    config.getConnect().setType(base.getConnect().getType());
    config.getConnect().setClusterName(base.getConnect().getClusterName());
    config.getConnect().setDateFormat(base.getConnect().getDateFormat());
    config.getConnect().setFieldDateFormat(base.getConnect().getFieldDateFormat());
    config.getConnect().setSocketTimeout(base.getConnect().getSocketTimeout());
    config.getConnect().setConnectTimeout(base.getConnect().getConnectTimeout());
    config.getConnect().setUrl(base.getConnect().getUrl());
    config.getConnect().setUsername(base.getConnect().getUsername());
    config.getConnect().setPassword(base.getConnect().getPassword());
    config.getConnect().setSecurity(base.getConnect().getSecurity());
    config.getConnect().setInterceptorPlugins(base.getConnect().getInterceptorPlugins());
    config.getConnect().setAwsEnabled(base.getConnect().isAwsEnabled());
    config.getConnect().setIndexPrefix(CUSTOM_PREFIX + "-" + suffix);
    config.getBulk().setSize(bulkSize);
    // Suppress IncidentUpdateTask for the duration of this test. The task runs in a background
    // thread and modifies incident/flow-node fields after ES auto-refreshes documents. This test
    // is only concerned with handler flush-boundary consistency, not background task convergence,
    // so we set an effectively infinite reschedule delay (first execution at exporter open sees
    // no data; next would be ~24 days later) to prevent timing-dependent divergence between the
    // fast reference run and the slower small-bulk working runs.
    config.getPostExport().setDelayBetweenRuns(Integer.MAX_VALUE);
    config.getPostExport().setMaxDelayBetweenRuns(Integer.MAX_VALUE);
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
      List<PostImporterQueueEntity> postImporterQueue,
      List<VariableEntity> variables,
      List<TaskEntity> tasks) {}
}
