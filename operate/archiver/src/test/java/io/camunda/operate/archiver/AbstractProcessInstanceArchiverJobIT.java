/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.archiver;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.operate.entities.EventEntity;
import io.camunda.operate.entities.FlowNodeInstanceEntity;
import io.camunda.operate.entities.IncidentEntity;
import io.camunda.operate.entities.JobEntity;
import io.camunda.operate.entities.OperateEntity;
import io.camunda.operate.entities.OperationEntity;
import io.camunda.operate.entities.SequenceFlowEntity;
import io.camunda.operate.entities.UserTaskEntity;
import io.camunda.operate.entities.VariableEntity;
import io.camunda.operate.entities.dmn.DecisionInstanceEntity;
import io.camunda.operate.entities.listview.FlowNodeInstanceForListViewEntity;
import io.camunda.operate.entities.listview.ListViewJoinRelation;
import io.camunda.operate.entities.listview.ProcessInstanceForListViewEntity;
import io.camunda.operate.entities.listview.VariableForListViewEntity;
import io.camunda.operate.entities.post.PostImporterQueueEntity;
import io.camunda.operate.schema.templates.AbstractTemplateDescriptor;
import io.camunda.operate.schema.templates.DecisionInstanceTemplate;
import io.camunda.operate.schema.templates.EventTemplate;
import io.camunda.operate.schema.templates.FlowNodeInstanceTemplate;
import io.camunda.operate.schema.templates.IncidentTemplate;
import io.camunda.operate.schema.templates.JobTemplate;
import io.camunda.operate.schema.templates.ListViewTemplate;
import io.camunda.operate.schema.templates.OperationTemplate;
import io.camunda.operate.schema.templates.PostImporterQueueTemplate;
import io.camunda.operate.schema.templates.ProcessInstanceDependant;
import io.camunda.operate.schema.templates.SequenceFlowTemplate;
import io.camunda.operate.schema.templates.UserTaskTemplate;
import io.camunda.operate.schema.templates.VariableTemplate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

public abstract class AbstractProcessInstanceArchiverJobIT extends ArchiverJobIT {

  @Autowired private ListViewTemplate listViewTemplate;
  @Autowired private FlowNodeInstanceTemplate flowNodeInstanceTemplate;
  @Autowired private VariableTemplate variableTemplate;
  @Autowired private IncidentTemplate incidentTemplate;
  @Autowired private OperationTemplate operationTemplate;
  @Autowired private SequenceFlowTemplate sequenceFlowTemplate;
  @Autowired private JobTemplate jobTemplate;
  @Autowired private EventTemplate eventTemplate;
  @Autowired private PostImporterQueueTemplate postImporterQueueTemplate;
  @Autowired private UserTaskTemplate userTaskTemplate;
  @Autowired private DecisionInstanceTemplate decisionInstanceTemplate;

  protected ListViewTemplate getListViewTemplate() {
    return listViewTemplate;
  }

  protected List<ProcessInstanceDependant> getDependantTemplates() {
    return List.of(
        flowNodeInstanceTemplate,
        variableTemplate,
        incidentTemplate,
        operationTemplate,
        sequenceFlowTemplate,
        jobTemplate,
        eventTemplate,
        postImporterQueueTemplate,
        userTaskTemplate,
        decisionInstanceTemplate);
  }

  @Test
  void shouldArchiveLoneProcessInstance() throws Exception {
    withArchiverJob(
        job -> {
          final var pi = processInstanceForListViewEntity("2020-01-01T00:00:00+00:00");
          store(listViewTemplate, pi);
          refresh();

          final var result = job.archiveNextBatch();
          assertThat(result).succeedsWithin(ARCHIVE_TIMEOUT).isEqualTo(1);

          verifyMoved(listViewTemplate, pi.getId(), "2020-01-01");
        });
  }

  @Test
  void shouldOnlyArchiveOneBatchAtATime() throws Exception {
    final int batchSize = 5;
    final int previousBatchSize = operateProperties.getArchiver().getRolloverBatchSize();
    operateProperties.getArchiver().setRolloverBatchSize(batchSize);
    try {
      withArchiverJob(
          job -> {
            final var instances = new ArrayList<ProcessInstanceForListViewEntity>();
            for (int i = 0; i < batchSize; i++) {
              instances.add(processInstanceForListViewEntity("2020-01-01T00:00:00+00:00"));
            }
            instances.add(processInstanceForListViewEntity("2020-01-02T00:00:00+00:00"));

            for (final var pi : instances) {
              store(listViewTemplate, pi);
            }
            refresh();

            final var result = job.archiveNextBatch();
            assertThat(result).succeedsWithin(ARCHIVE_TIMEOUT).isEqualTo(5);

            for (int i = 0; i < batchSize; i++) {
              verifyMoved(listViewTemplate, instances.get(i).getId(), "2020-01-01");
            }
            verifyNotMoved(listViewTemplate, instances.get(batchSize).getId());
          });
    } finally {
      operateProperties.getArchiver().setRolloverBatchSize(previousBatchSize);
    }
  }

