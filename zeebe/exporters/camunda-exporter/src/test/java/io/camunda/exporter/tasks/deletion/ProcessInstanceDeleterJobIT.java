/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.deletion;

import static io.camunda.zeebe.protocol.record.value.StorageOrdinalRelated.MAIN_INDEX;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.exporter.ExporterResourceProvider;
import io.camunda.exporter.config.ConnectionTypes;
import io.camunda.exporter.config.ExporterConfiguration;
import io.camunda.exporter.index.TargetIndexLocator;
import io.camunda.exporter.tasks.BackgroundTaskIT;
import io.camunda.search.test.utils.SearchClientAdapter;
import io.camunda.webapps.schema.descriptors.IndexTemplateDescriptor;
import io.camunda.webapps.schema.descriptors.ProcessInstanceDependant;
import io.camunda.webapps.schema.descriptors.template.ListViewTemplate;
import io.camunda.webapps.schema.entities.ExporterEntity;
import io.camunda.webapps.schema.entities.listview.ProcessInstanceForListViewEntity;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.TestTemplate;

@TestInstance(Lifecycle.PER_CLASS)
class ProcessInstanceDeleterJobIT extends BackgroundTaskIT<ProcessInstanceDeleterJob> {
  private static final int STORAGE_ORDINAL1 = 1;
  private static final int STORAGE_ORDINAL2 = 2;

  @TestTemplate
  void shouldDeleteLoneProcessInstance(
      final ExporterConfiguration config, final SearchClientAdapter client) throws Exception {
    withTask(
        config,
        (job, resourceProvider) -> {
          // given
          final var listViewTemplate =
              resourceProvider.getIndexTemplateDescriptor(ListViewTemplate.class);

          final ProcessInstanceForListViewEntity processInstance =
              processInstanceForListViewEntity("2020-01-01T00:00:00+00:00", STORAGE_ORDINAL1);

          store(listViewTemplate, STORAGE_ORDINAL1, client, processInstance);

          verifyExists(listViewTemplate, STORAGE_ORDINAL1, client, processInstance);

          client.refresh(testPrefix);

          // when
          final var deleted = job.execute();

          // then
          assertThat(deleted).succeedsWithin(EXECUTE_TIMEOUT).isEqualTo(1);

          // check that the process is no longer in the index
          verifyDeleted(listViewTemplate, STORAGE_ORDINAL1, client, processInstance);
        });
  }

  @TestTemplate
  void shouldDeleteFromOneOrdinalAtATime(
      final ExporterConfiguration config, final SearchClientAdapter client) throws Exception {
    withTask(
        config,
        (job, resourceProvider) -> {
          // given
          final var listViewTemplate =
              resourceProvider.getIndexTemplateDescriptor(ListViewTemplate.class);

          final ProcessInstanceForListViewEntity processInstance1 =
              processInstanceForListViewEntity("2020-01-01T00:00:00+00:00", STORAGE_ORDINAL1);
          final ProcessInstanceForListViewEntity processInstance2 =
              processInstanceForListViewEntity("2020-01-01T00:00:00+00:00", STORAGE_ORDINAL2);

          store(listViewTemplate, STORAGE_ORDINAL1, client, processInstance1);
          store(listViewTemplate, STORAGE_ORDINAL2, client, processInstance2);

          verifyExists(listViewTemplate, STORAGE_ORDINAL1, client, processInstance1);
          verifyExists(listViewTemplate, STORAGE_ORDINAL2, client, processInstance2);

          client.refresh(testPrefix);

          // when
          final var deleted1 = job.execute();

          // then
          assertThat(deleted1).succeedsWithin(EXECUTE_TIMEOUT).isEqualTo(1);

          // check that the process is no longer in the index
          verifyDeleted(listViewTemplate, STORAGE_ORDINAL1, client, processInstance1);
          // but the other process instance should still exist
          verifyExists(listViewTemplate, STORAGE_ORDINAL2, client, processInstance2);

          // now run the job again to delete the second process instance
          // given
          client.refresh(testPrefix);

          // when
          final var deleted2 = job.execute();

          // then
          assertThat(deleted2).succeedsWithin(EXECUTE_TIMEOUT).isEqualTo(1);

          // now both should be gone
          verifyDeleted(listViewTemplate, STORAGE_ORDINAL1, client, processInstance1);
        });
  }

