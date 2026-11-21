/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.archiver;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.exporter.ExporterResourceProvider;
import io.camunda.exporter.config.ExporterConfiguration;
import io.camunda.search.test.utils.SearchClientAdapter;
import io.camunda.webapps.schema.descriptors.ProcessInstanceDependant;
import io.camunda.webapps.schema.descriptors.template.ListViewTemplate;
import io.camunda.webapps.schema.entities.ExporterEntity;
import io.camunda.webapps.schema.entities.listview.ProcessInstanceForListViewEntity;
import java.util.ArrayList;
import java.util.List;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.TestTemplate;

@TestInstance(Lifecycle.PER_CLASS)
public class ProcessInstanceArchiverJobIT extends ArchiverJobIT<ProcessInstanceArchiverJob> {

  @TestTemplate
  void shouldArchiveLoneProcessInstance(
      final ExporterConfiguration config, final SearchClientAdapter client) throws Exception {
    withTask(
        config,
        (job, resourceProvider) -> {
          // given
          final var listViewTemplate =
              resourceProvider.getIndexTemplateDescriptor(ListViewTemplate.class);

          final ProcessInstanceForListViewEntity processInstance =
              processInstanceForListViewEntity("2020-01-01T00:00:00+00:00");

          store(listViewTemplate, client, processInstance);

          client.refresh(testPrefix);

          // when
          final var archived = job.execute();

          // then
          assertThat(archived).succeedsWithin(EXECUTE_TIMEOUT).isEqualTo(1);

          // check that the process is no longer in the main index
          verifyMoved(listViewTemplate, client, processInstance, "2020-01-01");
        });
  }

  @TestTemplate
  void shouldOnlyArchiveOneBatchAtATime(
      final ExporterConfiguration config, final SearchClientAdapter client) throws Exception {
    final int batchSize = 5;
    config.getHistory().setRolloverBatchSize(batchSize);
    withTask(
        config,
        (job, resourceProvider) -> {
          // given
          final var listViewTemplate =
              resourceProvider.getIndexTemplateDescriptor(ListViewTemplate.class);

          final List<ProcessInstanceForListViewEntity> processInstances = new ArrayList<>();
          for (var i = 0; i < batchSize; i++) {
            processInstances.add(processInstanceForListViewEntity("2020-01-01T00:00:00+00:00"));
          }
          // slightly later end date, so should not be in first batch
          processInstances.add(processInstanceForListViewEntity("2020-01-01T00:00:10+00:00"));

          for (final var processInstance : processInstances) {
            store(listViewTemplate, client, processInstance);
          }

          client.refresh(testPrefix);

          // when
          final var archived = job.execute();

          // then
          assertThat(archived).succeedsWithin(EXECUTE_TIMEOUT).isEqualTo(5);

          // check that the first batch of processes are no longer in the main index
          for (var i = 0; i < batchSize; i++) {
            verifyMoved(listViewTemplate, client, processInstances.get(i), "2020-01-01");
          }
          // but the last doc is still there
          verifyNotMoved(listViewTemplate, client, processInstances.getLast());
        });
  }

  @TestTemplate
  void shouldOnlyArchiveFinishedProcessInstances(
      final ExporterConfiguration config, final SearchClientAdapter client) throws Exception {
    withTask(
        config,
        (job, resourceProvider) -> {
          // given
          final var listViewTemplate =
              resourceProvider.getIndexTemplateDescriptor(ListViewTemplate.class);

          final ProcessInstanceForListViewEntity finishedInstance =
              processInstanceForListViewEntity("2020-01-01T00:00:00+00:00");
          final ProcessInstanceForListViewEntity unfinishedInstance =
              processInstanceForListViewEntity(null);

          store(listViewTemplate, client, finishedInstance);
          store(listViewTemplate, client, unfinishedInstance);

          client.refresh(testPrefix);

          // when
          final var archived = job.execute();

          // then
          assertThat(archived).succeedsWithin(EXECUTE_TIMEOUT).isEqualTo(1);

          // check that the finished process is no longer in the main index
          verifyMoved(listViewTemplate, client, finishedInstance, "2020-01-01");
          verifyNotMoved(listViewTemplate, client, unfinishedInstance);
        });
  }

