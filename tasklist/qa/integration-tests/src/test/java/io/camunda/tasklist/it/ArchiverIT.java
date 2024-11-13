/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.it;

import static io.camunda.tasklist.util.TestCheck.PROCESS_INSTANCE_IS_CANCELED_CHECK;
import static io.camunda.tasklist.util.TestCheck.PROCESS_INSTANCE_IS_COMPLETED_CHECK;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.tasklist.archiver.ArchiverUtil;
import io.camunda.tasklist.archiver.ProcessInstanceArchiverJob;
import io.camunda.tasklist.archiver.TaskArchiverJob;
import io.camunda.tasklist.entities.TaskEntity;
import io.camunda.tasklist.exceptions.ArchiverException;
import io.camunda.tasklist.schema.v86.templates.TaskTemplate;
import io.camunda.tasklist.schema.v86.templates.TaskVariableTemplate;
import io.camunda.tasklist.store.TaskStore;
import io.camunda.tasklist.util.CollectionUtil;
import io.camunda.tasklist.util.NoSqlHelper;
import io.camunda.tasklist.util.TasklistZeebeIntegrationTest;
import io.camunda.tasklist.util.TestCheck;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

public class ArchiverIT extends TasklistZeebeIntegrationTest {

  @Autowired private BeanFactory beanFactory;

  @Autowired private ArchiverUtil archiverUtil;

  @Autowired private TaskTemplate taskTemplate;

  @Autowired private TaskVariableTemplate taskVariableTemplate;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private TaskStore taskStore;

  @Autowired private NoSqlHelper noSqlHelper;

  @Autowired
  @Qualifier(PROCESS_INSTANCE_IS_COMPLETED_CHECK)
  private TestCheck processInstanceIsCompletedCheck;

  @Autowired
  @Qualifier(PROCESS_INSTANCE_IS_CANCELED_CHECK)
  private TestCheck processInstanceIsCanceledCheck;

  private TaskArchiverJob archiverJob;
  private ProcessInstanceArchiverJob processInstanceArchiverJob;

  private Random random = new Random();

  private DateTimeFormatter dateTimeFormatter;

  @Override
  @BeforeEach
  public void before() {
    super.before();
    dateTimeFormatter =
        DateTimeFormatter.ofPattern(tasklistProperties.getArchiver().getRolloverDateFormat())
            .withZone(ZoneId.systemDefault());
    archiverJob = beanFactory.getBean(TaskArchiverJob.class, partitionHolder.getPartitionIds());
    processInstanceArchiverJob =
        beanFactory.getBean(ProcessInstanceArchiverJob.class, partitionHolder.getPartitionIds());
    clearMetrics();
  }

  @Test
  public void testArchivingTasks() throws ArchiverException, IOException {
    final DateTimeFormatter sdf = DateTimeFormatter.ofPattern("YYYY-MM-dd");
    final Map<String, Integer> mapCount = new HashMap<>();

    final Instant currentTime = pinZeebeTime();

    // having
    // deploy process
    offsetZeebeTime(Duration.ofDays(-4));
    final String processId = "demoProcess";
    final String flowNodeBpmnId = "task1";
    deployProcessWithOneFlowNode(processId, flowNodeBpmnId);

    // start and finish instances 2 days ago
    final int count1 = random.nextInt(6) + 3;
    final Instant endDate1 = currentTime.minus(2, ChronoUnit.DAYS);
    final List<String> ids1 =
        startInstancesAndCompleteTasks(processId, flowNodeBpmnId, count1, endDate1);
    mapCount.put(dateTimeFormatter.format(endDate1), count1);

    // start and finish instances 1 day ago
    final int count2 = random.nextInt(6) + 3;
    final Instant endDate2 = currentTime.minus(1, ChronoUnit.DAYS);
    final List<String> ids2 =
        startInstancesAndCompleteTasks(processId, flowNodeBpmnId, count2, endDate2);
    mapCount.put(dateTimeFormatter.format(endDate2), count2);

    // start instances 1 day ago
    final int count3 = random.nextInt(6) + 3;
    final List<String> ids3 =
        startInstances(processId, flowNodeBpmnId, count3, currentTime.minus(1, ChronoUnit.DAYS));
    resetZeebeTime();

    // when
    final Map.Entry<String, Integer> result1 = archiverJob.archiveNextBatch().join();
    assertThat(mapCount.get(result1.getKey())).isEqualTo(result1.getValue());
    databaseTestExtension.refreshIndexesInElasticsearch();

    final Map.Entry<String, Integer> result2 = archiverJob.archiveNextBatch().join();
    assertThat(mapCount.get(result2.getKey())).isEqualTo(result2.getValue());
    databaseTestExtension.refreshIndexesInElasticsearch();

    assertThat(archiverJob.archiveNextBatch().join())
        .isEqualTo(
            Map.entry(
                "NothingToArchive",
                0)); // 3rd run should not move anything, as the rest of the tasks are not completed

    databaseTestExtension.refreshIndexesInElasticsearch();

    // then
    assertTasksInCorrectIndex(count1, ids1, endDate1);
    assertTasksInCorrectIndex(count2, ids2, endDate2);
    assertTasksInCorrectIndex(count3, ids3, null);

    assertAllInstancesInAlias(count1 + count2 + count3, ids1.get(0));
  }

