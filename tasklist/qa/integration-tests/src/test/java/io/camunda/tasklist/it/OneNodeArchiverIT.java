/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE (“USE”), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * “Licensee” means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
 */
package io.camunda.tasklist.it;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.tasklist.archiver.ArchiverUtil;
import io.camunda.tasklist.archiver.TaskArchiverJob;
import io.camunda.tasklist.entities.TaskEntity;
import io.camunda.tasklist.exceptions.ArchiverException;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.schema.templates.TaskTemplate;
import io.camunda.tasklist.schema.templates.TaskVariableTemplate;
import io.camunda.tasklist.util.NoSqlHelper;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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

  @Autowired private ArchiverUtil archiverUtil;

  @Autowired private NoSqlHelper noSqlHelper;
  // @Autowired private RestHighLevelClient esClient;

  @Autowired private TaskTemplate taskTemplate;

  @Autowired private TaskVariableTemplate taskVariableTemplate;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private PartitionHolder partitionHolder;

  private Random random = new Random();

  private DateTimeFormatter dateTimeFormatter;

  @BeforeEach
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
    assertThat(archiverJob.archiveNextBatch().join().getValue())
        .isGreaterThanOrEqualTo(expectedCount);
    databaseTestExtension.refreshIndexesInElasticsearch();
    assertThat(archiverJob.archiveNextBatch().join().getValue())
        .isLessThanOrEqualTo(expectedCount + 1);

    databaseTestExtension.refreshIndexesInElasticsearch();

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
          archiverUtil.getDestinationIndexName(
              taskTemplate.getFullQualifiedName(), dateTimeFormatter.format(endDate));
    } else {
      destinationIndexName =
          archiverUtil.getDestinationIndexName(taskTemplate.getFullQualifiedName(), "");
    }

    final List<TaskEntity> taskEntities = noSqlHelper.getAllTasks(destinationIndexName);

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
