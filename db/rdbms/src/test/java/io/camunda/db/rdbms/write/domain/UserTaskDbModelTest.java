/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class UserTaskDbModelTest {

  @Test
  public void testBuilder() {
    final OffsetDateTime now = OffsetDateTime.now();
    final UserTaskDbModel model =
        new UserTaskDbModel.Builder()
            .key(1L)
            .flowNodeBpmnId("flowNodeBpmnId")
            .processDefinitionId("processDefinitionId")
            .creationTime(now)
            .completionTime(now)
            .assignee("assignee")
            .state(UserTaskDbModel.UserTaskState.CREATED)
            .formKey(2L)
            .processDefinitionKey(3L)
            .processInstanceKey(4L)
            .elementInstanceKey(5L)
            .tenantId("tenantId")
            .dueDate(now)
            .followUpDate(now)
            .candidateGroups(List.of("group1", "group2"))
            .candidateUsers(List.of("user1", "user2"))
            .externalFormReference("externalFormReference")
            .processDefinitionVersion(1)
            .customHeaders(Map.of("key", "value"))
            .priority(10)
            .build();

    assertThat(model).isNotNull();
    assertThat(model.key()).isEqualTo(1L);
    assertThat(model.flowNodeBpmnId()).isEqualTo("flowNodeBpmnId");
    assertThat(model.processDefinitionId()).isEqualTo("processDefinitionId");
    assertThat(model.creationTime()).isEqualTo(now);
    assertThat(model.completionTime()).isEqualTo(now);
    assertThat(model.assignee()).isEqualTo("assignee");
    assertThat(model.state()).isEqualTo(UserTaskDbModel.UserTaskState.CREATED);
    assertThat(model.formKey()).isEqualTo(2L);
    assertThat(model.processDefinitionKey()).isEqualTo(3L);
    assertThat(model.processInstanceKey()).isEqualTo(4L);
    assertThat(model.elementInstanceKey()).isEqualTo(5L);
    assertThat(model.tenantId()).isEqualTo("tenantId");
    assertThat(model.dueDate()).isEqualTo(now);
    assertThat(model.followUpDate()).isEqualTo(now);
    assertThat(model.candidateGroups()).containsExactly("group1", "group2");
    assertThat(model.candidateUsers()).containsExactly("user1", "user2");
    assertThat(model.externalFormReference()).isEqualTo("externalFormReference");
    assertThat(model.processDefinitionVersion()).isEqualTo(1);
    assertThat(model.serializedCustomHeaders()).isEqualTo("{\"key\":\"value\"}");
    assertThat(model.customHeaders()).containsEntry("key", "value");
    assertThat(model.priority()).isEqualTo(10);
  }

  @Test
  public void testToBuilder() {
    final OffsetDateTime now = OffsetDateTime.now();
    final UserTaskDbModel model =
        new UserTaskDbModel.Builder()
            .key(1L)
            .flowNodeBpmnId("flowNodeBpmnId")
            .processDefinitionId("processDefinitionId")
            .creationTime(now)
            .completionTime(now)
            .assignee("assignee")
            .state(UserTaskDbModel.UserTaskState.CREATED)
            .formKey(2L)
            .processDefinitionKey(3L)
            .processInstanceKey(4L)
            .elementInstanceKey(5L)
            .tenantId("tenantId")
            .dueDate(now)
            .followUpDate(now)
            .candidateGroups(List.of("group1", "group2"))
            .candidateUsers(List.of("user1", "user2"))
            .externalFormReference("externalFormReference")
            .processDefinitionVersion(1)
            .customHeaders(Map.of("key", "value"))
            .priority(10)
            .build();

    final UserTaskDbModel.Builder builder = model.toBuilder();
    final UserTaskDbModel newModel = builder.priority(20).build();

    assertThat(newModel).isNotNull();
    assertThat(newModel.key()).isEqualTo(1L);
    assertThat(newModel.flowNodeBpmnId()).isEqualTo("flowNodeBpmnId");
    assertThat(newModel.processDefinitionId()).isEqualTo("processDefinitionId");
    assertThat(newModel.creationTime()).isEqualTo(now);
    assertThat(newModel.completionTime()).isEqualTo(now);
    assertThat(newModel.assignee()).isEqualTo("assignee");
    assertThat(newModel.state()).isEqualTo(UserTaskDbModel.UserTaskState.CREATED);
    assertThat(newModel.formKey()).isEqualTo(2L);
    assertThat(newModel.processDefinitionKey()).isEqualTo(3L);
    assertThat(newModel.processInstanceKey()).isEqualTo(4L);
    assertThat(newModel.elementInstanceKey()).isEqualTo(5L);
    assertThat(newModel.tenantId()).isEqualTo("tenantId");
    assertThat(newModel.dueDate()).isEqualTo(now);
    assertThat(newModel.followUpDate()).isEqualTo(now);
    assertThat(newModel.candidateGroups()).containsExactly("group1", "group2");
    assertThat(newModel.candidateUsers()).containsExactly("user1", "user2");
    assertThat(newModel.externalFormReference()).isEqualTo("externalFormReference");
    assertThat(newModel.processDefinitionVersion()).isEqualTo(1);
    assertThat(newModel.serializedCustomHeaders()).isEqualTo("{\"key\":\"value\"}");
    assertThat(newModel.customHeaders()).containsEntry("key", "value");
    assertThat(newModel.priority()).isEqualTo(20);
  }
}