  private void assertAllInstancesInAlias(int count, String id) throws IOException {
    assertThat(tester.getAllTasks().get("$.data.tasks.length()")).isEqualTo(String.valueOf(count));
    final String taskId = tester.getTaskById(id).get("$.data.task.id");
    assertThat(taskId).isEqualTo(id);
  }

  @Test
  public void testArchivingOnlyOneHourOldData() throws ArchiverException, IOException {
    final Instant currentTime = pinZeebeTime();

    // having
    // deploy process
    offsetZeebeTime(Duration.ofDays(-4));
    final String processId = "demoProcess";
    final String flowNodeBpmnId = "task1";
    deployProcessWithOneFlowNode(processId, flowNodeBpmnId);

    // start and finish instances 2 hours ago
    final int count1 = random.nextInt(6) + 3;
    final Instant endDate1 = currentTime.minus(2, ChronoUnit.HOURS);
    final List<String> ids1 =
        startInstancesAndCompleteTasks(processId, flowNodeBpmnId, count1, endDate1);

    // start and finish instances 50 minutes ago
    final int count2 = random.nextInt(6) + 3;
    final Instant endDate2 = currentTime.minus(50, ChronoUnit.MINUTES);
    final List<String> ids2 =
        startInstancesAndCompleteTasks(processId, flowNodeBpmnId, count2, endDate2);

    resetZeebeTime();

    // when
    assertThat(archiverJob.archiveNextBatch().join().getValue()).isEqualTo(count1);
    databaseTestExtension.refreshIndexesInElasticsearch();
    // 2rd run should not move anything, as the rest of the tasks are completed less then 1 hour ago
    assertThat(archiverJob.archiveNextBatch().join()).isEqualTo(Map.entry("NothingToArchive", 0));

    databaseTestExtension.refreshIndexesInElasticsearch();

    // then
    assertTasksInCorrectIndex(count1, ids1, endDate1);
    assertTasksInCorrectIndex(count2, ids2, null);
  }

  @Test
  public void shouldDeleteProcessInstanceRelatedData() throws ArchiverException, IOException {
    final Instant currentTime = pinZeebeTime();

    // having
    // deploy process
    offsetZeebeTime(Duration.ofDays(-4));
    final String processId = "demoProcess";
    final String flowNodeBpmnId = "task1";
    deployProcessWithOneFlowNode(processId, flowNodeBpmnId);

    // start and complete instances 2 hours ago
    final int count1 = random.nextInt(6) + 3;
    final Instant endDate1 = currentTime.minus(2, ChronoUnit.HOURS);
    final List<String> ids1 =
        startAndCompleteInstances(processId, flowNodeBpmnId, count1, endDate1);

    // start and cancel instances 2 hours ago
    final int count2 = random.nextInt(6) + 3;
    final List<String> ids2 = startAndCancelInstances(processId, flowNodeBpmnId, count2, endDate1);

    // start and finish instances 50 minutes ago
    final int count3 = random.nextInt(6) + 3;
    final Instant endDate2 = currentTime.minus(50, ChronoUnit.MINUTES);
    final List<String> ids3 =
        startAndCompleteInstances(processId, flowNodeBpmnId, count3, endDate2);

    resetZeebeTime();
    databaseTestExtension.refreshIndexesInElasticsearch();

    // when
    assertThat(processInstanceArchiverJob.archiveNextBatch().join().getValue())
        .isEqualTo(count1 + count2);
    databaseTestExtension.refreshIndexesInElasticsearch();
    // 2rd run should not move anything, as the rest of the tasks are completed less then 1 hour ago
    assertThat(processInstanceArchiverJob.archiveNextBatch().join())
        .isEqualTo(Map.entry("NothingToArchive", 0));

    databaseTestExtension.refreshIndexesInElasticsearch();

    // then
    assertProcessInstancesAreDeleted(ids1);
    assertProcessInstancesAreDeleted(ids2);
    assertProcessInstancesExist(ids3);
  }

  private void assertProcessInstancesExist(final List<String> ids) {
    assertThat(noSqlHelper.getProcessInstances(ids)).hasSize(ids.size());
  }

