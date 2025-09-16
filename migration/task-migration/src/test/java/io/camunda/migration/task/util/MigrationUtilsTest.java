/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.task.util;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.webapps.schema.entities.usertask.TaskEntity;
import io.camunda.webapps.schema.entities.usertask.TaskEntity.TaskImplementation;
import io.camunda.webapps.schema.entities.usertask.TaskJoinRelationship;
import io.camunda.webapps.schema.entities.usertask.TaskState;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class MigrationUtilsTest {

  @Test
  void testTaskConsolidation() {
    // Given
    final var originalEntity = new TaskEntity();
    originalEntity.setId("taskId-123");
    originalEntity.setKey(123L);
    originalEntity.setTenantId("<default>");
    originalEntity.setPartitionId(1);
    originalEntity.setFlowNodeBpmnId("flowNodeBpmnId-123");
    originalEntity.setName("Test Task");
    originalEntity.setFlowNodeInstanceId("flowNodeInstanceId-123");
    originalEntity.setCompletionTime(null);
    originalEntity.setProcessInstanceId("processInstanceId-123");
    originalEntity.setPosition(100L);
    originalEntity.setState(TaskState.CREATED);
    originalEntity.setCreationTime(OffsetDateTime.now());
    originalEntity.setBpmnProcessId("bpmnProcessId-123");
    originalEntity.setProcessDefinitionId("processDefinitionId-123");
    originalEntity.setAssignee(null);
    originalEntity.setCandidateGroups(new String[] {"group1", "group2"});
    originalEntity.setCandidateUsers(new String[] {"user1", "user2"});
    originalEntity.setFormKey("formKey-123");
    originalEntity.setFormId("formId-123");
    originalEntity.setFormVersion(4);
    originalEntity.setIsFormEmbedded(true);
    originalEntity.setFollowUpDate(null);
    originalEntity.setDueDate(null);
    originalEntity.setExternalFormReference("externalFormReference-123");
    originalEntity.setProcessDefinitionVersion(2);
    originalEntity.setCustomHeaders(Map.of("headerKey", "headerValue"));
    originalEntity.setPriority(4);
    originalEntity.setAction("");
    originalEntity.setChangedAttributes(List.of());
    originalEntity.setJoin(new TaskJoinRelationship("task", 123L));
    originalEntity.setImplementation(TaskImplementation.ZEEBE_USER_TASK);

    final var partiallyUpdatedEntity = new TaskEntity();
    partiallyUpdatedEntity.setId("taskId-123");
    partiallyUpdatedEntity.setKey(123L);
    partiallyUpdatedEntity.setTenantId("<default>");
    partiallyUpdatedEntity.setPartitionId(0);
    partiallyUpdatedEntity.setProcessInstanceId("processInstanceId-123");
    partiallyUpdatedEntity.setState(TaskState.COMPLETED);
    partiallyUpdatedEntity.setCompletionTime(OffsetDateTime.now());
    partiallyUpdatedEntity.setAssignee("assignee-123");
    partiallyUpdatedEntity.setAction("assign");
    partiallyUpdatedEntity.setChangedAttributes(List.of("assignee"));
    partiallyUpdatedEntity.setJoin(new TaskJoinRelationship("task", 123L));

    // When
    final var consolidatedEntity =
        MigrationUtils.consolidate(originalEntity, partiallyUpdatedEntity);

    // Then
    assertThat(consolidatedEntity)
        .usingRecursiveComparison()
        .ignoringFields("assignee", "completionTime", "state", "changedAttributes", "action")
        .isEqualTo(originalEntity);

    assertThat(consolidatedEntity.getAssignee()).isEqualTo(partiallyUpdatedEntity.getAssignee());
    assertThat(consolidatedEntity.getCompletionTime())
        .isEqualTo(partiallyUpdatedEntity.getCompletionTime());
    assertThat(consolidatedEntity.getState()).isEqualTo(partiallyUpdatedEntity.getState());
    assertThat(consolidatedEntity.getChangedAttributes())
        .containsExactlyInAnyOrderElementsOf(partiallyUpdatedEntity.getChangedAttributes());
    assertThat(consolidatedEntity.getAction()).isEqualTo(partiallyUpdatedEntity.getAction());
  }

  @Test
  void testGetUpdateMap() {
    // Given
    final var originalEntity = new TaskEntity();
    originalEntity.setId("taskId-123");
    originalEntity.setKey(123L);
    originalEntity.setTenantId("<default>");
    originalEntity.setPartitionId(1);
    originalEntity.setFlowNodeBpmnId("flowNodeBpmnId-123");
    originalEntity.setName("Test Task");
    originalEntity.setFlowNodeInstanceId("flowNodeInstanceId-123");
    originalEntity.setCompletionTime(OffsetDateTime.now());
    originalEntity.setProcessInstanceId("processInstanceId-123");
    originalEntity.setPosition(100L);
    originalEntity.setState(TaskState.CREATED);
    originalEntity.setCreationTime(OffsetDateTime.now());
    originalEntity.setBpmnProcessId("bpmnProcessId-123");
    originalEntity.setProcessDefinitionId("processDefinitionId-123");
    originalEntity.setAssignee("user");
    originalEntity.setCandidateGroups(new String[] {"group1", "group2"});
    originalEntity.setCandidateUsers(new String[] {"user1", "user2"});
    originalEntity.setFormKey("formKey-123");
    originalEntity.setFormId("formId-123");
    originalEntity.setFormVersion(4);
    originalEntity.setIsFormEmbedded(true);
    originalEntity.setFollowUpDate(OffsetDateTime.now());
    originalEntity.setDueDate(OffsetDateTime.now());
    originalEntity.setExternalFormReference("externalFormReference-123");
    originalEntity.setProcessDefinitionVersion(2);
    originalEntity.setCustomHeaders(Map.of("headerKey", "headerValue"));
    originalEntity.setPriority(4);
    originalEntity.setAction("assign");
    originalEntity.setChangedAttributes(List.of("assignee"));
    originalEntity.setJoin(new TaskJoinRelationship("task", 123L));
    originalEntity.setImplementation(TaskEntity.TaskImplementation.ZEEBE_USER_TASK);

    // When
    final var updateMap = MigrationUtils.getUpdateMap(originalEntity);

    // Then
    final Map<String, Object> expectedUpdateMap = new HashMap<>();
    expectedUpdateMap.put("id", "taskId-123");
    expectedUpdateMap.put("key", 123L);
    expectedUpdateMap.put("tenantId", "<default>");
    expectedUpdateMap.put("partitionId", 1);
    expectedUpdateMap.put("flowNodeBpmnId", "flowNodeBpmnId-123");
    expectedUpdateMap.put("name", "Test Task");
    expectedUpdateMap.put("flowNodeInstanceId", "flowNodeInstanceId-123");
    expectedUpdateMap.put("completionTime", originalEntity.getCompletionTime());
    expectedUpdateMap.put("processInstanceId", "processInstanceId-123");
    expectedUpdateMap.put("position", 100L);
    expectedUpdateMap.put("state", TaskState.CREATED);
    expectedUpdateMap.put("creationTime", originalEntity.getCreationTime());
    expectedUpdateMap.put("bpmnProcessId", "bpmnProcessId-123");
    expectedUpdateMap.put("processDefinitionId", "processDefinitionId-123");
    expectedUpdateMap.put("assignee", "user");
    expectedUpdateMap.put("candidateGroups", new String[] {"group1", "group2"});
    expectedUpdateMap.put("candidateUsers", new String[] {"user1", "user2"});
    expectedUpdateMap.put("formKey", "formKey-123");
    expectedUpdateMap.put("formId", "formId-123");
    expectedUpdateMap.put("formVersion", 4L);
    expectedUpdateMap.put("isFormEmbedded", true);
    expectedUpdateMap.put("followUpDate", originalEntity.getFollowUpDate());
    expectedUpdateMap.put("dueDate", originalEntity.getDueDate());
    expectedUpdateMap.put("externalFormReference", "externalFormReference-123");
    expectedUpdateMap.put("processDefinitionVersion", 2);
    expectedUpdateMap.put("customHeaders", Map.of("headerKey", "headerValue"));
    expectedUpdateMap.put("priority", 4);
    expectedUpdateMap.put("action", "assign");
    expectedUpdateMap.put("changedAttributes", List.of("assignee"));
    expectedUpdateMap.put("join", originalEntity.getJoin());
    expectedUpdateMap.put("implementation", TaskEntity.TaskImplementation.ZEEBE_USER_TASK);
    assertThat(updateMap).containsExactlyInAnyOrderEntriesOf(expectedUpdateMap);
  }
}
