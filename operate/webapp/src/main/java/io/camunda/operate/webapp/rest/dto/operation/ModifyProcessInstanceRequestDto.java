/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
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
  public boolean equals(final Object o) {
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
      for (final String flowNodeId : variables.keySet()) {
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

    public Modification setNewTokensCount(final Integer newTokensCount) {
      this.newTokensCount = newTokensCount;
      return this;
    }

    public Long getAncestorElementInstanceKey() {
      return ancestorElementInstanceKey;
    }

    public Modification setAncestorElementInstanceKey(final Long ancestorElementInstanceKey) {
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
    public boolean equals(final Object o) {
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
