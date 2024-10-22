/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.domain;

import static org.junit.jupiter.api.Assertions.*;

import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

public class UserTaskDbModelTest {

  @Test
  public void testBuilder() {
    OffsetDateTime now = OffsetDateTime.now();
    UserTaskDbModel model =
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
            .serializedCustomHeaders("{\"key\":\"value\"}")
            .priority(10)
            .build();

    assertNotNull(model);
    assertEquals(1L, model.key());
    assertEquals("flowNodeBpmnId", model.flowNodeBpmnId());
    assertEquals("processDefinitionId", model.processDefinitionId());
    assertEquals(now, model.creationTime());
    assertEquals(now, model.completionTime());
    assertEquals("assignee", model.assignee());
    assertEquals(UserTaskDbModel.UserTaskState.CREATED, model.state());
    assertEquals(2L, model.formKey());
    assertEquals(3L, model.processDefinitionKey());
    assertEquals(4L, model.processInstanceKey());
    assertEquals(5L, model.elementInstanceKey());
    assertEquals("tenantId", model.tenantId());
    assertEquals(now, model.dueDate());
    assertEquals(now, model.followUpDate());
    assertEquals(List.of("group1", "group2"), model.candidateGroups());
    assertEquals(List.of("user1", "user2"), model.candidateUsers());
    assertEquals("externalFormReference", model.externalFormReference());
    assertEquals(1, model.processDefinitionVersion());
    assertEquals("{\"key\":\"value\"}", model.serializedCustomHeaders());
    assertEquals(10, model.priority());
  }

  @Test
  public void testToBuilder() {
    OffsetDateTime now = OffsetDateTime.now();
    UserTaskDbModel model =
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
            .serializedCustomHeaders("{\"key\":\"value\"}")
            .priority(10)
            .build();

    UserTaskDbModel.Builder builder = model.toBuilder();
    UserTaskDbModel newModel = builder.priority(20).build();

    assertNotNull(newModel);
    assertEquals(1L, newModel.key());
    assertEquals("flowNodeBpmnId", newModel.flowNodeBpmnId());
    assertEquals("processDefinitionId", newModel.processDefinitionId());
    assertEquals(now, newModel.creationTime());
    assertEquals(now, newModel.completionTime());
    assertEquals("assignee", newModel.assignee());
    assertEquals(UserTaskDbModel.UserTaskState.CREATED, newModel.state());
    assertEquals(2L, newModel.formKey());
    assertEquals(3L, newModel.processDefinitionKey());
    assertEquals(4L, newModel.processInstanceKey());
    assertEquals(5L, newModel.elementInstanceKey());
    assertEquals("tenantId", newModel.tenantId());
    assertEquals(now, newModel.dueDate());
    assertEquals(now, newModel.followUpDate());
    assertEquals(List.of("group1", "group2"), newModel.candidateGroups());
    assertEquals(List.of("user1", "user2"), newModel.candidateUsers());
    assertEquals("externalFormReference", newModel.externalFormReference());
    assertEquals(1, newModel.processDefinitionVersion());
    assertEquals("{\"key\":\"value\"}", newModel.serializedCustomHeaders());
    assertEquals(20, newModel.priority());
  }
}
