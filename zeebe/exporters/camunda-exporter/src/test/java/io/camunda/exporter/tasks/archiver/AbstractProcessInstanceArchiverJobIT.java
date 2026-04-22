/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.archiver;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.exporter.config.ExporterConfiguration;
import io.camunda.search.test.utils.SearchClientAdapter;
import io.camunda.webapps.schema.descriptors.template.ListViewTemplate;
import io.camunda.webapps.schema.entities.listview.ProcessInstanceForListViewEntity;
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
              new ProcessInstanceForListViewEntity();
          processInstance.setId("1234");
          processInstance.setKey(1234L);
          processInstance.setPartitionId(PARTITION_ID);
          processInstance.setEndDate(OffsetDateTime.parse("2020-01-01T00:00:00+00:00"));

          client.index(
              processInstance.getId(),
              resourceProvider
                  .getIndexTemplateDescriptor(ListViewTemplate.class)
                  .getFullQualifiedName(),
              processInstance);

          client.refresh();

          // when
          final var archived = job.execute();

          // then
          assertThat(archived).succeedsWithin(Duration.ofSeconds(5L)).isEqualTo(1);

          // check that the process is no longer in the main index
          final var listViewTemplate =
              resourceProvider.getIndexTemplateDescriptor(ListViewTemplate.class);
          final var oldIndexPI =
              client.get(
                  processInstance.getId(),
                  listViewTemplate.getFullQualifiedName(),
                  ProcessInstanceForListViewEntity.class);
          assertThat(oldIndexPI).isNull();

          // process should now be in the dated index
          final var dateIndex = listViewTemplate.getFullQualifiedName() + "2020-01-01";
          final var newIndexPI =
              client.get(
                  processInstance.getId(), dateIndex, ProcessInstanceForListViewEntity.class);
          assertThat(newIndexPI).isEqualTo(processInstance);
        });
  }
}
