/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.it;

import java.io.IOException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.camunda.operate.util.ElasticsearchUtil;
import org.camunda.operate.archiver.Archiver;
import org.camunda.operate.archiver.WorkflowInstancesArchiverJob;
import org.camunda.operate.entities.OperationType;
import org.camunda.operate.entities.listview.WorkflowInstanceForListViewEntity;
import org.camunda.operate.webapp.es.writer.BatchOperationWriter;
import org.camunda.operate.exceptions.ArchiverException;
import org.camunda.operate.property.OperateProperties;
import org.camunda.operate.schema.templates.IncidentTemplate;
import org.camunda.operate.schema.templates.ListViewTemplate;
import org.camunda.operate.schema.templates.SequenceFlowTemplate;
import org.camunda.operate.schema.templates.WorkflowInstanceDependant;
import org.camunda.operate.util.CollectionUtil;
import org.camunda.operate.util.OperateZeebeIntegrationTest;
import org.camunda.operate.util.TestUtil;
import org.camunda.operate.util.ZeebeTestUtil;
import org.camunda.operate.webapp.rest.dto.listview.ListViewQueryDto;
import org.camunda.operate.webapp.rest.dto.operation.CreateBatchOperationRequestDto;
import org.camunda.operate.zeebe.PartitionHolder;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.TermQueryBuilder;
import org.elasticsearch.index.query.TermsQueryBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.operate.schema.templates.ListViewTemplate.JOIN_RELATION;
import static org.camunda.operate.schema.templates.ListViewTemplate.WORKFLOW_INSTANCE_JOIN_RELATION;
import static org.elasticsearch.index.query.QueryBuilders.constantScoreQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.index.query.QueryBuilders.termsQuery;

@TestPropertySource(properties = { OperateProperties.PREFIX + ".importer.startLoadingDataOnStartup = false",
    OperateProperties.PREFIX + ".clusterNode.nodeCount = 2",
    OperateProperties.PREFIX + ".clusterNode.currentNodeId = 0" })
public class OneNodeArchiverIT extends OperateZeebeIntegrationTest {

  private WorkflowInstancesArchiverJob archiverJob;

  @Autowired
  private BeanFactory beanFactory;

  @Autowired
  private Archiver archiver;

  @Autowired
  private RestHighLevelClient esClient;

  @Autowired
  private ListViewTemplate listViewTemplate;

  @Autowired
  private BatchOperationWriter batchOperationWriter;

  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private PartitionHolder partitionHolder;

  @Autowired
  private List<WorkflowInstanceDependant> workflowInstanceDependantTemplates;

  private Random random = new Random();

  private DateTimeFormatter dateTimeFormatter;

  @Before
  public void before() {
    super.before();
    dateTimeFormatter = DateTimeFormatter.ofPattern(operateProperties.getArchiver().getRolloverDateFormat()).withZone(ZoneId.systemDefault());
    archiverJob = beanFactory.getBean(WorkflowInstancesArchiverJob.class, partitionHolder.getPartitionIds());
  }

  @Test
  public void testArchiving() throws ArchiverException, IOException {
    brokerRule.getClock().pinCurrentTime();
    final Instant currentTime = brokerRule.getClock().getCurrentTime();

    //having
    //deploy process
    brokerRule.getClock().setCurrentTime(currentTime.minus(4, ChronoUnit.DAYS));
    String processId = "demoProcess";
    final String activityId = "task1";
    deployProcessWithOneActivity(processId, activityId);

    //start instances 3 days ago
    int count = random.nextInt(6) + 5;
    final List<Long> ids1 = startInstances(processId, count, currentTime.minus(3, ChronoUnit.DAYS));
    createOperations(ids1);
    //finish instances 2 days ago
    final Instant endDate = currentTime.minus(2, ChronoUnit.DAYS);
    finishInstances(count, endDate, activityId);
    elasticsearchTestRule.processAllRecordsAndWait(workflowInstancesAreFinishedCheck, ids1);

    brokerRule.getClock().setCurrentTime(currentTime);

    //when
    int expectedCount = count / operateProperties.getClusterNode().getNodeCount(); // we're archiving only part of the partitions
    assertThat(archiverJob.archiveNextBatch()).isGreaterThanOrEqualTo(expectedCount);
    assertThat(archiverJob.archiveNextBatch()).isLessThanOrEqualTo(expectedCount + 1);

    elasticsearchTestRule.refreshIndexesInElasticsearch();

    //then
    assertInstancesInCorrectIndex(expectedCount, endDate);
  }

