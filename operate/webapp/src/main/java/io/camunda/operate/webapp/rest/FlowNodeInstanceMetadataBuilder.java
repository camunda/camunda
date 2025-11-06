/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.rest;

import io.camunda.operate.webapp.reader.DecisionInstanceReader;
import io.camunda.operate.webapp.reader.JobReader;
import io.camunda.operate.webapp.reader.ListViewReader;
import io.camunda.operate.webapp.reader.MessageSubscriptionReader;
import io.camunda.operate.webapp.reader.UserTaskReader;
import io.camunda.operate.webapp.rest.dto.metadata.BusinessRuleTaskInstanceMetadataDto;
import io.camunda.operate.webapp.rest.dto.metadata.CallActivityInstanceMetadataDto;
import io.camunda.operate.webapp.rest.dto.metadata.FlowNodeInstanceMetadata;
import io.camunda.operate.webapp.rest.dto.metadata.FlowNodeInstanceMetadataDto;
import io.camunda.operate.webapp.rest.dto.metadata.ServiceTaskInstanceMetadataDto;
import io.camunda.operate.webapp.rest.dto.metadata.UserTaskInstanceMetadataDto;
import io.camunda.spring.utils.ConditionalOnRdbmsDisabled;
import io.camunda.webapps.schema.entities.JobEntity;
import io.camunda.webapps.schema.entities.flownode.FlowNodeInstanceEntity;
import io.camunda.webapps.schema.entities.flownode.FlowNodeType;
import io.camunda.webapps.schema.entities.messagesubscription.MessageSubscriptionEntity;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnRdbmsDisabled
public class FlowNodeInstanceMetadataBuilder {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(FlowNodeInstanceMetadataBuilder.class);

  private static final String ID_PATTERN = "%s_%s";
  private final DecisionInstanceReader decisionInstanceReader;

  private final ListViewReader listViewReader;

  private final MessageSubscriptionReader messageSubscriptionReader;

  private final UserTaskReader userTaskReader;

  private final JobReader jobReader;

  private final Map<FlowNodeType, Function<FlowNodeInstanceEntity, FlowNodeInstanceMetadata>>
      flowNodeTypeToFlowNodeInstanceMetadata =
          Map.of(
              FlowNodeType.USER_TASK,
              this::getUserTaskInstanceMetadataDto,
              FlowNodeType.CALL_ACTIVITY,
              this::getCallActivityInstanceMetadataDto,
              FlowNodeType.BUSINESS_RULE_TASK,
              this::getBusinessRuleTaskInstanceMetadataDto,
              FlowNodeType.SERVICE_TASK,
              this::getServiceTaskInstanceMetadataDto);

  public FlowNodeInstanceMetadataBuilder(
      final ListViewReader listViewReader,
      final DecisionInstanceReader decisionInstanceReader,
      final MessageSubscriptionReader messageSubscriptionReader,
      final UserTaskReader userTaskReader,
      final JobReader jobReader) {
    this.listViewReader = listViewReader;
    this.decisionInstanceReader = decisionInstanceReader;
    this.messageSubscriptionReader = messageSubscriptionReader;
    this.userTaskReader = userTaskReader;
    this.jobReader = jobReader;
  }

  public FlowNodeInstanceMetadata buildFrom(final FlowNodeInstanceEntity flowNodeInstance) {
    final FlowNodeType type = flowNodeInstance.getType();
    if (type == null) {
      LOGGER.error(
          "FlowNodeType for FlowNodeInstance with id {} is null", flowNodeInstance.getId());
      return null;
    }
    final var flowNodeInstanceMetadataProvider =
        flowNodeTypeToFlowNodeInstanceMetadata.getOrDefault(
            type, this::getDefaultFlowNodeInstanceMetadataDto);
    return flowNodeInstanceMetadataProvider.apply(flowNodeInstance);
  }

  private FlowNodeInstanceMetadataDto getDefaultFlowNodeInstanceMetadataDto(
      final FlowNodeInstanceEntity flowNodeInstanceEntity) {
    final MessageSubscriptionEntity messageSubscription =
        messageSubscriptionReader
            .getMessageSubscriptionEntityByFlowNodeInstanceId(flowNodeInstanceEntity.getId())
            .orElse(null);
    return new FlowNodeInstanceMetadataDto(
        flowNodeInstanceEntity.getFlowNodeId(),
        flowNodeInstanceEntity.getId(),
        flowNodeInstanceEntity.getType(),
        flowNodeInstanceEntity.getStartDate(),
        flowNodeInstanceEntity.getEndDate(),
        generateEventId(flowNodeInstanceEntity),
        messageSubscription);
  }

  private String generateEventId(final FlowNodeInstanceEntity flowNodeInstanceEntity) {
    return String.format(
        ID_PATTERN,
        flowNodeInstanceEntity.getProcessInstanceKey(),
        flowNodeInstanceEntity.getKey());
  }

