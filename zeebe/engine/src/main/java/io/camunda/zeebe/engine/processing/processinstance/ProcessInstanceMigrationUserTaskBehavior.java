/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.processinstance;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.el.Expression;
import io.camunda.zeebe.engine.Loggers;
import io.camunda.zeebe.engine.processing.bpmn.BpmnElementContextImpl;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnBehaviors;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnUserTaskBehavior;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnUserTaskBehavior.UserTaskProperties;
import io.camunda.zeebe.engine.processing.deployment.model.element.AbstractFlowElement;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableJobWorkerElement;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableMultiInstanceBody;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableUserTask;
import io.camunda.zeebe.engine.processing.processinstance.ProcessInstanceMigrationMigrateProcessor.SafetyCheckFailedException;
import io.camunda.zeebe.engine.processing.processinstance.ProcessInstanceMigrationPreconditions.ProcessInstanceMigrationPreconditionFailedException;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.state.deployment.DeployedProcess;
import io.camunda.zeebe.engine.state.immutable.JobState;
import io.camunda.zeebe.engine.state.instance.ElementInstance;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.impl.record.value.usertask.UserTaskRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.HashMap;
import java.util.Map;
import org.agrona.DirectBuffer;
import org.slf4j.Logger;

public class ProcessInstanceMigrationUserTaskBehavior {

  private static final String ERROR_JOB_NOT_FOUND =
      """
                  Expected to migrate a job for user task '%s' \
                  on process instance with key '%d', \
                  but could not find job with key '%d'. \
                  Please resolve any incidents on the user task before migrating the process instance.""";

  private static final String ERROR_TARGET_FORM_EVALUATION =
      """
                  Expected to migrate form of user task with id '%s' \
                  to %s form with reference '%s' \
                  defined in target element '%s', \
                  but reference evaluation failed with message: %s""";

  private static final String WARN_EMBEDDED_FORM_MIGRATION =
      """
                  Migrated embedded form of user task with id '%s' \
                  to target element '%s' with Camunda user task implementation and no form reference \
                  for process instance with key '%s'.
                  """;

  private static final Logger LOGGER = Loggers.ENGINE_PROCESSING_LOGGER;
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private final long processInstanceKey;
  private final ElementInstance elementInstance;
  private final DeployedProcess sourceProcessDefinition;
  private final DeployedProcess targetProcessDefinition;
  private final String targetElementId;
  private final ProcessInstanceRecord updatedElementInstanceRecord;
  private final StateWriter stateWriter;
  private final BpmnUserTaskBehavior userTaskBehavior;
  private final JobState jobState;

  public ProcessInstanceMigrationUserTaskBehavior(
      final long processInstanceKey,
      final ElementInstance elementInstance,
      final DeployedProcess sourceProcessDefinition,
      final DeployedProcess targetProcessDefinition,
      final BpmnBehaviors bpmnBehaviors,
      final String targetElementId,
      final ProcessInstanceRecord updatedElementInstanceRecord,
      final StateWriter stateWriter,
      final JobState jobState) {
    this.processInstanceKey = processInstanceKey;
    this.elementInstance = elementInstance;
    this.sourceProcessDefinition = sourceProcessDefinition;
    this.targetProcessDefinition = targetProcessDefinition;
    this.targetElementId = targetElementId;
    this.updatedElementInstanceRecord = updatedElementInstanceRecord;
    this.stateWriter = stateWriter;
    this.jobState = jobState;
    userTaskBehavior = bpmnBehaviors.userTaskBehavior();
  }

