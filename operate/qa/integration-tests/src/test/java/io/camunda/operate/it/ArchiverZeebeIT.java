/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.it;

import static io.camunda.operate.qa.util.RestAPITestUtil.createGetAllProcessInstancesQuery;
import static io.camunda.operate.qa.util.RestAPITestUtil.createGetAllProcessInstancesRequest;
import static io.camunda.operate.util.MetricAssert.assertThatMetricsFrom;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.containsString;

import io.camunda.operate.archiver.Archiver;
import io.camunda.operate.archiver.BatchOperationArchiverJob;
import io.camunda.operate.archiver.ProcessInstancesArchiverJob;
import io.camunda.operate.entities.BatchOperationEntity;
import io.camunda.operate.entities.OperationType;
import io.camunda.operate.exceptions.ArchiverException;
import io.camunda.operate.metric.ImporterMetricsZeebeImportIT.ManagementPropertyRemoval;
import io.camunda.operate.schema.templates.BatchOperationTemplate;
import io.camunda.operate.util.CollectionUtil;
import io.camunda.operate.util.MetricAssert;
import io.camunda.operate.util.OperateZeebeAbstractIT;
import io.camunda.operate.util.SearchTestRule;
import io.camunda.operate.util.ZeebeTestUtil;
import io.camunda.operate.util.searchrepository.TestSearchRepository;
import io.camunda.operate.webapp.reader.ListViewReader;
import io.camunda.operate.webapp.rest.dto.listview.ListViewQueryDto;
import io.camunda.operate.webapp.rest.dto.listview.ListViewRequestDto;
import io.camunda.operate.webapp.rest.dto.listview.ListViewResponseDto;
import io.camunda.operate.webapp.rest.dto.operation.CreateBatchOperationRequestDto;
import io.camunda.operate.webapp.writer.BatchOperationWriter;
import io.camunda.operate.webapp.zeebe.operation.CancelProcessInstanceHandler;
import io.camunda.webapps.schema.descriptors.operate.ProcessInstanceDependant;
import io.camunda.webapps.schema.descriptors.operate.template.IncidentTemplate;
import io.camunda.webapps.schema.descriptors.operate.template.ListViewTemplate;
import io.camunda.webapps.schema.descriptors.operate.template.SequenceFlowTemplate;
import io.camunda.webapps.schema.entities.operate.listview.ProcessInstanceForListViewEntity;
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
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;

@ContextConfiguration(initializers = ManagementPropertyRemoval.class)
public class ArchiverZeebeIT extends OperateZeebeAbstractIT {
  @Rule public SearchTestRule searchTestRule = new SearchTestRule();

  @Autowired private TestSearchRepository testSearchRepository;

  @Autowired private ListViewReader listViewReader;

  @Autowired private BeanFactory beanFactory;

  @Autowired private Archiver archiver;

  @Autowired private ListViewTemplate processInstanceTemplate;

  @Autowired private BatchOperationTemplate batchOperationTemplate;

  @Autowired private BatchOperationWriter batchOperationWriter;

  @Autowired private List<ProcessInstanceDependant> processInstanceDependantTemplates;

  @Autowired private CancelProcessInstanceHandler cancelProcessInstanceHandler;

  private ProcessInstancesArchiverJob archiverJob;

  private final Random random = new Random();

  private DateTimeFormatter dateTimeFormatter;

  @Override
  @Before
  public void before() {
    super.before();
    dateTimeFormatter =
        DateTimeFormatter.ofPattern(operateProperties.getArchiver().getRolloverDateFormat())
            .withZone(ZoneId.systemDefault());
    archiverJob =
        beanFactory.getBean(
            ProcessInstancesArchiverJob.class, archiver, partitionHolder.getPartitionIds());
    cancelProcessInstanceHandler.setZeebeClient(super.getClient());
    clearMetrics();
  }

