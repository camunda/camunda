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
package io.camunda.tasklist.zeebeimport.v860.processors.common;

import static org.apache.commons.lang3.StringUtils.isNotEmpty;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.tasklist.entities.TaskEntity;
import io.camunda.tasklist.entities.TaskImplementation;
import io.camunda.tasklist.entities.TaskState;
import io.camunda.tasklist.schema.templates.TaskTemplate;
import io.camunda.tasklist.store.FormStore;
import io.camunda.tasklist.util.DateUtil;
import io.camunda.tasklist.zeebeimport.v860.record.Intent;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.value.UserTaskRecordValue;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class UserTaskRecordToTaskEntityMapper {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(UserTaskRecordToTaskEntityMapper.class);

  private static final Set<Intent> SUPPORTED_INTENTS =
      EnumSet.of(
          Intent.CREATED,
          Intent.COMPLETED,
          Intent.CANCELED,
          Intent.MIGRATED,
          Intent.ASSIGNED,
          Intent.UPDATED);
  private final FormStore formStore;

  private final ObjectMapper objectMapper;

  public UserTaskRecordToTaskEntityMapper(
      final ObjectMapper objectMapper, final FormStore formStore) {
    this.objectMapper = objectMapper;
    this.formStore = formStore;
  }

  public Optional<TaskEntity> map(final Record<UserTaskRecordValue> record) {
    final Intent intent = (Intent) record.getIntent();
    LOGGER.debug("Intent {}", intent);
    if (intent == null || !SUPPORTED_INTENTS.contains(intent)) {
      LOGGER.debug("Unsupported intent={}. Skipping it", intent);
      return Optional.empty();
    }

    final UserTaskRecordValue recordValue = record.getValue();
    final String processDefinitionId = String.valueOf(recordValue.getProcessDefinitionKey());
    final TaskEntity entity =
        new TaskEntity()
            .setImplementation(TaskImplementation.ZEEBE_USER_TASK)
            .setId(String.valueOf(record.getKey()))
            .setKey(record.getKey())
            .setPartitionId(record.getPartitionId())
            .setFlowNodeBpmnId(recordValue.getElementId())
            .setFlowNodeInstanceId(String.valueOf(recordValue.getElementInstanceKey()))
            .setProcessInstanceId(String.valueOf(recordValue.getProcessInstanceKey()))
            .setBpmnProcessId(recordValue.getBpmnProcessId())
            .setProcessDefinitionId(processDefinitionId)
            .setTenantId(recordValue.getTenantId());

    switch (intent) {
      case CANCELED ->
          entity
              .setState(TaskState.CANCELED)
              .setCompletionTime(
                  DateUtil.toOffsetDateTime(Instant.ofEpochMilli(record.getTimestamp())));
      case COMPLETED ->
          entity
              .setState(TaskState.COMPLETED)
              .setCompletionTime(
                  DateUtil.toOffsetDateTime(Instant.ofEpochMilli(record.getTimestamp())));
      case CREATED ->
          entity
              .setState(TaskState.CREATED)
              .setCreationTime(
                  DateUtil.toOffsetDateTime(Instant.ofEpochMilli(record.getTimestamp())));
      case MIGRATED -> entity.setState(TaskState.CREATED);
      default -> {}
    }

    final String dueDate = recordValue.getDueDate();
    if (isNotEmpty(dueDate)) {
      final OffsetDateTime offSetDueDate = DateUtil.toOffsetDateTime(dueDate);
      if (offSetDueDate != null) {
        entity.setDueDate(offSetDueDate);
      }
    }

    final String followUpDate = recordValue.getFollowUpDate();
    if (isNotEmpty(followUpDate)) {
      final OffsetDateTime offSetFollowUpDate = DateUtil.toOffsetDateTime(followUpDate);
      if (offSetFollowUpDate != null) {
        entity.setFollowUpDate(offSetFollowUpDate);
      }
    }

    final long formKey = recordValue.getFormKey();
    if (formKey != -1) {
      final String strFromKey = String.valueOf(formKey);
      entity.setFormKey(strFromKey).setIsFormEmbedded(false);
      formStore
          .getHighestVersionFormByKey(strFromKey)
          .ifPresentOrElse(
              linkedForm -> {
                entity.setFormVersion(linkedForm.version());
                entity.setFormId(linkedForm.bpmnId());
              },
              () -> {
                LOGGER.warn("Form with key={} cannot be found", strFromKey);
              });
    }

    // TODO handle removal of attributes in Zeebe https://github.com/camunda/tasklist/issues/4306

    final String assignee = recordValue.getAssignee();
    if (isNotEmpty(assignee)) {
      entity.setAssignee(assignee);
    }

    final List<String> candidateGroups = recordValue.getCandidateGroupsList();

    if (!candidateGroups.isEmpty()) {
      entity.setCandidateGroups(candidateGroups.toArray(new String[candidateGroups.size()]));
    }

    final List<String> candidateUsers = recordValue.getCandidateUsersList();

    if (!candidateUsers.isEmpty()) {
      entity.setCandidateUsers(candidateUsers.toArray(new String[candidateUsers.size()]));
    }
    return Optional.of(entity);
  }

  public Map<String, Object> getUpdateFieldsMap(
      final TaskEntity entity, final Record<UserTaskRecordValue> record) {
    final Map<String, Object> updateFields = new HashMap<>();
    final Intent intent = (Intent) record.getIntent();
    if (entity.getState() != null) {
      updateFields.put(TaskTemplate.STATE, entity.getState());
    }
    switch (intent) {
      case MIGRATED -> {
        updateFields.put(TaskTemplate.FLOW_NODE_BPMN_ID, entity.getFlowNodeBpmnId());
        updateFields.put(TaskTemplate.BPMN_PROCESS_ID, entity.getBpmnProcessId());
        updateFields.put(TaskTemplate.PROCESS_DEFINITION_ID, entity.getProcessDefinitionId());
      }
      case COMPLETED, CANCELED -> {
        updateFields.put(TaskTemplate.STATE, entity.getState());
        updateFields.put(TaskTemplate.COMPLETION_TIME, entity.getCompletionTime());
      }
      case ASSIGNED -> {
        updateFields.put(TaskTemplate.ASSIGNEE, entity.getAssignee());
      }
      case UPDATED -> {
        final UserTaskRecordValue recordValue = record.getValue();
        final List<String> changedAttributes = recordValue.getChangedAttributes();
        for (final String attribute : changedAttributes) {
          switch (attribute) {
            case "candidateGroupsList" ->
                updateFields.put(TaskTemplate.CANDIDATE_GROUPS, entity.getCandidateGroups());
            case "candidateUsersList" ->
                updateFields.put(TaskTemplate.CANDIDATE_USERS, entity.getCandidateUsers());
            case "dueDate" -> updateFields.put(TaskTemplate.DUE_DATE, entity.getDueDate());
            case "followUpDate" ->
                updateFields.put(TaskTemplate.FOLLOW_UP_DATE, entity.getFollowUpDate());
            default -> {
              LOGGER.warn(
                  "Attribute update not mapped while importing ZEEBE_USER_TASKS: {}", attribute);
            }
          }
        }
      }
      default -> {}
        // TODO handle update of other fields in https://github.com/camunda/tasklist/issues/4306
    }
    return updateFields;
  }
}
