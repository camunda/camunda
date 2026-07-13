/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.incident;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.camunda.exporter.ExporterMetadata;
import io.camunda.exporter.ExporterResourceProvider;
import io.camunda.exporter.config.ConnectionTypes;
import io.camunda.exporter.config.ExporterConfiguration;
import io.camunda.exporter.notifier.IncidentNotifier;
import io.camunda.exporter.tasks.BackgroundTaskIT;
import io.camunda.search.test.utils.SearchClientAdapter;
import io.camunda.search.test.utils.TestObjectMapper;
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
import java.io.IOException;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;

class IncidentUpdateTaskIT extends BackgroundTaskIT<IncidentUpdateTask> {
  private static final int BATCH_SIZE = 10;

  private ExporterMetadata exporterMetadata;
  private IncidentNotifier incidentNotifier;

  @Override
  @BeforeEach
  protected void setup() {
    super.setup();
    incidentNotifier = mock(IncidentNotifier.class);
    exporterMetadata = new ExporterMetadata(TestObjectMapper.objectMapper());
  }

  @Override
  @TestTemplate
  protected void shouldExecuteWithoutErrorsWhenNothingToDo(
      final ExporterConfiguration config, final SearchClientAdapter ignored) throws Exception {
    super.shouldExecuteWithoutErrorsWhenNothingToDo(config, ignored);

    assertThat(exporterMetadata.getLastIncidentUpdatePosition()).isEqualTo(-1L);
  }

  @Override
  protected IncidentUpdateTask createBackgroundTask(
      final ExporterConfiguration config, final ExporterResourceProvider resourceProvider) {
    final var repository = createIncidentUpdateRepository(config, resourceProvider);

    return new IncidentUpdateTask(
        exporterMetadata,
        repository,
        false,
        BATCH_SIZE,
        executor,
        incidentNotifier,
        exporterMetrics,
        LOGGER,
        Duration.ofSeconds(1));
  }

  @TestTemplate
  void shouldThrowExceptionWhenIncidentsToUpdateAreNotFound(
      final ExporterConfiguration config, final SearchClientAdapter client) throws Exception {
    withTask(
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
              .failsWithin(EXECUTE_TIMEOUT)
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
    withTask(
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
          assertThat(updated).succeedsWithin(EXECUTE_TIMEOUT).isEqualTo(1);

          assertThat(exporterMetadata.getLastIncidentUpdatePosition()).isEqualTo(-1L);
        });
  }

  @TestTemplate
  void shouldThrowExceptionWhenFlowNodeInstanceToUpdateAreNotFound(
      final ExporterConfiguration config, final SearchClientAdapter client) throws Exception {
    withTask(
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
              .failsWithin(EXECUTE_TIMEOUT)
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
    withTask(
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
                  .setProcessInstanceKey(processInstance.getKey())
                  .setRootProcessInstanceKey(processInstance.getKey());
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
                  .setRootProcessInstanceKey(processInstance.getKey());
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
          assertThat(updated).succeedsWithin(EXECUTE_TIMEOUT).isEqualTo(4);

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
          assertThat(updatedIncident.getState()).isEqualTo(IncidentState.ACTIVE);

          assertThat(exporterMetadata.getLastIncidentUpdatePosition()).isEqualTo(1L);
        });
  }