  private void assertProcessInstancesAreDeleted(final List<String> ids) {
    assertThat(noSqlHelper.getProcessInstances(ids)).isEmpty();
  }

  private void deployProcessWithOneFlowNode(String processId, String flowNodeBpmnId) {
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(processId)
            .startEvent("start")
            .userTask(flowNodeBpmnId)
            .endEvent()
            .done();
    tester.deployProcess(process, processId + ".bpmn").waitUntil().processIsDeployed();
  }

  private void assertTasksInCorrectIndex(int tasksCount, List<String> ids, Instant endDate)
      throws IOException {
    assertTaskIndex(tasksCount, ids, endDate);
    assertDependentIndex(
        taskVariableTemplate.getFullQualifiedName(), TaskVariableTemplate.TASK_ID, ids, endDate);
  }

  private void assertTaskIndex(int tasksCount, List<String> ids, Instant endDate)
      throws IOException {
    final String destinationIndexName;
    if (endDate != null) {
      destinationIndexName =
          archiverUtil.getDestinationIndexName(
              taskTemplate.getFullQualifiedName(), dateTimeFormatter.format(endDate));
    } else {
      destinationIndexName =
          archiverUtil.getDestinationIndexName(taskTemplate.getFullQualifiedName(), "");
    }

    final List<TaskEntity> tasksResponse =
        noSqlHelper.getTasksFromIdAndIndex(
            destinationIndexName, Arrays.stream(CollectionUtil.toSafeArrayOfStrings(ids)).toList());

    assertThat(tasksResponse).hasSize(tasksCount);
    assertThat(tasksResponse).extracting(TaskTemplate.ID).containsExactlyInAnyOrderElementsOf(ids);
    if (endDate != null) {
      assertThat(tasksResponse)
          .extracting(TaskTemplate.COMPLETION_TIME)
          .allMatch(ed -> ((OffsetDateTime) ed).toInstant().equals(endDate));
    }
  }

  private void assertDependentIndex(
      String mainIndexName, String idFieldName, List<String> ids, Instant endDate)
      throws IOException {
    final String destinationIndexName;
    if (endDate != null) {
      destinationIndexName =
          archiverUtil.getDestinationIndexName(mainIndexName, dateTimeFormatter.format(endDate));
    } else {
      destinationIndexName = archiverUtil.getDestinationIndexName(mainIndexName, "");
    }

    final List<String> idsFromEls =
        noSqlHelper.getIdsFromIndex(idFieldName, destinationIndexName, ids);
    assertThat(idsFromEls).as(mainIndexName).isSubsetOf(ids);
  }

  private List<String> startInstancesAndCompleteTasks(
      String processId, String flowNodeBpmnId, int count, Instant currentTime) {
    assertThat(count).isGreaterThan(0);
    pinZeebeTime(currentTime);
    final List<String> ids = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      ids.add(
          tester
              .startProcessInstance(processId, "{\"var\": 123}")
              .waitUntil()
              .taskIsCreated(flowNodeBpmnId)
              .claimAndCompleteHumanTask(flowNodeBpmnId)
              .waitUntil()
              .taskIsCompleted(flowNodeBpmnId)
              .getTaskId());
    }
    return ids;
  }

  private List<String> startAndCancelInstances(
      String processId, String flowNodeBpmnId, int count, Instant currentTime) {
    assertThat(count).isGreaterThan(0);
    pinZeebeTime(currentTime);
    final List<String> ids = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      ids.add(
          tester
              .startProcessInstance(processId, "{\"var\": 123}")
              .waitUntil()
              .taskIsCreated(flowNodeBpmnId)
              .and()
              .cancelProcessInstance()
              .waitUntil()
              .processInstanceIsCanceled()
              .getProcessInstanceId());
    }
    return ids;
  }

  private List<String> startAndCompleteInstances(
      String processId, String flowNodeBpmnId, int count, Instant currentTime) {
    assertThat(count).isGreaterThan(0);
    pinZeebeTime(currentTime);
    final List<String> ids = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      ids.add(
          tester
              .startProcessInstance(processId, "{\"var\": 123}")
              .waitUntil()
              .taskIsCreated(flowNodeBpmnId)
              .claimAndCompleteHumanTask(flowNodeBpmnId)
              .waitUntil()
              .processInstanceIsCompleted()
              .getProcessInstanceId());
    }
    return ids;
  }

  private List<String> startInstances(
      String processId, String flowNodeBpmnId, int count, Instant currentTime) {
    assertThat(count).isGreaterThan(0);
    pinZeebeTime(currentTime);
    final List<String> ids = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      ids.add(
          tester
              .startProcessInstance(processId, "{\"var\": 123}")
              .waitUntil()
              .taskIsCreated(flowNodeBpmnId)
              .getTaskId());
    }
    return ids;
  }
}
