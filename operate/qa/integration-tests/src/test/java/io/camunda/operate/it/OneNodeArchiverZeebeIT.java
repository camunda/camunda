/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.it;

import static io.camunda.operate.qa.util.RestAPITestUtil.createGetAllFinishedRequest;
import static io.camunda.operate.qa.util.RestAPITestUtil.createGetAllProcessInstancesQuery;
import static io.camunda.webapps.schema.descriptors.operate.template.ListViewTemplate.PROCESS_INSTANCE_JOIN_RELATION;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.archiver.Archiver;
import io.camunda.operate.archiver.ProcessInstancesArchiverJob;
import io.camunda.operate.entities.OperationType;
import io.camunda.operate.exceptions.ArchiverException;
import io.camunda.operate.property.OperateProperties;
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
import io.camunda.webapps.schema.descriptors.operate.ProcessInstanceDependant;
import io.camunda.webapps.schema.descriptors.operate.template.IncidentTemplate;
import io.camunda.webapps.schema.descriptors.operate.template.ListViewTemplate;
import io.camunda.webapps.schema.descriptors.operate.template.SequenceFlowTemplate;
import io.camunda.webapps.schema.entities.operate.listview.ProcessInstanceForListViewEntity;
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

  protected void createOperations(final List<Long> ids1) {
    final ListViewQueryDto query = createGetAllProcessInstancesQuery();
    query.setIds(CollectionUtil.toSafeListOfStrings(ids1));
    final CreateBatchOperationRequestDto batchOperationRequest =
        new CreateBatchOperationRequestDto(
            query, OperationType.CANCEL_PROCESS_INSTANCE); // the type does not matter
    batchOperationWriter.scheduleBatchOperation(batchOperationRequest);
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

  private void assertInstancesInCorrectIndex(final int instancesCount, final Instant endDate)
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

  private List<Long> assertProcessInstanceIndex(final int instancesCount, final Instant endDate)
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
      final String mainIndexName,
      final String idFieldName,
      final List<Long> ids,
      final Instant endDate,
      final boolean ignoreAbsentIndex)
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
    } catch (final ElasticsearchStatusException ex) {
      if (!ex.getMessage().contains("index_not_found_exception") || !ignoreAbsentIndex) {
        throw ex;
      }
      // else ignore
    }
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
    return ids;
  }
}
