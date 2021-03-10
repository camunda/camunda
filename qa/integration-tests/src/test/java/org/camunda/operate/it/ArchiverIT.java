/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.operate.schema.templates.ListViewTemplate.JOIN_RELATION;
import static org.camunda.operate.schema.templates.ListViewTemplate.WORKFLOW_INSTANCE_JOIN_RELATION;
import static org.camunda.operate.util.ElasticsearchUtil.joinWithAnd;
import static org.camunda.operate.util.MetricAssert.assertThatMetricsFrom;
import static org.elasticsearch.index.query.QueryBuilders.constantScoreQuery;
import static org.elasticsearch.index.query.QueryBuilders.idsQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.index.query.QueryBuilders.termsQuery;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.containsString;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import java.io.IOException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.camunda.operate.archiver.Archiver;
import org.camunda.operate.archiver.BatchOperationArchiverJob;
import org.camunda.operate.archiver.WorkflowInstancesArchiverJob;
import org.camunda.operate.entities.BatchOperationEntity;
import org.camunda.operate.entities.OperationType;
import org.camunda.operate.entities.listview.WorkflowInstanceForListViewEntity;
import org.camunda.operate.exceptions.ArchiverException;
import org.camunda.operate.schema.templates.BatchOperationTemplate;
import org.camunda.operate.schema.templates.IncidentTemplate;
import org.camunda.operate.schema.templates.ListViewTemplate;
import org.camunda.operate.schema.templates.SequenceFlowTemplate;
import org.camunda.operate.schema.templates.WorkflowInstanceDependant;
import org.camunda.operate.util.CollectionUtil;
import org.camunda.operate.util.ElasticsearchUtil;
import org.camunda.operate.util.MetricAssert;
import org.camunda.operate.util.OperateZeebeIntegrationTest;
import org.camunda.operate.util.TestUtil;
import org.camunda.operate.util.ZeebeTestUtil;
import org.camunda.operate.webapp.es.reader.ListViewReader;
import org.camunda.operate.webapp.es.writer.BatchOperationWriter;
import org.camunda.operate.webapp.rest.dto.listview.ListViewQueryDto;
import org.camunda.operate.webapp.rest.dto.listview.ListViewRequestDto;
import org.camunda.operate.webapp.rest.dto.listview.ListViewResponseDto;
import org.camunda.operate.webapp.rest.dto.operation.CreateBatchOperationRequestDto;
import org.camunda.operate.webapp.zeebe.operation.CancelWorkflowInstanceHandler;
import org.elasticsearch.ElasticsearchStatusException;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.IdsQueryBuilder;
import org.elasticsearch.index.query.TermQueryBuilder;
import org.elasticsearch.index.query.TermsQueryBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;

public class ArchiverIT extends OperateZeebeIntegrationTest {

  @Autowired
  private ListViewReader listViewReader;

  @Autowired
  private BeanFactory beanFactory;

  @Autowired
  private Archiver archiver;

  @Autowired
  private RestHighLevelClient esClient;

  @Autowired
  private ListViewTemplate workflowInstanceTemplate;

  @Autowired
  private BatchOperationTemplate batchOperationTemplate;

  @Autowired
  private BatchOperationWriter batchOperationWriter;

  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private List<WorkflowInstanceDependant> workflowInstanceDependantTemplates;

  @Autowired
  private CancelWorkflowInstanceHandler cancelWorkflowInstanceHandler;

  private WorkflowInstancesArchiverJob archiverJob;

  private Random random = new Random();

  private DateTimeFormatter dateTimeFormatter;

  @Before
  public void before() {
    super.before();
    dateTimeFormatter = DateTimeFormatter.ofPattern(operateProperties.getArchiver().getRolloverDateFormat()).withZone(ZoneId.systemDefault());
    archiverJob = beanFactory.getBean(WorkflowInstancesArchiverJob.class, partitionHolder.getPartitionIds());
    cancelWorkflowInstanceHandler.setZeebeClient(super.getClient());
    clearMetrics();
  }

