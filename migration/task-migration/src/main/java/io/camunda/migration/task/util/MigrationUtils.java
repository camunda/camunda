/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.task.util;

import io.camunda.migration.api.MigrationException;
import io.camunda.migration.commons.storage.ProcessorStep;
import io.camunda.webapps.schema.descriptors.template.TaskTemplate;
import io.camunda.webapps.schema.entities.usertask.TaskEntity;
import io.camunda.zeebe.util.VersionUtil;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;

public class MigrationUtils {

  private static final String TASK_ID_PREFIX = "taskId-";
  private static final String MIGRATION_STEP_COMPLETED_CONTENT = "status-completed";

  public static TaskEntity consolidate(
      final TaskEntity originalEntity, final TaskEntity newEntity) {
    final TaskEntity taskEntity = new TaskEntity();
    taskEntity.setId(originalEntity.getId());
    taskEntity.setKey(originalEntity.getKey());
    taskEntity.setTenantId(originalEntity.getTenantId());
    taskEntity.setPartitionId(originalEntity.getPartitionId());
    taskEntity.setFlowNodeBpmnId(
        getOrDefault(newEntity.getFlowNodeBpmnId(), originalEntity.getFlowNodeBpmnId()));
    taskEntity.setName(originalEntity.getName());
    taskEntity.setFlowNodeInstanceId(originalEntity.getFlowNodeInstanceId());
    taskEntity.setCompletionTime(newEntity.getCompletionTime());
    taskEntity.setProcessInstanceId(originalEntity.getProcessInstanceId());
    taskEntity.setPosition(getOrDefault(newEntity.getPosition(), originalEntity.getPosition()));
    taskEntity.setState(getOrDefault(newEntity.getState(), originalEntity.getState()));
    taskEntity.setCreationTime(originalEntity.getCreationTime());
    taskEntity.setBpmnProcessId(
        getOrDefault(newEntity.getBpmnProcessId(), originalEntity.getBpmnProcessId()));
    taskEntity.setProcessDefinitionId(
        getOrDefault(newEntity.getProcessDefinitionId(), originalEntity.getProcessDefinitionId()));
    taskEntity.setAssignee(newEntity.getAssignee());
    taskEntity.setCandidateGroups(
        getOrDefault(newEntity.getCandidateGroups(), originalEntity.getCandidateGroups()));
    taskEntity.setCandidateUsers(
        getOrDefault(newEntity.getCandidateUsers(), originalEntity.getCandidateUsers()));
    taskEntity.setFormKey(getOrDefault(newEntity.getFormKey(), originalEntity.getFormKey()));
    taskEntity.setFormId(getOrDefault(newEntity.getFormId(), originalEntity.getFormId()));
    taskEntity.setFormVersion(
        getOrDefault(newEntity.getFormVersion(), originalEntity.getFormVersion()));
    taskEntity.setIsFormEmbedded(
        getOrDefault(newEntity.getIsFormEmbedded(), originalEntity.getIsFormEmbedded()));
    taskEntity.setFollowUpDate(
        getOrDefault(newEntity.getFollowUpDate(), originalEntity.getFollowUpDate()));
    taskEntity.setDueDate(getOrDefault(newEntity.getDueDate(), originalEntity.getDueDate()));
    taskEntity.setExternalFormReference(
        getOrDefault(
            newEntity.getExternalFormReference(), originalEntity.getExternalFormReference()));
    taskEntity.setProcessDefinitionVersion(
        getOrDefault(
            newEntity.getProcessDefinitionVersion(), originalEntity.getProcessDefinitionVersion()));
    taskEntity.setCustomHeaders(originalEntity.getCustomHeaders());
    taskEntity.setPriority(getOrDefault(newEntity.getPriority(), originalEntity.getPriority()));
    taskEntity.setAction(getOrDefault(newEntity.getAction(), originalEntity.getAction()));
    taskEntity.setChangedAttributes(
        getOrDefault(newEntity.getChangedAttributes(), originalEntity.getChangedAttributes()));
    taskEntity.setJoin(getOrDefault(newEntity.getJoin(), originalEntity.getJoin()));
    taskEntity.setImplementation(originalEntity.getImplementation());
    return taskEntity;
  }

  private static <T> T getOrDefault(final T value, final T defaultValue) {
    return value != null ? value : defaultValue;
  }

