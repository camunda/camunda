/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.it;

import static org.assertj.core.api.Assertions.assertThat;
import static io.camunda.operate.schema.templates.ListViewTemplate.JOIN_RELATION;
import static io.camunda.operate.schema.templates.ListViewTemplate.PROCESS_INSTANCE_JOIN_RELATION;
import static io.camunda.operate.util.ElasticsearchUtil.joinWithAnd;
import static io.camunda.operate.util.MetricAssert.assertThatMetricsFrom;
import static org.elasticsearch.index.query.QueryBuilders.constantScoreQuery;
import static org.elasticsearch.index.query.QueryBuilders.idsQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.index.query.QueryBuilders.termsQuery;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.containsString;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import io.camunda.operate.archiver.Archiver;
import io.camunda.operate.archiver.BatchOperationArchiverJob;
import io.camunda.operate.archiver.ProcessInstancesArchiverJob;
import io.camunda.operate.entities.BatchOperationEntity;
import io.camunda.operate.entities.OperationType;
import io.camunda.operate.entities.listview.ProcessInstanceForListViewEntity;
import io.camunda.operate.exceptions.ArchiverException;
import io.camunda.operate.schema.templates.BatchOperationTemplate;
import io.camunda.operate.schema.templates.IncidentTemplate;
import io.camunda.operate.schema.templates.ListViewTemplate;
import io.camunda.operate.schema.templates.SequenceFlowTemplate;
import io.camunda.operate.schema.templates.ProcessInstanceDependant;
import io.camunda.operate.util.CollectionUtil;
import io.camunda.operate.util.ElasticsearchUtil;
import io.camunda.operate.util.MetricAssert;
import io.camunda.operate.util.OperateZeebeIntegrationTest;
import io.camunda.operate.util.TestUtil;
import io.camunda.operate.util.ZeebeTestUtil;
import io.camunda.operate.webapp.es.reader.ListViewReader;
import io.camunda.operate.webapp.es.writer.BatchOperationWriter;
import io.camunda.operate.webapp.rest.dto.listview.ListViewQueryDto;
import io.camunda.operate.webapp.rest.dto.listview.ListViewRequestDto;
import io.camunda.operate.webapp.rest.dto.listview.ListViewResponseDto;
import io.camunda.operate.webapp.rest.dto.operation.CreateBatchOperationRequestDto;
import io.camunda.operate.webapp.zeebe.operation.CancelProcessInstanceHandler;
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
  private ListViewTemplate processInstanceTemplate;

  @Autowired
  private BatchOperationTemplate batchOperationTemplate;

  @Autowired
  private BatchOperationWriter batchOperationWriter;

  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private List<ProcessInstanceDependant> processInstanceDependantTemplates;

  @Autowired
  private CancelProcessInstanceHandler cancelProcessInstanceHandler;

  private ProcessInstancesArchiverJob archiverJob;

  private Random random = new Random();

  private DateTimeFormatter dateTimeFormatter;

  @Before
  public void before() {
    super.before();
    dateTimeFormatter = DateTimeFormatter.ofPattern(operateProperties.getArchiver().getRolloverDateFormat()).withZone(ZoneId.systemDefault());
    archiverJob = beanFactory.getBean(ProcessInstancesArchiverJob.class, partitionHolder.getPartitionIds());
    cancelProcessInstanceHandler.setZeebeClient(super.getClient());
    clearMetrics();
  }

  @Test
  public void testArchivingProcessInstances() throws ArchiverException, IOException {

    final Instant currentTime = pinZeebeTime();

    //having
    //deploy process
    offsetZeebeTime(Duration.ofDays(-4));
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
    elasticsearchTestRule.processAllRecordsAndWait(processInstancesAreFinishedCheck, ids1);

    //start instances 2 days ago
    int count2 = random.nextInt(6) + 5;
    final List<Long> ids2 = startInstances(processId, count2, endDate1);
    createOperations(ids2);
    //finish instances 1 day ago
    final Instant endDate2 = currentTime.minus(1, ChronoUnit.DAYS);
    finishInstances(count2, endDate2, activityId);
    elasticsearchTestRule.processAllRecordsAndWait(processInstancesAreFinishedCheck, ids2);

    //assert metrics for finished process instances
    assertThatMetricsFrom(mockMvc, new MetricAssert.ValueMatcher("operate_events_processed_finished_process_instances_total", d -> d.doubleValue() == count1 + count2));

    //start instances 1 day ago
    int count3 = random.nextInt(6) + 5;
    final List<Long> ids3 = startInstances(processId, count3, endDate2);

    resetZeebeTime();

    //when
    assertThat(archiverJob.archiveNextBatch()).isEqualTo(count1);
    elasticsearchTestRule.refreshIndexesInElasticsearch();
    assertThat(archiverJob.archiveNextBatch()).isEqualTo(count2);
    elasticsearchTestRule.refreshIndexesInElasticsearch();
    assertThat(archiverJob.archiveNextBatch()).isEqualTo(0);     //3rd run should not move anything, as the rest of the instances are not completed

    elasticsearchTestRule.refreshIndexesInElasticsearch();

    //then
    assertInstancesInCorrectIndex(count1, ids1, endDate1, true);
    assertInstancesInCorrectIndex(count2, ids2, endDate2, true);
    assertInstancesInCorrectIndex(count3, ids3, null, true);

    assertAllInstancesInAlias(count1 + count2 + count3);

    //assert metrics for archived process instances
    assertThatMetricsFrom(mockMvc, allOf(
            new MetricAssert.ValueMatcher("operate_archived_process_instances_total", d -> d.doubleValue() == count1 + count2),
            containsString("operate_archiver_query"),
            containsString("operate_archiver_reindex_query"),
            containsString("operate_archiver_delete_query")
        ));
  }

  protected void createOperations(List<Long> ids1) {
    final ListViewQueryDto query = TestUtil.createGetAllProcessInstancesQuery();
    query.setIds(CollectionUtil.toSafeListOfStrings(ids1));
    CreateBatchOperationRequestDto batchOperationRequest = new CreateBatchOperationRequestDto(query, OperationType.CANCEL_PROCESS_INSTANCE);   //the type does not matter
    batchOperationWriter.scheduleBatchOperation(batchOperationRequest);
  }

  private void assertAllInstancesInAlias(int count) {
    final ListViewRequestDto request = TestUtil.createGetAllProcessInstancesRequest();
    request.setPageSize(count + 100);
    final ListViewResponseDto responseDto = listViewReader.queryProcessInstances(request);
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

  @Test
  public void testArchivingDecisionInstances() throws Exception {

    final Instant currentTime = pinZeebeTime();

    //having
    final Instant endDate = currentTime.minus(4, ChronoUnit.DAYS);
    pinZeebeTime(endDate);

    final String bpmnProcessId = "process";
    final String demoDecisionId2 = "invoiceAssignApprover";

    final String elementId = "task";
    final BpmnModelInstance instance =
        Bpmn.createExecutableProcess(bpmnProcessId)
            .startEvent()
            .businessRuleTask(elementId, task -> task.zeebeCalledDecisionId(demoDecisionId2)
                .zeebeResultVariable("approverGroups"))
            .done();

    final Long processInstanceKey = tester.deployProcess(instance, "test.bpmn")
        .deployDecision("invoiceBusinessDecisions_v_1.dmn")
        .waitUntil()
        .processIsDeployed()
        .and()
        .decisionsAreDeployed(2)
        //when
        .startProcessInstance(bpmnProcessId, "{\"amount\": 100, \"invoiceCategory\": \"Misc\"}")
        .waitUntil()
        .processInstanceIsStarted()
        .decisionInstancesAreCreated(2)
        .getProcessInstanceKey();

    resetZeebeTime();

    assertThat(archiverJob.archiveNextBatch()).isEqualTo(1);

    assertInstancesInCorrectIndex(1, Arrays.asList(processInstanceKey), endDate, true);

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
    final Instant currentTime = pinZeebeTime();

    //having
    //deploy process
    offsetZeebeTime(Duration.ofDays(-4));
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
    elasticsearchTestRule.processAllRecordsAndWait(processInstancesAreFinishedCheck, ids1);

    //start instances 1 hour ago
    int count2 = random.nextInt(6) + 5;
    final List<Long> ids2 = startInstances(processId, count2, currentTime.minus(1, ChronoUnit.HOURS));
    //finish instances 59 minutes ago
    final Instant endDate2 = currentTime.minus(50, ChronoUnit.MINUTES);
    finishInstances(count2, endDate2, activityId);
    elasticsearchTestRule.processAllRecordsAndWait(processInstancesAreFinishedCheck, ids2);

    resetZeebeTime();

    //when
    assertThat(archiverJob.archiveNextBatch()).isEqualTo(count1);
    elasticsearchTestRule.refreshIndexesInElasticsearch();
    //2rd run should not move anything, as the rest of the instances are somcpleted less then 1 hour ago
    assertThat(archiverJob.archiveNextBatch()).isEqualTo(0);

    elasticsearchTestRule.refreshIndexesInElasticsearch();

    //then
    assertInstancesInCorrectIndex(count1, ids1, endDate1,true);
    assertInstancesInCorrectIndex(count2, ids2, null);
  }

  /**
   * This test takes long time to run, but can be unignored to test archiving of one big process instance locally.
   * Related with OPE-1071.
   * @throws ArchiverException
   * @throws IOException
   */
  @Test
  @Ignore
  public void testArchivingBigInstance() throws ArchiverException, IOException {
    final Instant currentTime = pinZeebeTime();

    //having
    //deploy process
    final Instant endDate = currentTime.minus(4, ChronoUnit.DAYS);
    pinZeebeTime(endDate);
    final Long processDefinitionKey = deployProcess("sequential-noop.bpmn");
    elasticsearchTestRule.processAllRecordsAndWait(processIsDeployedCheck, processDefinitionKey);
    String processId = "sequential-noop";

    //start instance with 3000 of vars in loop
    String payload =
        "{\"items\": [" + IntStream.range(1, 3000).boxed().map(Object::toString).collect(
            Collectors.joining(",")) + "]}";
    final long processInstanceKey = ZeebeTestUtil
        .startProcessInstance(zeebeClient, processId, payload);
    //wait till it's finished
    elasticsearchTestRule.processAllRecordsAndWait(400, processInstanceIsCompletedCheck, processInstanceKey);

    resetZeebeTime();

    //when
    assertThat(archiverJob.archiveNextBatch()).isEqualTo(1);
    elasticsearchTestRule.refreshIndexesInElasticsearch();

    //then
    assertInstancesInCorrectIndex(1, Arrays.asList(processInstanceKey), endDate, true);
  }

  private void deployProcessWithOneActivity(String processId, String activityId) {
    BpmnModelInstance process = Bpmn.createExecutableProcess(processId)
      .startEvent("start")
      .serviceTask(activityId).zeebeJobType(activityId)
      .endEvent()
      .done();
    deployProcess(process, processId + ".bpmn");
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
    assertProcessInstanceIndex(instancesCount, ids, endDate);
    for (ProcessInstanceDependant template : processInstanceDependantTemplates) {
      if (! (template instanceof IncidentTemplate || template instanceof SequenceFlowTemplate)) {
        assertDependentIndex(template.getFullQualifiedName(), ProcessInstanceDependant.PROCESS_INSTANCE_KEY, ids, endDate, ignoreAbsentIndex);
      }
    }
  }

  private void assertProcessInstanceIndex(int instancesCount, List<Long> ids, Instant endDate) throws IOException {
    final String destinationIndexName;
    if (endDate != null) {
      destinationIndexName = archiver.getDestinationIndexName(processInstanceTemplate.getFullQualifiedName(), dateTimeFormatter.format(endDate));
    } else {
      destinationIndexName = archiver.getDestinationIndexName(processInstanceTemplate.getFullQualifiedName(), "");
    }
    final IdsQueryBuilder idsQ = idsQuery().addIds(CollectionUtil.toSafeArrayOfStrings(ids));
    final TermQueryBuilder isProcessInstanceQuery = termQuery(JOIN_RELATION, PROCESS_INSTANCE_JOIN_RELATION);

    final SearchRequest searchRequest = new SearchRequest(destinationIndexName)
      .source(new SearchSourceBuilder()
        .query(constantScoreQuery(joinWithAnd(idsQ, isProcessInstanceQuery)))
        .size(100));

    final SearchResponse response = esClient.search(searchRequest, RequestOptions.DEFAULT);

    final List<ProcessInstanceForListViewEntity> processInstances = ElasticsearchUtil
      .mapSearchHits(response.getHits().getHits(), objectMapper, ProcessInstanceForListViewEntity.class);
    assertThat(processInstances).hasSize(instancesCount);
    assertThat(processInstances).extracting(ListViewTemplate.PROCESS_INSTANCE_KEY).containsExactlyInAnyOrderElementsOf(ids);
    if (endDate != null) {
      assertThat(processInstances).extracting(ListViewTemplate.END_DATE).allMatch(ed -> ((OffsetDateTime) ed).toInstant().equals(endDate));
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
    pinZeebeTime(currentTime);
    ZeebeTestUtil.completeTask(getClient(), taskId, getWorkerName(), null, count);
  }

  private List<Long> startInstances(String processId, int count, Instant currentTime) {
    assertThat(count).isGreaterThan(0);
    pinZeebeTime(currentTime);
    List<Long> ids = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      ids.add(ZeebeTestUtil.startProcessInstance(zeebeClient, processId, "{\"var\": 123}"));
    }
    elasticsearchTestRule.processAllRecordsAndWait(processInstancesAreStartedCheck, ids);
    return ids;
  }

  //OPE-671
  @Test
  public void testArchivedOperationsWillNotBeLocked() throws Exception {
      // given (set up) : disabled OperationExecutor
      tester.disableOperationExecutor();
      // and given processInstance
      final String bpmnProcessId = "startEndProcess";
      final BpmnModelInstance startEndProcess =
        Bpmn.createExecutableProcess(bpmnProcessId)
          .startEvent()
          .endEvent()
          .done();

      tester
        .deployProcess(startEndProcess, "startEndProcess.bpmn").processIsDeployed()
        .and()
        .startProcessInstance(bpmnProcessId).waitUntil().processInstanceIsStarted()
        .and()
        // when
        // 1. Schedule operation (with disabled operation executor)
        .cancelProcessInstanceOperation().waitUntil().operationIsCompleted()
        // 2. Finish process instance
        .then()
        .waitUntil().processInstanceIsFinished()
        // 3. Wait till process instance is archived
        .archive().waitUntil().archiveIsDone()
        // 4. Enable the operation executor
        .then()
        .enableOperationExecutor();
  }
}