  @TestTemplate
  void shouldOnlyDeleteOneBatchAtATime(
      final ExporterConfiguration config, final SearchClientAdapter client) throws Exception {
    final int batchSize = 5;
    config.getHistory().getRetention().getDeleter().setEntityBatchSize(batchSize);
    withTask(
        config,
        (job, resourceProvider) -> {
          // given
          final var listViewTemplate =
              resourceProvider.getIndexTemplateDescriptor(ListViewTemplate.class);

          final List<ProcessInstanceForListViewEntity> processInstances = new ArrayList<>();
          for (var i = 0; i < batchSize; i++) {
            processInstances.add(
                processInstanceForListViewEntity("2020-01-01T00:00:00+00:00", STORAGE_ORDINAL1));
          }
          // slightly later end date, so should not be in first batch
          processInstances.add(
              processInstanceForListViewEntity("2020-01-01T00:00:10+00:00", STORAGE_ORDINAL1));

          for (final var processInstance : processInstances) {
            store(listViewTemplate, STORAGE_ORDINAL1, client, processInstance);
          }

          client.refresh(testPrefix);

          // when
          final var deleted = job.execute();

          // then
          assertThat(deleted).succeedsWithin(EXECUTE_TIMEOUT).isEqualTo(5);

          // check that the first batch of processes are no longer in the index
          for (var i = 0; i < batchSize; i++) {
            verifyDeleted(listViewTemplate, STORAGE_ORDINAL1, client, processInstances.get(i));
          }
          // but the last doc is still there
          verifyExists(listViewTemplate, STORAGE_ORDINAL1, client, processInstances.getLast());
        });
  }

  @TestTemplate
  void shouldOnlyDeleteFinishedProcessInstances(
      final ExporterConfiguration config, final SearchClientAdapter client) throws Exception {
    withTask(
        config,
        (job, resourceProvider) -> {
          // given
          final var listViewTemplate =
              resourceProvider.getIndexTemplateDescriptor(ListViewTemplate.class);

          final ProcessInstanceForListViewEntity finishedInstance =
              processInstanceForListViewEntity("2020-01-01T00:00:00+00:00", STORAGE_ORDINAL1);
          final ProcessInstanceForListViewEntity unfinishedInstance =
              processInstanceForListViewEntity(null, STORAGE_ORDINAL1);

          store(listViewTemplate, STORAGE_ORDINAL1, client, finishedInstance);
          store(listViewTemplate, STORAGE_ORDINAL1, client, unfinishedInstance);

          client.refresh(testPrefix);

          // when
          final var deleted = job.execute();

          // then
          assertThat(deleted).succeedsWithin(EXECUTE_TIMEOUT).isEqualTo(1);

          // check that the finished process is no longer in the index
          verifyDeleted(listViewTemplate, STORAGE_ORDINAL1, client, finishedInstance);
          verifyExists(listViewTemplate, STORAGE_ORDINAL1, client, unfinishedInstance);
        });
  }

  @TestTemplate
  void shouldOnlyDeleteProcessInstancesCompletedRetentionPeriod(
      final ExporterConfiguration config, final SearchClientAdapter client) throws Exception {
    withTask(
        config,
        (job, resourceProvider) -> {
          // given
          final var listViewTemplate =
              resourceProvider.getIndexTemplateDescriptor(ListViewTemplate.class);

          final ProcessInstanceForListViewEntity finishedInstance =
              processInstanceForListViewEntity("2020-01-01T00:00:00+00:00", STORAGE_ORDINAL1);
          final ProcessInstanceForListViewEntity notOldEnoughInstance =
              processInstanceForListViewEntity("2099-01-01T00:00:00+00:00", STORAGE_ORDINAL1);

          store(listViewTemplate, STORAGE_ORDINAL1, client, finishedInstance);
          store(listViewTemplate, STORAGE_ORDINAL1, client, notOldEnoughInstance);

          client.refresh(testPrefix);

          // when
          final var deleted = job.execute();

          // then
          assertThat(deleted).succeedsWithin(EXECUTE_TIMEOUT).isEqualTo(1);

          // check that the finished process is no longer in the index
          verifyDeleted(listViewTemplate, STORAGE_ORDINAL1, client, finishedInstance);
          verifyExists(listViewTemplate, STORAGE_ORDINAL1, client, notOldEnoughInstance);
        });
  }

