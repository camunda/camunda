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
import io.camunda.webapps.schema.descriptors.IndexTemplateDescriptor;
import io.camunda.webapps.schema.descriptors.template.FlowNodeInstanceTemplate;
import io.camunda.webapps.schema.descriptors.template.ListViewTemplate;
import io.camunda.webapps.schema.entities.ExporterEntity;
import io.camunda.webapps.schema.entities.flownode.FlowNodeInstanceEntity;
import io.camunda.webapps.schema.entities.listview.FlowNodeInstanceForListViewEntity;
import io.camunda.webapps.schema.entities.listview.ProcessInstanceForListViewEntity;
import java.io.IOException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.TestTemplate;

public abstract class AbstractProcessInstanceArchiverJobIT<T extends ProcessInstanceArchiverJob>
    extends ArchiverJobIT<T> {
  private static final AtomicLong ID_GENERATOR = new AtomicLong(1);

  @TestTemplate
  void shouldArchiveLoneProcessInstance(
      final ExporterConfiguration config, final SearchClientAdapter client) throws Exception {
    withArchiverJob(
        config,
        (job, resourceProvider) -> {
          // given
          final var listViewTemplate =
              resourceProvider.getIndexTemplateDescriptor(ListViewTemplate.class);

          final ProcessInstanceForListViewEntity processInstance =
              processInstanceForListViewEntity("2020-01-01T00:00:00+00:00");

          store(listViewTemplate, client, processInstance);

          client.refresh();

          // when
          final var archived = job.execute();

          // then
          assertThat(archived).succeedsWithin(Duration.ofSeconds(5L)).isEqualTo(1);

          // check that the process is no longer in the main index
          verifyMoved(listViewTemplate, client, processInstance, "2020-01-01");
        });
  }

  @TestTemplate
  void shouldOnlyArchiveFinishedProcessInstances(
      final ExporterConfiguration config, final SearchClientAdapter client) throws Exception {
    withArchiverJob(
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

          client.refresh();

          // when
          final var archived = job.execute();

          // then
          assertThat(archived).succeedsWithin(Duration.ofSeconds(5L)).isEqualTo(1);

          // check that the finished process is no longer in the main index
          verifyMoved(listViewTemplate, client, finishedInstance, "2020-01-01");
          verifyNotMoved(listViewTemplate, client, unfinishedInstance);
        });
  }

  @TestTemplate
  void shouldOnlyArchiveProcessInstancesCompletedAfterAWhile(
      final ExporterConfiguration config, final SearchClientAdapter client) throws Exception {
    withArchiverJob(
        config,
        (job, resourceProvider) -> {
          // given
          final var listViewTemplate =
              resourceProvider.getIndexTemplateDescriptor(ListViewTemplate.class);

          final ProcessInstanceForListViewEntity finishedInstance =
              processInstanceForListViewEntity("2020-01-01T00:00:00+00:00");
          final ProcessInstanceForListViewEntity unfinishedInstance =
              processInstanceForListViewEntity("2099-01-01T00:00:00+00:00");

          store(listViewTemplate, client, finishedInstance);
          store(listViewTemplate, client, unfinishedInstance);

          client.refresh();

          // when
          final var archived = job.execute();

          // then
          assertThat(archived).succeedsWithin(Duration.ofSeconds(5L)).isEqualTo(1);

          // check that the finished process is no longer in the main index
          verifyMoved(listViewTemplate, client, finishedInstance, "2020-01-01");
          verifyNotMoved(listViewTemplate, client, unfinishedInstance);
        });
  }

  @TestTemplate
  void shouldArchiveProcessInstanceAndDependentListViewFlowNodes(
      final ExporterConfiguration config, final SearchClientAdapter client) throws Exception {
    withArchiverJob(
        config,
        (job, resourceProvider) -> {
          // given
          final var listViewTemplate =
              resourceProvider.getIndexTemplateDescriptor(ListViewTemplate.class);

          final ProcessInstanceForListViewEntity processInstance =
              processInstanceForListViewEntity("2020-01-01T00:00:00+00:00");

          final List<FlowNodeInstanceForListViewEntity> flowNodes =
              List.of(
                  flowNodeInstanceForListViewEntity(processInstance),
                  flowNodeInstanceForListViewEntity(processInstance),
                  flowNodeInstanceForListViewEntity(processInstance));

          store(listViewTemplate, client, processInstance);
          for (final var flowNode : flowNodes) {
            store(listViewTemplate, client, processInstance, flowNode);
          }

          client.refresh();

          // when
          final var archived = job.execute();

          // then
          assertThat(archived).succeedsWithin(Duration.ofSeconds(5L)).isEqualTo(1);

          // check that the process is no longer in the main index
          verifyMoved(listViewTemplate, client, processInstance, "2020-01-01");
          for (final var flowNode : flowNodes) {
            verifyMoved(listViewTemplate, client, flowNode, "2020-01-01");
          }
        });
  }

  @TestTemplate
  void shouldArchiveProcessInstanceAndDependentFlowNodeInstances(
      final ExporterConfiguration config, final SearchClientAdapter client) throws Exception {
    withArchiverJob(
        config,
        (job, resourceProvider) -> {
          // given
          final var listViewTemplate =
              resourceProvider.getIndexTemplateDescriptor(ListViewTemplate.class);
          final var flowNodeInstanceTemplate =
              resourceProvider.getIndexTemplateDescriptor(FlowNodeInstanceTemplate.class);

          final ProcessInstanceForListViewEntity processInstance =
              processInstanceForListViewEntity("2020-01-01T00:00:00+00:00");

          final List<FlowNodeInstanceEntity> flowNodes =
              List.of(
                  flowNodeInstanceEntity(processInstance),
                  flowNodeInstanceEntity(processInstance),
                  flowNodeInstanceEntity(processInstance));

          store(listViewTemplate, client, processInstance);
          for (final var flowNode : flowNodes) {
            store(flowNodeInstanceTemplate, client, processInstance, flowNode);
          }

          client.refresh();

          // when
          final var archived = job.execute();

          // then
          assertThat(archived).succeedsWithin(Duration.ofSeconds(5L)).isEqualTo(1);

          // check that the process is no longer in the main index
          verifyMoved(listViewTemplate, client, processInstance, "2020-01-01");
          for (final var flowNode : flowNodes) {
            verifyMoved(flowNodeInstanceTemplate, client, flowNode, "2020-01-01");
          }
        });
  }

  private void store(
      final IndexTemplateDescriptor template,
      final SearchClientAdapter client,
      final ExporterEntity<?> entity)
      throws IOException {
    client.index(entity.getId(), template.getFullQualifiedName(), entity);
  }

  private void store(
      final IndexTemplateDescriptor template,
      final SearchClientAdapter client,
      final ExporterEntity<?> parent,
      final ExporterEntity<?> child)
      throws IOException {
    client.index(child.getId(), parent.getId(), template.getFullQualifiedName(), child);
  }

  private ProcessInstanceForListViewEntity processInstanceForListViewEntity(final String endDate) {
    final ProcessInstanceForListViewEntity processInstance = new ProcessInstanceForListViewEntity();
    final long id = ID_GENERATOR.incrementAndGet();
    processInstance.setId(String.valueOf(id));
    processInstance.setKey(id);
    processInstance.setPartitionId(PARTITION_ID);
    if (endDate != null) {
      processInstance.setEndDate(OffsetDateTime.parse(endDate));
    }

    return processInstance;
  }

  private FlowNodeInstanceForListViewEntity flowNodeInstanceForListViewEntity(
      final ProcessInstanceForListViewEntity processInstance) {
    final FlowNodeInstanceForListViewEntity flowNode = new FlowNodeInstanceForListViewEntity();
    final long id = ID_GENERATOR.incrementAndGet();
    flowNode.setId(String.valueOf(id));
    flowNode.setKey(id);
    flowNode.setPartitionId(PARTITION_ID);
    flowNode.setProcessInstanceKey(processInstance.getKey());
    flowNode.getJoinRelation().setParent(processInstance.getKey());

    return flowNode;
  }

  private FlowNodeInstanceEntity flowNodeInstanceEntity(
      final ProcessInstanceForListViewEntity processInstance) {
    final FlowNodeInstanceEntity flowNode = new FlowNodeInstanceEntity();
    final long id = ID_GENERATOR.incrementAndGet();
    flowNode.setId(String.valueOf(id));
    flowNode.setKey(id);
    flowNode.setPartitionId(PARTITION_ID);
    flowNode.setProcessInstanceKey(processInstance.getKey());

    return flowNode;
  }

  private void verifyMoved(
      final IndexTemplateDescriptor templateDescriptor,
      final SearchClientAdapter client,
      final ExporterEntity<?> entity,
      final String datedIndexSuffix)
      throws IOException {
    // should no longer be in the original index
    final var originalIndexEntity =
        client.get(entity.getId(), templateDescriptor.getFullQualifiedName(), entity.getClass());
    assertThat(originalIndexEntity).isNull();

    // should now be in the dated index
    final var dateIndex = templateDescriptor.getFullQualifiedName() + datedIndexSuffix;
    final var newIndexEntity = client.get(entity.getId(), dateIndex, entity.getClass());
    assertThat(newIndexEntity).isEqualTo(entity);
  }

  private void verifyNotMoved(
      final IndexTemplateDescriptor templateDescriptor,
      final SearchClientAdapter client,
      final ExporterEntity<?> entity)
      throws IOException {
    final var originalIndexEntity =
        client.get(entity.getId(), templateDescriptor.getFullQualifiedName(), entity.getClass());
    assertThat(originalIndexEntity).isEqualTo(entity);
  }
}
