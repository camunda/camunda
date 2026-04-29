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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * Verifies that the final indexed state produced by the CamundaExporter is identical regardless of
 * the bulk flush size.
 *
 * <p>The exporter caches entities in memory, allowing multiple handlers from different record types
 * to accumulate updates onto the same entity before a flush. When the bulk size is small (e.g. 1),
 * a flush can happen between two handlers updating the same entity. If a handler uses a full
 * document replace instead of a partial update, fields written by an earlier handler are silently
 * lost. This test catches that regression by asserting that flushing at bulk size 1 produces the
 * same indexed document as flushing at bulk size 5000.
 *
 * <p>The record sequence covers all multi-handler scenarios for the primary entities:
 *
 * <ul>
 *   <li>{@code FlowNodeInstanceForListViewEntity} — written by three handlers responding to
 *       PROCESS_INSTANCE, JOB, and INCIDENT records respectively
 *   <li>{@code FlowNodeInstanceEntity} — written by two handlers responding to PROCESS_INSTANCE and
 *       INCIDENT records
 *   <li>{@code ProcessInstanceForListViewEntity} — written by one handler across ELEMENT_ACTIVATING
 *       and ELEMENT_COMPLETED intents
 * </ul>
 */
@TestInstance(Lifecycle.PER_CLASS)
final class ExporterBulkConsistencyIT {

  private static final long PROCESS_INSTANCE_KEY = 100L;
  private static final long FLOW_NODE_KEY = 200L;
  private static final long JOB_KEY = 300L;
  private static final long INCIDENT_KEY = 400L;
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
    // given — a realistic process instance lifecycle where the same entities are updated
    // by handlers from different record types (the multi-handler scenario that can regress
    // if a handler switches from upsert to a full document replace):
    //
    //  FlowNodeInstanceForListViewEntity(FLOW_NODE_KEY): updated by three distinct handlers
    //    - ListViewFlowNodeFromProcessInstanceHandler (PROCESS_INSTANCE records)
    //    - ListViewFlowNodeFromJobHandler             (JOB records)
    //    - ListViewFlowNodeFromIncidentHandler        (INCIDENT records)
    //
    //  FlowNodeInstanceEntity(FLOW_NODE_KEY): updated by two distinct handlers
    //    - FlowNodeInstanceFromProcessInstanceHandler (PROCESS_INSTANCE records)
    //    - FlowNodeInstanceFromIncidentHandler        (INCIDENT records)
    //
    //  ProcessInstanceForListViewEntity(PROCESS_INSTANCE_KEY): updated by one handler across
    //    two intents (ELEMENT_ACTIVATING then ELEMENT_COMPLETED)
    final List<Record<?>> records =
        List.of(
            buildProcessActivatingRecord(),
            buildFlowNodeActivatingRecord(),
            buildJobFailedRecord(),
            buildIncidentCreatedRecord(),
            buildFlowNodeCompletedRecord(),
            buildProcessCompletedRecord());

    // when — run the exporter at each bulk size; each run uses an isolated index prefix
    final Map<Integer, EntitySnapshot> snapshots = new LinkedHashMap<>();
    for (final int bulkSize : List.of(1, 10, 100, 1_000, 5_000)) {
      final var config = configWithBulkSize(baseConfig, bulkSize);
      createSchemas(config);

      final var exporter = new CamundaExporter();
      exporter.configure(getContextFromConfig(config));
      exporter.open(new ExporterTestController());
      records.forEach(exporter::export);
      exporter.close(); // triggers final flush of any remaining buffered entities

      clientAdapter.refresh();
      snapshots.put(bulkSize, captureSnapshot(clientAdapter, config));
    }

    // then — confirm the reference (bulk size 5000) has all expected fields populated,
    // which proves each handler was actually invoked and contributed its fields
    final EntitySnapshot reference = snapshots.get(5_000);