  @TestTemplate
  void shouldDeleteProcessInstanceAndDependentChildListViewEntities(
      final ExporterConfiguration config, final SearchClientAdapter client) throws Exception {
    withTask(
        config,
        (job, resourceProvider) -> {
          // given
          final var listViewTemplate =
              resourceProvider.getIndexTemplateDescriptor(ListViewTemplate.class);

          final ProcessInstanceForListViewEntity processInstance =
              processInstanceForListViewEntity("2020-01-01T00:00:00+00:00", STORAGE_ORDINAL1);

          final List<ExporterEntity<?>> childEntities =
              List.of(
                  flowNodeInstanceForListViewEntity(processInstance),
                  flowNodeInstanceForListViewEntity(processInstance),
                  variableForListViewEntity(processInstance));

          store(listViewTemplate, STORAGE_ORDINAL1, client, processInstance);
          for (final var child : childEntities) {
            store(listViewTemplate, STORAGE_ORDINAL1, client, processInstance, child);
          }

          client.refresh(testPrefix);

          // when
          final var deleted = job.execute();

          // then
          assertThat(deleted).succeedsWithin(EXECUTE_TIMEOUT).isEqualTo(1);

          // check that the process is no longer in the index
          verifyDeleted(listViewTemplate, STORAGE_ORDINAL1, client, processInstance);
          for (final var child : childEntities) {
            verifyDeleted(listViewTemplate, STORAGE_ORDINAL1, client, processInstance, child);
          }
        });
  }

  @TestTemplate
  void shouldDeleteProcessInstanceAndLargeNumberOfDependentChildListViewEntities(
      final ExporterConfiguration config, final SearchClientAdapter client) throws Exception {
    // set smaller delete batch size to verify things work when multiple batches are needed
    config.getHistory().getRetention().getDeleter().setDocumentBatchSize(10);
    withTask(
        config,
        (job, resourceProvider) -> {
          // given
          final var listViewTemplate =
              resourceProvider.getIndexTemplateDescriptor(ListViewTemplate.class);

          final ProcessInstanceForListViewEntity processInstance =
              processInstanceForListViewEntity("2020-01-01T00:00:00+00:00", STORAGE_ORDINAL1);

          final List<ExporterEntity<?>> childEntities = new ArrayList<>();
          for (var i = 0; i < 90; i++) {
            childEntities.add(flowNodeInstanceForListViewEntity(processInstance));
            childEntities.add(variableForListViewEntity(processInstance));
          }

          store(listViewTemplate, STORAGE_ORDINAL1, client, processInstance);
          for (final var child : childEntities) {
            store(listViewTemplate, STORAGE_ORDINAL1, client, processInstance, child);
          }

          client.refresh(testPrefix);

          // when
          final var deleted = job.execute();

          // then
          assertThat(deleted).succeedsWithin(EXECUTE_TIMEOUT).isEqualTo(1);

          // check that the process is no longer in the index
          verifyDeleted(listViewTemplate, STORAGE_ORDINAL1, client, processInstance);
          for (final var child : childEntities) {
            verifyDeleted(listViewTemplate, STORAGE_ORDINAL1, client, processInstance, child);
          }
        });
  }

