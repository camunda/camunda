/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade.migrate34To35;

import lombok.SneakyThrows;
import org.camunda.optimize.dto.optimize.ProcessInstanceDto;
import org.camunda.optimize.dto.optimize.persistence.AssigneeOperationDto;
import org.camunda.optimize.dto.optimize.persistence.CandidateGroupOperationDto;
import org.camunda.optimize.dto.optimize.query.event.process.FlowNodeInstanceDto;
import org.camunda.optimize.upgrade.plan.UpgradePlan;
import org.camunda.optimize.upgrade.plan.factories.Upgrade34to35PlanFactory;
import org.elasticsearch.search.SearchHit;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static java.util.stream.Collectors.toMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.FLOW_NODE_ID;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.FLOW_NODE_INSTANCES;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.FLOW_NODE_INSTANCE_ID;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.FLOW_NODE_TOTAL_DURATION;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.FLOW_NODE_TYPE;
import static org.camunda.optimize.service.util.importing.EngineConstants.FLOW_NODE_TYPE_USER_TASK;
import static org.camunda.optimize.util.SuppressionConstants.UNCHECKED_CAST;

public class MigrateUserTaskAndFlowNodeDataInProcessInstanceIndicesIT extends AbstractUpgrade34IT {

  private static final String OLD_USER_TASKS_FIELD_NAME = "userTasks";
  private static final String OLD_FLOW_NODE_INSTANCES_FIELD_NAME = "events";
  private static final String[] OLD_FLOW_NODE_FIELDS =
    new String[]{"id", "activityId", "activityType", "durationInMs"};
  private static final String[] NEW_FLOW_NODE_FIELDS =
    new String[]{FLOW_NODE_ID, FLOW_NODE_INSTANCE_ID, FLOW_NODE_TYPE, FLOW_NODE_TOTAL_DURATION};

  private static final FlowNodeInstanceDto EXPECTED_FLOW_NODE_1 = FlowNodeInstanceDto.builder()
    .flowNodeInstanceId("firstFlowNodeInstanceId")
    .flowNodeId("firstFlowNodeId")
    .flowNodeType("firstFlowNodeType")
    .totalDurationInMs(10L)
    .startDate(OffsetDateTime.parse("2021-04-15T10:00:00+02:00"))
    .endDate(OffsetDateTime.parse("2021-04-15T11:00:00+02:00"))
    .canceled(false)
    .build();

  private static final FlowNodeInstanceDto EXPECTED_FLOW_NODE_2 = FlowNodeInstanceDto.builder()
    .flowNodeInstanceId("secondFlowNodeInstanceId")
    .flowNodeId("secondFlowNodeId")
    .flowNodeType("secondFlowNodeType")
    .totalDurationInMs(20L)
    .startDate(OffsetDateTime.parse("2021-04-15T12:00:00+02:00"))
    .endDate(OffsetDateTime.parse("2021-04-15T13:00:00+02:00"))
    .canceled(false)
    .build();

  private static final FlowNodeInstanceDto EXPECTED_USER_TASK_1 = FlowNodeInstanceDto.builder()
    .flowNodeInstanceId("firstUserTaskInstanceId")
    .flowNodeId("firstUserTaskFlowNodeId")
    .userTaskInstanceId("firstUserTaskId")
    .flowNodeType(FLOW_NODE_TYPE_USER_TASK)
    .startDate(OffsetDateTime.parse("2021-04-15T14:00:00+02:00"))
    .endDate(OffsetDateTime.parse("2021-04-15T15:00:00+02:00"))
    .canceled(false)
    .dueDate(OffsetDateTime.parse("2021-04-16T15:00:00+02:00"))
    .deleteReason("completed")
    .assignee("meggle")
    .assigneeOperations(Arrays.asList(
      new AssigneeOperationDto("430", "demo", "add", OffsetDateTime.parse("2021-04-15T14:15:00+02:00")),
      new AssigneeOperationDto("431", "demo", "delete", OffsetDateTime.parse("2021-04-15T14:30:00+02:00")),
      new AssigneeOperationDto("432", "meggle", "add", OffsetDateTime.parse("2021-04-15T14:45:00+02:00"))
    ))
    .totalDurationInMs(30L)
    .idleDurationInMs(10L)
    .workDurationInMs(20L)
    .build();