  @Test
  void shouldOnlyArchiveFinishedProcessInstances() throws Exception {
    withArchiverJob(
        job -> {
          final var finished = processInstanceForListViewEntity("2020-01-01T00:00:00+00:00");
          final var unfinished = processInstanceForListViewEntity(null);

          store(listViewTemplate, finished);
          store(listViewTemplate, unfinished);
          refresh();

          final var result = job.archiveNextBatch();
          assertThat(result).succeedsWithin(ARCHIVE_TIMEOUT).isEqualTo(1);

          verifyMoved(listViewTemplate, finished.getId(), "2020-01-01");
          verifyNotMoved(listViewTemplate, unfinished.getId());
        });
  }

  @Test
  void shouldOnlyArchiveProcessInstancesCompletedAfterAWhile() throws Exception {
    withArchiverJob(
        job -> {
          final var old = processInstanceForListViewEntity("2020-01-01T00:00:00+00:00");
          final var notOldEnough = processInstanceForListViewEntity("2099-01-01T00:00:00+00:00");

          store(listViewTemplate, old);
          store(listViewTemplate, notOldEnough);
          refresh();

          final var result = job.archiveNextBatch();
          assertThat(result).succeedsWithin(ARCHIVE_TIMEOUT).isEqualTo(1);

          verifyMoved(listViewTemplate, old.getId(), "2020-01-01");
          verifyNotMoved(listViewTemplate, notOldEnough.getId());
        });
  }

  @Test
  void shouldArchiveProcessInstanceAndDependentChildListViewEntities() throws Exception {
    withArchiverJob(
        job -> {
          final var pi = processInstanceForListViewEntity("2020-01-01T00:00:00+00:00");
          final var fni = flowNodeInstanceForListViewEntity(pi);
          final var variable = variableForListViewEntity(pi);

          store(listViewTemplate, pi);
          store(listViewTemplate, fni, String.valueOf(pi.getKey()));
          store(listViewTemplate, variable, String.valueOf(pi.getKey()));
          refresh();

          final var result = job.archiveNextBatch();
          assertThat(result).succeedsWithin(ARCHIVE_TIMEOUT).isEqualTo(1);

          verifyMoved(listViewTemplate, pi.getId(), "2020-01-01");
          verifyMoved(listViewTemplate, fni.getId(), String.valueOf(pi.getKey()), "2020-01-01");
          verifyMoved(
              listViewTemplate, variable.getId(), String.valueOf(pi.getKey()), "2020-01-01");
        });
  }

  @Test
  void shouldArchiveProcessInstanceAndProcessInstanceDependentEntities() throws Exception {
    withArchiverJob(
        job -> {
          final var pi = processInstanceForListViewEntity("2020-01-01T00:00:00+00:00");
          store(listViewTemplate, pi);

          final var dependants = getProcessInstanceDependentEntities(pi);
          for (final var dep : dependants) {
            for (final var entity : dep.entities()) {
              store(dep.template(), entity);
            }
          }

          refresh();

          final var result = job.archiveNextBatch();
          assertThat(result).succeedsWithin(ARCHIVE_TIMEOUT).isEqualTo(1);

          verifyMoved(listViewTemplate, pi.getId(), "2020-01-01");
          for (final var dep : dependants) {
            for (final var entity : dep.entities()) {
              verifyMoved(dep.template(), entity.getId(), "2020-01-01");
            }
          }
        });
  }

  @Test
  void shouldOnlyArchiveFinishedAndDependants() throws Exception {
    withArchiverJob(
        job -> {
          final var finishedPi = processInstanceForListViewEntity("2020-01-01T00:00:00+00:00");
          final var finishedFni = flowNodeInstanceEntity(finishedPi);
          store(listViewTemplate, finishedPi);
          store(flowNodeInstanceTemplate, finishedFni);

          final var unfinishedPi = processInstanceForListViewEntity(null);
          final var unfinishedFni = flowNodeInstanceEntity(unfinishedPi);
          store(listViewTemplate, unfinishedPi);
          store(flowNodeInstanceTemplate, unfinishedFni);

          refresh();

          final var result = job.archiveNextBatch();
          assertThat(result).succeedsWithin(ARCHIVE_TIMEOUT).isEqualTo(1);

          verifyMoved(listViewTemplate, finishedPi.getId(), "2020-01-01");
          verifyMoved(flowNodeInstanceTemplate, finishedFni.getId(), "2020-01-01");
          verifyNotMoved(listViewTemplate, unfinishedPi.getId());
          verifyNotMoved(flowNodeInstanceTemplate, unfinishedFni.getId());
        });
  }

