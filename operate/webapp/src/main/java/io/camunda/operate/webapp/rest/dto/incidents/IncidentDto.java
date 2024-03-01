/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE, YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
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
package io.camunda.operate.webapp.rest.dto.incidents;

import io.camunda.operate.entities.IncidentEntity;
import io.camunda.operate.entities.OperationEntity;
import io.camunda.operate.entities.OperationState;
import io.camunda.operate.util.ConversionUtils;
import io.camunda.operate.webapp.data.IncidentDataHolder;
import io.camunda.operate.webapp.rest.dto.DtoCreator;
import io.camunda.operate.webapp.rest.dto.OperationDto;
import io.camunda.operate.webapp.rest.dto.ProcessInstanceReferenceDto;
import io.camunda.operate.webapp.rest.dto.metadata.DecisionInstanceReferenceDto;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class IncidentDto {

  public static final Comparator<IncidentDto> INCIDENT_DEFAULT_COMPARATOR =
      (o1, o2) -> {
        if (o1.getErrorType().equals(o2.getErrorType())) {
          return o1.getId().compareTo(o2.getId());
        }
        return o1.getErrorType().compareTo(o2.getErrorType());
      };

  public static final String FALLBACK_PROCESS_DEFINITION_NAME = "Unknown process";

  private String id;

  private ErrorTypeDto errorType;

  private String errorMessage;

  private String flowNodeId;

  private String flowNodeInstanceId;

  private String jobId;

  private OffsetDateTime creationTime;

  private boolean hasActiveOperation = false;

  private OperationDto lastOperation;

  private ProcessInstanceReferenceDto rootCauseInstance;

  private DecisionInstanceReferenceDto rootCauseDecision;

  public static <T> IncidentDto createFrom(
      final IncidentEntity incidentEntity,
      final Map<Long, String> processNames,
      IncidentDataHolder incidentData,
      DecisionInstanceReferenceDto rootCauseDecision) {
    return createFrom(
        incidentEntity, Collections.emptyList(), processNames, incidentData, rootCauseDecision);
  }

  public static IncidentDto createFrom(
      IncidentEntity incidentEntity,
      List<OperationEntity> operations,
      Map<Long, String> processNames,
      IncidentDataHolder incidentData,
      DecisionInstanceReferenceDto rootCauseDecision) {
    if (incidentEntity == null) {
      return null;
    }

    IncidentDto incident =
        new IncidentDto()
            .setId(incidentEntity.getId())
            .setFlowNodeId(incidentEntity.getFlowNodeId())
            .setFlowNodeInstanceId(
                ConversionUtils.toStringOrNull(incidentEntity.getFlowNodeInstanceKey()))
            .setErrorMessage(incidentEntity.getErrorMessage())
            .setErrorType(ErrorTypeDto.createFrom(incidentEntity.getErrorType()))
            .setJobId(ConversionUtils.toStringOrNull(incidentEntity.getJobKey()))
            .setCreationTime(incidentEntity.getCreationTime());

    if (operations != null && operations.size() > 0) {
      OperationEntity lastOperation = operations.get(0); // operations are
      // sorted by start date
      // descendant
      incident
          .setLastOperation(DtoCreator.create(lastOperation, OperationDto.class))
          .setHasActiveOperation(
              operations.stream()
                  .anyMatch(
                      o ->
                          o.getState().equals(OperationState.SCHEDULED)
                              || o.getState().equals(OperationState.LOCKED)
                              || o.getState().equals(OperationState.SENT)));
    }

    // do not return root cause when it's a "local" incident
    if (incidentData != null
        && incident.getFlowNodeInstanceId() != incidentData.getFinalFlowNodeInstanceId()) {
      incident.setFlowNodeId(incidentData.getFinalFlowNodeId());
      incident.setFlowNodeInstanceId(incidentData.getFinalFlowNodeInstanceId());

      final ProcessInstanceReferenceDto rootCauseInstance =
          new ProcessInstanceReferenceDto()
              .setInstanceId(String.valueOf(incidentEntity.getProcessInstanceKey()))
              .setProcessDefinitionId(String.valueOf(incidentEntity.getProcessDefinitionKey()));
      if (processNames != null
          && processNames.get(incidentEntity.getProcessDefinitionKey()) != null) {
        rootCauseInstance.setProcessDefinitionName(
            processNames.get(incidentEntity.getProcessDefinitionKey()));
      } else {
        rootCauseInstance.setProcessDefinitionName(FALLBACK_PROCESS_DEFINITION_NAME);
      }
      incident.setRootCauseInstance(rootCauseInstance);
    }

    if (rootCauseDecision != null) {
      incident.setRootCauseDecision(rootCauseDecision);
    }

    return incident;
  }

  public static List<IncidentDto> createFrom(
      List<IncidentEntity> incidentEntities,
      Map<Long, List<OperationEntity>> operations,
      Map<Long, String> processNames,
      Map<String, IncidentDataHolder> incidentData) {
    if (incidentEntities != null) {
      return incidentEntities.stream()
          .filter(inc -> inc != null)
          .map(
              inc ->
                  createFrom(
                      inc,
                      operations.get(inc.getKey()),
                      processNames,
                      incidentData.get(inc.getId()),
                      null))
          .collect(Collectors.toList());
    }
    return new ArrayList<>();
  }

  public static List<IncidentDto> sortDefault(List<IncidentDto> incidents) {
    Collections.sort(incidents, INCIDENT_DEFAULT_COMPARATOR);
    return incidents;
  }

  public String getId() {
    return id;
  }

  public IncidentDto setId(final String id) {
    this.id = id;
    return this;
  }

  public ErrorTypeDto getErrorType() {
    return errorType;
  }

  public IncidentDto setErrorType(final ErrorTypeDto errorType) {
    this.errorType = errorType;
    return this;
  }

  public String getErrorMessage() {
    return errorMessage;
  }

  public IncidentDto setErrorMessage(final String errorMessage) {
    this.errorMessage = errorMessage;
    return this;
  }

  public String getFlowNodeId() {
    return flowNodeId;
  }

  public IncidentDto setFlowNodeId(final String flowNodeId) {
    this.flowNodeId = flowNodeId;
    return this;
  }

  public String getFlowNodeInstanceId() {
    return flowNodeInstanceId;
  }

  public IncidentDto setFlowNodeInstanceId(final String flowNodeInstanceId) {
    this.flowNodeInstanceId = flowNodeInstanceId;
    return this;
  }

  public String getJobId() {
    return jobId;
  }

  public IncidentDto setJobId(final String jobId) {
    this.jobId = jobId;
    return this;
  }

  public OffsetDateTime getCreationTime() {
    return creationTime;
  }

  public IncidentDto setCreationTime(final OffsetDateTime creationTime) {
    this.creationTime = creationTime;
    return this;
  }

  public boolean isHasActiveOperation() {
    return hasActiveOperation;
  }

  public IncidentDto setHasActiveOperation(final boolean hasActiveOperation) {
    this.hasActiveOperation = hasActiveOperation;
    return this;
  }

  public OperationDto getLastOperation() {
    return lastOperation;
  }

  public IncidentDto setLastOperation(final OperationDto lastOperation) {
    this.lastOperation = lastOperation;
    return this;
  }

  public ProcessInstanceReferenceDto getRootCauseInstance() {
    return rootCauseInstance;
  }

  public IncidentDto setRootCauseInstance(final ProcessInstanceReferenceDto rootCauseInstance) {
    this.rootCauseInstance = rootCauseInstance;
    return this;
  }

  public DecisionInstanceReferenceDto getRootCauseDecision() {
    return rootCauseDecision;
  }

  public IncidentDto setRootCauseDecision(final DecisionInstanceReferenceDto rootCauseDecision) {
    this.rootCauseDecision = rootCauseDecision;
    return this;
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        id,
        errorType,
        errorMessage,
        flowNodeId,
        flowNodeInstanceId,
        jobId,
        creationTime,
        hasActiveOperation,
        lastOperation,
        rootCauseInstance,
        rootCauseDecision);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final IncidentDto that = (IncidentDto) o;
    return hasActiveOperation == that.hasActiveOperation
        && Objects.equals(id, that.id)
        && Objects.equals(errorType, that.errorType)
        && Objects.equals(errorMessage, that.errorMessage)
        && Objects.equals(flowNodeId, that.flowNodeId)
        && Objects.equals(flowNodeInstanceId, that.flowNodeInstanceId)
        && Objects.equals(jobId, that.jobId)
        && Objects.equals(creationTime, that.creationTime)
        && Objects.equals(lastOperation, that.lastOperation)
        && Objects.equals(rootCauseInstance, that.rootCauseInstance)
        && Objects.equals(rootCauseDecision, that.rootCauseDecision);
  }

  @Override
  public String toString() {
    return "IncidentDto{"
        + "id='"
        + id
        + '\''
        + ", errorType="
        + errorType
        + ", errorMessage='"
        + errorMessage
        + '\''
        + ", flowNodeId='"
        + flowNodeId
        + '\''
        + ", flowNodeInstanceId='"
        + flowNodeInstanceId
        + '\''
        + ", jobId='"
        + jobId
        + '\''
        + ", creationTime="
        + creationTime
        + ", hasActiveOperation="
        + hasActiveOperation
        + ", lastOperation="
        + lastOperation
        + ", rootCauseInstance="
        + rootCauseInstance
        + ", rootCauseDecision="
        + rootCauseDecision
        + '}';
  }
}