  @TestTemplate
  void shouldOnlyDeleteFinishedProcessInstanceAndDependentChildListViewEntities(
      final ExporterConfiguration config, final SearchClientAdapter client) throws Exception {
    withTask(
        config,
        (job, resourceProvider) -> {
          // given
          final var listViewTemplate =
              resourceProvider.getIndexTemplateDescriptor(ListViewTemplate.class);

          final ProcessInstanceForListViewEntity finishedInstance =
              processInstanceForListViewEntity("2020-01-01T00:00:00+00:00", STORAGE_ORDINAL1);

          final List<ExporterEntity<?>> finishedChildEntities =
              List.of(
                  flowNodeInstanceForListViewEntity(finishedInstance),
                  flowNodeInstanceForListViewEntity(finishedInstance),
                  variableForListViewEntity(finishedInstance));

          store(listViewTemplate, STORAGE_ORDINAL1, client, finishedInstance);
          for (final var child : finishedChildEntities) {
            store(listViewTemplate, STORAGE_ORDINAL1, client, finishedInstance, child);
          }

          final ProcessInstanceForListViewEntity unfinishedInstance =
              processInstanceForListViewEntity(null);

          final List<ExporterEntity<?>> unfinishedChildEntities =
              List.of(
                  flowNodeInstanceForListViewEntity(unfinishedInstance),
                  flowNodeInstanceForListViewEntity(unfinishedInstance),
                  variableForListViewEntity(unfinishedInstance));

          store(listViewTemplate, STORAGE_ORDINAL1, client, unfinishedInstance);
          for (final var child : unfinishedChildEntities) {
            store(listViewTemplate, STORAGE_ORDINAL1, client, unfinishedInstance, child);
          }

          client.refresh(testPrefix);

          // when
          final var delete = job.execute();

          // then
          assertThat(delete).succeedsWithin(EXECUTE_TIMEOUT).isEqualTo(1);

          // check that the process is no longer in the index
          verifyDeleted(listViewTemplate, STORAGE_ORDINAL1, client, finishedInstance);
          for (final var child : finishedChildEntities) {
            verifyDeleted(listViewTemplate, STORAGE_ORDINAL1, client, finishedInstance, child);
          }

          verifyExists(listViewTemplate, STORAGE_ORDINAL1, client, unfinishedInstance);
          for (final var child : unfinishedChildEntities) {
            verifyExists(listViewTemplate, STORAGE_ORDINAL1, client, unfinishedInstance, child);
          }
        });
  }

  @TestTemplate
  void shouldOnlyDeleteFinishedProcessInstanceAndProcessInstanceDependentEntities(
      final ExporterConfiguration config, final SearchClientAdapter client) throws Exception {
    withTask(
        config,
        (job, resourceProvider) -> {
          // given
          final var listViewTemplate =
              resourceProvider.getIndexTemplateDescriptor(ListViewTemplate.class);

          final ProcessInstanceForListViewEntity finishedInstance =
              processInstanceForListViewEntity("2020-01-01T00:00:00+00:00", STORAGE_ORDINAL1);

          final var finishedInstanceDependent =
              getProcessInstanceDependentEntities(resourceProvider, finishedInstance);

          store(listViewTemplate, STORAGE_ORDINAL1, client, finishedInstance);
          for (final var dependent : finishedInstanceDependent) {
            for (final var entity : dependent.entities()) {
              store(dependent.template(), STORAGE_ORDINAL1, client, entity);
            }
          }

          final ProcessInstanceForListViewEntity unfinishedInstance =
              processInstanceForListViewEntity(null, STORAGE_ORDINAL1);
          final var unfinishedInstanceDependent =
              getProcessInstanceDependentEntities(resourceProvider, unfinishedInstance);

          store(listViewTemplate, STORAGE_ORDINAL1, client, unfinishedInstance);
          for (final var dependent : unfinishedInstanceDependent) {
            for (final var entity : dependent.entities()) {
              store(dependent.template(), STORAGE_ORDINAL1, client, entity);
            }
          }

          client.refresh(testPrefix);

          // when
          final var deleted = job.execute();

          // then
          assertThat(deleted).succeedsWithin(EXECUTE_TIMEOUT).isEqualTo(1);

          // check that the process is no longer in the index
          verifyDeleted(listViewTemplate, STORAGE_ORDINAL1, client, finishedInstance);
          for (final var dependent : finishedInstanceDependent) {
            for (final var entity : dependent.entities()) {
              verifyDeleted(dependent.template(), STORAGE_ORDINAL1, client, entity);
            }
          }

          verifyExists(listViewTemplate, STORAGE_ORDINAL1, client, unfinishedInstance);
          for (final var dependent : unfinishedInstanceDependent) {
            for (final var entity : dependent.entities()) {
              verifyExists(dependent.template(), STORAGE_ORDINAL1, client, entity);
            }
          }
        });
  }

