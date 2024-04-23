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
package io.camunda.operate.zeebeimport.v8_6.processors;

import static io.camunda.operate.schema.indices.IndexDescriptor.DEFAULT_TENANT_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.entities.UserTaskEntity;
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.schema.templates.UserTaskTemplate;
import io.camunda.operate.store.BatchRequest;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import io.camunda.zeebe.protocol.record.value.ImmutableUserTaskRecordValue;
import io.camunda.zeebe.protocol.record.value.UserTaskRecordValue;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserTaskZeebeRecordProcessorTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Mock private UserTaskTemplate userTaskTemplate;
  private UserTaskZeebeRecordProcessor userTaskZeebeRecordProcessor;

  @BeforeEach
  void setUp() {
    userTaskZeebeRecordProcessor = new UserTaskZeebeRecordProcessor(userTaskTemplate, objectMapper);
    assertThat(userTaskZeebeRecordProcessor).isNotNull();
  }

  @Test
  void createdEvent() throws PersistenceException {
    /* given */
    final var batchRequest = mock(BatchRequest.class);
    final var userTaskRecord = (Record<UserTaskRecordValue>) mock(Record.class);
    final var userTaskRecordValue =
        ImmutableUserTaskRecordValue.builder()
            .withUserTaskKey(1L)
            .withTenantId(DEFAULT_TENANT_ID)
            .build();
    when(userTaskTemplate.getFullQualifiedName()).thenReturn("user-task-index");
    when(userTaskRecord.getIntent()).thenReturn(UserTaskIntent.CREATED);
    when(userTaskRecord.getValue()).thenReturn(userTaskRecordValue);
    /* when */
    userTaskZeebeRecordProcessor.processUserTaskRecord(batchRequest, userTaskRecord);
    /* then */
    final var expectedUserEntity =
        new UserTaskEntity()
            .setId("1")
            .setKey(1L)
            .setUserTaskKey(1L)
            .setVariables("{}")
            .setCandidateUsers(List.of())
            .setCandidateGroups(List.of())
            .setFormKey(0L)
            .setElementInstanceKey(0L)
            .setProcessDefinitionKey(0L)
            .setProcessDefinitionVersion(0)
            .setProcessInstanceKey(0L)
            .setTenantId(DEFAULT_TENANT_ID)
            .setChangedAttributes(List.of());
    verify(batchRequest).addWithId("user-task-index", "1", expectedUserEntity);
  }

  @Test
  void migratedEvent() throws PersistenceException {
    /* given */
    final var batchRequest = mock(BatchRequest.class);
    final var userTaskRecord = (Record<UserTaskRecordValue>) mock(Record.class);
    final var userTaskRecordValue =
        ImmutableUserTaskRecordValue.builder()
            .withUserTaskKey(1L)
            .withTenantId(DEFAULT_TENANT_ID)
            .withBpmnProcessId("bpmn process id")
            .withProcessDefinitionVersion(2)
            .withProcessDefinitionKey(1L)
            .withElementId("element id")
            .build();
    when(userTaskTemplate.getFullQualifiedName()).thenReturn("user-task-index");
    when(userTaskRecord.getIntent()).thenReturn(UserTaskIntent.MIGRATED);
    when(userTaskRecord.getValue()).thenReturn(userTaskRecordValue);
    /* when */
    userTaskZeebeRecordProcessor.processUserTaskRecord(batchRequest, userTaskRecord);
    /* then */
    final Map<String, Object> expectedUpdateFields =
        Map.of(
            "bpmnProcessId",
            "bpmn process id",
            "processDefinitionVersion",
            2,
            "processDefinitionKey",
            1L,
            "elementId",
            "element id");
    verify(batchRequest).update("user-task-index", "1", expectedUpdateFields);
  }

  @Test
  void assignedEvent() throws PersistenceException {
    /* given */
    final var batchRequest = mock(BatchRequest.class);
    final var userTaskRecord = (Record<UserTaskRecordValue>) mock(Record.class);
    final var userTaskRecordValue =
        ImmutableUserTaskRecordValue.builder()
            .withUserTaskKey(1L)
            .withTenantId(DEFAULT_TENANT_ID)
            .withAssignee("Homer Simpson")
            .withAction("assign")
            .build();
    when(userTaskTemplate.getFullQualifiedName()).thenReturn("user-task-index");
    when(userTaskRecord.getIntent()).thenReturn(UserTaskIntent.ASSIGNED);
    when(userTaskRecord.getValue()).thenReturn(userTaskRecordValue);
    /* when */
    userTaskZeebeRecordProcessor.processUserTaskRecord(batchRequest, userTaskRecord);
    /* then */
    final Map<String, Object> expectedUpdateFields =
        Map.of("assignee", "Homer Simpson", "action", "assign");
    verify(batchRequest).update("user-task-index", "1", expectedUpdateFields);
  }

  @Test
  // Unassigned is implemented as assigned event with an empty assignee
  void unassignedEvent() throws PersistenceException {
    /* given */
    final var batchRequest = mock(BatchRequest.class);
    final var userTaskRecord = (Record<UserTaskRecordValue>) mock(Record.class);
    final var userTaskRecordValue =
        ImmutableUserTaskRecordValue.builder()
            .withUserTaskKey(1L)
            .withTenantId(DEFAULT_TENANT_ID)
            .withAssignee("")
            .withAction("assign")
            .build();
    when(userTaskTemplate.getFullQualifiedName()).thenReturn("user-task-index");
    when(userTaskRecord.getIntent()).thenReturn(UserTaskIntent.ASSIGNED);
    when(userTaskRecord.getValue()).thenReturn(userTaskRecordValue);
    /* when */
    userTaskZeebeRecordProcessor.processUserTaskRecord(batchRequest, userTaskRecord);
    /* then */
    final Map<String, Object> expectedUpdateFields = Map.of("assignee", "", "action", "assign");
    verify(batchRequest).update("user-task-index", "1", expectedUpdateFields);
  }

  @Test
  void updatedEvent() throws PersistenceException {
    /* given */
    final var batchRequest = mock(BatchRequest.class);
    final var userTaskRecord = (Record<UserTaskRecordValue>) mock(Record.class);
    final var userTaskRecordValue =
        ImmutableUserTaskRecordValue.builder()
            .withUserTaskKey(1L)
            .withTenantId(DEFAULT_TENANT_ID)
            .withCandidateUsersList(List.of("Homer", "Marge"))
            .withCandidateGroupsList(List.of("Simpsons", "Flanders"))
            .withDueDate("2023-05-23T01:02:03+01:00")
            .withAction("update")
            .withChangedAttributes(List.of("candidateUsersList", "candidateGroupsList", "dueDate"))
            .build();
    when(userTaskTemplate.getFullQualifiedName()).thenReturn("user-task-index");
    when(userTaskRecord.getIntent()).thenReturn(UserTaskIntent.UPDATED);
    when(userTaskRecord.getValue()).thenReturn(userTaskRecordValue);
    /* when */
    userTaskZeebeRecordProcessor.processUserTaskRecord(batchRequest, userTaskRecord);
    /* then */
    final Map<String, Object> expectedUpdateFields =
        Map.of(
            "candidateUsers",
            List.of("Homer", "Marge"),
            "candidateGroups",
            List.of("Simpsons", "Flanders"),
            "dueDate",
            OffsetDateTime.parse("2023-05-23T01:02:03+01:00"),
            "action",
            "update",
            "changedAttributes",
            List.of("candidateUsersList", "candidateGroupsList", "dueDate"));
    verify(batchRequest).update("user-task-index", "1", expectedUpdateFields);
  }

  @Test // https://github.com/camunda/zeebe/issues/17611
  void updateShouldIgnoreMissingAttributes() throws PersistenceException {
    /* given */
    final var batchRequest = mock(BatchRequest.class);
    final var userTaskRecord = (Record<UserTaskRecordValue>) mock(Record.class);
    final var userTaskRecordValue =
        ImmutableUserTaskRecordValue.builder()
            .withUserTaskKey(1L)
            .withTenantId(DEFAULT_TENANT_ID)
            .withCandidateUsersList(List.of("string"))
            .withCandidateGroupsList(List.of("string"))
            .withDueDate("2024-04-03T20:22:18.305Z")
            .withFollowUpDate("2024-04-03T20:22:18.305Z")
            .withAction("update")
            .withChangedAttributes(
                List.of(
                    "candidateUsersList",
                    "candidateGroupsList",
                    "dueDate",
                    "followUpDate",
                    "attribute-not-exist"))
            .build();
    when(userTaskTemplate.getFullQualifiedName()).thenReturn("user-task-index");
    when(userTaskRecord.getIntent()).thenReturn(UserTaskIntent.UPDATED);
    when(userTaskRecord.getValue()).thenReturn(userTaskRecordValue);
    /* when */
    userTaskZeebeRecordProcessor.processUserTaskRecord(batchRequest, userTaskRecord);
    /* then */
    final Map<String, Object> expectedUpdateFields =
        Map.of(
            "candidateUsers",
            List.of("string"),
            "candidateGroups",
            List.of("string"),
            "dueDate",
            OffsetDateTime.parse("2024-04-03T20:22:18.305Z"),
            "followUpDate",
            OffsetDateTime.parse("2024-04-03T20:22:18.305Z"),
            "action",
            "update",
            "changedAttributes",
            List.of(
                "candidateUsersList",
                "candidateGroupsList",
                "dueDate",
                "followUpDate",
                "attribute-not-exist"));
    verify(batchRequest).update("user-task-index", "1", expectedUpdateFields);
  }

  @Test
  void completedEvent() throws PersistenceException, JsonProcessingException {
    /* given */
    final var batchRequest = mock(BatchRequest.class);
    final var userTaskRecord = (Record<UserTaskRecordValue>) mock(Record.class);
    final var variablesDueCompletion =
        Map.of("answer", 42, "duff", Map.of("price", 5, "amount", "5l"));
    final var userTaskRecordValue =
        ImmutableUserTaskRecordValue.builder()
            .withUserTaskKey(1L)
            .withTenantId(DEFAULT_TENANT_ID)
            .withAction("complete")
            .withVariables(variablesDueCompletion)
            .build();
    when(userTaskTemplate.getFullQualifiedName()).thenReturn("user-task-index");
    when(userTaskRecord.getIntent()).thenReturn(UserTaskIntent.COMPLETED);
    when(userTaskRecord.getValue()).thenReturn(userTaskRecordValue);
    /* when */
    userTaskZeebeRecordProcessor.processUserTaskRecord(batchRequest, userTaskRecord);
    /* then */
    final Map<String, Object> expectedUpdateFields =
        Map.of(
            "action",
            "complete",
            "variables",
            objectMapper.writeValueAsString(variablesDueCompletion));
    verify(batchRequest).update("user-task-index", "1", expectedUpdateFields);
  }

  @Test
  void canceledEvent() throws Exception {
    /* given */
    final var batchRequest = mock(BatchRequest.class);
    final var userTaskRecord = (Record<UserTaskRecordValue>) mock(Record.class);
    final var userTaskRecordValue =
        ImmutableUserTaskRecordValue.builder()
            .withUserTaskKey(1L)
            .withTenantId(DEFAULT_TENANT_ID)
            .withAction("")
            .build();
    when(userTaskTemplate.getFullQualifiedName()).thenReturn("user-task-index");
    when(userTaskRecord.getIntent()).thenReturn(UserTaskIntent.CANCELED);
    when(userTaskRecord.getValue()).thenReturn(userTaskRecordValue);
    /* when */
    userTaskZeebeRecordProcessor.processUserTaskRecord(batchRequest, userTaskRecord);
    /* then */
    final Map<String, Object> expectedUpdateFields = Map.of("action", "");
    verify(batchRequest).update("user-task-index", "1", expectedUpdateFields);
  }
}