  @TestTemplate
  void shouldOnlyArchiveProcessInstancesCompletedAfterAWhile(
      final ExporterConfiguration config, final SearchClientAdapter client) throws Exception {
    withTask(
        config,
        (job, resourceProvider) -> {
          // given
          final var listViewTemplate =
              resourceProvider.getIndexTemplateDescriptor(ListViewTemplate.class);

          final ProcessInstanceForListViewEntity finishedInstance =
              processInstanceForListViewEntity("2020-01-01T00:00:00+00:00");
          final ProcessInstanceForListViewEntity notOldEnoughInstance =
              processInstanceForListViewEntity("2099-01-01T00:00:00+00:00");

          store(listViewTemplate, client, finishedInstance);
          store(listViewTemplate, client, notOldEnoughInstance);

          client.refresh(testPrefix);

          // when
          final var archived = job.execute();

          // then
          assertThat(archived).succeedsWithin(EXECUTE_TIMEOUT).isEqualTo(1);

          // check that the finished process is no longer in the main index
          verifyMoved(listViewTemplate, client, finishedInstance, "2020-01-01");
          verifyNotMoved(listViewTemplate, client, notOldEnoughInstance);
        });
  }

  @TestTemplate
  void shouldArchiveProcessInstanceAndDependentChildListViewEntities(
      final ExporterConfiguration config, final SearchClientAdapter client) throws Exception {
    withTask(
        config,
        (job, resourceProvider) -> {
          // given
          final var listViewTemplate =
              resourceProvider.getIndexTemplateDescriptor(ListViewTemplate.class);

          final ProcessInstanceForListViewEntity processInstance =
              processInstanceForListViewEntity("2020-01-01T00:00:00+00:00");

          final List<ExporterEntity<?>> childEntities =
              List.of(
                  flowNodeInstanceForListViewEntity(processInstance),
                  flowNodeInstanceForListViewEntity(processInstance),
                  variableForListViewEntity(processInstance));

          store(listViewTemplate, client, processInstance);
          for (final var child : childEntities) {
            store(listViewTemplate, client, processInstance, child);
          }

          client.refresh(testPrefix);

          // when
          final var archived = job.execute();

          // then
          assertThat(archived).succeedsWithin(EXECUTE_TIMEOUT).isEqualTo(1);

          // check that the process is no longer in the main index
          verifyMoved(listViewTemplate, client, processInstance, "2020-01-01");
          for (final var child : childEntities) {
            verifyMoved(listViewTemplate, client, processInstance, child, "2020-01-01");
          }
        });
  }

  @TestTemplate
  void shouldArchiveProcessInstanceAndLargeNumberOfDependentChildListViewEntities(
      final ExporterConfiguration config, final SearchClientAdapter client) throws Exception {
    // set smaller reindex batch size to verify things work when multiple batches are needed
    config.getHistory().setReindexBatchSize(10);
    withTask(
        config,
        (job, resourceProvider) -> {
          // given
          final var listViewTemplate =
              resourceProvider.getIndexTemplateDescriptor(ListViewTemplate.class);

          final ProcessInstanceForListViewEntity processInstance =
              processInstanceForListViewEntity("2020-01-01T00:00:00+00:00");

          final List<ExporterEntity<?>> childEntities = new ArrayList<>();
          for (var i = 0; i < 90; i++) {
            childEntities.add(flowNodeInstanceForListViewEntity(processInstance));
            childEntities.add(variableForListViewEntity(processInstance));
          }

          store(listViewTemplate, client, processInstance);
          for (final var child : childEntities) {
            store(listViewTemplate, client, processInstance, child);
          }

          client.refresh(testPrefix);

          // when
          final var archived = job.execute();

          // then
          assertThat(archived).succeedsWithin(EXECUTE_TIMEOUT).isEqualTo(1);

          // check that the process is no longer in the main index
          verifyMoved(listViewTemplate, client, processInstance, "2020-01-01");
          for (final var child : childEntities) {
            verifyMoved(listViewTemplate, client, processInstance, child, "2020-01-01");
          }
        });
  }