  @Test
  public void testArchivingProcessInstances() throws ArchiverException, IOException {

    final Instant currentTime = pinZeebeTime();

    // having
    // deploy process
    offsetZeebeTime(Duration.ofDays(-4));
    final String processId = "demoProcess";
    final String activityId = "task1";
    deployProcessWithOneActivity(processId, activityId);

    // start instances 3 days ago
    final int count1 = random.nextInt(6) + 5;
    final List<Long> ids1 =
        startInstances(processId, count1, currentTime.minus(3, ChronoUnit.DAYS));
    createOperations(ids1);
    // finish instances 2 days ago
    final Instant endDate1 = currentTime.minus(2, ChronoUnit.DAYS);
    finishInstances(count1, endDate1, activityId);
    searchTestRule.processAllRecordsAndWait(processInstancesAreFinishedCheck, ids1);

    // start instances 2 days ago
    final int count2 = random.nextInt(6) + 5;
    final List<Long> ids2 = startInstances(processId, count2, endDate1);
    createOperations(ids2);
    // finish instances 1 day ago
    final Instant endDate2 = currentTime.minus(1, ChronoUnit.DAYS);
    finishInstances(count2, endDate2, activityId);
    searchTestRule.processAllRecordsAndWait(processInstancesAreFinishedCheck, ids2);

    // assert metrics for finished process instances
    assertThatMetricsFrom(
        mockMvc,
        new MetricAssert.ValueMatcher(
            "operate_events_processed_finished_process_instances_total",
            d -> d.doubleValue() == count1 + count2));

    // start instances 1 day ago
    final int count3 = random.nextInt(6) + 5;
    final List<Long> ids3 = startInstances(processId, count3, endDate2);

    resetZeebeTime();

    // when
    assertThat(archiverJob.archiveNextBatch().join()).isEqualTo(count1);
    searchTestRule.refreshSerchIndexes();
    assertThat(archiverJob.archiveNextBatch().join()).isEqualTo(count2);
    searchTestRule.refreshSerchIndexes();
    assertThat(archiverJob.archiveNextBatch().join())
        .isEqualTo(
            0); // 3rd run should not move anything, as the rest of the instances are not completed

    searchTestRule.refreshSerchIndexes();

    // then
    assertInstancesInCorrectIndex(count1, ids1, endDate1, true);
    assertInstancesInCorrectIndex(count2, ids2, endDate2, true);
    assertInstancesInCorrectIndex(count3, ids3, null, true);

    assertAllInstancesInAlias(count1 + count2 + count3);

    // assert metrics for archived process instances
    assertThatMetricsFrom(
        mockMvc,
        allOf(
            new MetricAssert.ValueMatcher(
                "operate_archived_process_instances_total",
                d -> d.doubleValue() == count1 + count2),
            containsString("operate_archiver_query"),
            containsString("operate_archiver_reindex_query"),
            containsString("operate_archiver_delete_query")));
  }

  protected void createOperations(final List<Long> ids1) {
    final ListViewQueryDto query = createGetAllProcessInstancesQuery();
    query.setIds(CollectionUtil.toSafeListOfStrings(ids1));
    final CreateBatchOperationRequestDto batchOperationRequest =
        new CreateBatchOperationRequestDto(
            query, OperationType.CANCEL_PROCESS_INSTANCE); // the type does not matter
    batchOperationWriter.scheduleBatchOperation(batchOperationRequest);
  }

  private void assertAllInstancesInAlias(final int count) {
    final ListViewRequestDto request = createGetAllProcessInstancesRequest();
    request.setPageSize(count + 100);
    final ListViewResponseDto responseDto = listViewReader.queryProcessInstances(request);
    assertThat(responseDto.getTotalCount()).isEqualTo(count);
  }