  private List<DependentEntities> getProcessInstanceDependentEntities(
      final ProcessInstanceForListViewEntity pi) {
    return List.of(
        new DependentEntities(flowNodeInstanceTemplate, List.of(flowNodeInstanceEntity(pi))),
        new DependentEntities(variableTemplate, List.of(variableEntity(pi))),
        new DependentEntities(incidentTemplate, List.of(incidentEntity(pi))),
        new DependentEntities(operationTemplate, List.of(operationEntity(pi))),
        new DependentEntities(sequenceFlowTemplate, List.of(sequenceFlowEntity(pi))),
        new DependentEntities(jobTemplate, List.of(jobEntity(pi))),
        new DependentEntities(eventTemplate, List.of(eventEntity(pi))),
        new DependentEntities(postImporterQueueTemplate, List.of(postImporterQueueEntity(pi))),
        new DependentEntities(userTaskTemplate, List.of(userTaskEntity(pi))),
        new DependentEntities(decisionInstanceTemplate, List.of(decisionInstanceEntity(pi))));
  }

  protected ProcessInstanceForListViewEntity processInstanceForListViewEntity(
      final String endDate) {
    final long id = ID_GENERATOR.incrementAndGet();
    final var pi = new ProcessInstanceForListViewEntity();
    pi.setId(String.valueOf(id));
    pi.setKey(id);
    pi.setPartitionId(PARTITION_ID);
    if (endDate != null) {
      pi.setEndDate(OffsetDateTime.parse(endDate));
    }
    return pi;
  }

  protected FlowNodeInstanceForListViewEntity flowNodeInstanceForListViewEntity(
      final ProcessInstanceForListViewEntity parent) {
    final var entity = create(FlowNodeInstanceForListViewEntity::new);
    entity.setProcessInstanceKey(parent.getKey());
    final var join = new ListViewJoinRelation(ListViewTemplate.ACTIVITIES_JOIN_RELATION);
    join.setParent(parent.getKey());
    entity.setJoinRelation(join);
    return entity;
  }

  protected VariableForListViewEntity variableForListViewEntity(
      final ProcessInstanceForListViewEntity parent) {
    final var entity = create(VariableForListViewEntity::new);
    entity.setProcessInstanceKey(parent.getKey());
    final var join = new ListViewJoinRelation(ListViewTemplate.VARIABLES_JOIN_RELATION);
    join.setParent(parent.getKey());
    entity.setJoinRelation(join);
    return entity;
  }

  protected FlowNodeInstanceEntity flowNodeInstanceEntity(
      final ProcessInstanceForListViewEntity parent) {
    final var entity = create(FlowNodeInstanceEntity::new);
    entity.setProcessInstanceKey(parent.getKey());
    return entity;
  }

  protected VariableEntity variableEntity(final ProcessInstanceForListViewEntity parent) {
    final var entity = create(VariableEntity::new);
    entity.setProcessInstanceKey(parent.getKey());
    return entity;
  }

  protected IncidentEntity incidentEntity(final ProcessInstanceForListViewEntity parent) {
    final var entity = create(IncidentEntity::new);
    entity.setProcessInstanceKey(parent.getKey());
    entity.setErrorMessage("test error message");
    return entity;
  }

  protected OperationEntity operationEntity(final ProcessInstanceForListViewEntity parent) {
    final var entity = new OperationEntity();
    entity.setId(String.valueOf(ID_GENERATOR.incrementAndGet()));
    entity.setProcessInstanceKey(parent.getKey());
    return entity;
  }

  protected SequenceFlowEntity sequenceFlowEntity(final ProcessInstanceForListViewEntity parent) {
    final var entity = new SequenceFlowEntity();
    entity.setId(String.valueOf(ID_GENERATOR.incrementAndGet()));
    entity.setProcessInstanceKey(parent.getKey());
    return entity;
  }

  protected JobEntity jobEntity(final ProcessInstanceForListViewEntity parent) {
    final var entity = create(JobEntity::new);
    entity.setProcessInstanceKey(parent.getKey());
    return entity;
  }

  protected EventEntity eventEntity(final ProcessInstanceForListViewEntity parent) {
    final var entity = create(EventEntity::new);
    entity.setProcessInstanceKey(parent.getKey());
    return entity;
  }

  protected PostImporterQueueEntity postImporterQueueEntity(
      final ProcessInstanceForListViewEntity parent) {
    final var entity = new PostImporterQueueEntity();
    entity.setId(String.valueOf(ID_GENERATOR.incrementAndGet()));
    entity.setProcessInstanceKey(parent.getKey());
    return entity;
  }

  protected UserTaskEntity userTaskEntity(final ProcessInstanceForListViewEntity parent) {
    final var entity = create(UserTaskEntity::new);
    entity.setProcessInstanceKey(parent.getKey());
    return entity;
  }

  protected DecisionInstanceEntity decisionInstanceEntity(
      final ProcessInstanceForListViewEntity parent) {
    final var entity = create(DecisionInstanceEntity::new);
    entity.setProcessInstanceKey(parent.getKey());
    return entity;
  }

  record DependentEntities(
      AbstractTemplateDescriptor template, List<? extends OperateEntity<?>> entities) {}
}
