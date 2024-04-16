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
package io.camunda.tasklist.webapp.mapper;

import static org.apache.commons.lang3.ObjectUtils.defaultIfNull;

import io.camunda.tasklist.webapp.api.rest.v1.entities.TaskResponse;
import io.camunda.tasklist.webapp.api.rest.v1.entities.TaskSearchRequest;
import io.camunda.tasklist.webapp.api.rest.v1.entities.TaskSearchResponse;
import io.camunda.tasklist.webapp.api.rest.v1.entities.VariableSearchResponse;
import io.camunda.tasklist.webapp.es.cache.ProcessCache;
import io.camunda.tasklist.webapp.graphql.entity.TaskDTO;
import io.camunda.tasklist.webapp.graphql.entity.TaskQueryDTO;
import io.camunda.tasklist.webapp.graphql.entity.VariableDTO;
import java.util.Arrays;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class TaskMapper {

  public static final String TASK_DESCRIPTION = "taskContextDisplayName";

  @Autowired private ProcessCache processCache;

  public TaskSearchResponse toTaskSearchResponse(final TaskDTO taskDTO) {
    final TaskSearchResponse response =
        new TaskSearchResponse()
            .setId(taskDTO.getId())
            .setName(getName(taskDTO))
            .setTaskDefinitionId(taskDTO.getFlowNodeBpmnId())
            .setProcessName(getProcessName(taskDTO))
            .setCreationDate(taskDTO.getCreationTime())
            .setCompletionDate(taskDTO.getCompletionTime())
            .setAssignee(taskDTO.getAssignee())
            .setTaskState(taskDTO.getTaskState())
            .setSortValues(taskDTO.getSortValues())
            .setIsFirst(taskDTO.getIsFirst())
            .setFormKey(taskDTO.getFormKey())
            .setFormId(taskDTO.getFormId())
            .setFormVersion(taskDTO.getFormVersion())
            .setIsFormEmbedded(taskDTO.getIsFormEmbedded())
            .setProcessDefinitionKey(taskDTO.getProcessDefinitionId())
            .setProcessInstanceKey(taskDTO.getProcessInstanceId())
            .setTenantId(taskDTO.getTenantId())
            .setDueDate(taskDTO.getDueDate())
            .setFollowUpDate(taskDTO.getFollowUpDate())
            .setCandidateGroups(taskDTO.getCandidateGroups())
            .setCandidateUsers(taskDTO.getCandidateUsers())
            .setImplementation(taskDTO.getImplementation());

    if (taskDTO.getVariables() != null) {
      final VariableSearchResponse[] allVariables =
          Stream.of(taskDTO.getVariables())
              .map(this::toVariableSearchResponse)
              .toArray(VariableSearchResponse[]::new);

      final VariableSearchResponse[] filteredVariables =
          Arrays.stream(allVariables)
              .filter(variable -> !variable.getName().equals(TASK_DESCRIPTION))
              .toArray(VariableSearchResponse[]::new);

      String taskDescriptions =
          Arrays.stream(allVariables)
              .filter(variable -> variable.getName().equals(TASK_DESCRIPTION))
              .map(VariableSearchResponse::getValue)
              .map(value -> value.replaceAll("\"", "")) // Remove quotes for FE
              .collect(Collectors.joining());

      if (taskDescriptions.isEmpty() || "null".equals(taskDescriptions)) {
        taskDescriptions = null;
      }

      response.setVariables(filteredVariables);
      response.setContext(taskDescriptions);
    }

    return response;
  }

  private VariableSearchResponse toVariableSearchResponse(final VariableDTO variableDTO) {
    return new VariableSearchResponse()
        .setId(variableDTO.getId())
        .setName(variableDTO.getName())
        .setValue(
            variableDTO.getIsValueTruncated()
                ? null
                : variableDTO
                    .getPreviewValue()) // Currently, for big variables, only truncated values are
        // included in the Task Search response. So, we avoid
        // retrieving the fullValue from the database and populate
        // the output value with previewValue if it is not
        // truncated.
        .setIsValueTruncated(variableDTO.getIsValueTruncated())
        .setPreviewValue(variableDTO.getPreviewValue());
  }

  public TaskResponse toTaskResponse(final TaskDTO taskDTO) {
    return new TaskResponse()
        .setId(taskDTO.getId())
        .setName(getName(taskDTO))
        .setTaskDefinitionId(taskDTO.getFlowNodeBpmnId())
        .setProcessName(getProcessName(taskDTO))
        .setCreationDate(taskDTO.getCreationTime())
        .setCompletionDate(taskDTO.getCompletionTime())
        .setAssignee(taskDTO.getAssignee())
        .setTaskState(taskDTO.getTaskState())
        .setFormKey(taskDTO.getFormKey())
        .setFormId(taskDTO.getFormId())
        .setFormVersion(taskDTO.getFormVersion())
        .setIsFormEmbedded(taskDTO.getIsFormEmbedded())
        .setProcessDefinitionKey(taskDTO.getProcessDefinitionId())
        .setProcessInstanceKey(taskDTO.getProcessInstanceId())
        .setDueDate(taskDTO.getDueDate())
        .setFollowUpDate(taskDTO.getFollowUpDate())
        .setCandidateGroups(taskDTO.getCandidateGroups())
        .setCandidateUsers(taskDTO.getCandidateUsers())
        .setTenantId(taskDTO.getTenantId())
        .setImplementation(taskDTO.getImplementation());
  }

  public TaskQueryDTO toTaskQuery(final TaskSearchRequest searchRequest) {
    return new TaskQueryDTO()
        .setState(searchRequest.getState())
        .setAssigned(searchRequest.getAssigned())
        .setAssignee(searchRequest.getAssignee())
        .setAssignees(searchRequest.getAssignees())
        .setTaskDefinitionId(searchRequest.getTaskDefinitionId())
        .setCandidateGroup(searchRequest.getCandidateGroup())
        .setCandidateGroups(searchRequest.getCandidateGroups())
        .setCandidateUser(searchRequest.getCandidateUser())
        .setCandidateUsers(searchRequest.getCandidateUsers())
        .setProcessDefinitionId(searchRequest.getProcessDefinitionKey())
        .setProcessInstanceId(searchRequest.getProcessInstanceKey())
        .setPageSize(searchRequest.getPageSize())
        .setDueDate(searchRequest.getDueDate())
        .setFollowUpDate(searchRequest.getFollowUpDate())
        .setTaskVariables(searchRequest.getTaskVariables())
        .setTenantIds(searchRequest.getTenantIds())
        .setSort(searchRequest.getSort())
        .setSearchAfter(searchRequest.getSearchAfter())
        .setSearchAfterOrEqual(searchRequest.getSearchAfterOrEqual())
        .setSearchBefore(searchRequest.getSearchBefore())
        .setSearchBeforeOrEqual(searchRequest.getSearchBeforeOrEqual())
        .setImplementation(searchRequest.getImplementation());
  }

  public String getName(final TaskDTO task) {
    return defaultIfNull(
        processCache.getTaskName(task.getProcessDefinitionId(), task.getFlowNodeBpmnId()),
        task.getFlowNodeBpmnId());
  }

  public String getProcessName(final TaskDTO task) {
    return defaultIfNull(
        processCache.getProcessName(task.getProcessDefinitionId()), task.getBpmnProcessId());
  }
}