  @TestTemplate
  void shouldOnlyArchiveFinishedProcessInstanceAndDependentChildListViewEntities(
      final ExporterConfiguration config, final SearchClientAdapter client) throws Exception {
    withTask(
        config,
        (job, resourceProvider) -> {
          // given
          final var listViewTemplate =
              resourceProvider.getIndexTemplateDescriptor(ListViewTemplate.class);

          final ProcessInstanceForListViewEntity finishedInstance =
              processInstanceForListViewEntity("2020-01-01T00:00:00+00:00");

          final List<ExporterEntity<?>> finishedChildEntities =
              List.of(
                  flowNodeInstanceForListViewEntity(finishedInstance),
                  flowNodeInstanceForListViewEntity(finishedInstance),
                  variableForListViewEntity(finishedInstance));

          store(listViewTemplate, client, finishedInstance);
          for (final var child : finishedChildEntities) {
            store(listViewTemplate, client, finishedInstance, child);
          }

          final ProcessInstanceForListViewEntity unfinishedInstance =
              processInstanceForListViewEntity(null);

          final List<ExporterEntity<?>> unfinishedChildEntities =
              List.of(
                  flowNodeInstanceForListViewEntity(unfinishedInstance),
                  flowNodeInstanceForListViewEntity(unfinishedInstance),
                  variableForListViewEntity(unfinishedInstance));

          store(listViewTemplate, client, unfinishedInstance);
          for (final var child : unfinishedChildEntities) {
            store(listViewTemplate, client, unfinishedInstance, child);
          }

          client.refresh(testPrefix);

          // when
          final var archived = job.execute();

          // then
          assertThat(archived).succeedsWithin(EXECUTE_TIMEOUT).isEqualTo(1);

          // check that the process is no longer in the main index
          verifyMoved(listViewTemplate, client, finishedInstance, "2020-01-01");
          for (final var child : finishedChildEntities) {
            verifyMoved(listViewTemplate, client, finishedInstance, child, "2020-01-01");
          }

          verifyNotMoved(listViewTemplate, client, unfinishedInstance);
          for (final var child : unfinishedChildEntities) {
            verifyNotMoved(listViewTemplate, client, unfinishedInstance, child);
          }
        });
  }

  @TestTemplate
  void shouldOnlyArchiveFinishedProcessInstanceAndProcessInstanceDependentEntities(
      final ExporterConfiguration config, final SearchClientAdapter client) throws Exception {
    withTask(
        config,
        (job, resourceProvider) -> {
          // given
          final var listViewTemplate =
              resourceProvider.getIndexTemplateDescriptor(ListViewTemplate.class);

          final ProcessInstanceForListViewEntity finishedInstance =
              processInstanceForListViewEntity("2020-01-01T00:00:00+00:00");

          final var finishedInstanceDependent =
              getProcessInstanceDependentEntities(resourceProvider, finishedInstance);

          store(listViewTemplate, client, finishedInstance);
          for (final var dependent : finishedInstanceDependent) {
            for (final var entity : dependent.entities()) {
              store(dependent.template(), client, entity);
            }
          }

          final ProcessInstanceForListViewEntity unfinishedInstance =
              processInstanceForListViewEntity(null);
          final var unfinishedInstanceDependent =
              getProcessInstanceDependentEntities(resourceProvider, unfinishedInstance);

          store(listViewTemplate, client, unfinishedInstance);
          for (final var dependent : unfinishedInstanceDependent) {
            for (final var entity : dependent.entities()) {
              store(dependent.template(), client, entity);
            }
          }

          client.refresh(testPrefix);

          // when
          final var archived = job.execute();

          // then
          assertThat(archived).succeedsWithin(EXECUTE_TIMEOUT).isEqualTo(1);

          // check that the process is no longer in the main index
          verifyMoved(listViewTemplate, client, finishedInstance, "2020-01-01");
          for (final var dependent : finishedInstanceDependent) {
            for (final var entity : dependent.entities()) {
              verifyMoved(dependent.template(), client, entity, "2020-01-01");
            }
          }

          verifyNotMoved(listViewTemplate, client, unfinishedInstance);
          for (final var dependent : unfinishedInstanceDependent) {
            for (final var entity : dependent.entities()) {
              verifyNotMoved(dependent.template(), client, entity);
            }
          }
        });
  }