  @Test
  public void testArchivingWorkflowInstances() throws ArchiverException, IOException {
    brokerRule.getClock().pinCurrentTime();
    final Instant currentTime = brokerRule.getClock().getCurrentTime();

    //having
    //deploy process
    brokerRule.getClock().setCurrentTime(currentTime.minus(4, ChronoUnit.DAYS));
    String processId = "demoProcess";
    final String activityId = "task1";
    deployProcessWithOneActivity(processId, activityId);

    //start instances 3 days ago
    int count1 = random.nextInt(6) + 5;
    final List<Long> ids1 = startInstances(processId, count1, currentTime.minus(3, ChronoUnit.DAYS));
    createOperations(ids1);
    //finish instances 2 days ago
    final Instant endDate1 = currentTime.minus(2, ChronoUnit.DAYS);
    finishInstances(count1, endDate1, activityId);
    elasticsearchTestRule.processAllRecordsAndWait(workflowInstancesAreFinishedCheck, ids1);

    //start instances 2 days ago
    int count2 = random.nextInt(6) + 5;
    final List<Long> ids2 = startInstances(processId, count2, endDate1);
    createOperations(ids2);
    //finish instances 1 day ago
    final Instant endDate2 = currentTime.minus(1, ChronoUnit.DAYS);
    finishInstances(count2, endDate2, activityId);
    elasticsearchTestRule.processAllRecordsAndWait(workflowInstancesAreFinishedCheck, ids2);

    //assert metrics for finished workflow instances
    assertThatMetricsFrom(mockMvc, new MetricAssert.ValueMatcher("operate_events_processed_finished_workflow_instances_total", d -> d.doubleValue() == count1 + count2));

    //start instances 1 day ago
    int count3 = random.nextInt(6) + 5;
    final List<Long> ids3 = startInstances(processId, count3, endDate2);

    brokerRule.getClock().setCurrentTime(currentTime);

    //when
    assertThat(archiverJob.archiveNextBatch()).isEqualTo(count1);
    elasticsearchTestRule.refreshIndexesInElasticsearch();
    assertThat(archiverJob.archiveNextBatch()).isEqualTo(count2);
    elasticsearchTestRule.refreshIndexesInElasticsearch();
    assertThat(archiverJob.archiveNextBatch()).isEqualTo(0);     //3rd run should not move anything, as the rest of the instances are not completed

    elasticsearchTestRule.refreshIndexesInElasticsearch();

    //then
    assertInstancesInCorrectIndex(count1, ids1, endDate1);
    assertInstancesInCorrectIndex(count2, ids2, endDate2);
    assertInstancesInCorrectIndex(count3, ids3, null);

    assertAllInstancesInAlias(count1 + count2 + count3);

    //assert metrics for archived workflow instances
    assertThatMetricsFrom(mockMvc, allOf(
            new MetricAssert.ValueMatcher("operate_archived_workflow_instances_total", d -> d.doubleValue() == count1 + count2),
            containsString("operate_archiver_query"),
            containsString("operate_archiver_reindex_query"),
            containsString("operate_archiver_delete_query")
        ));
  }

  protected void createOperations(List<Long> ids1) {
    final ListViewQueryDto query = TestUtil.createGetAllWorkflowInstancesQuery();
    query.setIds(CollectionUtil.toSafeListOfStrings(ids1));
    CreateBatchOperationRequestDto batchOperationRequest = new CreateBatchOperationRequestDto(query, OperationType.CANCEL_WORKFLOW_INSTANCE);   //the type does not matter
    batchOperationWriter.scheduleBatchOperation(batchOperationRequest);
  }

  private void assertAllInstancesInAlias(int count) {
    final ListViewRequestDto request = TestUtil.createGetAllWorkflowInstancesRequest();
    request.setPageSize(count + 100);
    final ListViewResponseDto responseDto = listViewReader.queryWorkflowInstances(request);
    assertThat(responseDto.getTotalCount()).isEqualTo(count);
  }

