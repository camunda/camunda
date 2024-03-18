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
package io.camunda.operate.it;

import static io.camunda.operate.qa.util.RestAPITestUtil.createGetAllFinishedRequest;
import static io.camunda.operate.qa.util.RestAPITestUtil.createGetAllProcessInstancesQuery;
import static io.camunda.operate.schema.templates.ListViewTemplate.PROCESS_INSTANCE_JOIN_RELATION;
import static org.assertj.core.api.Assertions.assertThat;

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
import io.camunda.operate.util.OperateZeebeAbstractIT;
import io.camunda.operate.util.ZeebeTestUtil;
import io.camunda.operate.util.searchrepository.TestSearchRepository;
import io.camunda.operate.webapp.reader.ListViewReader;
import io.camunda.operate.webapp.rest.dto.listview.ListViewQueryDto;
import io.camunda.operate.webapp.rest.dto.listview.ListViewRequestDto;
import io.camunda.operate.webapp.rest.dto.listview.ListViewResponseDto;
import io.camunda.operate.webapp.rest.dto.operation.CreateBatchOperationRequestDto;
import io.camunda.operate.webapp.writer.BatchOperationWriter;
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
import java.util.function.Predicate;
import org.elasticsearch.ElasticsearchStatusException;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(
    properties = {
      OperateProperties.PREFIX + ".importer.startLoadingDataOnStartup = false",
      OperateProperties.PREFIX + ".clusterNode.nodeCount = 2",
      OperateProperties.PREFIX + ".clusterNode.currentNodeId = 0"
    })
public class OneNodeArchiverZeebeIT extends OperateZeebeAbstractIT {

  private ProcessInstancesArchiverJob archiverJob;

  @Autowired private BeanFactory beanFactory;

  @Autowired private Archiver archiver;

  @Autowired private TestSearchRepository testSearchRepository;

  @Autowired private ListViewTemplate listViewTemplate;

  @Autowired private BatchOperationWriter batchOperationWriter;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private PartitionHolder partitionHolder;

  @Autowired private ListViewReader listViewReader;

  @Autowired private List<ProcessInstanceDependant> processInstanceDependantTemplates;

  private Random random = new Random();

  private DateTimeFormatter dateTimeFormatter;

  @Before
  public void before() {
    super.before();
    dateTimeFormatter =
        DateTimeFormatter.ofPattern(operateProperties.getArchiver().getRolloverDateFormat())
            .withZone(ZoneId.systemDefault());
    archiverJob =
        beanFactory.getBean(
            ProcessInstancesArchiverJob.class, archiver, partitionHolder.getPartitionIds());
  }

  @Test
  public void testArchiving() throws ArchiverException, IOException {
    final Instant currentTime = pinZeebeTime();

    // having
    // deploy process
    pinZeebeTime(currentTime.minus(4, ChronoUnit.DAYS));
    final String processId = "demoProcess";
    final String activityId = "task1";
    deployProcessWithOneActivity(processId, activityId);

    // start instances 3 days ago
    final int count = random.nextInt(6) + 5;
    final List<Long> ids1 = startInstances(processId, count, currentTime.minus(3, ChronoUnit.DAYS));
    createOperations(ids1);
    // finish instances 2 days ago
    final Instant endDate = currentTime.minus(2, ChronoUnit.DAYS);
    finishInstances(count, endDate, activityId);
    searchTestRule.processAllRecordsAndWait(getPartOfProcessInstancesAreFinishedCheck(), ids1);

    pinZeebeTime(currentTime);

    // when
    final int expectedCount =
        count
            / operateProperties
                .getClusterNode()
                .getNodeCount(); // we're archiving only part of the partitions
    assertThat(archiverJob.archiveNextBatch().join()).isGreaterThanOrEqualTo(expectedCount);
    searchTestRule.refreshSerchIndexes();
    assertThat(archiverJob.archiveNextBatch().join()).isLessThanOrEqualTo(expectedCount + 1);

    searchTestRule.refreshSerchIndexes();

    // then
    assertInstancesInCorrectIndex(expectedCount, endDate);
  }

  public Predicate<Object[]> getPartOfProcessInstancesAreFinishedCheck() {
    return objects -> {
      assertThat(objects).hasSize(1);
      assertThat(objects[0]).isInstanceOf(List.class);
      @SuppressWarnings("unchecked")
      final List<Long> ids = (List<Long>) objects[0];
      final ListViewRequestDto getFinishedRequest =
          createGetAllFinishedRequest(q -> q.setIds(CollectionUtil.toSafeListOfStrings(ids)));
      getFinishedRequest.setPageSize(ids.size());
      final ListViewResponseDto responseDto =
          listViewReader.queryProcessInstances(getFinishedRequest);
      return responseDto.getTotalCount()
          >= ids.size() / operateProperties.getClusterNode().getNodeCount();
    };
  }

