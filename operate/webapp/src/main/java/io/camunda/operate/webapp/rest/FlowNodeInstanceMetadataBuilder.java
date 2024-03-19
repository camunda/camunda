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
package io.camunda.operate.webapp.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.entities.EventEntity;
import io.camunda.operate.entities.FlowNodeInstanceEntity;
import io.camunda.operate.entities.FlowNodeType;
import io.camunda.operate.webapp.reader.DecisionInstanceReader;
import io.camunda.operate.webapp.reader.EventReader;
import io.camunda.operate.webapp.reader.ListViewReader;
import io.camunda.operate.webapp.reader.UserTaskReader;
import io.camunda.operate.webapp.rest.dto.metadata.BusinessRuleTaskInstanceMetadataDto;
import io.camunda.operate.webapp.rest.dto.metadata.CallActivityInstanceMetadataDto;
import io.camunda.operate.webapp.rest.dto.metadata.FlowNodeInstanceMetadata;
import io.camunda.operate.webapp.rest.dto.metadata.FlowNodeInstanceMetadataDto;
import io.camunda.operate.webapp.rest.dto.metadata.ServiceTaskInstanceMetadataDto;
import io.camunda.operate.webapp.rest.dto.metadata.UserTaskInstanceMetadataDto;
import java.io.IOException;
import java.io.StringReader;
import java.util.Map;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class FlowNodeInstanceMetadataBuilder {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(FlowNodeInstanceMetadataBuilder.class);
  private final DecisionInstanceReader decisionInstanceReader;

  private final ListViewReader listViewReader;

  private final EventReader eventReader;

  private final UserTaskReader userTaskReader;

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
      final EventReader eventReader,
      final UserTaskReader userTaskReader) {
    this.listViewReader = listViewReader;
    this.decisionInstanceReader = decisionInstanceReader;
    this.eventReader = eventReader;
    this.userTaskReader = userTaskReader;
  }

  public FlowNodeInstanceMetadata buildFrom(final FlowNodeInstanceEntity flowNodeInstance) {
    final FlowNodeType type = flowNodeInstance.getType();
    if (type == null) {
      LOGGER.error(
          String.format(
              "FlowNodeType for FlowNodeInstance with id %s is null", flowNodeInstance.getId()));
      return null;
    }
    final var flowNodeInstanceMetadataProvider =
        flowNodeTypeToFlowNodeInstanceMetadata.getOrDefault(
            type, this::getDefaultFlowNodeInstanceMetadataDto);
    return flowNodeInstanceMetadataProvider.apply(flowNodeInstance);
  }

  private FlowNodeInstanceMetadataDto getDefaultFlowNodeInstanceMetadataDto(
      final FlowNodeInstanceEntity flowNodeInstanceEntity) {
    final var event =
        eventReader.getEventEntityByFlowNodeInstanceId(flowNodeInstanceEntity.getId());
    return new FlowNodeInstanceMetadataDto(
        flowNodeInstanceEntity.getFlowNodeId(),
        flowNodeInstanceEntity.getId(),
        flowNodeInstanceEntity.getType(),
        flowNodeInstanceEntity.getStartDate(),
        flowNodeInstanceEntity.getEndDate(),
        event);
  }

  private BusinessRuleTaskInstanceMetadataDto getBusinessRuleTaskInstanceMetadataDto(
      final FlowNodeInstanceEntity flowNodeInstance) {
    final var instanceIdAndDefinitionName =
        decisionInstanceReader.getCalledDecisionInstanceAndDefinitionByFlowNodeInstanceId(
            flowNodeInstance.getId());
    final EventEntity event =
        eventReader.getEventEntityByFlowNodeInstanceId(flowNodeInstance.getId());
    final var calledDecisionInstanceId = instanceIdAndDefinitionName.getLeft();
    final var calledDecisionDefinitionName = instanceIdAndDefinitionName.getRight();
    return new BusinessRuleTaskInstanceMetadataDto(
        flowNodeInstance.getFlowNodeId(),
        flowNodeInstance.getId(),
        flowNodeInstance.getType(),
        flowNodeInstance.getStartDate(),
        flowNodeInstance.getEndDate(),
        event,
        calledDecisionInstanceId,
        calledDecisionDefinitionName);
  }

  private UserTaskInstanceMetadataDto getUserTaskInstanceMetadataDto(
      final FlowNodeInstanceEntity flowNodeInstance) {
    final var userTask = userTaskReader.getUserTaskByFlowNodeInstanceKey(flowNodeInstance.getKey());
    final var event = eventReader.getEventEntityByFlowNodeInstanceId(flowNodeInstance.getId());
    final var result =
        new UserTaskInstanceMetadataDto(
            flowNodeInstance.getFlowNodeId(),
            flowNodeInstance.getId(),
            flowNodeInstance.getType(),
            flowNodeInstance.getStartDate(),
            flowNodeInstance.getEndDate(),
            event);
    if (userTask.isPresent()) {
      final var task = userTask.get();
      result
          .setUserTaskKey(task.getUserTaskKey())
          .setAssignee(task.getAssignee())
          .setCandidateUsers(task.getCandidateUsers())
          .setCandidateGroups(task.getCandidateGroups())
          .setAction(task.getAction())
          .setDueDate(task.getDueDate())
          .setFollowUpDate(task.getFollowUpDate())
          .setChangedAttributes(task.getChangedAttributes())
          .setTenantId(task.getTenantId())
          .setFormKey(task.getFormKey())
          .setExternalReference(task.getExternalReference());
      try {
        result.setVariables(
            new ObjectMapper().readValue(new StringReader(task.getVariables()), Map.class));
      } catch (final IOException e) {
        result.setVariables(Map.of());
      }
    }
    return result;
  }

  private CallActivityInstanceMetadataDto getCallActivityInstanceMetadataDto(
      final FlowNodeInstanceEntity flowNodeInstance) {
    final var processInstanceIdAndName =
        listViewReader.getCalledProcessInstanceIdAndNameByFlowNodeInstanceId(
            flowNodeInstance.getId());
    final EventEntity event =
        eventReader.getEventEntityByFlowNodeInstanceId(flowNodeInstance.getId());
    final var calledProcessInstanceId = processInstanceIdAndName.getLeft();
    final var calledProcessDefinitionName = processInstanceIdAndName.getRight();
    return new CallActivityInstanceMetadataDto(
        flowNodeInstance.getFlowNodeId(),
        flowNodeInstance.getId(),
        flowNodeInstance.getType(),
        flowNodeInstance.getStartDate(),
        flowNodeInstance.getEndDate(),
        event,
        calledProcessInstanceId,
        calledProcessDefinitionName);
  }

  private ServiceTaskInstanceMetadataDto getServiceTaskInstanceMetadataDto(
      final FlowNodeInstanceEntity flowNodeInstance) {
    final var event = eventReader.getEventEntityByFlowNodeInstanceId(flowNodeInstance.getId());
    return new ServiceTaskInstanceMetadataDto(
        flowNodeInstance.getFlowNodeId(),
        flowNodeInstance.getId(),
        flowNodeInstance.getType(),
        flowNodeInstance.getStartDate(),
        flowNodeInstance.getEndDate(),
        event);
  }
}