  @Test
  public void testArchivingBatchOperations() throws Exception {
    //having
    //create batch operations
    OffsetDateTime now = OffsetDateTime.now();
    OffsetDateTime twoHoursAgo = now.minus(2, ChronoUnit.HOURS);
    BatchOperationEntity bo1 = createBatchOperationEntity(now);
    elasticsearchTestRule.persistNew(bo1);
    BatchOperationEntity bo2 = createBatchOperationEntity(twoHoursAgo);
    elasticsearchTestRule.persistNew(bo2);
    BatchOperationEntity bo3 = createBatchOperationEntity(twoHoursAgo);
    elasticsearchTestRule.persistNew(bo3);

    //when
    BatchOperationArchiverJob batchOperationArchiverJob = beanFactory.getBean(BatchOperationArchiverJob.class);
    int count = batchOperationArchiverJob.archiveNextBatch();
    assertThat(count).isEqualTo(2);
    elasticsearchTestRule.refreshOperateESIndices();

    //then
    assertBatchOperationsInCorrectIndex(2, Arrays.asList(bo2.getId(), bo3.getId()), twoHoursAgo.toInstant());
    assertBatchOperationsInCorrectIndex(1, Arrays.asList(bo1.getId()), null);
  }

  private BatchOperationEntity createBatchOperationEntity(OffsetDateTime endDate) {
    BatchOperationEntity batchOperationEntity1 = new BatchOperationEntity();
    batchOperationEntity1.generateId();
    batchOperationEntity1.setStartDate(endDate.minus(5, ChronoUnit.MINUTES));
    batchOperationEntity1.setEndDate(endDate);
    return batchOperationEntity1;
  }

  @Test
  public void testArchivingOnlyOneHourOldData() throws ArchiverException, IOException {
    brokerRule.getClock().pinCurrentTime();
    final Instant currentTime = brokerRule.getClock().getCurrentTime();

    //having
    //deploy process
    brokerRule.getClock().setCurrentTime(currentTime.minus(4, ChronoUnit.DAYS));
    String processId = "demoProcess";
    final String activityId = "task1";
    deployProcessWithOneActivity(processId, activityId);

    //start instances 2 hours ago
    int count1 = random.nextInt(6) + 5;
    final List<Long> ids1 = startInstances(processId, count1, currentTime.minus(2, ChronoUnit.HOURS));
    createOperations(ids1);
    //finish instances 1 hour ago
    final Instant endDate1 = currentTime.minus(1, ChronoUnit.HOURS);
    finishInstances(count1, endDate1, activityId);
    elasticsearchTestRule.processAllRecordsAndWait(workflowInstancesAreFinishedCheck, ids1);

    //start instances 1 hour ago
    int count2 = random.nextInt(6) + 5;
    final List<Long> ids2 = startInstances(processId, count2, currentTime.minus(1, ChronoUnit.HOURS));
    //finish instances 59 minutes ago
    final Instant endDate2 = currentTime.minus(50, ChronoUnit.MINUTES);
    finishInstances(count2, endDate2, activityId);
    elasticsearchTestRule.processAllRecordsAndWait(workflowInstancesAreFinishedCheck, ids2);

    brokerRule.getClock().setCurrentTime(currentTime);

    //when
    assertThat(archiverJob.archiveNextBatch()).isEqualTo(count1);
    elasticsearchTestRule.refreshIndexesInElasticsearch();
    //2rd run should not move anything, as the rest of the instances are somcpleted less then 1 hour ago
    assertThat(archiverJob.archiveNextBatch()).isEqualTo(0);

    elasticsearchTestRule.refreshIndexesInElasticsearch();

    //then
    assertInstancesInCorrectIndex(count1, ids1, endDate1);
    assertInstancesInCorrectIndex(count2, ids2, null);
  }