  public void tryMigrateJobWorkerToCamundaUserTask() {
    final var jobKey = elementInstance.getJobKey();
    final String elementId = elementInstance.getValue().getElementId();

    final var job = jobState.getJob(jobKey);
    if (job == null) {
      throw new SafetyCheckFailedException(
          String.format(ERROR_JOB_NOT_FOUND, elementId, processInstanceKey, jobKey));
    }

    final ExecutableJobWorkerElement sourceElement =
        sourceProcessDefinition
            .getProcess()
            .getElementById(elementId, ExecutableJobWorkerElement.class);

    final ExecutableUserTask targetUserTask;
    final AbstractFlowElement flowNodeElement =
        targetProcessDefinition.getProcess().getElementById(targetElementId);
    if (flowNodeElement instanceof final ExecutableMultiInstanceBody mi) {
      targetUserTask = (ExecutableUserTask) mi.getInnerActivity();
    } else {
      targetUserTask = (ExecutableUserTask) flowNodeElement;
    }

    // Create new user task properties
    final Map<String, String> customHeaders = job.getCustomHeaders();
    final io.camunda.zeebe.engine.processing.deployment.model.element.UserTaskProperties
        targetProperties = targetUserTask.getUserTaskProperties();
    final var userTaskProperties = mapUserTaskProperties(customHeaders);

    // Create new Zeebe user task
    final var context = new BpmnElementContextImpl();
    context.init(
        elementInstance.getKey(), updatedElementInstanceRecord, elementInstance.getState());

    migrateForm(sourceElement, customHeaders, targetProperties, context, userTaskProperties);

    final var userTaskRecord =
        createNewUserTask(
            targetProperties,
            context,
            userTaskProperties,
            targetUserTask.getId(),
            getCustomHeaders(customHeaders));

    assignUser(userTaskProperties, userTaskRecord, context, targetProperties);

    job.setIsJobToUserTaskMigration(true);
    // Cancel previous job worker job
    stateWriter.appendFollowUpEvent(jobKey, JobIntent.CANCELED, job);
  }

  private void assignUser(
      final UserTaskProperties userTaskProperties,
      final UserTaskRecord userTaskRecord,
      final BpmnElementContextImpl context,
      final io.camunda.zeebe.engine.processing.deployment.model.element.UserTaskProperties
          targetProperties) {

    if (targetProperties.getAssignee() != null) {
      userTaskBehavior
          .evaluateAssigneeExpression(
              targetProperties.getAssignee(),
              context.getFlowScopeKey(),
              targetProcessDefinition.getTenantId())
          .ifRight(userTaskProperties::assignee);
    }

    final var assignee = userTaskProperties.getAssignee();
    // We always need to sync the assignee because for job-based tasks the assignee value could be
    // anything in secondary storage
    userTaskRecord.setAssignee(assignee);
    userTaskRecord.setAssigneeChanged();
    stateWriter.appendFollowUpEvent(
        userTaskRecord.getUserTaskKey(), UserTaskIntent.ASSIGNING, userTaskRecord);
    userTaskBehavior.userTaskAssigned(userTaskRecord, assignee);
  }

  private UserTaskRecord createNewUserTask(
      final io.camunda.zeebe.engine.processing.deployment.model.element.UserTaskProperties
          newProperties,
      final BpmnElementContextImpl context,
      final UserTaskProperties userTaskProperties,
      final DirectBuffer id,
      final Map<String, String> taskHeaders) {

    userTaskBehavior
        .evaluatePriorityExpression(
            newProperties.getPriority(),
            context.getFlowScopeKey(),
            targetProcessDefinition.getTenantId())
        .ifRight(userTaskProperties::priority);

    final var userTaskRecord =
        userTaskBehavior.createNewUserTask(context, id, userTaskProperties, taskHeaders);
    userTaskBehavior.userTaskCreated(userTaskRecord);
    elementInstance.setUserTaskKey(userTaskRecord.getUserTaskKey());
    return userTaskRecord;
  }

