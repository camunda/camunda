/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.elasticsearch.index.query.QueryBuilders.constantScoreQuery;
import static org.elasticsearch.index.query.QueryBuilders.matchAllQuery;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.tasklist.archiver.Archiver;
import io.camunda.tasklist.archiver.TaskArchiverJob;
import io.camunda.tasklist.entities.TaskEntity;
import io.camunda.tasklist.exceptions.ArchiverException;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.schema.templates.TaskTemplate;
import io.camunda.tasklist.schema.templates.TaskVariableTemplate;
import io.camunda.tasklist.util.ElasticsearchUtil;
import io.camunda.tasklist.util.TasklistZeebeIntegrationTest;
import io.camunda.tasklist.zeebe.PartitionHolder;
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
import java.util.List;
import java.util.Random;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(
    properties = {
      TasklistProperties.PREFIX + ".importer.startLoadingDataOnStartup = false",
      TasklistProperties.PREFIX + ".clusterNode.nodeCount = 2",
      TasklistProperties.PREFIX + ".clusterNode.currentNodeId = 0"
    })
public class OneNodeArchiverIT extends TasklistZeebeIntegrationTest {

  private TaskArchiverJob archiverJob;

  @Autowired private BeanFactory beanFactory;

  @Autowired private Archiver archiver;

  @Autowired private RestHighLevelClient esClient;

  @Autowired private TaskTemplate taskTemplate;

  @Autowired private TaskVariableTemplate taskVariableTemplate;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private PartitionHolder partitionHolder;

  private Random random = new Random();

  private DateTimeFormatter dateTimeFormatter;

  @Before
  public void before() {
    super.before();
    dateTimeFormatter =
        DateTimeFormatter.ofPattern(tasklistProperties.getArchiver().getRolloverDateFormat())
            .withZone(ZoneId.systemDefault());
    archiverJob = beanFactory.getBean(TaskArchiverJob.class, partitionHolder.getPartitionIds());
  }

  @Test
  public void testArchiving() throws ArchiverException, IOException {
    final Instant currentTime = pinZeebeTime();

    // having
    // deploy process
    offsetZeebeTime(Duration.ofDays(-4));
    final String processId = "demoProcess";
    final String flowNodeBpmnId = "task1";
    deployProcessWithOneFlowNode(processId, flowNodeBpmnId);

    // start instances 3 days ago
    final int count = random.nextInt(6) + 3;
    final Instant endDate = currentTime.minus(2, ChronoUnit.DAYS);
    startInstancesAndCompleteTasks(processId, flowNodeBpmnId, count, endDate);
    resetZeebeTime();

    // when
    final int expectedCount =
        count
            / tasklistProperties
                .getClusterNode()
                .getNodeCount(); // we're archiving only part of the partitions
    assertThat(archiverJob.archiveNextBatch()).isGreaterThanOrEqualTo(expectedCount);
    assertThat(archiverJob.archiveNextBatch()).isLessThanOrEqualTo(expectedCount + 1);

    elasticsearchTestRule.refreshIndexesInElasticsearch();

    // then
    assertTasksInCorrectIndex(expectedCount, endDate);
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

  private void assertTasksInCorrectIndex(int tasksCount, Instant endDate) throws IOException {
    assertTaskIndex(tasksCount, endDate);
  }

  private List<String> assertTaskIndex(int tasksCount, Instant endDate) throws IOException {
    final String destinationIndexName;
    if (endDate != null) {
      destinationIndexName =
          archiver.getDestinationIndexName(
              taskTemplate.getFullQualifiedName(), dateTimeFormatter.format(endDate));
    } else {
      destinationIndexName =
          archiver.getDestinationIndexName(taskTemplate.getFullQualifiedName(), "");
    }
    final SearchRequest searchRequest =
        new SearchRequest(destinationIndexName)
            .source(new SearchSourceBuilder().query(constantScoreQuery(matchAllQuery())).size(100));

    final SearchResponse response = esClient.search(searchRequest, RequestOptions.DEFAULT);

    final List<TaskEntity> taskEntities =
        ElasticsearchUtil.mapSearchHits(
            response.getHits().getHits(), objectMapper, TaskEntity.class);
    assertThat(taskEntities.size()).isGreaterThanOrEqualTo(tasksCount);
    assertThat(taskEntities.size()).isLessThanOrEqualTo(tasksCount + 1);

    if (endDate != null) {
      assertThat(taskEntities)
          .extracting(TaskTemplate.COMPLETION_TIME)
          .allMatch(ed -> ((OffsetDateTime) ed).toInstant().equals(endDate));
    }
    return taskEntities.stream()
        .collect(
            ArrayList::new,
            (list, hit) -> list.add(hit.getId()),
            (list1, list2) -> list1.addAll(list2));
  }

  private void startInstancesAndCompleteTasks(
      String processId, String flowNodeBpmnId, int count, Instant currentTime) {
    assertThat(count).isGreaterThan(0);
    pinZeebeTime(currentTime);
    for (int i = 0; i < count; i++) {
      tester.startProcessInstance(processId, "{\"var\": 123}").completeUserTaskInZeebe();
    }
    tester.waitUntil().taskIsCompleted(flowNodeBpmnId);
  }
}
