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
import io.camunda.webapps.schema.descriptors.IndexTemplateDescriptor;
import io.camunda.webapps.schema.descriptors.ProcessInstanceDependant;
import io.camunda.webapps.schema.descriptors.template.CorrelatedMessageSubscriptionTemplate;
import io.camunda.webapps.schema.descriptors.template.DecisionInstanceTemplate;
import io.camunda.webapps.schema.descriptors.template.EventTemplate;
import io.camunda.webapps.schema.descriptors.template.FlowNodeInstanceTemplate;
import io.camunda.webapps.schema.descriptors.template.IncidentTemplate;
import io.camunda.webapps.schema.descriptors.template.JobTemplate;
import io.camunda.webapps.schema.descriptors.template.ListViewTemplate;
import io.camunda.webapps.schema.descriptors.template.OperationTemplate;
import io.camunda.webapps.schema.descriptors.template.PostImporterQueueTemplate;
import io.camunda.webapps.schema.descriptors.template.SequenceFlowTemplate;
import io.camunda.webapps.schema.descriptors.template.SnapshotTaskVariableTemplate;
import io.camunda.webapps.schema.descriptors.template.TaskTemplate;
import io.camunda.webapps.schema.descriptors.template.VariableTemplate;
import io.camunda.webapps.schema.entities.CorrelatedMessageSubscriptionEntity;
import io.camunda.webapps.schema.entities.ExporterEntity;
import io.camunda.webapps.schema.entities.JobEntity;
import io.camunda.webapps.schema.entities.SequenceFlowEntity;
import io.camunda.webapps.schema.entities.VariableEntity;
import io.camunda.webapps.schema.entities.dmn.DecisionInstanceEntity;
import io.camunda.webapps.schema.entities.event.EventEntity;
import io.camunda.webapps.schema.entities.flownode.FlowNodeInstanceEntity;
import io.camunda.webapps.schema.entities.incident.IncidentEntity;
import io.camunda.webapps.schema.entities.listview.FlowNodeInstanceForListViewEntity;
import io.camunda.webapps.schema.entities.listview.ProcessInstanceForListViewEntity;
import io.camunda.webapps.schema.entities.listview.VariableForListViewEntity;
import io.camunda.webapps.schema.entities.operation.OperationEntity;
import io.camunda.webapps.schema.entities.post.PostImporterQueueEntity;
import io.camunda.webapps.schema.entities.usertask.SnapshotTaskVariableEntity;
import io.camunda.webapps.schema.entities.usertask.TaskEntity;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import java.util.stream.Collectors;
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
          final ProcessInstanceForListViewEntity notOldEnoughInstance =
              processInstanceForListViewEntity("2099-01-01T00:00:00+00:00");

          store(listViewTemplate, client, finishedInstance);
          store(listViewTemplate, client, notOldEnoughInstance);

          client.refresh();

          // when
          final var archived = job.execute();

          // then
          assertThat(archived).succeedsWithin(Duration.ofSeconds(5L)).isEqualTo(1);

          // check that the finished process is no longer in the main index
          verifyMoved(listViewTemplate, client, finishedInstance, "2020-01-01");
          verifyNotMoved(listViewTemplate, client, notOldEnoughInstance);
        });
  }

  @TestTemplate
  void shouldArchiveProcessInstanceAndDependentChildListViewEntities(
      final ExporterConfiguration config, final SearchClientAdapter client) throws Exception {
    withArchiverJob(
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

          client.refresh();

          // when
          final var archived = job.execute();

          // then
          assertThat(archived).succeedsWithin(Duration.ofSeconds(5L)).isEqualTo(1);

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
    withArchiverJob(
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

          client.refresh();

          // when
          final var archived = job.execute();

          // then
          assertThat(archived).succeedsWithin(Duration.ofSeconds(5L)).isEqualTo(1);

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
    withArchiverJob(
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

          client.refresh();

          // when
          final var archived = job.execute();

          // then
          assertThat(archived).succeedsWithin(Duration.ofSeconds(5L)).isEqualTo(1);

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
    withArchiverJob(
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

          client.refresh();

          // when
          final var archived = job.execute();

          // then
          assertThat(archived).succeedsWithin(Duration.ofSeconds(5L)).isEqualTo(1);

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
  void shouldHaveProcessInstanceDependentEntitiesSpecifiedInTests(
      final ExporterConfiguration config, final SearchClientAdapter client) throws Exception {
    withArchiverJob(
        config,
        (job, resourceProvider) -> {
          // given
          final var expectedProcessInstanceDependent =
              resourceProvider.getIndexTemplateDescriptors().stream()
                  .filter(ProcessInstanceDependant.class::isInstance)
                  .collect(Collectors.toSet());

          final ProcessInstanceForListViewEntity processInstance =
              processInstanceForListViewEntity("2020-01-01T00:00:00+00:00");

          // when
          final var processInstanceDependent =
              getProcessInstanceDependentEntities(resourceProvider, processInstance).stream()
                  .map(DependentEntities::template)
                  .collect(Collectors.toSet());

          // then
          assertThat(processInstanceDependent).isEqualTo(expectedProcessInstanceDependent);
        });
  }

  private List<DependentEntities> getProcessInstanceDependentEntities(
      final ExporterResourceProvider resourceProvider,
      final ProcessInstanceForListViewEntity processInstance) {
    return List.of(
        new DependentEntities(
            resourceProvider.getIndexTemplateDescriptor(FlowNodeInstanceTemplate.class),
            List.of(
                flowNodeInstanceEntity(processInstance), flowNodeInstanceEntity(processInstance))),
        new DependentEntities(
            resourceProvider.getIndexTemplateDescriptor(VariableTemplate.class),
            List.of(variableEntity(processInstance))),
        new DependentEntities(
            resourceProvider.getIndexTemplateDescriptor(IncidentTemplate.class),
            List.of(incidentEntity(processInstance))),
        new DependentEntities(
            resourceProvider.getIndexTemplateDescriptor(JobTemplate.class),
            List.of(jobEntity(processInstance))),
        new DependentEntities(
            resourceProvider.getIndexTemplateDescriptor(OperationTemplate.class),
            List.of(operationEntity(processInstance))),
        new DependentEntities(
            resourceProvider.getIndexTemplateDescriptor(EventTemplate.class),
            List.of(eventEntity(processInstance))),
        new DependentEntities(
            resourceProvider.getIndexTemplateDescriptor(
                CorrelatedMessageSubscriptionTemplate.class),
            List.of(correlatedMessageSubscriptionEntity(processInstance))),
        new DependentEntities(
            resourceProvider.getIndexTemplateDescriptor(SequenceFlowTemplate.class),
            List.of(sequenceFlowEntity(processInstance))),
        new DependentEntities(
            resourceProvider.getIndexTemplateDescriptor(PostImporterQueueTemplate.class),
            List.of(postImporterQueueEntity(processInstance))),
        new DependentEntities(
            resourceProvider.getIndexTemplateDescriptor(SnapshotTaskVariableTemplate.class),
            List.of(snapshotTaskVariableEntity(processInstance))),
        new DependentEntities(
            resourceProvider.getIndexTemplateDescriptor(DecisionInstanceTemplate.class),
            List.of(decisionInstanceEntity(processInstance))),
        new DependentEntities(
            resourceProvider.getIndexTemplateDescriptor(TaskTemplate.class),
            List.of(taskEntity(processInstance))));
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
    final FlowNodeInstanceForListViewEntity entity = create(FlowNodeInstanceForListViewEntity::new);
    entity.setProcessInstanceKey(processInstance.getKey());
    entity.getJoinRelation().setParent(processInstance.getKey());

    return entity;
  }

  private VariableForListViewEntity variableForListViewEntity(
      final ProcessInstanceForListViewEntity processInstance) {
    final VariableForListViewEntity entity = create(VariableForListViewEntity::new);
    entity.setProcessInstanceKey(processInstance.getKey());
    entity.getJoinRelation().setParent(processInstance.getKey());

    return entity;
  }

  private FlowNodeInstanceEntity flowNodeInstanceEntity(
      final ProcessInstanceForListViewEntity processInstance) {
    final FlowNodeInstanceEntity entity = create(FlowNodeInstanceEntity::new);
    entity.setProcessInstanceKey(processInstance.getKey());

    return entity;
  }

  private VariableEntity variableEntity(final ProcessInstanceForListViewEntity processInstance) {
    final VariableEntity entity = create(VariableEntity::new);
    entity.setProcessInstanceKey(processInstance.getKey());

    return entity;
  }

  private IncidentEntity incidentEntity(final ProcessInstanceForListViewEntity processInstance) {
    final IncidentEntity entity = create(IncidentEntity::new);
    entity.setProcessInstanceKey(processInstance.getKey());
    entity.setErrorMessage("Error message");

    return entity;
  }

  private JobEntity jobEntity(final ProcessInstanceForListViewEntity processInstance) {
    final JobEntity entity = create(JobEntity::new);
    entity.setProcessInstanceKey(processInstance.getKey());

    return entity;
  }

  private OperationEntity operationEntity(final ProcessInstanceForListViewEntity processInstance) {
    final OperationEntity entity = create(OperationEntity::new);
    entity.setProcessInstanceKey(processInstance.getKey());

    return entity;
  }

  private EventEntity eventEntity(final ProcessInstanceForListViewEntity processInstance) {
    final EventEntity entity = create(EventEntity::new);
    entity.setProcessInstanceKey(processInstance.getKey());

    return entity;
  }

  private CorrelatedMessageSubscriptionEntity correlatedMessageSubscriptionEntity(
      final ProcessInstanceForListViewEntity processInstance) {
    final CorrelatedMessageSubscriptionEntity entity =
        create(CorrelatedMessageSubscriptionEntity::new);
    entity.setProcessInstanceKey(processInstance.getKey());

    return entity;
  }

  private SequenceFlowEntity sequenceFlowEntity(
      final ProcessInstanceForListViewEntity processInstance) {
    final SequenceFlowEntity entity = create(SequenceFlowEntity::new);
    entity.setProcessInstanceKey(processInstance.getKey());
    return entity;
  }

  private PostImporterQueueEntity postImporterQueueEntity(
      final ProcessInstanceForListViewEntity processInstance) {
    final PostImporterQueueEntity entity = create(PostImporterQueueEntity::new);
    entity.setProcessInstanceKey(processInstance.getKey());
    return entity;
  }

  private SnapshotTaskVariableEntity snapshotTaskVariableEntity(
      final ProcessInstanceForListViewEntity processInstance) {
    final SnapshotTaskVariableEntity entity = create(SnapshotTaskVariableEntity::new);
    entity.setProcessInstanceKey(processInstance.getKey());
    return entity;
  }

  private DecisionInstanceEntity decisionInstanceEntity(
      final ProcessInstanceForListViewEntity processInstance) {
    final DecisionInstanceEntity entity = create(DecisionInstanceEntity::new);
    entity.setProcessInstanceKey(processInstance.getKey());
    return entity;
  }

  private TaskEntity taskEntity(final ProcessInstanceForListViewEntity processInstance) {
    final TaskEntity entity = create(TaskEntity::new);
    entity.setProcessInstanceId(String.valueOf(processInstance.getKey()));
    return entity;
  }

  private <T extends ExporterEntity<T>> T create(final Supplier<T> constructor) {
    final long id = ID_GENERATOR.incrementAndGet();
    final var entity = constructor.get();
    entity.setId(String.valueOf(id));
    return entity;
  }

  record DependentEntities(IndexTemplateDescriptor template, List<ExporterEntity<?>> entities) {}
}