  private static final FlowNodeInstanceDto EXPECTED_USER_TASK_2 = FlowNodeInstanceDto.builder()
    .flowNodeInstanceId("secondUserTaskInstanceId")
    .flowNodeId("secondUserTaskFlowNodeId")
    .userTaskInstanceId("secondUserTaskId")
    .flowNodeType(FLOW_NODE_TYPE_USER_TASK)
    .startDate(OffsetDateTime.parse("2021-04-15T16:00:00+02:00"))
    .endDate(OffsetDateTime.parse("2021-04-15T17:00:00+02:00"))
    .canceled(false)
    .deleteReason("completed")
    .candidateGroups(Arrays.asList("firstGroup", "secondGroup"))
    .candidateGroupOperations(Arrays.asList(
      new CandidateGroupOperationDto("5080", "firstGroup", "add", OffsetDateTime.parse("2021-04-15T16:15:00+02:00")),
      new CandidateGroupOperationDto("5081", "secondGroup", "add", OffsetDateTime.parse("2021-04-15T16:45:00+02:00"))
    ))
    .totalDurationInMs(40L)
    .idleDurationInMs(15L)
    .workDurationInMs(25L)
    .build();

  // This userTask does not have a related flowNodeInstance in the 34-process-instance.json data to cover the edge
  // case of a userTask being imported without the associated flowNode import
  private static final FlowNodeInstanceDto EXPECTED_USER_TASK_3 = FlowNodeInstanceDto.builder()
    .flowNodeInstanceId("thirdUserTaskInstanceId")
    .flowNodeId("thirdUserTaskFlowNodeId")
    .userTaskInstanceId("thirdUserTaskId")
    .flowNodeType(FLOW_NODE_TYPE_USER_TASK)
    .startDate(OffsetDateTime.parse("2021-04-17T16:00:00+02:00"))
    .endDate(OffsetDateTime.parse("2021-04-17T17:00:00+02:00"))
    .canceled(false)
    .deleteReason("completed")
    .candidateGroups(Collections.singletonList("firstGroup"))
    .candidateGroupOperations(Collections.singletonList(
      new CandidateGroupOperationDto("5080", "firstGroup", "add", OffsetDateTime.parse("2021-04-15T16:15:00+02:00"))
    ))
    .totalDurationInMs(80L)
    .idleDurationInMs(35L)
    .workDurationInMs(45L)
    .build();

  @SneakyThrows
  @Test
  public void flowNodesMigrationInEventBasedInstanceIndices() {
    // given
    executeBulk("steps/3.4/processinstance/34-event-process-instances.json");
    final UpgradePlan upgradePlan = new Upgrade34to35PlanFactory().createUpgradePlan(upgradeDependencies);

    // when
    upgradeProcedure.performUpgrade(upgradePlan);

    // then
    final SearchHit[] hitsAfterUpgrade = getAllDocumentsOfIndex(EVENT_PROCESS_INSTANCE_INDEX.getIndexName());
    final List<ProcessInstanceDto> instancesAfterUpgrade =
      getAllDocumentsOfIndexAs(EVENT_PROCESS_INSTANCE_INDEX.getIndexName(), ProcessInstanceDto.class);

    assertNewMapping(hitsAfterUpgrade);
    assertNonUserTaskFlowNodeContent(instancesAfterUpgrade);
    assertProcessInstanceIdInFlowNodeData(instancesAfterUpgrade);
  }

  @SneakyThrows
  @Test
  public void flowNodesMigrationInNonEventBasedInstanceIndices() {
    // given
    executeBulk("steps/3.4/processinstance/34-process-instances.json");
    final UpgradePlan upgradePlan = new Upgrade34to35PlanFactory().createUpgradePlan(upgradeDependencies);

    // when
    upgradeProcedure.performUpgrade(upgradePlan);

    // then
    final SearchHit[] hitsAfterUpgrade = getAllDocumentsOfIndex(PROCESS_INSTANCE_INDEX.getIndexName());
    final List<ProcessInstanceDto> instancesAfterUpgrade =
      getAllDocumentsOfIndexAs(PROCESS_INSTANCE_INDEX.getIndexName(), ProcessInstanceDto.class);

    assertNewMapping(hitsAfterUpgrade);
    assertNonUserTaskFlowNodeContent(instancesAfterUpgrade);
    assertUserTaskContent(instancesAfterUpgrade);
    assertProcessInstanceIdInFlowNodeData(instancesAfterUpgrade);
  }

  @SuppressWarnings(UNCHECKED_CAST)
  private void assertNewMapping(final SearchHit[] hitsAfterUpgrade) {
    assertThat(hitsAfterUpgrade)
      .hasSize(2)
      .allSatisfy(
        instance -> {
          // userTasks Field has been removed
          assertThat(instance.getSourceAsMap()).doesNotContainKey(OLD_USER_TASKS_FIELD_NAME);
          assertThat(instance.getSourceAsMap()).doesNotContainKey(OLD_FLOW_NODE_INSTANCES_FIELD_NAME);
          assertThat(instance.getSourceAsMap()).containsKey(FLOW_NODE_INSTANCES);

          // flowNode instance fields have been renamed
          final List<Map<String, Object>> flowNodeInstances =
            (List<Map<String, Object>>) instance.getSourceAsMap().get(FLOW_NODE_INSTANCES);

          assertThat(flowNodeInstances)
            .isNotEmpty()
            .allSatisfy(
              flowNode -> {
                assertThat(flowNode).doesNotContainKeys(OLD_FLOW_NODE_FIELDS);
                assertThat(flowNode).containsKeys(NEW_FLOW_NODE_FIELDS);
              });
        }
      );
  }

