/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.it;

import static io.camunda.operate.schema.templates.ListViewTemplate.JOIN_RELATION;
import static io.camunda.operate.schema.templates.ListViewTemplate.PROCESS_INSTANCE_JOIN_RELATION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.elasticsearch.index.query.QueryBuilders.constantScoreQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.index.query.QueryBuilders.termsQuery;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.archiver.Archiver;
import io.camunda.operate.archiver.ProcessInstancesArchiverJob;
import io.camunda.operate.entities.OperationType;
import io.camunda.operate.entities.listview.ProcessInstanceForListViewEntity;
import io.camunda.operate.exceptions.ArchiverException;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.schema.templates.IncidentTemplate;
import io.camunda.operate.schema.templates.ListViewTemplate;
import io.camunda.operate.schema.templates.ProcessInstanceDependant;
import io.camunda.operate.schema.templates.SequenceFlowTemplate;
import io.camunda.operate.util.CollectionUtil;
import io.camunda.operate.util.ElasticsearchUtil;
import io.camunda.operate.util.OperateZeebeIntegrationTest;
import io.camunda.operate.util.TestUtil;
import io.camunda.operate.util.ZeebeTestUtil;
import io.camunda.operate.webapp.es.writer.BatchOperationWriter;
import io.camunda.operate.webapp.rest.dto.listview.ListViewQueryDto;
import io.camunda.operate.webapp.rest.dto.operation.CreateBatchOperationRequestDto;
import io.camunda.operate.zeebe.PartitionHolder;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import java.io.IOException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.elasticsearch.ElasticsearchStatusException;
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

@TestPropertySource(properties = { OperateProperties.PREFIX + ".importer.startLoadingDataOnStartup = false",
    OperateProperties.PREFIX + ".clusterNode.nodeCount = 2",
    OperateProperties.PREFIX + ".clusterNode.currentNodeId = 0" })
public class OneNodeArchiverIT extends OperateZeebeIntegrationTest {

  private ProcessInstancesArchiverJob archiverJob;

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
  private List<ProcessInstanceDependant> processInstanceDependantTemplates;

  private Random random = new Random();

  private DateTimeFormatter dateTimeFormatter;

  @Before
  public void before() {
    super.before();
    dateTimeFormatter = DateTimeFormatter.ofPattern(operateProperties.getArchiver().getRolloverDateFormat()).withZone(ZoneId.systemDefault());
    archiverJob = beanFactory.getBean(ProcessInstancesArchiverJob.class, partitionHolder.getPartitionIds());
  }

  @Test
  public void testArchiving() throws ArchiverException, IOException {
    final Instant currentTime = pinZeebeTime();

    //having
    //deploy process
    pinZeebeTime(currentTime.minus(4, ChronoUnit.DAYS));
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
    elasticsearchTestRule.processAllRecordsAndWait(processInstancesAreFinishedCheck, ids1);

    pinZeebeTime(currentTime);

    //when
    int expectedCount = count / operateProperties.getClusterNode().getNodeCount(); // we're archiving only part of the partitions
    assertThat(archiverJob.archiveNextBatch().join()).isGreaterThanOrEqualTo(expectedCount);
    elasticsearchTestRule.refreshIndexesInElasticsearch();
    assertThat(archiverJob.archiveNextBatch().join()).isLessThanOrEqualTo(expectedCount + 1);

    elasticsearchTestRule.refreshIndexesInElasticsearch();

    //then
    assertInstancesInCorrectIndex(expectedCount, endDate);
  }

  protected void createOperations(List<Long> ids1) {
    final ListViewQueryDto query = TestUtil.createGetAllProcessInstancesQuery();
    query.setIds(CollectionUtil.toSafeListOfStrings(ids1));
    CreateBatchOperationRequestDto batchOperationRequest = new CreateBatchOperationRequestDto(query, OperationType.CANCEL_PROCESS_INSTANCE);  //the type does not matter
    batchOperationWriter.scheduleBatchOperation(batchOperationRequest);
  }

  private void deployProcessWithOneActivity(String processId, String activityId) {
    BpmnModelInstance process = Bpmn.createExecutableProcess(processId)
        .startEvent("start")
        .serviceTask(activityId).zeebeJobType(activityId)
        .endEvent()
        .done();
    deployProcess(process, processId + ".bpmn");
  }

  private void assertInstancesInCorrectIndex(int instancesCount, Instant endDate) throws IOException {
    List<Long> ids = assertProcessInstanceIndex(instancesCount, endDate);
    for (ProcessInstanceDependant template : processInstanceDependantTemplates) {
      if (! (template instanceof IncidentTemplate || template instanceof SequenceFlowTemplate)) {
        assertDependentIndex(template.getFullQualifiedName(), ProcessInstanceDependant.PROCESS_INSTANCE_KEY, ids, endDate, true);
      }
    }
  }

  private List<Long> assertProcessInstanceIndex(int instancesCount, Instant endDate) throws IOException {
    final String destinationIndexName = archiver.getDestinationIndexName(listViewTemplate.getFullQualifiedName(), dateTimeFormatter.format(endDate));
    final TermQueryBuilder isProcessInstanceQuery = termQuery(JOIN_RELATION, PROCESS_INSTANCE_JOIN_RELATION);

    final SearchRequest searchRequest = new SearchRequest(destinationIndexName)
        .source(new SearchSourceBuilder()
            .query(constantScoreQuery(isProcessInstanceQuery))
            .size(100));

    final SearchResponse response = esClient.search(searchRequest, RequestOptions.DEFAULT);

    final List<ProcessInstanceForListViewEntity> processInstances = ElasticsearchUtil
        .mapSearchHits(response.getHits().getHits(), objectMapper, ProcessInstanceForListViewEntity.class);
    assertThat(processInstances.size()).isGreaterThanOrEqualTo(instancesCount);
    assertThat(processInstances.size()).isLessThanOrEqualTo(instancesCount + 1);
    if (endDate != null) {
      assertThat(processInstances).extracting(ListViewTemplate.END_DATE).allMatch(ed -> ((OffsetDateTime) ed).toInstant().equals(endDate));
    }
    //assert partitions
    List<Integer> partitionIds = partitionHolder.getPartitionIds();
    assertThat(processInstances).extracting(ListViewTemplate.PARTITION_ID).containsOnly(partitionIds.toArray());
    //return ids
    return processInstances.stream().collect(ArrayList::new, (list, hit) -> list.add(Long.valueOf(hit.getId())), (list1, list2) -> list1.addAll(list2));
  }

  private void assertDependentIndex(String mainIndexName, String idFieldName, List<Long> ids, Instant endDate, boolean ignoreAbsentIndex) throws IOException {
    final String destinationIndexName;
    try {
      if (endDate != null) {
        destinationIndexName = archiver
            .getDestinationIndexName(mainIndexName, dateTimeFormatter.format(endDate));
      } else {
        destinationIndexName = archiver.getDestinationIndexName(mainIndexName, "");
      }
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

}
