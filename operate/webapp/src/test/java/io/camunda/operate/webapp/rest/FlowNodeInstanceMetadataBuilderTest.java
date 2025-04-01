/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.rest;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.when;

import io.camunda.operate.util.Tuple;
import io.camunda.operate.webapp.reader.DecisionInstanceReader;
import io.camunda.operate.webapp.reader.EventReader;
import io.camunda.operate.webapp.reader.ListViewReader;
import io.camunda.operate.webapp.reader.UserTaskReader;
import io.camunda.operate.webapp.rest.dto.metadata.BusinessRuleTaskInstanceMetadataDto;
import io.camunda.operate.webapp.rest.dto.metadata.CallActivityInstanceMetadataDto;
import io.camunda.operate.webapp.rest.dto.metadata.FlowNodeInstanceMetadata;
import io.camunda.operate.webapp.rest.dto.metadata.FlowNodeInstanceMetadataDto;
import io.camunda.operate.webapp.rest.dto.metadata.JobFlowNodeInstanceMetadataDto;
import io.camunda.operate.webapp.rest.dto.metadata.ServiceTaskInstanceMetadataDto;
import io.camunda.operate.webapp.rest.dto.metadata.UserTaskInstanceMetadataDto;
import io.camunda.webapps.schema.entities.event.EventEntity;
import io.camunda.webapps.schema.entities.event.EventMetadataEntity;
import io.camunda.webapps.schema.entities.flownode.FlowNodeInstanceEntity;
import io.camunda.webapps.schema.entities.flownode.FlowNodeType;
import io.camunda.webapps.schema.entities.usertask.SnapshotTaskVariableEntity;
import io.camunda.webapps.schema.entities.usertask.TaskEntity;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FlowNodeInstanceMetadataBuilderTest {

  private FlowNodeInstanceMetadataBuilder builder;
  @Mock private ListViewReader listViewReader;
  @Mock private DecisionInstanceReader decisionInstanceReader;
  @Mock private EventReader eventReader;
  @Mock private UserTaskReader userTaskReader;
  private OffsetDateTime startDate;
  private OffsetDateTime endDate;

  @BeforeEach
  void setUp() {
    builder =
        new FlowNodeInstanceMetadataBuilder(
            listViewReader, decisionInstanceReader, eventReader, userTaskReader);
    assertThat(builder).isNotNull();
    startDate = OffsetDateTime.now();
    endDate = startDate.plusHours(5);
  }

  @Test
  void returnMetadataNullForTypeOfNull() {
    final var flowNodeInstance = new FlowNodeInstanceEntity();
    assertThat(builder.buildFrom(flowNodeInstance)).isNull();
  }

  @Test
  void buildDefaultMetadata() {
    final var flowNodeInstance =
        new FlowNodeInstanceEntity().setType(FlowNodeType.INCLUSIVE_GATEWAY);
    fillStandardValues(flowNodeInstance);
    when(eventReader.getEventEntityByFlowNodeInstanceId(flowNodeInstance.getId()))
        .thenReturn(
            new EventEntity()
                .setId("eventId")
                .setMetadata(
                    new EventMetadataEntity()
                        .setMessageName("Last order")
                        .setCorrelationKey("23-05")));
    final var metadata = builder.buildFrom(flowNodeInstance);
    assertThat(metadata).isInstanceOf(FlowNodeInstanceMetadataDto.class);
    assertStandardValues(metadata);
    assertThat(metadata.getEventId()).isEqualTo("eventId");
    assertThat(metadata.getFlowNodeType()).isEqualTo(FlowNodeType.INCLUSIVE_GATEWAY);
  }

  @Test
  void buildUserTaskMetadata() {
    final var flowNodeInstance = new FlowNodeInstanceEntity().setType(FlowNodeType.USER_TASK);
    fillStandardValues(flowNodeInstance);
    final var dueDate = OffsetDateTime.now();
    final var followUpDate = dueDate.plusHours(3);
    final var userTask =
        Optional.of(
            new TaskEntity()
                .setKey(42L)
                .setAction("action")
                .setAssignee("Marge")
                .setCandidateUsers(new String[] {"Lisa", "Bart"})
                .setCandidateGroups(new String[] {"Springfield", "Shelbyville"})
                .setDueDate(dueDate)
                .setFollowUpDate(followUpDate)
                .setFormKey(String.valueOf(5L)));
    when(eventReader.getEventEntityByFlowNodeInstanceId(flowNodeInstance.getId()))
        .thenReturn(
            new EventEntity()
                .setId("eventId")
                .setMetadata(
                    new EventMetadataEntity()
                        .setMessageName("Last order")
                        .setCorrelationKey("23-05")));
    when(userTaskReader.getUserTaskByFlowNodeInstanceKey(flowNodeInstance.getKey()))
        .thenReturn(userTask);
    when(userTaskReader.getUserTaskVariables(userTask.get().getKey()))
        .thenReturn(
            List.of(
                new SnapshotTaskVariableEntity().setName("name").setValue("Homer Simpson"),
                new SnapshotTaskVariableEntity().setName("City").setValue("Springfield")));
    final var metadata = (UserTaskInstanceMetadataDto) builder.buildFrom(flowNodeInstance);
    assertThat(metadata.getFlowNodeType()).isEqualTo(FlowNodeType.USER_TASK);
    assertStandardValues(metadata);
    assertThat(metadata.getVariables())
        .isEqualTo(Map.of("name", "Homer Simpson", "City", "Springfield"));
    assertThat(metadata.getAction()).isEqualTo("action");
    assertThat(metadata.getAssignee()).isEqualTo("Marge");
    assertThat(metadata.getCandidateUsers()).isEqualTo(List.of("Lisa", "Bart"));
    assertThat(metadata.getCandidateGroups()).isEqualTo(List.of("Springfield", "Shelbyville"));
    assertThat(metadata.getDueDate()).isEqualTo(dueDate);
    assertThat(metadata.getFollowUpDate()).isEqualTo(followUpDate);
    assertThat(metadata.getTenantId()).isEqualTo("<default>");
    assertThat(metadata.getFormKey()).isEqualTo(5L);
    assertThat(metadata.getEventId()).isEqualTo("eventId");
    assertThat(metadata.getChangedAttributes()).isNull();
    assertThat(metadata.getExternalReference()).isNull();
    assertThat(metadata.getUserTaskKey()).isEqualTo(42L);
  }

  @Test
  void buildUserTaskMetadataWithInvalidVariables() {
    final var flowNodeInstance = new FlowNodeInstanceEntity().setType(FlowNodeType.USER_TASK);
    fillStandardValues(flowNodeInstance);
    final var userTask = Optional.of(new TaskEntity().setKey(42L));
    when(eventReader.getEventEntityByFlowNodeInstanceId(flowNodeInstance.getId()))
        .thenReturn(
            new EventEntity()
                .setId("eventId")
                .setMetadata(
                    new EventMetadataEntity()
                        .setMessageName("Last order")
                        .setCorrelationKey("23-05")));
    when(userTaskReader.getUserTaskByFlowNodeInstanceKey(flowNodeInstance.getKey()))
        .thenReturn(userTask);
    final var metadata = (UserTaskInstanceMetadataDto) builder.buildFrom(flowNodeInstance);
    assertThat(metadata.getFlowNodeType()).isEqualTo(FlowNodeType.USER_TASK);
    assertStandardValues(metadata);
    assertThat(metadata.getVariables()).isEqualTo(Map.of());
  }

  @Test
  void buildServiceTaskMetadata() {
    final var flowNodeInstance = new FlowNodeInstanceEntity().setType(FlowNodeType.SERVICE_TASK);
    fillStandardValues(flowNodeInstance);

    final var jobDeadline = OffsetDateTime.now();
    setJobValues(jobDeadline, flowNodeInstance);

    final var metadata = (ServiceTaskInstanceMetadataDto) builder.buildFrom(flowNodeInstance);
    assertStandardValues(metadata);
    assertJobMetadata(metadata, jobDeadline);
  }

  private void setJobValues(
      final OffsetDateTime jobDeadline, final FlowNodeInstanceEntity flowNodeInstance) {
    final EventMetadataEntity eventMetadata =
        new EventMetadataEntity()
            .setJobCustomHeaders(Map.of("header", "value"))
            .setJobDeadline(jobDeadline)
            .setJobRetries(5)
            .setJobType("manual")
            .setJobWorker("Moe")
            .setMessageName("Last order")
            .setCorrelationKey("23-05");
    when(eventReader.getEventEntityByFlowNodeInstanceId(flowNodeInstance.getId()))
        .thenReturn(new EventEntity().setId("eventId").setMetadata(eventMetadata));
  }

  private static void assertJobMetadata(
      final JobFlowNodeInstanceMetadataDto metadata, final OffsetDateTime jobDeadline) {
    assertThat(metadata.getJobCustomHeaders()).isEqualTo(Map.of("header", "value"));
    assertThat(metadata.getJobDeadline()).isEqualTo(jobDeadline);
    assertThat(metadata.getJobRetries()).isEqualTo(5);
    assertThat(metadata.getJobType()).isEqualTo("manual");
    assertThat(metadata.getJobWorker()).isEqualTo("Moe");
  }

  @Test
  void buildCallActivityMetadata() {
    final var flowNodeInstance = new FlowNodeInstanceEntity().setType(FlowNodeType.CALL_ACTIVITY);
    fillStandardValues(flowNodeInstance);
    final var jobDeadline = OffsetDateTime.now();
    setJobValues(jobDeadline, flowNodeInstance);
    when(listViewReader.getCalledProcessInstanceIdAndNameByFlowNodeInstanceId(
            flowNodeInstance.getId()))
        .thenReturn(Tuple.of("calledProcessInstanceId", "calledProcessDefinitionName"));

    final var metadata = (CallActivityInstanceMetadataDto) builder.buildFrom(flowNodeInstance);

    assertStandardValues(metadata);
    assertThat(metadata.getCalledProcessInstanceId()).isEqualTo("calledProcessInstanceId");
    assertThat(metadata.getCalledProcessDefinitionName()).isEqualTo("calledProcessDefinitionName");
    assertJobMetadata(metadata, jobDeadline);
    assertThat(metadata.getEventId()).isEqualTo("eventId");
    assertThat(metadata.getFlowNodeType()).isEqualTo(FlowNodeType.CALL_ACTIVITY);
  }

  @Test
  void buildBusinessRuleTaskMetadata() {
    final var flowNodeInstance =
        new FlowNodeInstanceEntity().setType(FlowNodeType.BUSINESS_RULE_TASK);
    fillStandardValues(flowNodeInstance);
    final var jobDeadline = OffsetDateTime.now();
    setJobValues(jobDeadline, flowNodeInstance);

    when(decisionInstanceReader.getCalledDecisionInstanceAndDefinitionByFlowNodeInstanceId(
            flowNodeInstance.getId()))
        .thenReturn(Tuple.of("calledDecisionInstanceId", "calledDecisionDefinitionName"));

    final var metadata = (BusinessRuleTaskInstanceMetadataDto) builder.buildFrom(flowNodeInstance);
    assertStandardValues(metadata);
    assertThat(metadata.getCalledDecisionInstanceId()).isEqualTo("calledDecisionInstanceId");
    assertThat(metadata.getCalledDecisionDefinitionName())
        .isEqualTo("calledDecisionDefinitionName");
    assertThat(metadata.getJobCustomHeaders()).isEqualTo(Map.of("header", "value"));
    assertThat(metadata.getJobDeadline()).isEqualTo(jobDeadline);
    assertThat(metadata.getJobRetries()).isEqualTo(5);
    assertThat(metadata.getJobType()).isEqualTo("manual");
    assertThat(metadata.getJobWorker()).isEqualTo("Moe");
    assertThat(metadata.getEventId()).isEqualTo("eventId");
    assertThat(metadata.getMessageName()).isEqualTo("Last order");
    assertThat(metadata.getCorrelationKey()).isEqualTo("23-05");
    assertThat(metadata.getFlowNodeType()).isEqualTo(FlowNodeType.BUSINESS_RULE_TASK);
  }

  private void fillStandardValues(final FlowNodeInstanceEntity flowNodeInstance) {
    flowNodeInstance
        .setFlowNodeId("flowNodeId")
        .setId("id")
        .setStartDate(startDate)
        .setEndDate(endDate);
  }

  private void assertStandardValues(final FlowNodeInstanceMetadata metadata) {
    assertThat(metadata.getFlowNodeId()).isEqualTo("flowNodeId");
    assertThat(metadata.getFlowNodeInstanceId()).isEqualTo("id");
    assertThat(metadata.getStartDate()).isEqualTo(startDate);
    assertThat(metadata.getEndDate()).isEqualTo(endDate);
    assertThat(metadata.getMessageName()).isEqualTo("Last order");
    assertThat(metadata.getCorrelationKey()).isEqualTo("23-05");
  }
}