    assertThat(reference.listViewFlowNode())
        .as("FlowNodeInstanceForListViewEntity must be indexed")
        .isNotNull();
    assertThat(reference.listViewFlowNode().getActivityState())
        .as("activityState set by ListViewFlowNodeFromProcessInstanceHandler (COMPLETED intent)")
        .isEqualTo(FlowNodeState.COMPLETED);
    assertThat(reference.listViewFlowNode().isJobFailedWithRetriesLeft())
        .as("jobFailedWithRetriesLeft set by ListViewFlowNodeFromJobHandler (FAILED with retries)")
        .isTrue();
    assertThat(reference.listViewFlowNode().getErrorMessage())
        .as("errorMessage set by ListViewFlowNodeFromIncidentHandler (CREATED intent)")
        .isEqualTo(ERROR_MESSAGE);

    assertThat(reference.flowNodeInstance())
        .as("FlowNodeInstanceEntity must be indexed")
        .isNotNull();
    assertThat(reference.flowNodeInstance().getState())
        .as("state set by FlowNodeInstanceFromProcessInstanceHandler (COMPLETED intent)")
        .isEqualTo(FlowNodeState.COMPLETED);
    assertThat(reference.flowNodeInstance().getIncidentKey())
        .as("incidentKey set by FlowNodeInstanceFromIncidentHandler")
        .isNotNull();

    assertThat(reference.listViewProcessInstance())
        .as("ProcessInstanceForListViewEntity must be indexed")
        .isNotNull();
    assertThat(reference.listViewProcessInstance().getState())
        .as("state set by ListViewProcessInstanceFromProcessInstanceHandler (COMPLETED intent)")
        .isEqualTo(ProcessInstanceState.COMPLETED);

