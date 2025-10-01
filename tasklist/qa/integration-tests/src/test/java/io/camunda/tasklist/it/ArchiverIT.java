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
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.tasklist.archiver.ArchiverUtil;
import io.camunda.tasklist.archiver.ProcessInstanceArchiverJob;
import io.camunda.tasklist.archiver.TaskArchiverJob;
import io.camunda.tasklist.entities.TaskEntity;
import io.camunda.tasklist.exceptions.ArchiverException;
import io.camunda.tasklist.schema.indices.ProcessInstanceIndex;
import io.camunda.tasklist.schema.templates.TaskTemplate;
import io.camunda.tasklist.schema.templates.TaskVariableTemplate;
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
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
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
  @Autowired private ProcessInstanceIndex processInstanceIndex;

  @Autowired
  @Qualifier(PROCESS_INSTANCE_IS_COMPLETED_CHECK)
  private TestCheck processInstanceIsCompletedCheck;

  @Autowired
  @Qualifier(PROCESS_INSTANCE_IS_CANCELED_CHECK)
  private TestCheck processInstanceIsCanceledCheck;

  private TaskArchiverJob archiverJob;
  private ProcessInstanceArchiverJob processInstanceArchiverJob;

  private final Random random = new Random();

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

  @Override
  @AfterEach
  public void after() {
    tasklistProperties.getArchiver().setRolloverInterval("1d");
    super.after();
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

  private void assertAllInstancesInAlias(final int count, final String id) throws IOException {
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

  private static Stream<Arguments> archiverTestInputs() {
    return Stream.of(
        Arguments.of(
            LocalDate.of(2024, 10, 10).atTime(13, 13).toInstant(ZoneOffset.UTC),
            "1w",
            LocalDate.of(2024, 10, 7).atStartOfDay().toInstant(ZoneOffset.UTC)
            // 1-week interval so the 10th date will fall in the 7-14 bucket
            ),
        Arguments.of(
            LocalDate.of(2024, 10, 10).atTime(13, 13).toInstant(ZoneOffset.UTC),
            "1d",
            LocalDate.of(2024, 10, 10).atStartOfDay().toInstant(ZoneOffset.UTC)),
        Arguments.of(
            LocalDate.of(2024, 10, 10).atTime(13, 13).toInstant(ZoneOffset.UTC),
            "1M",
            LocalDate.of(2024, 10, 1).atStartOfDay().toInstant(ZoneOffset.UTC)),
        // 1-month interval so 10th date will fall into 1-31 bucket
        Arguments.of(
            LocalDate.of(2024, 10, 16).atTime(13, 13).toInstant(ZoneOffset.UTC),
            "1w",
            LocalDate.of(2024, 10, 14).atStartOfDay().toInstant(ZoneOffset.UTC))
        // 1-week interval so 16th will fall into 14-21 bucket
        );
  }

  @ParameterizedTest
  @MethodSource("archiverTestInputs")
  public void shouldApplyRolloverIntervalCorrectly(
      final Instant taskEndTime,
      final String rolloverInterval,
      final Instant expectedArchiveBucket) {
    // given
    tasklistProperties.getArchiver().setRolloverInterval(rolloverInterval);

    // deploy process
    final String processId = "demoProcess";
    final String flowNodeBpmnId = "task1";
    deployProcessWithOneFlowNode(processId, flowNodeBpmnId);

    // when
    final List<String> id =
        startInstancesAndCompleteTasks(processId, flowNodeBpmnId, 1, taskEndTime);

    // Required as a "tick" or the completion time of the above task will be incorrect.
    startInstances(processId, flowNodeBpmnId, 1, Instant.now());

    assertThat(archiverJob.archiveNextBatch().join().getValue()).isEqualTo(1);
    databaseTestExtension.refreshIndexesInElasticsearch();

    // 2nd run should not move anything, as the rest of the tasks are completed less then 1 hour ago
    assertThat(archiverJob.archiveNextBatch().join()).isEqualTo(Map.entry("NothingToArchive", 0));
    databaseTestExtension.refreshIndexesInElasticsearch();

    // then
    await()
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(() -> assertTasksInCorrectIndex(1, id, expectedArchiveBucket, taskEndTime));
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

  @Test
  public void shouldArchiveDocumentsOnlyFromDeclaredProcessInstanceIndex() throws IOException {
    final String piCustomPrefix = UUID.randomUUID().toString().substring(0, 10);
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

    resetZeebeTime();
    databaseTestExtension.refreshIndexesInElasticsearch();

    final List<String> allIds = new ArrayList<>();
    allIds.addAll(ids1);
    allIds.addAll(ids2);
    databaseTestExtension.createIndex(piCustomPrefix + processInstanceIndex.getIndexName());

    try {
      databaseTestExtension.reindex(
          processInstanceIndex.getFullQualifiedName(),
          piCustomPrefix + processInstanceIndex.getIndexName());
      // when
      assertThat(processInstanceArchiverJob.archiveNextBatch().join().getValue())
          .isEqualTo(count1 + count2);
      databaseTestExtension.refreshIndexesInElasticsearch();
      // 2rd run should not move anything, as no processes are left
      assertThat(processInstanceArchiverJob.archiveNextBatch().join())
          .isEqualTo(Map.entry("NothingToArchive", 0));

      final List<String> customIndexIds =
          noSqlHelper.getIdsFromIndex(
              ProcessInstanceIndex.ID,
              piCustomPrefix + processInstanceIndex.getIndexName(),
              allIds);
      assertThat(customIndexIds).containsExactlyInAnyOrderElementsOf(allIds);
      assertThat(noSqlHelper.getProcessInstances(allIds)).isEmpty();
    } finally {
      databaseTestExtension.deleteIndex(piCustomPrefix + processInstanceIndex.getIndexName());
    }
  }

  private void assertProcessInstancesExist(final List<String> ids) {
    assertThat(noSqlHelper.getProcessInstances(ids)).hasSize(ids.size());
  }

  private void assertProcessInstancesAreDeleted(final List<String> ids) {
    assertThat(noSqlHelper.getProcessInstances(ids)).isEmpty();
  }

  private void deployProcessWithOneFlowNode(final String processId, final String flowNodeBpmnId) {
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(processId)
            .startEvent("start")
            .userTask(flowNodeBpmnId)
            .endEvent()
            .done();
    tester.deployProcess(process, processId + ".bpmn").waitUntil().processIsDeployed();
  }

  private void assertTasksInCorrectIndex(
      final int tasksCount, final List<String> ids, final Instant endDate) throws IOException {
    assertTasksInCorrectIndex(tasksCount, ids, endDate, endDate);
  }

  private void assertTasksInCorrectIndex(
      final int tasksCount,
      final List<String> ids,
      final Instant archiveIndexDateStamp,
      final Instant taskCompletionDate)
      throws IOException {
    assertTaskIndex(tasksCount, ids, archiveIndexDateStamp, taskCompletionDate);
    assertDependentIndex(
        taskVariableTemplate.getFullQualifiedName(),
        TaskVariableTemplate.TASK_ID,
        ids,
        archiveIndexDateStamp);
  }

  private void assertTaskIndex(
      final int tasksCount,
      final List<String> ids,
      final Instant archiveIndexDateStamp,
      final Instant taskCompletionDate)
      throws IOException {
    final String destinationIndexName;
    if (archiveIndexDateStamp != null) {
      destinationIndexName =
          archiverUtil.getDestinationIndexName(
              taskTemplate.getFullQualifiedName(), dateTimeFormatter.format(archiveIndexDateStamp));
    } else {
      destinationIndexName =
          archiverUtil.getDestinationIndexName(taskTemplate.getFullQualifiedName(), "");
    }

    final List<TaskEntity> tasksResponse =
        noSqlHelper.getTasksFromIdAndIndex(
            destinationIndexName, Arrays.stream(CollectionUtil.toSafeArrayOfStrings(ids)).toList());

    assertThat(tasksResponse).hasSize(tasksCount);
    assertThat(tasksResponse).extracting(TaskTemplate.ID).containsExactlyInAnyOrderElementsOf(ids);
    if (taskCompletionDate != null) {
      assertThat(tasksResponse)
          .extracting(TaskTemplate.COMPLETION_TIME)
          .allMatch(ed -> ((OffsetDateTime) ed).toInstant().equals(taskCompletionDate));
    }
  }

  private void assertDependentIndex(
      final String mainIndexName,
      final String idFieldName,
      final List<String> ids,
      final Instant endDate)
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
      final String processId,
      final String flowNodeBpmnId,
      final int count,
      final Instant currentTime) {
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
      final String processId,
      final String flowNodeBpmnId,
      final int count,
      final Instant currentTime) {
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
      final String processId,
      final String flowNodeBpmnId,
      final int count,
      final Instant currentTime) {
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
      final String processId,
      final String flowNodeBpmnId,
      final int count,
      final Instant currentTime) {
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