  public static Map<String, Object> getUpdateMap(final TaskEntity entity) {
    final Map<String, Object> updateMap = new HashMap<>();
    if (entity.getId() != null) {
      updateMap.put(TaskTemplate.ID, entity.getId());
    }
    updateMap.put(TaskTemplate.KEY, entity.getKey());

    if (entity.getCreationTime() != null) {
      updateMap.put(TaskTemplate.CREATION_TIME, entity.getCreationTime());
    }
    if (entity.getCompletionTime() != null) {
      updateMap.put(TaskTemplate.COMPLETION_TIME, entity.getCompletionTime());
    }
    if (entity.getDueDate() != null) {
      updateMap.put(TaskTemplate.DUE_DATE, entity.getDueDate());
    }
    if (entity.getFollowUpDate() != null) {
      updateMap.put(TaskTemplate.FOLLOW_UP_DATE, entity.getFollowUpDate());
    }

    if (entity.getState() != null) {
      updateMap.put(TaskTemplate.STATE, entity.getState());
    }
    if (entity.getImplementation() != null) {
      updateMap.put(TaskTemplate.IMPLEMENTATION, entity.getImplementation());
    }
    updateMap.put(TaskTemplate.ASSIGNEE, entity.getAssignee());
    if (entity.getCandidateGroups() != null) {
      updateMap.put(TaskTemplate.CANDIDATE_GROUPS, entity.getCandidateGroups());
    }
    if (entity.getCandidateUsers() != null) {
      updateMap.put(TaskTemplate.CANDIDATE_USERS, entity.getCandidateUsers());
    }
    if (entity.getCustomHeaders() != null) {
      updateMap.put(TaskTemplate.CUSTOM_HEADERS, entity.getCustomHeaders());
    }
    if (entity.getPriority() != null) {
      updateMap.put(TaskTemplate.PRIORITY, entity.getPriority());
    }
    if (entity.getAction() != null) {
      updateMap.put(TaskTemplate.ACTION, entity.getAction());
    }
    if (entity.getChangedAttributes() != null) {
      updateMap.put(TaskTemplate.CHANGED_ATTRIBUTES, entity.getChangedAttributes());
    }

    if (entity.getFormId() != null) {
      updateMap.put(TaskTemplate.FORM_ID, entity.getFormId());
    }
    if (entity.getFormKey() != null) {
      updateMap.put(TaskTemplate.FORM_KEY, entity.getFormKey());
    }
    if (entity.getFormVersion() != null) {
      updateMap.put(TaskTemplate.FORM_VERSION, entity.getFormVersion());
    }
    if (entity.getIsFormEmbedded() != null) {
      updateMap.put("isFormEmbedded", entity.getIsFormEmbedded());
    }
    if (entity.getExternalFormReference() != null) {
      updateMap.put(TaskTemplate.EXTERNAL_FORM_REFERENCE, entity.getExternalFormReference());
    }
    if (entity.getTenantId() != null) {
      updateMap.put(TaskTemplate.TENANT_ID, entity.getTenantId());
    }
    updateMap.put(TaskTemplate.PARTITION_ID, entity.getPartitionId());
    if (entity.getPosition() != null) {
      updateMap.put(TaskTemplate.POSITION, entity.getPosition());
    }

    if (entity.getBpmnProcessId() != null) {
      updateMap.put(TaskTemplate.BPMN_PROCESS_ID, entity.getBpmnProcessId());
    }
    if (entity.getProcessDefinitionId() != null) {
      updateMap.put(TaskTemplate.PROCESS_DEFINITION_ID, entity.getProcessDefinitionId());
    }
    if (entity.getFlowNodeBpmnId() != null) {
      updateMap.put(TaskTemplate.FLOW_NODE_BPMN_ID, entity.getFlowNodeBpmnId());
    }
    if (entity.getName() != null) {
      updateMap.put(TaskTemplate.NAME, entity.getName());
    }
    if (entity.getFlowNodeInstanceId() != null) {
      updateMap.put(TaskTemplate.FLOW_NODE_INSTANCE_ID, entity.getFlowNodeInstanceId());
    }
    if (entity.getProcessInstanceId() != null) {
      updateMap.put(TaskTemplate.PROCESS_INSTANCE_ID, entity.getProcessInstanceId());
    }
    if (entity.getProcessDefinitionVersion() != null) {
      updateMap.put(TaskTemplate.PROCESS_DEFINITION_VERSION, entity.getProcessDefinitionVersion());
    }
    if (entity.getJoin() != null) {
      updateMap.put(TaskTemplate.JOIN_FIELD_NAME, entity.getJoin());
    }
    return updateMap;
  }

  public static ProcessorStep getTaskMigrationCompletionStep() {
    return taskMigrationStepTmp(generateMigrationStepCompletedContent());
  }

  public static ProcessorStep getTaskMigrationStepForTaskId(final String taskId) {
    return taskMigrationStepTmp(generateMigrationStepTaskIdContent(taskId));
  }

  private static ProcessorStep taskMigrationStepTmp(final String content) {
    final ProcessorStep step = new ProcessorStep();
    step.setContent(content);
    step.setDescription("Task migration last migrated document ID or completion status");

    final var appliedDate = OffsetDateTime.now(ZoneId.systemDefault());
    step.setAppliedDate(appliedDate);

    step.setIndexName(TaskTemplate.INDEX_NAME);
    step.setVersion(VersionUtil.getVersion());
    step.setApplied(true);
    return step;
  }

  public static String generateNewIndexNameFromLegacy(final String legacyIndex) {
    if (legacyIndex == null || !legacyIndex.contains("task-8.5.0_")) {
      throw new MigrationException("Invalid legacy index: " + legacyIndex);
    }
    return legacyIndex.replace("8.5.0", "8.8.0");
  }

  public static String getTaskIdFromMigrationStepContent(final String stepContent) {
    if (stepContent != null && stepContent.startsWith(TASK_ID_PREFIX)) {
      return stepContent.replace(TASK_ID_PREFIX, "");
    } else {
      return null;
    }
  }

  public static String generateMigrationStepTaskIdContent(final String taskId) {
    return TASK_ID_PREFIX + taskId;
  }

  public static boolean isMigrationStepCompleted(final String stepContent) {
    return MIGRATION_STEP_COMPLETED_CONTENT.equals(stepContent);
  }

  public static String generateMigrationStepCompletedContent() {
    return MIGRATION_STEP_COMPLETED_CONTENT;
  }
}