  private void assertNonUserTaskFlowNodeContent(final List<ProcessInstanceDto> instancesAfterUpgrade) {
    assertThat(instancesAfterUpgrade)
      .hasSize(2)
      .extracting(ProcessInstanceDto::getFlowNodeInstances)
      .isNotEmpty()
      .allSatisfy(
        flowNodeInstances -> assertThat(flowNodeInstances)
          .filteredOn(flowNode -> !FLOW_NODE_TYPE_USER_TASK.equalsIgnoreCase(flowNode.getFlowNodeType()))
          .hasSize(2)
          .usingElementComparatorIgnoringFields(FlowNodeInstanceDto.Fields.processInstanceId)
          .containsExactlyInAnyOrder(EXPECTED_FLOW_NODE_1, EXPECTED_FLOW_NODE_2)
      );
  }

  private void assertUserTaskContent(final List<ProcessInstanceDto> instancesAfterUpgrade) {
    assertThat(instancesAfterUpgrade)
      .hasSize(2)
      .extracting(ProcessInstanceDto::getUserTasks)
      .isNotEmpty()
      .allSatisfy(
        userTaskInstances -> {
          assertThat(userTaskInstances)
            .hasSize(3)
            .usingElementComparatorIgnoringFields(FlowNodeInstanceDto.Fields.processInstanceId)
            .containsExactlyInAnyOrder(EXPECTED_USER_TASK_1, EXPECTED_USER_TASK_2, EXPECTED_USER_TASK_3);

          // Separately assert content of assignee- and candidateGroupOperations to ensure field by field comparison
          final Map<String, FlowNodeInstanceDto> userTaskMap = userTaskInstances.stream()
            .collect(toMap(FlowNodeInstanceDto::getFlowNodeInstanceId, Function.identity()));

          assertThat(userTaskMap.get(EXPECTED_USER_TASK_1.getFlowNodeInstanceId()))
            .extracting(FlowNodeInstanceDto::getAssigneeOperations)
            .usingRecursiveComparison()
            .isEqualTo(EXPECTED_USER_TASK_1.getAssigneeOperations());
          assertThat(userTaskMap.get(EXPECTED_USER_TASK_2.getFlowNodeInstanceId()))
            .extracting(FlowNodeInstanceDto::getAssigneeOperations)
            .usingRecursiveComparison()
            .isEqualTo(EXPECTED_USER_TASK_2.getAssigneeOperations());
          assertThat(userTaskMap.get(EXPECTED_USER_TASK_3.getFlowNodeInstanceId()))
            .extracting(FlowNodeInstanceDto::getAssigneeOperations)
            .usingRecursiveComparison()
            .isEqualTo(EXPECTED_USER_TASK_3.getAssigneeOperations());

          assertThat(userTaskMap.get(EXPECTED_USER_TASK_1.getFlowNodeInstanceId()))
            .extracting(FlowNodeInstanceDto::getCandidateGroupOperations)
            .usingRecursiveComparison()
            .isEqualTo(EXPECTED_USER_TASK_1.getCandidateGroupOperations());
          assertThat(userTaskMap.get(EXPECTED_USER_TASK_2.getFlowNodeInstanceId()))
            .extracting(FlowNodeInstanceDto::getCandidateGroupOperations)
            .usingRecursiveComparison()
            .isEqualTo(EXPECTED_USER_TASK_2.getCandidateGroupOperations());
          assertThat(userTaskMap.get(EXPECTED_USER_TASK_3.getFlowNodeInstanceId()))
            .extracting(FlowNodeInstanceDto::getCandidateGroupOperations)
            .usingRecursiveComparison()
            .isEqualTo(EXPECTED_USER_TASK_3.getCandidateGroupOperations());
        });
  }

  private void assertProcessInstanceIdInFlowNodeData(final List<ProcessInstanceDto> instancesAfterUpgrade) {
    assertThat(instancesAfterUpgrade)
      .isNotEmpty()
      .allSatisfy(
        procInstance -> assertThat(procInstance.getFlowNodeInstances())
          .extracting(FlowNodeInstanceDto::getProcessInstanceId)
          .containsOnly(procInstance.getProcessInstanceId())
      );
  }

}