  @TestTemplate
  void shouldDeleteProcessInstanceAndProcessInstanceDependentEntities(
      final ExporterConfiguration config, final SearchClientAdapter client) throws Exception {
    withTask(
        config,
        (job, resourceProvider) -> {
          // given
          final var listViewTemplate =
              resourceProvider.getIndexTemplateDescriptor(ListViewTemplate.class);

          final ProcessInstanceForListViewEntity processInstance =
              processInstanceForListViewEntity("2020-01-01T00:00:00+00:00", STORAGE_ORDINAL1);

          final var processInstanceDependent =
              getProcessInstanceDependentEntities(resourceProvider, processInstance);

          store(listViewTemplate, STORAGE_ORDINAL1, client, processInstance);
          for (final var dependent : processInstanceDependent) {
            for (final var entity : dependent.entities()) {
              store(dependent.template(), STORAGE_ORDINAL1, client, entity);
            }
          }

          client.refresh(testPrefix);

          // when
          final var deleted = job.execute();

          // then
          assertThat(deleted).succeedsWithin(EXECUTE_TIMEOUT).isEqualTo(1);

          // check that the process is no longer in the index
          verifyDeleted(listViewTemplate, STORAGE_ORDINAL1, client, processInstance, "2020-01-01");
          for (final var dependent : processInstanceDependent) {
            for (final var entity : dependent.entities()) {
              verifyDeleted(dependent.template(), STORAGE_ORDINAL1, client, entity, "2020-01-01");
            }
          }
        });
  }

  @TestTemplate
  void shouldNotDeleteFinishedProcessInstancesInMainIndex(
      final ExporterConfiguration config, final SearchClientAdapter client) throws Exception {
    withTask(
        config,
        (job, resourceProvider) -> {
          // given
          final var listViewTemplate =
              resourceProvider.getIndexTemplateDescriptor(ListViewTemplate.class);

          final ProcessInstanceForListViewEntity processInstance =
              processInstanceForListViewEntity("2020-01-01T00:00:00+00:00");

          final var processInstanceDependent =
              getProcessInstanceDependentEntities(resourceProvider, processInstance);

          store(listViewTemplate, client, processInstance);
          for (final var dependent : processInstanceDependent) {
            for (final var entity : dependent.entities()) {
              store(dependent.template(), client, entity);
            }
          }

          client.refresh(testPrefix);

          // when
          final var deleted = job.execute();

          // then
          assertThat(deleted).succeedsWithin(EXECUTE_TIMEOUT).isEqualTo(0);

          // process instance was in the main index, so should not have been touched by the deleter
          // as it only operates on the storage ordinal indexes
          verifyExists(listViewTemplate, MAIN_INDEX, client, processInstance);
          for (final var dependent : processInstanceDependent) {
            for (final var entity : dependent.entities()) {
              verifyExists(dependent.template(), MAIN_INDEX, client, entity);
            }
          }
        });
  }

  @TestTemplate
  void shouldHaveProcessInstanceDependentEntitiesSpecifiedInTests(
      final ExporterConfiguration config, final SearchClientAdapter client) throws Exception {
    verifyDependentEntitiesSpecifiedInTests(config);
  }

