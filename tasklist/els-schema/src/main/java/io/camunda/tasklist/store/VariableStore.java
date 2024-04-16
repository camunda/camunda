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
package io.camunda.tasklist.store;

import io.camunda.tasklist.entities.*;
import io.camunda.tasklist.views.TaskSearchView;
import java.util.*;

public interface VariableStore {

  public List<VariableEntity> getVariablesByFlowNodeInstanceIds(
      List<String> flowNodeInstanceIds, List<String> varNames, final Set<String> fieldNames);

  public Map<String, List<TaskVariableEntity>> getTaskVariablesPerTaskId(
      final List<GetVariablesRequest> requests);

  Map<String, String> getTaskVariablesIdsWithIndexByTaskIds(final List<String> taskIds);

  public void persistTaskVariables(final Collection<TaskVariableEntity> finalVariables);

  public List<FlowNodeInstanceEntity> getFlowNodeInstances(final List<String> processInstanceIds);

  public VariableEntity getRuntimeVariable(final String variableId, Set<String> fieldNames);

  public TaskVariableEntity getTaskVariable(final String variableId, Set<String> fieldNames);

  public List<String> getProcessInstanceIdsWithMatchingVars(
      List<String> varNames, List<String> varValues);

  static class FlowNodeTree extends HashMap<String, String> {

    public String getParent(String currentFlowNodeInstanceId) {
      return super.get(currentFlowNodeInstanceId);
    }

    public void setParent(String currentFlowNodeInstanceId, String parentFlowNodeInstanceId) {
      super.put(currentFlowNodeInstanceId, parentFlowNodeInstanceId);
    }

    public Set<String> getFlowNodeInstanceIds() {
      return super.keySet();
    }
  }

  static class VariableMap extends HashMap<String, VariableEntity> {

    public void putAll(final VariableMap m) {
      for (Entry<String, VariableEntity> entry : m.entrySet()) {
        // since we build variable map from bottom to top of the flow node tree, we don't overwrite
        // the values from lower (inner) scopes with those from upper (outer) scopes
        putIfAbsent(entry.getKey(), entry.getValue());
      }
    }

    @Override
    @Deprecated
    public void putAll(final Map<? extends String, ? extends VariableEntity> m) {
      super.putAll(m);
    }
  }

  public static class GetVariablesRequest {

    private String taskId;
    private TaskState state;
    private String flowNodeInstanceId;
    private String processInstanceId;
    private List<String> varNames;
    private Set<String> fieldNames = new HashSet<>();

    public static GetVariablesRequest createFrom(TaskEntity taskEntity, Set<String> fieldNames) {
      return new GetVariablesRequest()
          .setTaskId(taskEntity.getId())
          .setFlowNodeInstanceId(taskEntity.getFlowNodeInstanceId())
          .setState(taskEntity.getState())
          .setProcessInstanceId(taskEntity.getProcessInstanceId())
          .setFieldNames(fieldNames);
    }

    public static GetVariablesRequest createFrom(TaskEntity taskEntity) {
      return new GetVariablesRequest()
          .setTaskId(taskEntity.getId())
          .setFlowNodeInstanceId(taskEntity.getFlowNodeInstanceId())
          .setState(taskEntity.getState())
          .setProcessInstanceId(taskEntity.getProcessInstanceId());
    }

    public static GetVariablesRequest createFrom(
        TaskSearchView taskSearchView, List<String> varNames, Set<String> fieldNames) {
      return new GetVariablesRequest()
          .setTaskId(taskSearchView.getId())
          .setFlowNodeInstanceId(taskSearchView.getFlowNodeInstanceId())
          .setState(taskSearchView.getState())
          .setProcessInstanceId(taskSearchView.getProcessInstanceId())
          .setVarNames(varNames)
          .setFieldNames(fieldNames);
    }

    public String getTaskId() {
      return taskId;
    }

    public GetVariablesRequest setTaskId(final String taskId) {
      this.taskId = taskId;
      return this;
    }

    public TaskState getState() {
      return state;
    }

    public GetVariablesRequest setState(final TaskState state) {
      this.state = state;
      return this;
    }

    public String getFlowNodeInstanceId() {
      return flowNodeInstanceId;
    }

    public GetVariablesRequest setFlowNodeInstanceId(final String flowNodeInstanceId) {
      this.flowNodeInstanceId = flowNodeInstanceId;
      return this;
    }

    public String getProcessInstanceId() {
      return processInstanceId;
    }

    public GetVariablesRequest setProcessInstanceId(final String processInstanceId) {
      this.processInstanceId = processInstanceId;
      return this;
    }

    public List<String> getVarNames() {
      return varNames;
    }

    public GetVariablesRequest setVarNames(final List<String> varNames) {
      this.varNames = varNames;
      return this;
    }

    public Set<String> getFieldNames() {
      return fieldNames;
    }

    public GetVariablesRequest setFieldNames(final Set<String> fieldNames) {
      this.fieldNames = fieldNames;
      return this;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      final GetVariablesRequest that = (GetVariablesRequest) o;
      return Objects.equals(taskId, that.taskId)
          && state == that.state
          && Objects.equals(flowNodeInstanceId, that.flowNodeInstanceId)
          && Objects.equals(processInstanceId, that.processInstanceId)
          && Objects.equals(varNames, that.varNames)
          && Objects.equals(fieldNames, that.fieldNames);
    }

    @Override
    public int hashCode() {
      return Objects.hash(
          taskId, state, flowNodeInstanceId, processInstanceId, varNames, fieldNames);
    }
  }
}