  @Test
  public void testArchivingBatchOperations() throws Exception {
    // having
    // create batch operations
    final OffsetDateTime now = OffsetDateTime.now();
    final OffsetDateTime twoHoursAgo = now.minus(2, ChronoUnit.HOURS);
    final BatchOperationEntity bo1 = createBatchOperationEntity(now);
    searchTestRule.persistNew(bo1);
    final BatchOperationEntity bo2 = createBatchOperationEntity(twoHoursAgo);
    searchTestRule.persistNew(bo2);
    final BatchOperationEntity bo3 = createBatchOperationEntity(twoHoursAgo);
    searchTestRule.persistNew(bo3);

    // when
    final BatchOperationArchiverJob batchOperationArchiverJob =
        beanFactory.getBean(BatchOperationArchiverJob.class);
    final int count = batchOperationArchiverJob.archiveNextBatch().join();
    assertThat(count).isEqualTo(2);
    searchTestRule.refreshOperateSearchIndices();

    // then
    assertBatchOperationsInCorrectIndex(
        2, Arrays.asList(bo2.getId(), bo3.getId()), twoHoursAgo.toInstant());
    assertBatchOperationsInCorrectIndex(1, Arrays.asList(bo1.getId()), null);
  }

  @Test
  public void testArchivingDecisionInstances() throws Exception {

    final Instant currentTime = pinZeebeTime();

    // having
    final Instant endDate = currentTime.minus(4, ChronoUnit.DAYS);
    pinZeebeTime(endDate);

    final String bpmnProcessId = "process";
    final String demoDecisionId2 = "invoiceAssignApprover";

    final String elementId = "task";
    final BpmnModelInstance instance =
        Bpmn.createExecutableProcess(bpmnProcessId)
            .startEvent()
            .businessRuleTask(
                elementId,
                task ->
                    task.zeebeCalledDecisionId(demoDecisionId2)
                        .zeebeResultVariable("approverGroups"))
            .done();

    final Long processInstanceKey =
        tester
            .deployProcess(instance, "test.bpmn")
            .deployDecision("invoiceBusinessDecisions_v_1.dmn")
            .waitUntil()
            .processIsDeployed()
            .and()
            .decisionsAreDeployed(2)
            // when
            .startProcessInstance(bpmnProcessId, "{\"amount\": 100, \"invoiceCategory\": \"Misc\"}")
            .waitUntil()
            .decisionInstancesAreCreated(2)
            .processInstanceIsCompleted()
            .getProcessInstanceKey();

    resetZeebeTime();

    assertThat(archiverJob.archiveNextBatch().join()).isEqualTo(1);
    searchTestRule.refreshSerchIndexes();
    assertInstancesInCorrectIndex(1, Arrays.asList(processInstanceKey), endDate, true);
  }

  private BatchOperationEntity createBatchOperationEntity(final OffsetDateTime endDate) {
    final BatchOperationEntity batchOperationEntity1 = new BatchOperationEntity();
    batchOperationEntity1.generateId();
    batchOperationEntity1.setStartDate(endDate.minus(5, ChronoUnit.MINUTES));
    batchOperationEntity1.setEndDate(endDate);
    return batchOperationEntity1;
  }

  @Test
  public void testArchivingOnlyOneHourOldData() throws ArchiverException, IOException {
    final Instant currentTime = pinZeebeTime();

    // having
    // deploy process
    offsetZeebeTime(Duration.ofDays(-4));
    final String processId = "demoProcess";
    final String activityId = "task1";
    deployProcessWithOneActivity(processId, activityId);

    // start instances 2 hours ago
    final int count1 = random.nextInt(6) + 5;
    final List<Long> ids1 =
        startInstances(processId, count1, currentTime.minus(2, ChronoUnit.HOURS));
    createOperations(ids1);
    // finish instances 1 hour ago
    final Instant endDate1 = currentTime.minus(1, ChronoUnit.HOURS);
    finishInstances(count1, endDate1, activityId);
    searchTestRule.processAllRecordsAndWait(processInstancesAreFinishedCheck, ids1);

    // start instances 1 hour ago
    final int count2 = random.nextInt(6) + 5;
    final List<Long> ids2 =
        startInstances(processId, count2, currentTime.minus(1, ChronoUnit.HOURS));
    // finish instances 59 minutes ago
    final Instant endDate2 = currentTime.minus(50, ChronoUnit.MINUTES);
    finishInstances(count2, endDate2, activityId);
    searchTestRule.processAllRecordsAndWait(processInstancesAreFinishedCheck, ids2);

    resetZeebeTime();

    // when
    assertThat(archiverJob.archiveNextBatch().join()).isEqualTo(count1);
    searchTestRule.refreshSerchIndexes();
    // 2rd run should not move anything, as the rest of the instances are somcpleted less then 1
    // hour ago
    assertThat(archiverJob.archiveNextBatch().join()).isEqualTo(0);

    searchTestRule.refreshSerchIndexes();

    // then
    assertInstancesInCorrectIndex(count1, ids1, endDate1, true);
    assertInstancesInCorrectIndex(count2, ids2, null);
  }