  @TestTemplate
  void shouldUpdateRelevantEntitiesAndCreateSparseTreePath(
      final ExporterConfiguration config, final SearchClientAdapter client) throws Exception {
    withTask(
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
                  .setProcessInstanceKey(processInstance.getKey())
                  .setRootProcessInstanceKey(processInstance.getKey());
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
                  .setRootProcessInstanceKey(processInstance.getKey());
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
          assertThat(updated).succeedsWithin(EXECUTE_TIMEOUT).isEqualTo(4);

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
          assertThat(updatedIncident.getState()).isEqualTo(IncidentState.ACTIVE);

          assertThat(exporterMetadata.getLastIncidentUpdatePosition()).isEqualTo(1L);
        });
  }

  @TestTemplate
  void shouldUpdateRelevantEntitiesWhenIncidentIsProcessInstanceLevel(
      final ExporterConfiguration config, final SearchClientAdapter client) throws Exception {
    withTask(
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
          assertThat(updated).succeedsWithin(EXECUTE_TIMEOUT).isEqualTo(2);

          client.refresh(testPrefix);

          final var updatedProcessInstance =
              getFromIndex(listViewTemplate, client, processInstance);
          assertThat(updatedProcessInstance.isIncident()).isTrue();

          final var updatedIncident = getFromIndex(incidentTemplate, client, incidentEntity);
          assertThat(updatedIncident.getState()).isEqualTo(IncidentState.ACTIVE);
          assertThat(updatedIncident.getTreePath())
              .isEqualTo(String.format("%s/FNI_%d", treePath, processInstanceKey));

          assertThat(exporterMetadata.getLastIncidentUpdatePosition()).isEqualTo(1L);
        });
  }

  @TestTemplate
  void shouldUpdateRelevantEntitiesForResolvedIncident(
      final ExporterConfiguration config, final SearchClientAdapter client) throws Exception {
    withTask(
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
                  .setRootProcessInstanceKey(processInstance.getKey())
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
                  .setRootProcessInstanceKey(processInstance.getKey())
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
          assertThat(updated).succeedsWithin(EXECUTE_TIMEOUT).isEqualTo(4);

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

          final var updatedIncident = getFromIndex(incidentTemplate, client, incidentEntity);
          assertThat(updatedIncident.getState()).isEqualTo(IncidentState.RESOLVED);

          assertThat(exporterMetadata.getLastIncidentUpdatePosition()).isEqualTo(1L);
        });
  }

  @TestTemplate
  void shouldResolveIncidentButNotUnsetFlagsWhenOtherIncidentsStillActive(
      final ExporterConfiguration config, final SearchClientAdapter client) throws Exception {
    withTask(
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
                  .setRootProcessInstanceKey(processInstance.getKey())
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
                  .setRootProcessInstanceKey(processInstance.getKey())
                  .setIncident(true);
          store(flowNodeInstanceTemplate, client, flowNodeInstance);

          client.refresh(listViewTemplate.getFullQualifiedName());
          client.refresh(flowNodeInstanceTemplate.getFullQualifiedName());

          final var incidentTemplate = resources.getIndexTemplateDescriptor(IncidentTemplate.class);

          final var activeIncidentKey = ID_GENERATOR.getAndIncrement();
          final IncidentEntity activeIncidentEntity =
              new IncidentEntity()
                  .setId(String.valueOf(activeIncidentKey))
                  .setPartitionId(PARTITION_ID)
                  .setKey(activeIncidentKey)
                  .setState(IncidentState.ACTIVE)
                  .setErrorMessage("An error happened")
                  .setProcessInstanceKey(processInstanceKey)
                  .setFlowNodeInstanceKey(flowNodeInstanceKey)
                  .setTreePath(String.format("%s/FNI_%d", treePath, flowNodeInstanceKey));

          store(incidentTemplate, client, activeIncidentEntity);

          final var resolvedIncidentKey = ID_GENERATOR.getAndIncrement();
          final IncidentEntity resolvedIncidentEntity =
              new IncidentEntity()
                  .setId(String.valueOf(resolvedIncidentKey))
                  .setPartitionId(PARTITION_ID)
                  .setKey(resolvedIncidentKey)
                  .setState(IncidentState.ACTIVE)
                  .setErrorMessage("An error happened")
                  .setProcessInstanceKey(processInstanceKey)
                  .setFlowNodeInstanceKey(flowNodeInstanceKey)
                  .setTreePath(String.format("%s/FNI_%d", treePath, flowNodeInstanceKey));

          store(incidentTemplate, client, resolvedIncidentEntity);
          client.refresh(incidentTemplate.getFullQualifiedName());

          final var postImporterTemplate =
              resources.getIndexTemplateDescriptor(PostImporterQueueTemplate.class);

          final PostImporterQueueEntity queueEntity =
              new PostImporterQueueEntity()
                  .setId("queue-1")
                  .setPartitionId(PARTITION_ID)
                  .setActionType(PostImporterActionType.INCIDENT)
                  .setIntent("RESOLVED")
                  .setKey(resolvedIncidentKey)
                  .setPosition(1L);

          store(postImporterTemplate, client, queueEntity);
          client.refresh(postImporterTemplate.getFullQualifiedName());

          // when
          final var updated = job.execute();

          // then
          assertThat(updated).succeedsWithin(EXECUTE_TIMEOUT).isEqualTo(1);

          client.refresh(testPrefix);

          // still have an active incident, so the flags are not unset
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

          // but the incident itself will have been marked as resolved
          final var updatedResolvedIncident =
              getFromIndex(incidentTemplate, client, resolvedIncidentEntity);
          assertThat(updatedResolvedIncident.getState()).isEqualTo(IncidentState.RESOLVED);

          final var updatedActiveIncident =
              getFromIndex(incidentTemplate, client, activeIncidentEntity);
          assertThat(updatedActiveIncident.getState()).isEqualTo(IncidentState.ACTIVE);

          assertThat(exporterMetadata.getLastIncidentUpdatePosition()).isEqualTo(1L);
        });
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
}