    // all smaller bulk sizes must produce documents identical to the reference
    for (final int bulkSize : List.of(1, 10, 100, 1_000)) {
      final EntitySnapshot snapshot = snapshots.get(bulkSize);
      assertThat(snapshot.listViewFlowNode())
          .as(
              "FlowNodeInstanceForListViewEntity: bulk size %d should match bulk size 5000",
              bulkSize)
          .usingRecursiveComparison()
          .isEqualTo(reference.listViewFlowNode());
      assertThat(snapshot.flowNodeInstance())
          .as("FlowNodeInstanceEntity: bulk size %d should match bulk size 5000", bulkSize)
          .usingRecursiveComparison()
          .isEqualTo(reference.flowNodeInstance());
      assertThat(snapshot.listViewProcessInstance())
          .as(
              "ProcessInstanceForListViewEntity: bulk size %d should match bulk size 5000",
              bulkSize)
          .usingRecursiveComparison()
          .isEqualTo(reference.listViewProcessInstance());
    }
  }

  // PROCESS_INSTANCE ELEMENT_ACTIVATING for the root process itself.
  // Handled by: ListViewProcessInstanceFromProcessInstanceHandler →
  // ProcessInstanceForListViewEntity
  private Record<?> buildProcessActivatingRecord() {
    return factory.generateRecord(
        ValueType.PROCESS_INSTANCE,
        r ->
            r.withKey(PROCESS_INSTANCE_KEY)
                .withBrokerVersion("8.8.0")
                .withTimestamp(System.currentTimeMillis())
                .withPartitionId(PARTITION_ID)
                .withIntent(ProcessInstanceIntent.ELEMENT_ACTIVATING)
                .withValue(
                    ImmutableProcessInstanceRecordValue.builder()
                        .from(factory.generateObject(ImmutableProcessInstanceRecordValue.class))
                        .withProcessInstanceKey(PROCESS_INSTANCE_KEY)
                        .withBpmnElementType(BpmnElementType.PROCESS)
                        .withParentProcessInstanceKey(-1L)
                        .withRootProcessInstanceKey(PROCESS_INSTANCE_KEY)
                        .withTenantId(TENANT_ID)
                        .build()));
  }

  // PROCESS_INSTANCE ELEMENT_ACTIVATING for the service task flow node.
  // Handled by: ListViewFlowNodeFromProcessInstanceHandler → FlowNodeInstanceForListViewEntity
  //             FlowNodeInstanceFromProcessInstanceHandler → FlowNodeInstanceEntity
  private Record<?> buildFlowNodeActivatingRecord() {
    return factory.generateRecord(
        ValueType.PROCESS_INSTANCE,
        r ->
            r.withKey(FLOW_NODE_KEY)
                .withBrokerVersion("8.8.0")
                .withTimestamp(System.currentTimeMillis())
                .withPartitionId(PARTITION_ID)
                .withIntent(ProcessInstanceIntent.ELEMENT_ACTIVATING)
                .withValue(
                    ImmutableProcessInstanceRecordValue.builder()
                        .from(factory.generateObject(ImmutableProcessInstanceRecordValue.class))
                        .withProcessInstanceKey(PROCESS_INSTANCE_KEY)
                        .withBpmnElementType(BpmnElementType.SERVICE_TASK)
                        .withElementId(ELEMENT_ID)
                        .withFlowScopeKey(PROCESS_INSTANCE_KEY)
                        .withRootProcessInstanceKey(PROCESS_INSTANCE_KEY)
                        .withTenantId(TENANT_ID)
                        .build()));
  }

  // JOB FAILED with retries remaining.
  // Handled by: ListViewFlowNodeFromJobHandler → FlowNodeInstanceForListViewEntity
  //             JobHandler                     → JobEntity
  private Record<?> buildJobFailedRecord() {
    return factory.generateRecord(
        ValueType.JOB,
        r ->
            r.withKey(JOB_KEY)
                .withBrokerVersion("8.8.0")
                .withTimestamp(System.currentTimeMillis())
                .withPartitionId(PARTITION_ID)
                .withIntent(JobIntent.FAILED)
                .withValue(
                    ImmutableJobRecordValue.builder()
                        .from(factory.generateObject(ImmutableJobRecordValue.class))
                        .withElementInstanceKey(FLOW_NODE_KEY)
                        .withProcessInstanceKey(PROCESS_INSTANCE_KEY)
                        .withRootProcessInstanceKey(PROCESS_INSTANCE_KEY)
                        .withRetries(2)
                        .withDeadline(System.currentTimeMillis() + 60_000L)
                        .withTenantId(TENANT_ID)
                        .withJobToUserTaskMigration(false)
                        .build()));
  }

  // INCIDENT CREATED on the service task flow node.
  // Handled by: ListViewFlowNodeFromIncidentHandler → FlowNodeInstanceForListViewEntity
  //             FlowNodeInstanceFromIncidentHandler → FlowNodeInstanceEntity
  //             IncidentHandler                    → IncidentEntity
  private Record<?> buildIncidentCreatedRecord() {
    return factory.generateRecord(
        ValueType.INCIDENT,
        r ->
            r.withKey(INCIDENT_KEY)
                .withBrokerVersion("8.8.0")
                .withTimestamp(System.currentTimeMillis())
                .withPartitionId(PARTITION_ID)
                .withIntent(IncidentIntent.CREATED)
                .withValue(
                    ImmutableIncidentRecordValue.builder()
                        .from(factory.generateObject(ImmutableIncidentRecordValue.class))
                        .withElementInstanceKey(FLOW_NODE_KEY)
                        .withProcessInstanceKey(PROCESS_INSTANCE_KEY)
                        .withElementId(ELEMENT_ID)
                        .withTenantId(TENANT_ID)
                        .withErrorMessage(ERROR_MESSAGE)
                        .build()));
  }

  // PROCESS_INSTANCE ELEMENT_COMPLETED for the service task flow node.
  // Handled by: ListViewFlowNodeFromProcessInstanceHandler → FlowNodeInstanceForListViewEntity
  //             FlowNodeInstanceFromProcessInstanceHandler → FlowNodeInstanceEntity
  private Record<?> buildFlowNodeCompletedRecord() {
    return factory.generateRecord(
        ValueType.PROCESS_INSTANCE,
        r ->
            r.withKey(FLOW_NODE_KEY)
                .withBrokerVersion("8.8.0")
                .withTimestamp(System.currentTimeMillis())
                .withPartitionId(PARTITION_ID)
                .withIntent(ProcessInstanceIntent.ELEMENT_COMPLETED)
                .withValue(
                    ImmutableProcessInstanceRecordValue.builder()
                        .from(factory.generateObject(ImmutableProcessInstanceRecordValue.class))
                        .withProcessInstanceKey(PROCESS_INSTANCE_KEY)
                        .withBpmnElementType(BpmnElementType.SERVICE_TASK)
                        .withElementId(ELEMENT_ID)
                        .withFlowScopeKey(PROCESS_INSTANCE_KEY)
                        .withRootProcessInstanceKey(PROCESS_INSTANCE_KEY)
                        .withTenantId(TENANT_ID)
                        .build()));
  }

  // PROCESS_INSTANCE ELEMENT_COMPLETED for the root process.
  // Handled by: ListViewProcessInstanceFromProcessInstanceHandler →
  // ProcessInstanceForListViewEntity
  private Record<?> buildProcessCompletedRecord() {
    return factory.generateRecord(
        ValueType.PROCESS_INSTANCE,
        r ->
            r.withKey(PROCESS_INSTANCE_KEY)
                .withBrokerVersion("8.8.0")
                .withTimestamp(System.currentTimeMillis())
                .withPartitionId(PARTITION_ID)
                .withIntent(ProcessInstanceIntent.ELEMENT_COMPLETED)
                .withValue(
                    ImmutableProcessInstanceRecordValue.builder()
                        .from(factory.generateObject(ImmutableProcessInstanceRecordValue.class))
                        .withProcessInstanceKey(PROCESS_INSTANCE_KEY)
                        .withBpmnElementType(BpmnElementType.PROCESS)
                        .withParentProcessInstanceKey(-1L)
                        .withRootProcessInstanceKey(PROCESS_INSTANCE_KEY)
                        .withTenantId(TENANT_ID)
                        .build()));
  }

  private EntitySnapshot captureSnapshot(
      final SearchClientAdapter clientAdapter, final ExporterConfiguration config)
      throws IOException {
    final boolean isElasticsearch = ConnectionTypes.isElasticSearch(config.getConnect().getType());
    final String prefix = config.getConnect().getIndexPrefix();

    final var listViewIndex = new ListViewTemplate(prefix, isElasticsearch).getFullQualifiedName();
    final var flowNodeInstanceIndex =
        new FlowNodeInstanceTemplate(prefix, isElasticsearch).getFullQualifiedName();

    final var listViewFlowNode =
        clientAdapter.get(
            String.valueOf(FLOW_NODE_KEY),
            String.valueOf(PROCESS_INSTANCE_KEY),
            listViewIndex,
            FlowNodeInstanceForListViewEntity.class);
    final var flowNodeInstance =
        clientAdapter.get(
            String.valueOf(FLOW_NODE_KEY), flowNodeInstanceIndex, FlowNodeInstanceEntity.class);
    final var listViewProcessInstance =
        clientAdapter.get(
            String.valueOf(PROCESS_INSTANCE_KEY),
            listViewIndex,
            ProcessInstanceForListViewEntity.class);

    return new EntitySnapshot(listViewFlowNode, flowNodeInstance, listViewProcessInstance);
  }

  private ExporterConfiguration configWithBulkSize(
      final ExporterConfiguration base, final int bulkSize) {
    final var config = new ExporterConfiguration();
    config.getAuditLog().setEnabled(false);
    config.getConnect().setUrl(base.getConnect().getUrl());
    config.getConnect().setType(base.getConnect().getType());
    config.getConnect().setClusterName(base.getConnect().getClusterName());
    config.getConnect().setAwsEnabled(base.getConnect().isAwsEnabled());
    config.getConnect().setIndexPrefix(CUSTOM_PREFIX + "-bs-" + bulkSize);
    config.getBulk().setSize(bulkSize);
    return config;
  }

  private Context getContextFromConfig(final ExporterConfiguration config) {
    return new ExporterTestContext()
        .setConfiguration(new ExporterTestConfiguration<>(config.getConnect().getType(), config))
        .setPartitionId(1);
  }

  private record EntitySnapshot(
      FlowNodeInstanceForListViewEntity listViewFlowNode,
      FlowNodeInstanceEntity flowNodeInstance,
      ProcessInstanceForListViewEntity listViewProcessInstance) {}
}