  /**
   * This test takes long time to run, but can be unignored to test archiving of one big process
   * instance locally. Related with OPE-1071.
   *
   * @throws ArchiverException
   * @throws IOException
   */
  @Test
  @Ignore
  public void testArchivingBigInstance() throws ArchiverException, IOException {
    final Instant currentTime = pinZeebeTime();

    // having
    // deploy process
    final Instant endDate = currentTime.minus(4, ChronoUnit.DAYS);
    pinZeebeTime(endDate);
    final Long processDefinitionKey = deployProcess("sequential-noop.bpmn");
    searchTestRule.processAllRecordsAndWait(processIsDeployedCheck, processDefinitionKey);
    final String processId = "sequential-noop";

    // start instance with 3000 of vars in loop
    final String payload =
        "{\"items\": ["
            + IntStream.range(1, 3000)
                .boxed()
                .map(Object::toString)
                .collect(Collectors.joining(","))
            + "]}";
    final long processInstanceKey =
        ZeebeTestUtil.startProcessInstance(zeebeClient, processId, payload);
    // wait till it's finished
    searchTestRule.processAllRecordsAndWait(
        400, processInstanceIsCompletedCheck, processInstanceKey);

    resetZeebeTime();

    // when
    assertThat(archiverJob.archiveNextBatch().join()).isEqualTo(1);
    searchTestRule.refreshSerchIndexes();

    // then
    assertInstancesInCorrectIndex(1, Arrays.asList(processInstanceKey), endDate, true);
  }

  private void deployProcessWithOneActivity(final String processId, final String activityId) {
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(processId)
            .startEvent("start")
            .serviceTask(activityId)
            .zeebeJobType(activityId)
            .endEvent()
            .done();
    deployProcess(process, processId + ".bpmn");
  }

  private void assertBatchOperationsInCorrectIndex(
      final int instancesCount, final List<String> ids, final Instant endDate) throws IOException {
    final String destinationIndexName;
    if (endDate != null) {
      destinationIndexName =
          archiver.getDestinationIndexName(
              batchOperationTemplate.getFullQualifiedName(), dateTimeFormatter.format(endDate));
    } else {
      destinationIndexName =
          archiver.getDestinationIndexName(batchOperationTemplate.getFullQualifiedName(), "");
    }

    final List<BatchOperationEntity> bos =
        testSearchRepository.getBatchOperationEntities(destinationIndexName, ids);

    assertThat(bos).hasSize(instancesCount);
    assertThat(bos).extracting(BatchOperationTemplate.ID).containsExactlyInAnyOrderElementsOf(ids);
  }

  private void assertInstancesInCorrectIndex(
      final int instancesCount, final List<Long> ids, final Instant endDate) throws IOException {
    assertInstancesInCorrectIndex(instancesCount, ids, endDate, false);
  }

  private void assertInstancesInCorrectIndex(
      final int instancesCount,
      final List<Long> ids,
      final Instant endDate,
      final boolean ignoreAbsentIndex)
      throws IOException {
    assertProcessInstanceIndex(instancesCount, ids, endDate);
    for (final ProcessInstanceDependant template : processInstanceDependantTemplates) {
      if (!(template instanceof IncidentTemplate || template instanceof SequenceFlowTemplate)) {
        assertDependentIndex(
            template.getFullQualifiedName(),
            ProcessInstanceDependant.PROCESS_INSTANCE_KEY,
            ids,
            endDate,
            ignoreAbsentIndex);
      }
    }
  }