  private BusinessRuleTaskInstanceMetadataDto getBusinessRuleTaskInstanceMetadataDto(
      final FlowNodeInstanceEntity flowNodeInstance) {
    final var instanceIdAndDefinitionName =
        decisionInstanceReader.getCalledDecisionInstanceAndDefinitionByFlowNodeInstanceId(
            flowNodeInstance.getId());
    final MessageSubscriptionEntity messageSubscription =
        messageSubscriptionReader
            .getMessageSubscriptionEntityByFlowNodeInstanceId(flowNodeInstance.getId())
            .orElse(null);
    final JobEntity job =
        jobReader.getJobByFlowNodeInstanceId(flowNodeInstance.getId()).orElse(null);
    final var calledDecisionInstanceId = instanceIdAndDefinitionName.getLeft();
    final var calledDecisionDefinitionName = instanceIdAndDefinitionName.getRight();
    return new BusinessRuleTaskInstanceMetadataDto(
        flowNodeInstance.getFlowNodeId(),
        flowNodeInstance.getId(),
        flowNodeInstance.getType(),
        flowNodeInstance.getStartDate(),
        flowNodeInstance.getEndDate(),
        messageSubscription,
        job,
        generateEventId(flowNodeInstance),
        calledDecisionInstanceId,
        calledDecisionDefinitionName);
  }

  private UserTaskInstanceMetadataDto getUserTaskInstanceMetadataDto(
      final FlowNodeInstanceEntity flowNodeInstance) {
    final var userTask = userTaskReader.getUserTaskByFlowNodeInstanceKey(flowNodeInstance.getKey());
    final var event =
        messageSubscriptionReader
            .getMessageSubscriptionEntityByFlowNodeInstanceId(flowNodeInstance.getId())
            .orElse(null);
    final var job = jobReader.getJobByFlowNodeInstanceId(flowNodeInstance.getId()).orElse(null);
    final var result =
        new UserTaskInstanceMetadataDto(
            flowNodeInstance.getFlowNodeId(),
            flowNodeInstance.getId(),
            flowNodeInstance.getType(),
            flowNodeInstance.getStartDate(),
            flowNodeInstance.getEndDate(),
            generateEventId(flowNodeInstance),
            event,
            job);
    if (userTask.isPresent()) {
      final var variables = userTaskReader.getUserTaskVariables(userTask.get().getKey());
      final Map<String, Object> variablesMap = new HashMap<>();
      for (final var v : variables) {
        variablesMap.put(v.getName(), v.getValue());
      }

      final var task = userTask.get();
      result
          .setUserTaskKey(task.getKey())
          .setAssignee(task.getAssignee())
          .setCandidateUsers(
              task.getCandidateUsers() != null
                  ? Arrays.stream(task.getCandidateUsers()).toList()
                  : null)
          .setCandidateGroups(
              task.getCandidateGroups() != null
                  ? Arrays.stream(task.getCandidateGroups()).toList()
                  : null)
          .setAction(task.getAction())
          .setDueDate(task.getDueDate())
          .setFollowUpDate(task.getFollowUpDate())
          .setChangedAttributes(task.getChangedAttributes())
          .setTenantId(task.getTenantId())
          .setFormKey(task.getFormKey() != null ? Long.parseLong(task.getFormKey()) : null)
          .setExternalReference(task.getExternalFormReference())
          .setVariables(variablesMap);
    }
    return result;
  }

  private CallActivityInstanceMetadataDto getCallActivityInstanceMetadataDto(
      final FlowNodeInstanceEntity flowNodeInstance) {
    final var processInstanceIdAndName =
        listViewReader.getCalledProcessInstanceIdAndNameByFlowNodeInstanceId(
            flowNodeInstance.getId());
    final MessageSubscriptionEntity messageSubscription =
        messageSubscriptionReader
            .getMessageSubscriptionEntityByFlowNodeInstanceId(flowNodeInstance.getId())
            .orElse(null);
    final JobEntity job =
        jobReader.getJobByFlowNodeInstanceId(flowNodeInstance.getId()).orElse(null);
    final var calledProcessInstanceId = processInstanceIdAndName.getLeft();
    final var calledProcessDefinitionName = processInstanceIdAndName.getRight();

    return new CallActivityInstanceMetadataDto(
        flowNodeInstance.getFlowNodeId(),
        flowNodeInstance.getId(),
        flowNodeInstance.getType(),
        flowNodeInstance.getStartDate(),
        flowNodeInstance.getEndDate(),
        messageSubscription,
        job,
        generateEventId(flowNodeInstance),
        calledProcessInstanceId,
        calledProcessDefinitionName);
  }

  private ServiceTaskInstanceMetadataDto getServiceTaskInstanceMetadataDto(
      final FlowNodeInstanceEntity flowNodeInstance) {
    final MessageSubscriptionEntity messageSubscription =
        messageSubscriptionReader
            .getMessageSubscriptionEntityByFlowNodeInstanceId(flowNodeInstance.getId())
            .orElse(null);
    final JobEntity job =
        jobReader.getJobByFlowNodeInstanceId(flowNodeInstance.getId()).orElse(null);

    return new ServiceTaskInstanceMetadataDto(
        flowNodeInstance.getFlowNodeId(),
        flowNodeInstance.getId(),
        flowNodeInstance.getType(),
        flowNodeInstance.getStartDate(),
        flowNodeInstance.getEndDate(),
        generateEventId(flowNodeInstance),
        messageSubscription,
        job);
  }
}