  private void migrateForm(
      final ExecutableJobWorkerElement sourceElement,
      final Map<String, String> customHeaders,
      final io.camunda.zeebe.engine.processing.deployment.model.element.UserTaskProperties
          targetElementProperties,
      final BpmnElementContextImpl context,
      final UserTaskProperties userTaskProperties) {
    final String jobFormKey = customHeaders.get(Protocol.USER_TASK_FORM_KEY_HEADER_NAME);
    final Expression workerFormId = sourceElement.getJobWorkerProperties().getFormId();

    if (jobFormKey != null) {
      if (jobFormKey.toLowerCase().contains("bpmn:usertaskform")) {
        if (targetElementProperties.getExternalFormReference() != null) {
          // external form
          userTaskBehavior
              .evaluateExternalFormReferenceExpression(
                  targetElementProperties.getExternalFormReference(),
                  context.getFlowScopeKey(),
                  targetProcessDefinition.getTenantId())
              .ifRightOrLeft(
                  userTaskProperties::externalFormReference,
                  failure -> {
                    throw new ProcessInstanceMigrationPreconditionFailedException(
                        ERROR_TARGET_FORM_EVALUATION.formatted(
                            BufferUtil.bufferAsString(sourceElement.getId()),
                            "external",
                            targetElementProperties.getExternalFormReference(),
                            targetElementId,
                            failure.getMessage()),
                        RejectionType.INVALID_STATE);
                  });
        } else if (targetElementProperties.getFormId() != null) {
          // internal form
          userTaskBehavior
              .evaluateFormIdExpressionToFormKey(
                  targetElementProperties.getFormId(),
                  targetElementProperties.getFormBindingType(),
                  targetElementProperties.getFormVersionTag(),
                  context,
                  context.getFlowScopeKey(),
                  targetProcessDefinition.getTenantId())
              .ifRightOrLeft(
                  userTaskProperties::formKey,
                  failure -> {
                    throw new ProcessInstanceMigrationPreconditionFailedException(
                        ERROR_TARGET_FORM_EVALUATION.formatted(
                            BufferUtil.bufferAsString(sourceElement.getId()),
                            "internal",
                            targetElementProperties.getFormId().getExpression(),
                            targetElementId,
                            failure.getMessage()),
                        RejectionType.INVALID_STATE);
                  });
        } else {
          // none
          LOGGER.warn(
              WARN_EMBEDDED_FORM_MIGRATION.formatted(
                  BufferUtil.bufferAsString(sourceElement.getId()),
                  targetElementId,
                  processInstanceKey));
        }
      } else if (workerFormId == null) {
        // external form
        userTaskProperties.externalFormReference(jobFormKey);
      } else {
        // internal form
        userTaskProperties.formKey(Long.parseLong(jobFormKey));
      }
    }
  }

  private static UserTaskProperties mapUserTaskProperties(final Map<String, String> customHeaders) {
    final UserTaskProperties userTaskProperties = new UserTaskProperties();

    final String candidateGroups =
        customHeaders.get(Protocol.USER_TASK_CANDIDATE_GROUPS_HEADER_NAME);
    if (candidateGroups != null) {
      try {
        userTaskProperties.candidateGroups(
            OBJECT_MAPPER.readValue(candidateGroups, new TypeReference<>() {}));
      } catch (final JsonProcessingException e) {
        throw new ProcessInstanceMigrationPreconditionFailedException(
            "Failed to parse candidate groups: " + e.getMessage(), RejectionType.INVALID_STATE);
      }
    }
    final String candidateUsers = customHeaders.get(Protocol.USER_TASK_CANDIDATE_USERS_HEADER_NAME);
    if (candidateUsers != null) {
      try {
        userTaskProperties.candidateUsers(
            OBJECT_MAPPER.readValue(candidateUsers, new TypeReference<>() {}));
      } catch (final JsonProcessingException e) {
        throw new ProcessInstanceMigrationPreconditionFailedException(
            "Failed to parse candidate users: " + e.getMessage(), RejectionType.INVALID_STATE);
      }
    }
    final String dueDate = customHeaders.get(Protocol.USER_TASK_DUE_DATE_HEADER_NAME);
    if (dueDate != null) {
      userTaskProperties.dueDate(dueDate);
    }
    final String followUpDate = customHeaders.get(Protocol.USER_TASK_FOLLOW_UP_DATE_HEADER_NAME);
    if (followUpDate != null) {
      userTaskProperties.followUpDate(followUpDate);
    }
    return userTaskProperties;
  }

  // Filter custom headers to only include non-usertask-related headers
  private Map<String, String> getCustomHeaders(final Map<String, String> customHeaders) {
    final Map<String, String> newMap = new HashMap<>();
    for (final Map.Entry<String, String> entry : customHeaders.entrySet()) {
      if (!entry.getKey().startsWith(Protocol.RESERVED_HEADER_NAME_PREFIX)) {
        newMap.put(entry.getKey(), entry.getValue());
      }
    }
    return newMap;
  }
}