  /**
   * This test takes long time to run, but can be unignored to test archiving of one big workflow instance locally.
   * Related with OPE-1071.
   * @throws ArchiverException
   * @throws IOException
   */
  @Test
  @Ignore
  public void testArchivingBigInstance() throws ArchiverException, IOException {
    brokerRule.getClock().pinCurrentTime();
    final Instant currentTime = brokerRule.getClock().getCurrentTime();

    //having
    //deploy process
    final Instant endDate = currentTime.minus(4, ChronoUnit.DAYS);
    brokerRule.getClock().setCurrentTime(endDate);
    final Long workflowId = deployWorkflow("sequential-noop.bpmn");
    elasticsearchTestRule.processAllRecordsAndWait(workflowIsDeployedCheck, workflowId);
    String processId = "sequential-noop";

    //start instance with 3000 of vars in loop
    String payload =
        "{\"items\": [" + IntStream.range(1, 3000).boxed().map(Object::toString).collect(
            Collectors.joining(",")) + "]}";
    final long workflowInstanceKey = ZeebeTestUtil
        .startWorkflowInstance(zeebeClient, processId, payload);
    //wait till it's finished
    elasticsearchTestRule.processAllRecordsAndWait(400, workflowInstanceIsCompletedCheck, workflowInstanceKey);

    brokerRule.getClock().setCurrentTime(currentTime);

    //when
    assertThat(archiverJob.archiveNextBatch()).isEqualTo(1);
    elasticsearchTestRule.refreshIndexesInElasticsearch();

    //then
    assertInstancesInCorrectIndex(1, Arrays.asList(workflowInstanceKey), endDate, true);
  }

  private void deployProcessWithOneActivity(String processId, String activityId) {
    BpmnModelInstance workflow = Bpmn.createExecutableProcess(processId)
      .startEvent("start")
      .serviceTask(activityId).zeebeJobType(activityId)
      .endEvent()
      .done();
    deployWorkflow(workflow, processId + ".bpmn");
  }

  private void assertBatchOperationsInCorrectIndex(int instancesCount, List<String> ids, Instant endDate) throws IOException {
    final String destinationIndexName;
    if (endDate != null) {
      destinationIndexName = archiver.getDestinationIndexName(batchOperationTemplate.getFullQualifiedName(), dateTimeFormatter.format(endDate));
    } else {
      destinationIndexName = archiver.getDestinationIndexName(batchOperationTemplate.getFullQualifiedName(), "");
    }
    final IdsQueryBuilder idsQ = idsQuery().addIds(CollectionUtil.toSafeArrayOfStrings(ids));

    final SearchRequest searchRequest = new SearchRequest(destinationIndexName)
        .source(new SearchSourceBuilder()
            .query(constantScoreQuery(idsQ))
            .size(100));

    final SearchResponse response = esClient.search(searchRequest, RequestOptions.DEFAULT);

    final List<BatchOperationEntity> bos = ElasticsearchUtil
        .mapSearchHits(response.getHits().getHits(), objectMapper, BatchOperationEntity.class);
    assertThat(bos).hasSize(instancesCount);
    assertThat(bos).extracting(BatchOperationTemplate.ID).containsExactlyInAnyOrderElementsOf(ids);
  }

  private void assertInstancesInCorrectIndex(int instancesCount, List<Long> ids, Instant endDate) throws IOException {
    assertInstancesInCorrectIndex(instancesCount, ids, endDate, false);
  }

  private void assertInstancesInCorrectIndex(int instancesCount, List<Long> ids, Instant endDate, boolean ignoreAbsentIndex) throws IOException {
    assertWorkflowInstanceIndex(instancesCount, ids, endDate);
    for (WorkflowInstanceDependant template : workflowInstanceDependantTemplates) {
      if (! (template instanceof IncidentTemplate || template instanceof SequenceFlowTemplate)) {
        assertDependentIndex(template.getFullQualifiedName(), WorkflowInstanceDependant.WORKFLOW_INSTANCE_KEY, ids, endDate, ignoreAbsentIndex);
      }
    }
  }

