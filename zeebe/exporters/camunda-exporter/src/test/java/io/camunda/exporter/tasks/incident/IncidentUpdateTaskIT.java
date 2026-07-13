/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.incident;

import static io.camunda.exporter.utils.CamundaExporterSchemaUtils.createSchemas;
import static io.camunda.search.test.utils.SearchDBExtension.TEST_INTEGRATION_OPENSEARCH_AWS_URL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient;
import io.camunda.exporter.DefaultExporterResourceProvider;
import io.camunda.exporter.ExporterMetadata;
import io.camunda.exporter.ExporterResourceProvider;
import io.camunda.exporter.cache.ExporterEntityCacheProvider;
import io.camunda.exporter.config.ConnectionTypes;
import io.camunda.exporter.config.ExporterConfiguration;
import io.camunda.exporter.metrics.CamundaExporterMetrics;
import io.camunda.exporter.notifier.IncidentNotifier;
import io.camunda.exporter.utils.CamundaExporterITTemplateExtension;
import io.camunda.search.connect.es.ElasticsearchConnector;
import io.camunda.search.connect.os.OpensearchConnector;
import io.camunda.search.test.utils.SearchClientAdapter;
import io.camunda.search.test.utils.SearchDBExtension;
import io.camunda.search.test.utils.TestObjectMapper;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;
import io.camunda.webapps.schema.descriptors.IndexTemplateDescriptor;
import io.camunda.webapps.schema.descriptors.index.ImportPositionIndex;
import io.camunda.webapps.schema.descriptors.template.FlowNodeInstanceTemplate;
import io.camunda.webapps.schema.descriptors.template.IncidentTemplate;
import io.camunda.webapps.schema.descriptors.template.ListViewTemplate;
import io.camunda.webapps.schema.descriptors.template.OperationTemplate;
import io.camunda.webapps.schema.descriptors.template.PostImporterQueueTemplate;
import io.camunda.webapps.schema.entities.ExporterEntity;
import io.camunda.webapps.schema.entities.flownode.FlowNodeInstanceEntity;
import io.camunda.webapps.schema.entities.incident.IncidentEntity;
import io.camunda.webapps.schema.entities.incident.IncidentState;
import io.camunda.webapps.schema.entities.listview.FlowNodeInstanceForListViewEntity;
import io.camunda.webapps.schema.entities.listview.ProcessInstanceForListViewEntity;
import io.camunda.webapps.schema.entities.post.PostImporterActionType;
import io.camunda.webapps.schema.entities.post.PostImporterQueueEntity;
import io.camunda.zeebe.exporter.api.ExporterException;
import io.camunda.zeebe.exporter.api.context.Context;
import io.camunda.zeebe.exporter.test.ExporterTestContext;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import org.agrona.CloseHelper;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.opensearch.client.opensearch.OpenSearchAsyncClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class IncidentUpdateTaskIT {
  private static final Logger LOGGER = LoggerFactory.getLogger(IncidentUpdateTaskIT.class);
  private static final AtomicLong ID_GENERATOR = new AtomicLong(1);
  private static final int PARTITION_ID = 1;
  private static final Instant NOW = Instant.parse("2026-05-01T10:26:00Z");
  private static final int BATCH_SIZE = 10;
  private static final Duration UPDATE_TIMEOUT = Duration.ofSeconds(30L);

  @RegisterExtension private static SearchDBExtension searchDB = SearchDBExtension.create();

  @RegisterExtension
  private static CamundaExporterITTemplateExtension templateExtension =
      new CamundaExporterITTemplateExtension(searchDB);

  private CamundaExporterMetrics exporterMetrics;
  private ExecutorService executor;
  private IncidentNotifier incidentNotifier;
  private ExporterMetadata exporterMetadata;
  private Context context;
  private String testPrefix;

  private final List<AutoCloseable> resourcesToClose = new ArrayList<>();

  @BeforeEach
  void setup() {
    testPrefix = RandomStringUtils.insecure().nextAlphabetic(9).toLowerCase();
    context =
        new ExporterTestContext().setPartitionId(PARTITION_ID).setClock(InstantSource.fixed(NOW));
    exporterMetrics = new CamundaExporterMetrics(context.getMeterRegistry());
    executor = Executors.newSingleThreadExecutor();
    incidentNotifier = mock(IncidentNotifier.class);
    when(incidentNotifier.notifyAsync(any())).thenReturn(CompletableFuture.completedFuture(null));
    exporterMetadata = new ExporterMetadata(TestObjectMapper.objectMapper());
  }

  @AfterEach
  void teardown() throws Exception {
    final var openSearchAwsInstanceUrl =
        Optional.ofNullable(System.getProperty(TEST_INTEGRATION_OPENSEARCH_AWS_URL)).orElse("");
    if (openSearchAwsInstanceUrl.isEmpty()) {
      searchDB.esClient().indices().delete(req -> req.index(testPrefix + "*"));
    }
    searchDB.osClient().indices().delete(req -> req.index(testPrefix + "*"));

    if (executor != null) {
      executor.shutdown();
      executor = null;
    }

    CloseHelper.quietCloseAll(resourcesToClose);
    resourcesToClose.clear();
  }

  @TestTemplate
  void shouldExecuteWithoutErrorsWhenNoIncidentsToUpdate(
      final ExporterConfiguration config, final SearchClientAdapter ignored) throws Exception {
    // just a basic smoke test to verify that the job runs and creates well-formed queries etc
    // when there's nothing to actually update
    withIncidentUpdateTask(
        config,
        (job, resources) -> {
          // when
          final var updated = job.execute();

          // then
          assertThat(updated).succeedsWithin(UPDATE_TIMEOUT).isEqualTo(0);

          assertThat(exporterMetadata.getLastIncidentUpdatePosition()).isEqualTo(-1L);
        });
  }

  @TestTemplate
  void shouldThrowExceptionWhenIncidentsToUpdateAreNotFound(
      final ExporterConfiguration config, final SearchClientAdapter client) throws Exception {
    withIncidentUpdateTask(
        config,
        (job, resources) -> {
          final var postImporterTemplate =
              resources.getIndexTemplateDescriptor(PostImporterQueueTemplate.class);

          final PostImporterQueueEntity queueEntity =
              new PostImporterQueueEntity()
                  .setId("queue-1")
                  .setPartitionId(PARTITION_ID)
                  .setActionType(PostImporterActionType.INCIDENT)
                  .setIntent("CREATED")
                  .setKey(9999L)
                  .setPosition(1L);

          store(postImporterTemplate, client, queueEntity);
          client.refresh(postImporterTemplate.getFullQualifiedName());

          // when
          final var updated = job.execute();

          // then
          assertThat(updated)
              .failsWithin(UPDATE_TIMEOUT)
              .withThrowableThat()
              .withRootCauseInstanceOf(ExporterException.class)
              .withMessageContaining(
                  "Failed to fetch incidents associated with post-export updates")
              .withMessageContaining("Missing incident IDs: [[9999]]");

          assertThat(exporterMetadata.getLastIncidentUpdatePosition()).isEqualTo(-1L);
        });
  }

  @TestTemplate
  void shouldExecuteWithoutErrorsButNotUpdatePositionWhenProcessInstanceNotFound(
      final ExporterConfiguration config, final SearchClientAdapter client) throws Exception {
    withIncidentUpdateTask(
        config,
        (job, resources) -> {
          final var incidentTemplate = resources.getIndexTemplateDescriptor(IncidentTemplate.class);

          final var incidentKey = ID_GENERATOR.getAndIncrement();
          final IncidentEntity incidentEntity =
              new IncidentEntity()
                  .setId(String.valueOf(incidentKey))
                  .setPartitionId(PARTITION_ID)
                  .setKey(incidentKey)
                  .setErrorMessage("An error happened")
                  .setProcessInstanceKey(9999L)
                  .setFlowNodeInstanceKey(9999L);

          store(incidentTemplate, client, incidentEntity);
          client.refresh(incidentTemplate.getFullQualifiedName());

          final var postImporterTemplate =
              resources.getIndexTemplateDescriptor(PostImporterQueueTemplate.class);

          final PostImporterQueueEntity queueEntity =
              new PostImporterQueueEntity()
                  .setId("queue-1")
                  .setPartitionId(PARTITION_ID)
                  .setActionType(PostImporterActionType.INCIDENT)
                  .setIntent("CREATED")
                  .setKey(incidentKey)
                  .setPosition(1L);

          store(postImporterTemplate, client, queueEntity);
          client.refresh(postImporterTemplate.getFullQualifiedName());

          // when
          final var updated = job.execute();

          // then
          assertThat(updated).succeedsWithin(UPDATE_TIMEOUT).isEqualTo(1);

          assertThat(exporterMetadata.getLastIncidentUpdatePosition()).isEqualTo(-1L);
        });
  }

  @TestTemplate
  void shouldThrowExceptionWhenFlowNodeInstanceToUpdateAreNotFound(
      final ExporterConfiguration config, final SearchClientAdapter client) throws Exception {
    withIncidentUpdateTask(
        config,
        (job, resources) -> {
          final var listViewTemplate = resources.getIndexTemplateDescriptor(ListViewTemplate.class);

          final var processInstanceKey = ID_GENERATOR.getAndIncrement();
          final ProcessInstanceForListViewEntity processInstance =
              new ProcessInstanceForListViewEntity()
                  .setId(String.valueOf(processInstanceKey))
                  .setPartitionId(PARTITION_ID)
                  .setKey(processInstanceKey)
                  .setProcessDefinitionKey(9999L)
                  .setBpmnProcessId("process-1");
          store(listViewTemplate, client, processInstance);
          client.refresh(listViewTemplate.getFullQualifiedName());

          final var incidentTemplate = resources.getIndexTemplateDescriptor(IncidentTemplate.class);

          final var incidentKey = ID_GENERATOR.getAndIncrement();
          final IncidentEntity incidentEntity =
              new IncidentEntity()
                  .setId(String.valueOf(incidentKey))
                  .setPartitionId(PARTITION_ID)
                  .setKey(incidentKey)
                  .setErrorMessage("An error happened")
                  .setProcessInstanceKey(processInstanceKey)
                  .setFlowNodeInstanceKey(9999L);

          store(incidentTemplate, client, incidentEntity);
          client.refresh(incidentTemplate.getFullQualifiedName());

          final var postImporterTemplate =
              resources.getIndexTemplateDescriptor(PostImporterQueueTemplate.class);

          final PostImporterQueueEntity queueEntity =
              new PostImporterQueueEntity()
                  .setId("queue-1")
                  .setPartitionId(PARTITION_ID)
                  .setActionType(PostImporterActionType.INCIDENT)
                  .setIntent("CREATED")
                  .setKey(incidentKey)
                  .setPosition(1L);

          store(postImporterTemplate, client, queueEntity);
          client.refresh(postImporterTemplate.getFullQualifiedName());

          // when
          final var updated = job.execute();

          // then
          assertThat(updated)
              .failsWithin(UPDATE_TIMEOUT)
              .withThrowableThat()
              .withRootCauseInstanceOf(ExporterException.class)
              .withMessageContaining("Flow node instance 9999 affected by incident")
              .withMessageContaining(
                  "cannot be updated because there is no document for it in the list view index yet")
              .withMessageContaining(" this will be retried later");

          assertThat(exporterMetadata.getLastIncidentUpdatePosition()).isEqualTo(-1L);
        });
  }

  @TestTemplate
  void shouldUpdateRelevantEntities(
      final ExporterConfiguration config, final SearchClientAdapter client) throws Exception {
    withIncidentUpdateTask(
        config,
        (job, resources) -> {
          final var listViewTemplate = resources.getIndexTemplateDescriptor(ListViewTemplate.class);

          final var processInstanceKey = ID_GENERATOR.getAndIncrement();
          final var treePath = String.format("PI_%d/FN_callActivity1", processInstanceKey);
          final ProcessInstanceForListViewEntity processInstance =
              new ProcessInstanceForListViewEntity()
                  .setId(String.valueOf(processInstanceKey))
                  .setPartitionId(PARTITION_ID)
                  .setKey(processInstanceKey)
                  .setProcessDefinitionKey(9999L)
                  .setBpmnProcessId("process-1")
                  .setTreePath(treePath);
          store(listViewTemplate, client, processInstance);

          final var flowNodeInstanceKey = ID_GENERATOR.getAndIncrement();

          final FlowNodeInstanceForListViewEntity listViewFlowNodeInstance =
              new FlowNodeInstanceForListViewEntity()
                  .setId(String.valueOf(flowNodeInstanceKey))
                  .setPartitionId(PARTITION_ID)
                  .setKey(flowNodeInstanceKey)
                  .setProcessInstanceKey(processInstance.getKey());
          listViewFlowNodeInstance.getJoinRelation().setParent(processInstance.getKey());
          store(listViewTemplate, client, processInstance, listViewFlowNodeInstance);

          final var flowNodeInstanceTemplate =
              resources.getIndexTemplateDescriptor(FlowNodeInstanceTemplate.class);

          final FlowNodeInstanceEntity flowNodeInstance =
              new FlowNodeInstanceEntity()
                  .setId(String.valueOf(flowNodeInstanceKey))
                  .setPartitionId(PARTITION_ID)
                  .setKey(flowNodeInstanceKey)
                  .setProcessInstanceKey(processInstance.getKey());
          store(flowNodeInstanceTemplate, client, flowNodeInstance);

          client.refresh(listViewTemplate.getFullQualifiedName());
          client.refresh(flowNodeInstanceTemplate.getFullQualifiedName());

          final var incidentTemplate = resources.getIndexTemplateDescriptor(IncidentTemplate.class);

          final var incidentKey = ID_GENERATOR.getAndIncrement();
          final IncidentEntity incidentEntity =
              new IncidentEntity()
                  .setId(String.valueOf(incidentKey))
                  .setPartitionId(PARTITION_ID)
                  .setKey(incidentKey)
                  .setErrorMessage("An error happened")
                  .setProcessInstanceKey(processInstanceKey)
                  .setFlowNodeInstanceKey(flowNodeInstanceKey);

          store(incidentTemplate, client, incidentEntity);
          client.refresh(incidentTemplate.getFullQualifiedName());

          final var postImporterTemplate =
              resources.getIndexTemplateDescriptor(PostImporterQueueTemplate.class);

          final PostImporterQueueEntity queueEntity =
              new PostImporterQueueEntity()
                  .setId("queue-1")
                  .setPartitionId(PARTITION_ID)
                  .setActionType(PostImporterActionType.INCIDENT)
                  .setIntent("CREATED")
                  .setKey(incidentKey)
                  .setPosition(1L);

          store(postImporterTemplate, client, queueEntity);
          client.refresh(postImporterTemplate.getFullQualifiedName());

          // when
          final var updated = job.execute();

          // then
          assertThat(updated).succeedsWithin(UPDATE_TIMEOUT).isEqualTo(4);

          client.refresh(testPrefix);

          final var updatedProcessInstance =
              getFromIndex(listViewTemplate, client, processInstance);
          assertThat(updatedProcessInstance.isIncident()).isTrue();

          final var updatedListViewFlowNodeInstance =
              getChildFromIndex(
                  listViewTemplate, client, processInstance, listViewFlowNodeInstance);
          assertThat(updatedListViewFlowNodeInstance.isIncident()).isTrue();

          final var updatedFlowNodeInstance =
              getFromIndex(flowNodeInstanceTemplate, client, flowNodeInstance);
          assertThat(updatedFlowNodeInstance.isIncident()).isTrue();

          final var updatedIncident = getFromIndex(incidentTemplate, client, incidentEntity);
          assertThat(updatedIncident.getTreePath())
              .isEqualTo(String.format("%s/FNI_%d", treePath, flowNodeInstanceKey));

          assertThat(exporterMetadata.getLastIncidentUpdatePosition()).isEqualTo(1L);
        });
  }

  @TestTemplate
  void shouldUpdateRelevantEntitiesAndCreateSparseTreePath(
      final ExporterConfiguration config, final SearchClientAdapter client) throws Exception {
    withIncidentUpdateTask(
        config,
        (job, resources) -> {
          final var listViewTemplate = resources.getIndexTemplateDescriptor(ListViewTemplate.class);

          final var processInstanceKey = ID_GENERATOR.getAndIncrement();
          final ProcessInstanceForListViewEntity processInstance =
              new ProcessInstanceForListViewEntity()
                  .setId(String.valueOf(processInstanceKey))
                  .setPartitionId(PARTITION_ID)
                  .setKey(processInstanceKey)
                  .setProcessDefinitionKey(9999L)
                  .setBpmnProcessId("process-1");
          store(listViewTemplate, client, processInstance);

          final var flowNodeInstanceKey = ID_GENERATOR.getAndIncrement();

          final FlowNodeInstanceForListViewEntity listViewFlowNodeInstance =
              new FlowNodeInstanceForListViewEntity()
                  .setId(String.valueOf(flowNodeInstanceKey))
                  .setPartitionId(PARTITION_ID)
                  .setKey(flowNodeInstanceKey)
                  .setProcessInstanceKey(processInstance.getKey());
          listViewFlowNodeInstance.getJoinRelation().setParent(processInstance.getKey());
          store(listViewTemplate, client, processInstance, listViewFlowNodeInstance);

          final var flowNodeInstanceTemplate =
              resources.getIndexTemplateDescriptor(FlowNodeInstanceTemplate.class);

          final FlowNodeInstanceEntity flowNodeInstance =
              new FlowNodeInstanceEntity()
                  .setId(String.valueOf(flowNodeInstanceKey))
                  .setPartitionId(PARTITION_ID)
                  .setKey(flowNodeInstanceKey)
                  .setProcessInstanceKey(processInstance.getKey());
          store(flowNodeInstanceTemplate, client, flowNodeInstance);

          client.refresh(listViewTemplate.getFullQualifiedName());
          client.refresh(flowNodeInstanceTemplate.getFullQualifiedName());

          final var incidentTemplate = resources.getIndexTemplateDescriptor(IncidentTemplate.class);

          final var incidentKey = ID_GENERATOR.getAndIncrement();
          final IncidentEntity incidentEntity =
              new IncidentEntity()
                  .setId(String.valueOf(incidentKey))
                  .setPartitionId(PARTITION_ID)
                  .setKey(incidentKey)
                  .setErrorMessage("An error happened")
                  .setProcessInstanceKey(processInstanceKey)
                  .setFlowNodeInstanceKey(flowNodeInstanceKey);

          store(incidentTemplate, client, incidentEntity);
          client.refresh(incidentTemplate.getFullQualifiedName());

          final var postImporterTemplate =
              resources.getIndexTemplateDescriptor(PostImporterQueueTemplate.class);

          final PostImporterQueueEntity queueEntity =
              new PostImporterQueueEntity()
                  .setId("queue-1")
                  .setPartitionId(PARTITION_ID)
                  .setActionType(PostImporterActionType.INCIDENT)
                  .setIntent("CREATED")
                  .setKey(incidentKey)
                  .setPosition(1L);

          store(postImporterTemplate, client, queueEntity);
          client.refresh(postImporterTemplate.getFullQualifiedName());

          // when
          final var updated = job.execute();

          // then
          assertThat(updated).succeedsWithin(UPDATE_TIMEOUT).isEqualTo(4);

          client.refresh(testPrefix);

          final var updatedProcessInstance =
              getFromIndex(listViewTemplate, client, processInstance);
          assertThat(updatedProcessInstance.isIncident()).isTrue();

          final var updatedListViewFlowNodeInstance =
              getChildFromIndex(
                  listViewTemplate, client, processInstance, listViewFlowNodeInstance);
          assertThat(updatedListViewFlowNodeInstance.isIncident()).isTrue();

          final var updatedFlowNodeInstance =
              getFromIndex(flowNodeInstanceTemplate, client, flowNodeInstance);
          assertThat(updatedFlowNodeInstance.isIncident()).isTrue();

          final String sparseTreePath =
              String.format("PI_%d/FNI_%d", processInstanceKey, flowNodeInstanceKey);
          final var updatedIncident = getFromIndex(incidentTemplate, client, incidentEntity);
          assertThat(updatedIncident.getTreePath()).isEqualTo(sparseTreePath);

          assertThat(exporterMetadata.getLastIncidentUpdatePosition()).isEqualTo(1L);
        });
  }

  @TestTemplate
  void shouldUpdateRelevantEntitiesWhenIncidentIsProcessInstanceLevel(
      final ExporterConfiguration config, final SearchClientAdapter client) throws Exception {
    withIncidentUpdateTask(
        config,
        (job, resources) -> {
          final var listViewTemplate = resources.getIndexTemplateDescriptor(ListViewTemplate.class);

          final var processInstanceKey = ID_GENERATOR.getAndIncrement();
          final var treePath = String.format("PI_%d", processInstanceKey);
          final ProcessInstanceForListViewEntity processInstance =
              new ProcessInstanceForListViewEntity()
                  .setId(String.valueOf(processInstanceKey))
                  .setPartitionId(PARTITION_ID)
                  .setKey(processInstanceKey)
                  .setProcessDefinitionKey(9999L)
                  .setBpmnProcessId("process-1")
                  .setTreePath(treePath);
          store(listViewTemplate, client, processInstance);

          client.refresh(listViewTemplate.getFullQualifiedName());

          final var incidentTemplate = resources.getIndexTemplateDescriptor(IncidentTemplate.class);

          final var incidentKey = ID_GENERATOR.getAndIncrement();
          final IncidentEntity incidentEntity =
              new IncidentEntity()
                  .setId(String.valueOf(incidentKey))
                  .setPartitionId(PARTITION_ID)
                  .setKey(incidentKey)
                  .setErrorMessage("An error happened")
                  .setProcessInstanceKey(processInstanceKey)
                  .setFlowNodeInstanceKey(processInstanceKey);

          store(incidentTemplate, client, incidentEntity);
          client.refresh(incidentTemplate.getFullQualifiedName());

          final var postImporterTemplate =
              resources.getIndexTemplateDescriptor(PostImporterQueueTemplate.class);

          final PostImporterQueueEntity queueEntity =
              new PostImporterQueueEntity()
                  .setId("queue-1")
                  .setPartitionId(PARTITION_ID)
                  .setActionType(PostImporterActionType.INCIDENT)
                  .setIntent("CREATED")
                  .setKey(incidentKey)
                  .setPosition(1L);

          store(postImporterTemplate, client, queueEntity);
          client.refresh(postImporterTemplate.getFullQualifiedName());

          // when
          final var updated = job.execute();

          // then
          assertThat(updated).succeedsWithin(UPDATE_TIMEOUT).isEqualTo(2);

          client.refresh(testPrefix);

          final var updatedProcessInstance =
              getFromIndex(listViewTemplate, client, processInstance);
          assertThat(updatedProcessInstance.isIncident()).isTrue();

          final var updatedIncident = getFromIndex(incidentTemplate, client, incidentEntity);
          assertThat(updatedIncident.getTreePath())
              .isEqualTo(String.format("%s/FNI_%d", treePath, processInstanceKey));

          assertThat(exporterMetadata.getLastIncidentUpdatePosition()).isEqualTo(1L);
        });
  }

  @TestTemplate
  void shouldUpdateRelevantEntities2(
      final ExporterConfiguration config, final SearchClientAdapter client) throws Exception {
    withIncidentUpdateTask(
        config,
        (job, resources) -> {
          final var listViewTemplate = resources.getIndexTemplateDescriptor(ListViewTemplate.class);

          final var processInstanceKey = ID_GENERATOR.getAndIncrement();
          final var treePath = String.format("PI_%d/FN_callActivity1", processInstanceKey);
          final ProcessInstanceForListViewEntity processInstance =
              new ProcessInstanceForListViewEntity()
                  .setId(String.valueOf(processInstanceKey))
                  .setPartitionId(PARTITION_ID)
                  .setKey(processInstanceKey)
                  .setProcessDefinitionKey(9999L)
                  .setBpmnProcessId("process-1")
                  .setTreePath(treePath)
                  .setIncident(true);
          store(listViewTemplate, client, processInstance);

          final var flowNodeInstanceKey = ID_GENERATOR.getAndIncrement();

          final FlowNodeInstanceForListViewEntity listViewFlowNodeInstance =
              new FlowNodeInstanceForListViewEntity()
                  .setId(String.valueOf(flowNodeInstanceKey))
                  .setPartitionId(PARTITION_ID)
                  .setKey(flowNodeInstanceKey)
                  .setProcessInstanceKey(processInstance.getKey())
                  .setIncident(true);
          listViewFlowNodeInstance.getJoinRelation().setParent(processInstance.getKey());
          store(listViewTemplate, client, processInstance, listViewFlowNodeInstance);

          final var flowNodeInstanceTemplate =
              resources.getIndexTemplateDescriptor(FlowNodeInstanceTemplate.class);

          final FlowNodeInstanceEntity flowNodeInstance =
              new FlowNodeInstanceEntity()
                  .setId(String.valueOf(flowNodeInstanceKey))
                  .setPartitionId(PARTITION_ID)
                  .setKey(flowNodeInstanceKey)
                  .setProcessInstanceKey(processInstance.getKey())
                  .setIncident(true);
          store(flowNodeInstanceTemplate, client, flowNodeInstance);

          client.refresh(listViewTemplate.getFullQualifiedName());
          client.refresh(flowNodeInstanceTemplate.getFullQualifiedName());

          final var incidentTemplate = resources.getIndexTemplateDescriptor(IncidentTemplate.class);

          final var incidentKey = ID_GENERATOR.getAndIncrement();
          final IncidentEntity incidentEntity =
              new IncidentEntity()
                  .setId(String.valueOf(incidentKey))
                  .setPartitionId(PARTITION_ID)
                  .setKey(incidentKey)
                  .setState(IncidentState.ACTIVE)
                  .setErrorMessage("An error happened")
                  .setProcessInstanceKey(processInstanceKey)
                  .setFlowNodeInstanceKey(flowNodeInstanceKey)
                  .setTreePath(String.format("%s/FNI_%d", treePath, flowNodeInstanceKey));

          store(incidentTemplate, client, incidentEntity);
          client.refresh(incidentTemplate.getFullQualifiedName());

          final var postImporterTemplate =
              resources.getIndexTemplateDescriptor(PostImporterQueueTemplate.class);

          final PostImporterQueueEntity queueEntity =
              new PostImporterQueueEntity()
                  .setId("queue-1")
                  .setPartitionId(PARTITION_ID)
                  .setActionType(PostImporterActionType.INCIDENT)
                  .setIntent("RESOLVED")
                  .setKey(incidentKey)
                  .setPosition(1L);

          store(postImporterTemplate, client, queueEntity);
          client.refresh(postImporterTemplate.getFullQualifiedName());

          // when
          final var updated = job.execute();

          // then
          assertThat(updated).succeedsWithin(UPDATE_TIMEOUT).isEqualTo(4);

          client.refresh(testPrefix);

          final var updatedProcessInstance =
              getFromIndex(listViewTemplate, client, processInstance);
          assertThat(updatedProcessInstance.isIncident()).isFalse();

          final var updatedListViewFlowNodeInstance =
              getChildFromIndex(
                  listViewTemplate, client, processInstance, listViewFlowNodeInstance);
          assertThat(updatedListViewFlowNodeInstance.isIncident()).isFalse();

          final var updatedFlowNodeInstance =
              getFromIndex(flowNodeInstanceTemplate, client, flowNodeInstance);
          assertThat(updatedFlowNodeInstance.isIncident()).isFalse();

          assertThat(exporterMetadata.getLastIncidentUpdatePosition()).isEqualTo(1L);
        });
  }

  void withIncidentUpdateTask(
      final ExporterConfiguration config, final IncidentUpdateTaskConsumer taskConsumer)
      throws Exception {
    config.getConnect().setIndexPrefix(testPrefix);
    config.getIndex().setNumberOfShards(3);
    config.getIndex().setNumberOfReplicas(0);
    createSchemas(config);

    final ExporterResourceProvider exporterResourceProvider = exporterResourceProvider(config);

    final var repository = createIncidentUpdateRepository(config, exporterResourceProvider);

    try (final IncidentUpdateTask task =
        new IncidentUpdateTask(
            exporterMetadata,
            repository,
            false,
            BATCH_SIZE,
            executor,
            incidentNotifier,
            exporterMetrics,
            LOGGER,
            Duration.ofSeconds(1))) {
      taskConsumer.accept(task, exporterResourceProvider);
    }
  }

  private <T extends ExporterEntity<T>> T getFromIndex(
      final IndexTemplateDescriptor templateDescriptor,
      final SearchClientAdapter client,
      final T entity)
      throws IOException {
    return client.get(
        entity.getId(), templateDescriptor.getFullQualifiedName(), (Class<T>) entity.getClass());
  }

  private <T extends ExporterEntity<T>> T getChildFromIndex(
      final IndexTemplateDescriptor templateDescriptor,
      final SearchClientAdapter client,
      final ExporterEntity<?> parent,
      final T entity)
      throws IOException {
    return client.get(
        entity.getId(),
        parent.getId(),
        templateDescriptor.getFullQualifiedName(),
        (Class<T>) entity.getClass());
  }

  private void store(
      final IndexDescriptor indexDescriptor,
      final SearchClientAdapter client,
      final ExporterEntity<?> entity)
      throws IOException {
    client.index(entity.getId(), indexDescriptor.getFullQualifiedName(), entity);
  }

  private void store(
      final IndexDescriptor indexDescriptor,
      final SearchClientAdapter client,
      final ExporterEntity<?> parent,
      final ExporterEntity<?> child)
      throws IOException {
    client.index(child.getId(), parent.getId(), indexDescriptor.getFullQualifiedName(), child);
  }

  private ExporterResourceProvider exporterResourceProvider(final ExporterConfiguration config) {
    final var cacheProvider = mock(ExporterEntityCacheProvider.class);
    when(cacheProvider.getProcessCacheLoader(anyString())).thenReturn(k -> null);
    when(cacheProvider.getBatchOperationCacheLoader(anyString())).thenReturn(k -> null);
    when(cacheProvider.getFormCacheLoader(anyString())).thenReturn(k -> null);
    final var resourceProvider = new DefaultExporterResourceProvider();
    resourceProvider.init(
        config,
        cacheProvider,
        context.getMeterRegistry(),
        new ExporterMetadata(TestObjectMapper.objectMapper()),
        TestObjectMapper.objectMapper());
    return resourceProvider;
  }

  private IncidentUpdateRepository createIncidentUpdateRepository(
      final ExporterConfiguration config, final ExporterResourceProvider resourceProvider) {

    final var listViewTemplate =
        resourceProvider.getIndexTemplateDescriptor(ListViewTemplate.class);
    final var flowNodeTemplate =
        resourceProvider.getIndexTemplateDescriptor(FlowNodeInstanceTemplate.class);
    final var incidentTemplate =
        resourceProvider.getIndexTemplateDescriptor(IncidentTemplate.class);
    final var postImporterTemplate =
        resourceProvider.getIndexTemplateDescriptor(PostImporterQueueTemplate.class);
    final var operationTemplate =
        resourceProvider.getIndexTemplateDescriptor(OperationTemplate.class);
    final var isElasticsearch = ConnectionTypes.isElasticSearch(config.getConnect().getType());
    final var importPositionIndex =
        new ImportPositionIndex(config.getConnect().getIndexPrefix(), isElasticsearch);
    if (isElasticsearch) {
      return closeLater(
          new ElasticsearchIncidentUpdateRepository(
              PARTITION_ID,
              postImporterTemplate.getAlias(),
              postImporterTemplate.getFullQualifiedName(),
              incidentTemplate.getAlias(),
              listViewTemplate.getAlias(),
              listViewTemplate.getFullQualifiedName(),
              flowNodeTemplate.getAlias(),
              operationTemplate.getAlias(),
              importPositionIndex.getFullQualifiedName(),
              createAsyncESClient(config),
              executor,
              LOGGER));
    } else {
      return closeLater(
          new OpenSearchIncidentUpdateRepository(
              PARTITION_ID,
              postImporterTemplate.getAlias(),
              postImporterTemplate.getFullQualifiedName(),
              incidentTemplate.getAlias(),
              listViewTemplate.getAlias(),
              listViewTemplate.getFullQualifiedName(),
              flowNodeTemplate.getAlias(),
              operationTemplate.getAlias(),
              importPositionIndex.getFullQualifiedName(),
              createOSAsyncClient(config),
              executor,
              LOGGER));
    }
  }

  protected ElasticsearchAsyncClient createAsyncESClient(final ExporterConfiguration config) {
    final var connector = new ElasticsearchConnector(config.getConnect());
    return connector.createAsyncClient();
  }

  protected OpenSearchAsyncClient createOSAsyncClient(final ExporterConfiguration config) {
    final var connector = new OpensearchConnector(config.getConnect());
    return connector.createAsyncClient();
  }

  protected <R extends AutoCloseable> R closeLater(final R resource) {
    resourcesToClose.add(resource);
    return resource;
  }

  interface IncidentUpdateTaskConsumer {
    void accept(IncidentUpdateTask task, ExporterResourceProvider exporterResourceProvider)
        throws Exception;
  }
}
