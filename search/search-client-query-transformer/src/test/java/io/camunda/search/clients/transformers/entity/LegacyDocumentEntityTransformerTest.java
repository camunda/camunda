/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.entity;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.webapps.schema.entities.CorrelatedMessageSubscriptionEntity;
import io.camunda.webapps.schema.entities.JobEntity;
import io.camunda.webapps.schema.entities.SequenceFlowEntity;
import io.camunda.webapps.schema.entities.VariableEntity;
import io.camunda.webapps.schema.entities.flownode.FlowNodeInstanceEntity;
import io.camunda.webapps.schema.entities.flownode.FlowNodeState;
import io.camunda.webapps.schema.entities.flownode.FlowNodeType;
import io.camunda.webapps.schema.entities.usertask.TaskEntity;
import io.camunda.webapps.schema.entities.usertask.TaskState;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

/**
 * Documents written before 8.8 (or before 8.9 for jobs) are still read in place, and they lack
 * fields that the 8.10 exporter always writes. Each test builds a document with only the fields the
 * legacy writer set and checks that it is transformed with the missing fields left null, instead of
 * failing the whole search page. See camunda/camunda#66159.
 */
class LegacyDocumentEntityTransformerTest {

  private static final OffsetDateTime NOW = OffsetDateTime.now();

  @Test
  void shouldTransformVariableWithoutProcessDefinitionId() {
    // given - Operate 8.1 and earlier didn't write bpmnProcessId on variables
    final var document =
        new VariableEntity()
            .setKey(1L)
            .setName("var")
            .setValue("\"value\"")
            .setIsPreview(false)
            .setScopeKey(2L)
            .setProcessInstanceKey(3L)
            .setTenantId("<default>");

    // when
    final var entity = new VariableEntityTransformer().apply(document);

    // then
    assertThat(entity.variableKey()).isEqualTo(1L);
    assertThat(entity.processDefinitionId()).isNull();
  }

  @Test
  void shouldTransformFlowNodeInstanceWithoutProcessDefinition() {
    // given - Operate 8.1 and earlier didn't write the process definition fields
    final var document =
        new FlowNodeInstanceEntity()
            .setKey(1L)
            .setProcessInstanceKey(2L)
            .setFlowNodeId("task")
            .setType(FlowNodeType.SERVICE_TASK)
            .setState(FlowNodeState.ACTIVE)
            .setTenantId("<default>");

    // when
    final var entity = new FlowNodeInstanceEntityTransformer().apply(document);

    // then
    assertThat(entity.flowNodeInstanceKey()).isEqualTo(1L);
    assertThat(entity.processDefinitionKey()).isNull();
    assertThat(entity.processDefinitionId()).isNull();
  }

  @Test
  void shouldTransformSequenceFlowWithoutProcessDefinition() {
    // given - Operate 8.1 and earlier didn't write the process definition fields
    final var document =
        new SequenceFlowEntity()
            .setId("2_flow")
            .setActivityId("flow")
            .setProcessInstanceKey(2L)
            .setTenantId("<default>");

    // when
    final var entity = new SequenceFlowEntityTransformer().apply(document);

    // then
    assertThat(entity.sequenceFlowId()).isEqualTo("2_flow");
    assertThat(entity.processDefinitionKey()).isNull();
    assertThat(entity.processDefinitionId()).isNull();
  }

  @Test
  void shouldTransformJobWithoutProcessDefinitionOrTimestampsOrElementId() {
    // given - Operate 8.7 and earlier didn't write the process definition fields, jobs written
    // before 8.9
    // have no creation and last update time, and failed jobs may have no element id
    final var document =
        new JobEntity()
            .setKey(1L)
            .setType("type")
            .setWorker("worker")
            .setState("FAILED")
            .setJobKind("BPMN_ELEMENT")
            .setListenerEventType("UNSPECIFIED")
            .setRetries(0)
            .setProcessInstanceKey(2L)
            .setFlowNodeInstanceId(3L)
            .setTenantId("<default>");

    // when
    final var entity = new JobEntityTransformer().apply(document);

    // then
    assertThat(entity.jobKey()).isEqualTo(1L);
    assertThat(entity.processDefinitionKey()).isNull();
    assertThat(entity.processDefinitionId()).isNull();
    assertThat(entity.elementId()).isNull();
    assertThat(entity.creationTime()).isNull();
    assertThat(entity.lastUpdateTime()).isNull();
  }

  @Test
  void shouldTransformCorrelatedMessageSubscriptionWithoutProcessDefinitionKey() {
    // given - the 8.8 exporter didn't write the process definition key for process message
    // subscriptions
    final var document =
        new CorrelatedMessageSubscriptionEntity()
            .setCorrelationTime(NOW)
            .setFlowNodeId("catch")
            .setMessageKey(1L)
            .setMessageName("message")
            .setPartitionId(1)
            .setBpmnProcessId("process")
            .setProcessInstanceKey(2L)
            .setSubscriptionKey(3L)
            .setTenantId("<default>");

    // when
    final var entity = new CorrelatedMessageSubscriptionEntityTransformer().apply(document);

    // then
    assertThat(entity.subscriptionKey()).isEqualTo(3L);
    assertThat(entity.processDefinitionKey()).isNull();
  }

  @Test
  void shouldTransformUserTaskWithoutProcessDefinitionVersion() {
    // given - the Tasklist 8.5/8.6 importer-850 didn't write the process definition version
    final var document =
        new TaskEntity()
            .setKey(1L)
            .setFlowNodeBpmnId("task")
            .setBpmnProcessId("process")
            .setProcessDefinitionId("2")
            .setProcessInstanceId("3")
            .setFlowNodeInstanceId("4")
            .setState(TaskState.CREATED)
            .setCreationTime(NOW)
            .setTenantId("<default>");

    // when
    final var entity = new UserTaskEntityTransformer().apply(document);

    // then
    assertThat(entity.userTaskKey()).isEqualTo(1L);
    assertThat(entity.processDefinitionVersion()).isNull();
  }
}
