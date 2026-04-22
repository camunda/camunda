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
import io.camunda.webapps.schema.descriptors.template.ListViewTemplate;
import io.camunda.webapps.schema.entities.listview.ProcessInstanceForListViewEntity;
import java.io.IOException;
import java.time.Duration;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.TestTemplate;

public abstract class AbstractProcessInstanceArchiverJobIT<T extends ProcessInstanceArchiverJob>
    extends ArchiverJobIT<T> {

  @TestTemplate
  void shouldArchiveLoneProcessInstance(
      final ExporterConfiguration config, final SearchClientAdapter client) throws Exception {
    withArchiverJob(
        config,
        (job, resourceProvider) -> {
          // given
          final ProcessInstanceForListViewEntity processInstance =
              processInstanceForListViewEntity(1234L, "2020-01-01T00:00:00+00:00");

          store(resourceProvider, client, processInstance);

          client.refresh();

          // when
          final var archived = job.execute();

          // then
          assertThat(archived).succeedsWithin(Duration.ofSeconds(5L)).isEqualTo(1);

          // check that the process is no longer in the main index
          final var listViewTemplate =
              resourceProvider.getIndexTemplateDescriptor(ListViewTemplate.class);
          final var oldIndexPI =
              get(client, processInstance, listViewTemplate.getFullQualifiedName());
          assertThat(oldIndexPI).isNull();

          // process should now be in the dated index
          final var dateIndex = listViewTemplate.getFullQualifiedName() + "2020-01-01";
          final var newIndexPI = get(client, processInstance, dateIndex);
          assertThat(newIndexPI).isEqualTo(processInstance);
        });
  }

  @TestTemplate
  void shouldOnlyArchiveFinishedProcessInstances(
      final ExporterConfiguration config, final SearchClientAdapter client) throws Exception {
    withArchiverJob(
        config,
        (job, resourceProvider) -> {
          // given
          final ProcessInstanceForListViewEntity finishedInstance =
              processInstanceForListViewEntity(1234L, "2020-01-01T00:00:00+00:00");
          final ProcessInstanceForListViewEntity unfinishedInstance =
              processInstanceForListViewEntity(1235L, null);

          store(resourceProvider, client, finishedInstance);
          store(resourceProvider, client, unfinishedInstance);

          client.refresh();

          // when
          final var archived = job.execute();

          // then
          assertThat(archived).succeedsWithin(Duration.ofSeconds(5L)).isEqualTo(1);

          // check that the finished process is no longer in the main index
          final var listViewTemplate =
              resourceProvider.getIndexTemplateDescriptor(ListViewTemplate.class);
          final var oldIndexFinishedPI =
              get(client, finishedInstance, listViewTemplate.getFullQualifiedName());
          assertThat(oldIndexFinishedPI).isNull();

          final var oldIndexUnfinishedPI =
              get(client, unfinishedInstance, listViewTemplate.getFullQualifiedName());
          assertThat(oldIndexUnfinishedPI).isEqualTo(unfinishedInstance);
        });
  }

  @TestTemplate
  void shouldOnlyArchiveProcessInstancesCompletedAfterAWhile(
      final ExporterConfiguration config, final SearchClientAdapter client) throws Exception {
    withArchiverJob(
        config,
        (job, resourceProvider) -> {
          // given
          final ProcessInstanceForListViewEntity finishedInstance =
              processInstanceForListViewEntity(1234L, "2020-01-01T00:00:00+00:00");
          final ProcessInstanceForListViewEntity unfinishedInstance =
              processInstanceForListViewEntity(1235L, "2099-01-01T00:00:00+00:00");

          store(resourceProvider, client, finishedInstance);
          store(resourceProvider, client, unfinishedInstance);

          client.refresh();

          // when
          final var archived = job.execute();

          // then
          assertThat(archived).succeedsWithin(Duration.ofSeconds(5L)).isEqualTo(1);

          // check that the finished process is no longer in the main index
          final var listViewTemplate =
              resourceProvider.getIndexTemplateDescriptor(ListViewTemplate.class);
          final var oldIndexFinishedPI =
              get(client, finishedInstance, listViewTemplate.getFullQualifiedName());
          assertThat(oldIndexFinishedPI).isNull();

          final var oldIndexUnfinishedPI =
              get(client, unfinishedInstance, listViewTemplate.getFullQualifiedName());
          assertThat(oldIndexUnfinishedPI).isEqualTo(unfinishedInstance);
        });
  }

  private void store(
      final ExporterResourceProvider resourceProvider,
      final SearchClientAdapter client,
      final ProcessInstanceForListViewEntity instance)
      throws IOException {
    client.index(
        instance.getId(),
        resourceProvider.getIndexTemplateDescriptor(ListViewTemplate.class).getFullQualifiedName(),
        instance);
  }

  private ProcessInstanceForListViewEntity get(
      final SearchClientAdapter client,
      final ProcessInstanceForListViewEntity instance,
      final String index)
      throws IOException {
    return client.get(instance.getId(), index, ProcessInstanceForListViewEntity.class);
  }

  private ProcessInstanceForListViewEntity processInstanceForListViewEntity(
      final long id, final String endDate) {
    final ProcessInstanceForListViewEntity processInstance = new ProcessInstanceForListViewEntity();
    processInstance.setId(String.valueOf(id));
    processInstance.setKey(id);
    processInstance.setPartitionId(PARTITION_ID);
    if (endDate != null) {
      processInstance.setEndDate(OffsetDateTime.parse(endDate));
    }

    return processInstance;
  }
}
