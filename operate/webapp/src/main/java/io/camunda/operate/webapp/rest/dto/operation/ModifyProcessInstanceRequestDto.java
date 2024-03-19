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
package io.camunda.operate.webapp.rest.dto.operation;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.apache.commons.collections4.MapUtils;

public class ModifyProcessInstanceRequestDto {
  private List<Modification> modifications;
  private String processInstanceKey;

  public String getProcessInstanceKey() {
    return processInstanceKey;
  }

  public ModifyProcessInstanceRequestDto setProcessInstanceKey(final String processInstanceKey) {
    this.processInstanceKey = processInstanceKey;
    return this;
  }

  public List<Modification> getModifications() {
    return modifications;
  }

  public ModifyProcessInstanceRequestDto setModifications(final List<Modification> modifications) {
    this.modifications = modifications;
    return this;
  }

  @Override
  public int hashCode() {
    return Objects.hash(modifications, processInstanceKey);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final ModifyProcessInstanceRequestDto that = (ModifyProcessInstanceRequestDto) o;
    return Objects.equals(modifications, that.modifications)
        && Objects.equals(processInstanceKey, that.processInstanceKey);
  }

  @Override
  public String toString() {
    return "ModifyProcessInstanceRequestDto{"
        + "modifications="
        + modifications
        + ", processInstanceKey="
        + processInstanceKey
        + '}';
  }

  public static class Modification {

    private Type modification;
    private String fromFlowNodeId;
    private String fromFlowNodeInstanceKey;
    private String toFlowNodeId;
    private Long scopeKey;
    private Long ancestorElementInstanceKey;
    private Integer newTokensCount;
    private Map<String, Object> variables;

    public Type getModification() {
      return modification;
    }

    public Modification setModification(final Type modification) {
      this.modification = modification;
      return this;
    }

    public String getFromFlowNodeId() {
      return fromFlowNodeId;
    }

    public Modification setFromFlowNodeId(final String fromFlowNodeId) {
      this.fromFlowNodeId = fromFlowNodeId;
      return this;
    }

    public String getToFlowNodeId() {
      return toFlowNodeId;
    }

    public Modification setToFlowNodeId(final String toFlowNodeId) {
      this.toFlowNodeId = toFlowNodeId;
      return this;
    }

    public Long getScopeKey() {
      return scopeKey;
    }

    public Modification setScopeKey(final Long scopeKey) {
      this.scopeKey = scopeKey;
      return this;
    }

    public Map<String, Object> getVariables() {
      return variables;
    }

    public Modification setVariables(final Map<String, Object> variables) {
      this.variables = variables;
      return this;
    }

    public Map<String, List<Map<String, Object>>> variablesForAddToken() {
      if (variables == null || MapUtils.isEmpty(variables)) {
        return null;
      }
      final Map<String, List<Map<String, Object>>> result = new HashMap<>();
      for (String flowNodeId : variables.keySet()) {
        final List<Map<String, Object>> variablesList =
            (List<Map<String, Object>>) variables.get(flowNodeId);
        result.put(flowNodeId, variablesList);
      }
      return result;
    }

    public String getFromFlowNodeInstanceKey() {
      return fromFlowNodeInstanceKey;
    }

    public Modification setFromFlowNodeInstanceKey(final String fromFlowNodeInstanceKey) {
      this.fromFlowNodeInstanceKey = fromFlowNodeInstanceKey;
      return this;
    }

    public Integer getNewTokensCount() {
      return newTokensCount;
    }

    public Modification setNewTokensCount(Integer newTokensCount) {
      this.newTokensCount = newTokensCount;
      return this;
    }

    public Long getAncestorElementInstanceKey() {
      return ancestorElementInstanceKey;
    }

    public Modification setAncestorElementInstanceKey(Long ancestorElementInstanceKey) {
      this.ancestorElementInstanceKey = ancestorElementInstanceKey;
      return this;
    }

    @Override
    public int hashCode() {
      return Objects.hash(
          modification,
          fromFlowNodeId,
          toFlowNodeId,
          scopeKey,
          newTokensCount,
          variables,
          fromFlowNodeInstanceKey,
          ancestorElementInstanceKey);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      final Modification that = (Modification) o;
      return modification == that.modification
          && Objects.equals(fromFlowNodeId, that.fromFlowNodeId)
          && Objects.equals(toFlowNodeId, that.toFlowNodeId)
          && Objects.equals(scopeKey, that.scopeKey)
          && Objects.equals(newTokensCount, that.newTokensCount)
          && Objects.equals(variables, that.variables)
          && Objects.equals(fromFlowNodeInstanceKey, that.fromFlowNodeInstanceKey)
          && Objects.equals(ancestorElementInstanceKey, that.ancestorElementInstanceKey);
    }

    @Override
    public String toString() {
      return "Modification{"
          + "modification="
          + modification
          + ", fromFlowNodeId='"
          + fromFlowNodeId
          + '\''
          + ", toFlowNodeId='"
          + toFlowNodeId
          + '\''
          + ", scopeKey="
          + scopeKey
          + ", newTokensCount="
          + newTokensCount
          + ", variables="
          + variables
          + ", fromFlowNodeInstanceKey="
          + fromFlowNodeInstanceKey
          + "}"
          + ", ancestorElementInstanceKey="
          + ancestorElementInstanceKey
          + "}";
    }

    public enum Type {
      ADD_TOKEN,
      CANCEL_TOKEN,
      MOVE_TOKEN,
      ADD_VARIABLE,
      EDIT_VARIABLE
    }
  }
}