  protected void verifyExists(
      final IndexTemplateDescriptor templateDescriptor,
      final int storageOrdinal,
      final SearchClientAdapter client,
      final ExporterEntity<?> parent,
      final ExporterEntity<?> entity)
      throws IOException {
    verifyExists(templateDescriptor, storageOrdinal, client, entity, parent.getId());
  }

  protected void verifyExists(
      final IndexTemplateDescriptor templateDescriptor,
      final int storageOrdinal,
      final SearchClientAdapter client,
      final ExporterEntity<?> entity)
      throws IOException {
    verifyExists(templateDescriptor, storageOrdinal, client, entity, (String) null);
  }

  protected void verifyExists(
      final IndexTemplateDescriptor templateDescriptor,
      final int storageOrdinal,
      final SearchClientAdapter client,
      final ExporterEntity<?> entity,
      final String routing)
      throws IOException {
    final var indexName =
        targetIndexLocator.locateOrdinalIndex(templateDescriptor, storageOrdinal).name();
    final var indexEntity = client.get(entity.getId(), routing, indexName, entity.getClass());
    assertThat(indexEntity)
        .describedAs(
            "Expected %s to still be in %s", entity, templateDescriptor.getFullQualifiedName())
        .isEqualTo(entity);
  }

  protected void verifyDeleted(
      final IndexTemplateDescriptor templateDescriptor,
      final int storageOrdinal,
      final SearchClientAdapter client,
      final ExporterEntity<?> parent,
      final ExporterEntity<?> entity)
      throws IOException {
    verifyDeleted(templateDescriptor, storageOrdinal, client, entity, parent.getId());
  }

  protected void verifyDeleted(
      final IndexTemplateDescriptor templateDescriptor,
      final int storageOrdinal,
      final SearchClientAdapter client,
      final ExporterEntity<?> entity)
      throws IOException {
    verifyDeleted(templateDescriptor, storageOrdinal, client, entity, (String) null);
  }

  protected void verifyDeleted(
      final IndexTemplateDescriptor templateDescriptor,
      final int storageOrdinal,
      final SearchClientAdapter client,
      final ExporterEntity<?> entity,
      final String routing)
      throws IOException {
    // should no longer be in the original index
    final var indexName =
        targetIndexLocator.locateOrdinalIndex(templateDescriptor, storageOrdinal).name();
    final var indexEntity = client.get(entity.getId(), routing, indexName, entity.getClass());
    assertThat(indexEntity)
        .describedAs("Expected %s to have been deleted from %s", entity, indexName)
        .isNull();
  }

  @Override
  protected ProcessInstanceDeleterJob createBackgroundTask(
      final ExporterConfiguration config, final ExporterResourceProvider resourceProvider) {

    final var listViewTemplate =
        resourceProvider.getIndexTemplateDescriptor(ListViewTemplate.class);
    final var dependantTemplates =
        resourceProvider.getIndexTemplateDescriptors().stream()
            .filter(ProcessInstanceDependant.class::isInstance)
            .map(ProcessInstanceDependant.class::cast)
            .toList();

    final var deletionRepository = createDeletionRepository(config, resourceProvider);

    return new ProcessInstanceDeleterJob(
        new TargetIndexLocator(),
        deletionRepository,
        listViewTemplate,
        dependantTemplates,
        exporterMetrics,
        LOGGER,
        executor);
  }

  private DeletionRepository createDeletionRepository(
      final ExporterConfiguration config, final ExporterResourceProvider resourceProvider) {
    final var isElasticsearch = ConnectionTypes.isElasticSearch(config.getConnect().getType());
    if (isElasticsearch) {
      return closeLater(
          new ElasticsearchDeletionRepository(
              PARTITION_ID,
              config.getHistory(),
              resourceProvider,
              createAsyncESClient(config),
              executor,
              exporterMetrics,
              LOGGER));
    } else {
      final var asyncClient = createOSAsyncClient(config);

      return closeLater(
          new OpensearchDeletionRepository(
              PARTITION_ID,
              config.getHistory(),
              resourceProvider,
              asyncClient,
              executor,
              exporterMetrics,
              LOGGER));
    }
  }
}