  @TestTemplate
  void shouldArchiveProcessInstanceAndProcessInstanceDependentEntities(
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
          final var archived = job.execute();

          // then
          assertThat(archived).succeedsWithin(EXECUTE_TIMEOUT).isEqualTo(1);

          // check that the process is no longer in the main index
          verifyMoved(listViewTemplate, client, processInstance, "2020-01-01");
          for (final var dependent : processInstanceDependent) {
            for (final var entity : dependent.entities()) {
              verifyMoved(dependent.template(), client, entity, "2020-01-01");
            }
          }
        });
  }

  @TestTemplate
  void shouldNotDeleteDocumentsWhenReindexDoesNotFullySucceed(
      final ExporterConfiguration config, final SearchClientAdapter client) throws Exception {
    withTask(
        config,
        (job, resourceProvider) -> {
          // given - a finished process instance together with its dependent records
          final var listViewTemplate =
              resourceProvider.getIndexTemplateDescriptor(ListViewTemplate.class);
          final ProcessInstanceForListViewEntity processInstance =
              processInstanceForListViewEntity("2026-01-01T00:00:00+00:00");
          final var dependents =
              getProcessInstanceDependentEntities(resourceProvider, processInstance);

          store(listViewTemplate, client, processInstance);
          for (final var dependent : dependents) {
            for (final var entity : dependent.entities()) {
              store(dependent.template(), client, entity);
            }
          }
          client.refresh(testPrefix);

          // and - every dated destination index is pre-created with a write block, so the reindex
          // step cannot copy any document. Elasticsearch will report the rejected writes in
          // failures[] array in an HTTP 200 reindex response (no exception), which the archiver
          // must detect before deleting the source documents.
          final var datedSuffix = "2026-01-01";
          client.createIndexWithWriteBlock(listViewTemplate.getFullQualifiedName() + datedSuffix);
          for (final var dependent : dependents) {
            client.createIndexWithWriteBlock(
                dependent.template().getFullQualifiedName() + datedSuffix);
          }

          // when
          final var result = job.execute().toCompletableFuture();
          Awaitility.await("until the archive job has finished")
              .atMost(EXECUTE_TIMEOUT)
              .until(result::isDone);
          client.refresh(testPrefix);

          // then - nothing was copied to the write-blocked dated indices, so no source document may
          // have been deleted: the reindex failure must never lead to silent data loss
          verifyNotMoved(listViewTemplate, client, processInstance);
          for (final var dependent : dependents) {
            for (final var entity : dependent.entities()) {
              verifyNotMoved(dependent.template(), client, entity);
            }
          }

          // and - the failure must be surfaced to the caller instead of being silently swallowed
          assertThat(result).isCompletedExceptionally();
        });
  }

  @TestTemplate
  void shouldHaveProcessInstanceDependentEntitiesSpecifiedInTests(
      final ExporterConfiguration config, final SearchClientAdapter client) throws Exception {
    verifyDependentEntitiesSpecifiedInTests(config);
  }

  @Override
  ProcessInstanceArchiverJob createArchiveJob(
      final ExporterConfiguration config,
      final ExporterResourceProvider resourceProvider,
      final ArchiverRepository repository) {

    final var dependantTemplates =
        resourceProvider.getIndexTemplateDescriptors().stream()
            .filter(ProcessInstanceDependant.class::isInstance)
            .map(ProcessInstanceDependant.class::cast)
            .toList();

    return new ProcessInstanceArchiverJob(
        config.getHistory(),
        repository,
        resourceProvider.getIndexTemplateDescriptor(ListViewTemplate.class),
        dependantTemplates,
        exporterMetrics,
        LOGGER,
        executor);
  }
}