  private void assertWorkflowInstanceIndex(int instancesCount, List<Long> ids, Instant endDate) throws IOException {
    final String destinationIndexName;
    if (endDate != null) {
      destinationIndexName = archiver.getDestinationIndexName(workflowInstanceTemplate.getFullQualifiedName(), dateTimeFormatter.format(endDate));
    } else {
      destinationIndexName = archiver.getDestinationIndexName(workflowInstanceTemplate.getFullQualifiedName(), "");
    }
    final IdsQueryBuilder idsQ = idsQuery().addIds(CollectionUtil.toSafeArrayOfStrings(ids));
    final TermQueryBuilder isWorkflowInstanceQuery = termQuery(JOIN_RELATION, WORKFLOW_INSTANCE_JOIN_RELATION);

    final SearchRequest searchRequest = new SearchRequest(destinationIndexName)
      .source(new SearchSourceBuilder()
        .query(constantScoreQuery(joinWithAnd(idsQ, isWorkflowInstanceQuery)))
        .size(100));

    final SearchResponse response = esClient.search(searchRequest, RequestOptions.DEFAULT);

    final List<WorkflowInstanceForListViewEntity> workflowInstances = ElasticsearchUtil
      .mapSearchHits(response.getHits().getHits(), objectMapper, WorkflowInstanceForListViewEntity.class);
    assertThat(workflowInstances).hasSize(instancesCount);
    assertThat(workflowInstances).extracting(ListViewTemplate.WORKFLOW_INSTANCE_KEY).containsExactlyInAnyOrderElementsOf(ids);
    if (endDate != null) {
      assertThat(workflowInstances).extracting(ListViewTemplate.END_DATE).allMatch(ed -> ((OffsetDateTime) ed).toInstant().equals(endDate));
    }
    //TODO assert children records - activities
  }



  private void assertDependentIndex(String mainIndexName, String idFieldName, List<Long> ids, Instant endDate, boolean ignoreAbsentIndex) throws IOException {
    final String destinationIndexName;
    if (endDate != null) {
      destinationIndexName = archiver.getDestinationIndexName(mainIndexName, dateTimeFormatter.format(endDate));
    } else {
      destinationIndexName = archiver.getDestinationIndexName(mainIndexName, "");
    }
    try {
      final TermsQueryBuilder q = termsQuery(idFieldName, CollectionUtil.toSafeArrayOfStrings(ids));
      final SearchRequest request = new SearchRequest(destinationIndexName)
          .source(new SearchSourceBuilder()
              .query(q)
              .size(100));
      final List<Long> idsFromEls = ElasticsearchUtil
          .scrollFieldToList(request, idFieldName, esClient);
      assertThat(idsFromEls).as(mainIndexName).isSubsetOf(ids);
    } catch (ElasticsearchStatusException ex) {
      if (!ex.getMessage().contains("index_not_found_exception") || !ignoreAbsentIndex) {
        throw ex;
      }
      //else ignore
    }
  }

  private void finishInstances(int count, Instant currentTime, String taskId) {
    brokerRule.getClock().setCurrentTime(currentTime);
    ZeebeTestUtil.completeTask(getClient(), taskId, getWorkerName(), null, count);
  }

  private List<Long> startInstances(String processId, int count, Instant currentTime) {
    assertThat(count).isGreaterThan(0);
    brokerRule.getClock().setCurrentTime(currentTime);
    List<Long> ids = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      ids.add(ZeebeTestUtil.startWorkflowInstance(zeebeClient, processId, "{\"var\": 123}"));
    }
    elasticsearchTestRule.processAllRecordsAndWait(workflowInstancesAreStartedCheck, ids);
    return ids;
  }

  //OPE-671
  @Test
  public void testArchivedOperationsWillNotBeLocked() throws Exception {
      // given (set up) : disabled OperationExecutor
      tester.disableOperationExecutor();
      // and given workflowInstance
      final String bpmnProcessId = "startEndProcess";
      final BpmnModelInstance startEndProcess =
        Bpmn.createExecutableProcess(bpmnProcessId)
          .startEvent()
          .endEvent()
          .done();

      tester
        .deployWorkflow(startEndProcess, "startEndProcess.bpmn").workflowIsDeployed()
        .and()
        .startWorkflowInstance(bpmnProcessId).waitUntil().workflowInstanceIsStarted()
        .and()
        // when
        // 1. Schedule operation (with disabled operation executor)
        .cancelWorkflowInstanceOperation().waitUntil().operationIsCompleted()
        // 2. Finish workflow instance
        .then()
        .waitUntil().workflowInstanceIsFinished()
        // 3. Wait till workflow instance is archived
        .archive().waitUntil().archiveIsDone()
        // 4. Enable the operation executor
        .then()
        .enableOperationExecutor();
  }
}