  protected void createOperations(List<Long> ids1) {
    final ListViewQueryDto query = TestUtil.createGetAllWorkflowInstancesQuery();
    query.setIds(CollectionUtil.toSafeListOfStrings(ids1));
    CreateBatchOperationRequestDto batchOperationRequest = new CreateBatchOperationRequestDto(query, OperationType.CANCEL_WORKFLOW_INSTANCE);  //the type does not matter
    batchOperationWriter.scheduleBatchOperation(batchOperationRequest);
  }

  private void deployProcessWithOneActivity(String processId, String activityId) {
    BpmnModelInstance workflow = Bpmn.createExecutableProcess(processId)
        .startEvent("start")
        .serviceTask(activityId).zeebeJobType(activityId)
        .endEvent()
        .done();
    deployWorkflow(workflow, processId + ".bpmn");
  }

  private void assertInstancesInCorrectIndex(int instancesCount, Instant endDate) throws IOException {
    List<Long> ids = assertWorkflowInstanceIndex(instancesCount, endDate);
    for (WorkflowInstanceDependant template : workflowInstanceDependantTemplates) {
      if (! (template instanceof IncidentTemplate || template instanceof SequenceFlowTemplate)) {
        assertDependentIndex(template.getFullQualifiedName(), WorkflowInstanceDependant.WORKFLOW_INSTANCE_KEY, ids, endDate);
      }
    }
  }

  private List<Long> assertWorkflowInstanceIndex(int instancesCount, Instant endDate) throws IOException {
    final String destinationIndexName = archiver.getDestinationIndexName(listViewTemplate.getFullQualifiedName(), dateTimeFormatter.format(endDate));
    final TermQueryBuilder isWorkflowInstanceQuery = termQuery(JOIN_RELATION, WORKFLOW_INSTANCE_JOIN_RELATION);

    final SearchRequest searchRequest = new SearchRequest(destinationIndexName)
        .source(new SearchSourceBuilder()
            .query(constantScoreQuery(isWorkflowInstanceQuery))
            .size(100));

    final SearchResponse response = esClient.search(searchRequest, RequestOptions.DEFAULT);

    final List<WorkflowInstanceForListViewEntity> workflowInstances = ElasticsearchUtil
        .mapSearchHits(response.getHits().getHits(), objectMapper, WorkflowInstanceForListViewEntity.class);
    assertThat(workflowInstances.size()).isGreaterThanOrEqualTo(instancesCount);
    assertThat(workflowInstances.size()).isLessThanOrEqualTo(instancesCount + 1);
    if (endDate != null) {
      assertThat(workflowInstances).extracting(ListViewTemplate.END_DATE).allMatch(ed -> ((OffsetDateTime) ed).toInstant().equals(endDate));
    }
    //assert partitions
    List<Integer> partitionIds = partitionHolder.getPartitionIds();
    assertThat(workflowInstances).extracting(ListViewTemplate.PARTITION_ID).containsOnly(partitionIds.toArray());
    //return ids
    return workflowInstances.stream().collect(ArrayList::new, (list, hit) -> list.add(Long.valueOf(hit.getId())), (list1, list2) -> list1.addAll(list2));
  }

  private void assertDependentIndex(String mainIndexName, String idFieldName, List<Long> ids, Instant endDate) throws IOException {
    final String destinationIndexName;
    if (endDate != null) {
      destinationIndexName = archiver.getDestinationIndexName(mainIndexName, dateTimeFormatter.format(endDate));
    } else {
      destinationIndexName = archiver.getDestinationIndexName(mainIndexName, "");
    }
    final TermsQueryBuilder q = termsQuery(idFieldName, CollectionUtil.toSafeArrayOfStrings(ids));
    final SearchRequest request = new SearchRequest(destinationIndexName)
        .source(new SearchSourceBuilder()
            .query(q)
            .size(100));
    final List<Long> idsFromEls = ElasticsearchUtil.scrollFieldToList(request, idFieldName, esClient);
    assertThat(idsFromEls).as(mainIndexName).isSubsetOf(ids);
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

}
