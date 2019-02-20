/* Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.camunda.operate.it;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Predicate;
import org.camunda.operate.entities.EventEntity;
import org.camunda.operate.entities.OperationType;
import org.camunda.operate.entities.WorkflowInstanceEntity;
import org.camunda.operate.es.archiver.Archiver;
import org.camunda.operate.es.archiver.ArchiverHelper;
import org.camunda.operate.es.reader.ListViewReader;
import org.camunda.operate.es.schema.templates.ActivityInstanceTemplate;
import org.camunda.operate.es.schema.templates.EventTemplate;
import org.camunda.operate.es.schema.templates.ListViewTemplate;
import org.camunda.operate.es.schema.templates.OperationTemplate;
import org.camunda.operate.es.schema.templates.WorkflowInstanceDependant;
import org.camunda.operate.es.schema.templates.WorkflowInstanceTemplate;
import org.camunda.operate.es.writer.BatchOperationWriter;
import org.camunda.operate.exceptions.PersistenceException;
import org.camunda.operate.exceptions.ReindexException;
import org.camunda.operate.property.OperateProperties;
import org.camunda.operate.rest.dto.WorkflowInstanceBatchOperationDto;
import org.camunda.operate.rest.dto.listview.ListViewQueryDto;
import org.camunda.operate.rest.dto.listview.ListViewResponseDto;
import org.camunda.operate.util.ElasticsearchUtil;
import org.camunda.operate.util.IdTestUtil;
import org.camunda.operate.util.OperateZeebeIntegrationTest;
import org.camunda.operate.util.TestUtil;
import org.camunda.operate.util.ZeebeTestUtil;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.index.query.IdsQueryBuilder;
import org.elasticsearch.index.query.TermsQueryBuilder;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.zeebe.client.ZeebeClient;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import static org.assertj.core.api.Assertions.assertThat;
import static org.elasticsearch.index.query.QueryBuilders.idsQuery;
import static org.elasticsearch.index.query.QueryBuilders.termsQuery;

public class ArchiverIT extends OperateZeebeIntegrationTest {

  @Autowired
  private ListViewReader listViewReader;

  @Autowired
  private Archiver archiver;

  @Autowired
  private ArchiverHelper reindexHelper;

  @Autowired
  private OperateProperties operateProperties;

  @Autowired
  private TransportClient esClient;

  @Autowired
  @Qualifier("activityIsActiveCheck")
  private Predicate<Object[]> activityIsActiveCheck;

  @Autowired
  @Qualifier("workflowInstanceIsCompletedCheck")
  private Predicate<Object[]> workflowInstanceIsCompletedCheck;

  @Autowired
  @Qualifier("workflowInstancesAreFinished")
  private Predicate<Object[]> workflowInstancesAreFinishedCheck;

  @Autowired
  @Qualifier("workflowInstancesAreStarted")
  private Predicate<Object[]> workflowInstancesAreStartedCheck;

  @Autowired
  private WorkflowInstanceTemplate workflowInstanceTemplate;

  @Autowired
  private EventTemplate eventTemplate;

  @Autowired
  private ListViewTemplate listViewTemplate;

  @Autowired
  private OperationTemplate operationTemplate;

  @Autowired
  private ActivityInstanceTemplate activityInstanceTemplate;

  @Autowired
  private BatchOperationWriter batchOperationWriter;

  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private List<WorkflowInstanceDependant> workflowInstanceDependantTemplates;

  private Random random = new Random();

  private ZeebeClient zeebeClient;

  private DateTimeFormatter dateTimeFormatter;

  @Before
  public void init() {
    super.before();
    zeebeClient = super.getClient();
    dateTimeFormatter = DateTimeFormatter.ofPattern(operateProperties.getElasticsearch().getRolloverDateFormat()).withZone(ZoneId.systemDefault());
  }

  @Test
  public void testArchiving() throws ReindexException, PersistenceException {
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
    final List<String> ids1 = startInstances(processId, count1, currentTime.minus(3, ChronoUnit.DAYS));
    createOperations(ids1);
    //finish instances 2 days ago
    final Instant endDate1 = currentTime.minus(2, ChronoUnit.DAYS);
    finishInstances(count1, endDate1, activityId);
    elasticsearchTestRule.processAllEventsAndWait(workflowInstancesAreFinishedCheck, ids1);

    //start instances 2 days ago
    int count2 = random.nextInt(6) + 5;
    final List<String> ids2 = startInstances(processId, count2, endDate1);
    createOperations(ids2);
    //finish instances 1 day ago
    final Instant endDate2 = currentTime.minus(1, ChronoUnit.DAYS);
    finishInstances(count2, endDate2, activityId);
    elasticsearchTestRule.processAllEventsAndWait(workflowInstancesAreFinishedCheck, ids2);

    //start instances 1 day ago
    int count3 = random.nextInt(6) + 5;
    final List<String> ids3 = startInstances(processId, count3, endDate2);

    brokerRule.getClock().setCurrentTime(currentTime);

    //when
    assertThat(archiver.archiveNextBatch()).isEqualTo(count1);
    assertThat(archiver.archiveNextBatch()).isEqualTo(count2);
    assertThat(archiver.archiveNextBatch()).isEqualTo(0);     //3rd run should not move anything, as the rest of the instances are not completed

    elasticsearchTestRule.refreshIndexesInElasticsearch();

    //then
    assertInstancesInCorrectIndex(count1, ids1, endDate1);
    assertInstancesInCorrectIndex(count2, ids2, endDate2);
    assertInstancesInCorrectIndex(count3, ids3, null);

    assertAllInstancesInAlias(count1 + count2 + count3);
  }

  protected void createOperations(List<String> ids1) throws PersistenceException {
    final List<ListViewQueryDto> queries = TestUtil.createGetAllWorkflowInstancesQuery().getQueries();
    queries.get(0).setIds(ids1);
    WorkflowInstanceBatchOperationDto batchOperationRequest = new WorkflowInstanceBatchOperationDto(queries);
    batchOperationRequest.setOperationType(OperationType.UPDATE_RETRIES); //the type does not matter
    batchOperationWriter.scheduleBatchOperation(batchOperationRequest);
  }

  private void assertAllInstancesInAlias(int count) {
    final ListViewResponseDto responseDto = listViewReader
      .queryWorkflowInstances(TestUtil.createGetAllWorkflowInstancesQuery(), 0, count + 100);
    assertThat(responseDto.getTotalCount()).isEqualTo(count);
  }

  @Test
  public void testArchivingOnlyOneHourOldData() throws ReindexException, PersistenceException {
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
    final List<String> ids1 = startInstances(processId, count1, currentTime.minus(2, ChronoUnit.HOURS));
    createOperations(ids1);
    //finish instances 1 hour ago
    final Instant endDate1 = currentTime.minus(1, ChronoUnit.HOURS);
    finishInstances(count1, endDate1, activityId);
    elasticsearchTestRule.processAllEventsAndWait(workflowInstancesAreFinishedCheck, ids1);

    //start instances 1 hour ago
    int count2 = random.nextInt(6) + 5;
    final List<String> ids2 = startInstances(processId, count2, currentTime.minus(1, ChronoUnit.HOURS));
    //finish instances 59 minutes ago
    final Instant endDate2 = currentTime.minus(50, ChronoUnit.MINUTES);
    finishInstances(count2, endDate2, activityId);
    elasticsearchTestRule.processAllEventsAndWait(workflowInstancesAreFinishedCheck, ids2);

    brokerRule.getClock().setCurrentTime(currentTime);

    //when
    assertThat(archiver.archiveNextBatch()).isEqualTo(count1);
    //2rd run should not move anything, as the rest of the instances are somcpleted less then 1 hour ago
    assertThat(archiver.archiveNextBatch()).isEqualTo(0);

    elasticsearchTestRule.refreshIndexesInElasticsearch();

    //then
    assertInstancesInCorrectIndex(count1, ids1, endDate1);
    assertInstancesInCorrectIndex(count2, ids2, null);
  }

  private void deployProcessWithOneActivity(String processId, String activityId) {
    BpmnModelInstance workflow = Bpmn.createExecutableProcess(processId)
      .startEvent("start")
      .serviceTask(activityId).zeebeTaskType(activityId)
      .endEvent()
      .done();
    deployWorkflow(workflow, processId + ".bpmn");
  }

  private void assertInstancesInCorrectIndex(int instancesCount, List<String> ids, Instant endDate) {
    assertWorkflowInstanceIndex(instancesCount, ids, endDate);
    for (WorkflowInstanceDependant template : workflowInstanceDependantTemplates) {
      assertDependentIndex(template.getMainIndexName(), WorkflowInstanceDependant.WORKFLOW_INSTANCE_ID, ids, endDate);
    }
  }

  private void assertWorkflowInstanceIndex(int instancesCount, List<String> ids, Instant endDate) {
    final String destinationIndexName;
    if (endDate != null) {
      destinationIndexName = reindexHelper.getDestinationIndexName(workflowInstanceTemplate.getMainIndexName(), dateTimeFormatter.format(endDate));
    } else {
      destinationIndexName = reindexHelper.getDestinationIndexName(workflowInstanceTemplate.getMainIndexName(), "");
    }
    final IdsQueryBuilder q = idsQuery().addIds(ids.toArray(new String[]{}));
    final SearchResponse response = esClient.prepareSearch(destinationIndexName)
      .setQuery(q)
      .setSize(100)
      .get();
    final List<WorkflowInstanceEntity> workflowInstances = ElasticsearchUtil
      .mapSearchHits(response.getHits().getHits(), objectMapper, WorkflowInstanceEntity.class);
    assertThat(workflowInstances).hasSize(instancesCount);
    assertThat(workflowInstances).extracting(WorkflowInstanceTemplate.ID).containsExactlyInAnyOrderElementsOf(ids);
    if (endDate != null) {
      assertThat(workflowInstances).extracting(WorkflowInstanceTemplate.END_DATE).allMatch(ed -> ((OffsetDateTime) ed).toInstant().equals(endDate));
    }
  }

  private void assertDependentIndex(String mainIndexName, String idFieldName, List<String> ids, Instant endDate) {
    final String destinationIndexName;
    if (endDate != null) {
      destinationIndexName = reindexHelper.getDestinationIndexName(mainIndexName, dateTimeFormatter.format(endDate));
    } else {
      destinationIndexName = reindexHelper.getDestinationIndexName(mainIndexName, "");
    }
    final TermsQueryBuilder q = termsQuery(idFieldName, ids.toArray(new String[] {}));
    final SearchResponse response = esClient.prepareSearch(destinationIndexName)
      .setQuery(q)
      .setSize(100)
      .get();
    final List<EventEntity> events = ElasticsearchUtil
      .mapSearchHits(response.getHits().getHits(), objectMapper, EventEntity.class);
    assertThat(events).extracting(idFieldName).isSubsetOf(ids);
  }

  private void finishInstances(int count, Instant currentTime, String taskId) {
    brokerRule.getClock().setCurrentTime(currentTime);
    ZeebeTestUtil.completeTask(getClient(), taskId, getWorkerName(), null, count);
  }

  private List<String> startInstances(String processId, int count, Instant currentTime) {
    assertThat(count).isGreaterThan(0);
    brokerRule.getClock().setCurrentTime(currentTime);
    List<String> ids = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      ids.add(IdTestUtil.getId(ZeebeTestUtil.startWorkflowInstance(zeebeClient, processId, "{\"var\": 123}")));
    }
    elasticsearchTestRule.processAllEventsAndWait(workflowInstancesAreStartedCheck, ids);
    return ids;
  }

}