  private void assertProcessInstanceIndex(
      final int instancesCount, final List<Long> ids, final Instant endDate) throws IOException {
    final String destinationIndexName;
    if (endDate != null) {
      destinationIndexName =
          archiver.getDestinationIndexName(
              processInstanceTemplate.getFullQualifiedName(), dateTimeFormatter.format(endDate));
    } else {
      destinationIndexName =
          archiver.getDestinationIndexName(processInstanceTemplate.getFullQualifiedName(), "");
    }

    final List<ProcessInstanceForListViewEntity> processInstances =
        testSearchRepository.getProcessInstances(destinationIndexName, ids);
    assertThat(processInstances)
        .extracting(ListViewTemplate.PROCESS_INSTANCE_KEY)
        .containsExactlyInAnyOrderElementsOf(ids);
    if (endDate != null) {
      assertThat(processInstances)
          .extracting(ListViewTemplate.END_DATE)
          .allMatch(ed -> ((OffsetDateTime) ed).toInstant().equals(endDate));
    }
    // TODO assert children records - activities
  }

  private void assertDependentIndex(
      final String mainIndexName,
      final String idFieldName,
      final List<Long> ids,
      final Instant endDate,
      final boolean ignoreAbsentIndex)
      throws IOException {
    final String destinationIndexName;
    if (endDate != null) {
      destinationIndexName =
          archiver.getDestinationIndexName(mainIndexName, dateTimeFormatter.format(endDate));
    } else {
      destinationIndexName = archiver.getDestinationIndexName(mainIndexName, "");
    }

    final Optional<List<Long>> maybeIdsFromDstIndex =
        testSearchRepository.getIds(destinationIndexName, idFieldName, ids, ignoreAbsentIndex);

    maybeIdsFromDstIndex.ifPresent(
        idsFromDstIndex -> assertThat(idsFromDstIndex).as(mainIndexName).isSubsetOf(ids));
  }

  private void finishInstances(final int count, final Instant currentTime, final String taskId) {
    pinZeebeTime(currentTime);
    ZeebeTestUtil.completeTask(getClient(), taskId, getWorkerName(), null, count);
  }

  private List<Long> startInstances(
      final String processId, final int count, final Instant currentTime) {
    assertThat(count).isGreaterThan(0);
    pinZeebeTime(currentTime);
    final List<Long> ids = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      ids.add(ZeebeTestUtil.startProcessInstance(zeebeClient, processId, "{\"var\": 123}"));
    }
    searchTestRule.processAllRecordsAndWait(processInstancesAreStartedCheck, ids);
    return ids;
  }

  // OPE-671
  @Test
  public void testArchivedOperationsWillNotBeLocked() throws Exception {
    // given (set up) : disabled OperationExecutor
    tester.disableOperationExecutor();
    // and given processInstance
    final String bpmnProcessId = "startEndProcess";
    final String taskId = "task";
    final BpmnModelInstance startEndProcess =
        Bpmn.createExecutableProcess(bpmnProcessId)
            .startEvent()
            .serviceTask(taskId)
            .zeebeJobType(taskId)
            .endEvent()
            .done();

    tester
        .deployProcess(startEndProcess, "startEndProcess.bpmn")
        .processIsDeployed()
        .and()
        .startProcessInstance(bpmnProcessId)
        .waitUntil()
        .processInstanceIsStarted()
        .and()
        // when
        // 1. Schedule operation (with disabled operation executor)
        .cancelProcessInstanceOperation()
        // 2. Finish process instance
        .and()
        .completeTask(taskId)
        .waitUntil()
        .processInstanceIsFinished()
        // 3. Wait till process instance is archived
        .archive()
        .waitUntil()
        .archiveIsDone()
        // 4. Enable the operation executor
        .then()
        .enableOperationExecutor();
  }
}