  protected void createOperations(List<Long> ids1) {
    final ListViewQueryDto query = createGetAllProcessInstancesQuery();
    query.setIds(CollectionUtil.toSafeListOfStrings(ids1));
    final CreateBatchOperationRequestDto batchOperationRequest =
        new CreateBatchOperationRequestDto(
            query, OperationType.CANCEL_PROCESS_INSTANCE); // the type does not matter
    batchOperationWriter.scheduleBatchOperation(batchOperationRequest);
  }

  private void deployProcessWithOneActivity(String processId, String activityId) {
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(processId)
            .startEvent("start")
            .serviceTask(activityId)
            .zeebeJobType(activityId)
            .endEvent()
            .done();
    deployProcess(process, processId + ".bpmn");
  }

  private void assertInstancesInCorrectIndex(int instancesCount, Instant endDate)
      throws IOException {
    final List<Long> ids = assertProcessInstanceIndex(instancesCount, endDate);
    for (final ProcessInstanceDependant template : processInstanceDependantTemplates) {
      if (!(template instanceof IncidentTemplate || template instanceof SequenceFlowTemplate)) {
        assertDependentIndex(
            template.getFullQualifiedName(),
            ProcessInstanceDependant.PROCESS_INSTANCE_KEY,
            ids,
            endDate,
            true);
      }
    }
  }

  private List<Long> assertProcessInstanceIndex(int instancesCount, Instant endDate)
      throws IOException {
    final String destinationIndexName =
        archiver.getDestinationIndexName(
            listViewTemplate.getFullQualifiedName(), dateTimeFormatter.format(endDate));
    final List<ProcessInstanceForListViewEntity> processInstances =
        testSearchRepository.searchJoinRelation(
            destinationIndexName,
            PROCESS_INSTANCE_JOIN_RELATION,
            ProcessInstanceForListViewEntity.class,
            100);
    assertThat(processInstances.size()).isGreaterThanOrEqualTo(instancesCount);
    assertThat(processInstances.size()).isLessThanOrEqualTo(instancesCount + 1);
    if (endDate != null) {
      assertThat(processInstances)
          .extracting(ListViewTemplate.END_DATE)
          .allMatch(ed -> ((OffsetDateTime) ed).toInstant().equals(endDate));
    }
    // assert partitions
    final List<Integer> partitionIds = partitionHolder.getPartitionIds();
    assertThat(processInstances)
        .extracting(ListViewTemplate.PARTITION_ID)
        .containsOnly(partitionIds.toArray());
    // return ids
    return processInstances.stream()
        .collect(
            ArrayList::new,
            (list, hit) -> list.add(Long.valueOf(hit.getId())),
            (list1, list2) -> list1.addAll(list2));
  }

  private void assertDependentIndex(
      String mainIndexName,
      String idFieldName,
      List<Long> ids,
      Instant endDate,
      boolean ignoreAbsentIndex)
      throws IOException {
    final String destinationIndexName;
    try {
      if (endDate != null) {
        destinationIndexName =
            archiver.getDestinationIndexName(mainIndexName, dateTimeFormatter.format(endDate));
      } else {
        destinationIndexName = archiver.getDestinationIndexName(mainIndexName, "");
      }
      final List<Long> idsFromEls =
          testSearchRepository.searchIds(destinationIndexName, idFieldName, ids, 100);
      assertThat(idsFromEls).as(mainIndexName).isSubsetOf(ids);
    } catch (ElasticsearchStatusException ex) {
      if (!ex.getMessage().contains("index_not_found_exception") || !ignoreAbsentIndex) {
        throw ex;
      }
      // else ignore
    }
  }

  private void finishInstances(int count, Instant currentTime, String taskId) {
    pinZeebeTime(currentTime);
    ZeebeTestUtil.completeTask(getClient(), taskId, getWorkerName(), null, count);
  }

  private List<Long> startInstances(String processId, int count, Instant currentTime) {
    assertThat(count).isGreaterThan(0);
    pinZeebeTime(currentTime);
    final List<Long> ids = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      ids.add(ZeebeTestUtil.startProcessInstance(zeebeClient, processId, "{\"var\": 123}"));
    }
    return ids;
  }
}
