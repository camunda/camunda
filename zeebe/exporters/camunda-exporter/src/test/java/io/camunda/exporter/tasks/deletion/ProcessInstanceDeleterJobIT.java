/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.deletion;

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
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.TestTemplate;

@TestInstance(Lifecycle.PER_CLASS)
class ProcessInstanceDeleterJobIT extends BackgroundTaskIT<ProcessInstanceDeleterJob> {
  private static final int STORAGE_ORDINAL1 = 1;

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
          final var archived = job.execute();

          // then
          assertThat(archived).succeedsWithin(EXECUTE_TIMEOUT).isEqualTo(1);

          // check that the process is no longer in the main index
          verifyDeleted(listViewTemplate, STORAGE_ORDINAL1, client, processInstance);
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
    final var history = config.getHistory();
    final var retention = history.getRetention();
    final var deleter = retention.getDeleter();
    final var listViewTemplate =
        resourceProvider.getIndexTemplateDescriptor(ListViewTemplate.class);
    if (isElasticsearch) {
      return closeLater(
          new ElasticsearchDeletionRepository(
              deleter,
              retention,
              history.getProcessInstanceRetentionMode(),
              PARTITION_ID,
              listViewTemplate,
              createAsyncESClient(config),
              executor,
              exporterMetrics,
              LOGGER));
    } else {
      final var asyncClient = createOSAsyncClient(config);

      return closeLater(
          new OpensearchDeletionRepository(
              deleter,
              retention,
              history.getProcessInstanceRetentionMode(),
              PARTITION_ID,
              listViewTemplate,
              asyncClient,
              executor,
              exporterMetrics,
              LOGGER));
    }
  }
}
