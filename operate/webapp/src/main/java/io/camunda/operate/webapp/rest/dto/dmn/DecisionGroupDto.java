/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.rest.dto.dmn;

import io.camunda.operate.webapp.rest.dto.DtoCreator;
import io.camunda.operate.webapp.security.permission.PermissionsService;
import io.camunda.webapps.schema.entities.dmn.definition.DecisionDefinitionEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.*;

@Schema(
    name = "Decision group object",
    description = "Group of decisions with the same decisionId with all versions included")
public class DecisionGroupDto {

  private String decisionId;

  private String tenantId;

  private String name;

  private Set<String> permissions;

  private List<DecisionDto> decisions;

  public static List<DecisionGroupDto> createFrom(
      final Map<String, List<DecisionDefinitionEntity>> decisionsGrouped) {
    return createFrom(decisionsGrouped, null);
  }

  public static List<DecisionGroupDto> createFrom(
      final Map<String, List<DecisionDefinitionEntity>> decisionsGrouped,
      final PermissionsService permissionsService) {
    final List<DecisionGroupDto> groups = new ArrayList<>();
    decisionsGrouped.values().stream()
        .forEach(
            group -> {
              final DecisionGroupDto groupDto = new DecisionGroupDto();
              final DecisionDefinitionEntity decision0 = group.get(0);
              groupDto.setDecisionId(decision0.getDecisionId());
              groupDto.setTenantId(decision0.getTenantId());
              groupDto.setName(decision0.getName());
              groupDto.setPermissions(
                  permissionsService.getDecisionDefinitionPermissions(decision0.getDecisionId()));
              groupDto.setDecisions(DtoCreator.create(group, DecisionDto.class));
              groups.add(groupDto);
            });
    groups.sort(new DecisionGroupComparator());
    return groups;
  }

  public String getDecisionId() {
    return decisionId;
  }

  public void setDecisionId(final String decisionId) {
    this.decisionId = decisionId;
  }

  public String getTenantId() {
    return tenantId;
  }

  public DecisionGroupDto setTenantId(final String tenantId) {
    this.tenantId = tenantId;
    return this;
  }

  public String getName() {
    return name;
  }

  public void setName(final String name) {
    this.name = name;
  }

  public Set<String> getPermissions() {
    return permissions;
  }

  public void setPermissions(final Set<String> permissions) {
    this.permissions = permissions;
  }

  public List<DecisionDto> getDecisions() {
    return decisions;
  }

  public void setDecisions(final List<DecisionDto> decisions) {
    this.decisions = decisions;
  }

  @Override
  public int hashCode() {
    return Objects.hash(decisionId, tenantId, name, permissions, decisions);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final DecisionGroupDto that = (DecisionGroupDto) o;
    return Objects.equals(decisionId, that.decisionId)
        && Objects.equals(tenantId, that.tenantId)
        && Objects.equals(name, that.name)
        && Objects.equals(permissions, that.permissions)
        && Objects.equals(decisions, that.decisions);
  }

  public static class DecisionGroupComparator implements Comparator<DecisionGroupDto> {
    @Override
    public int compare(final DecisionGroupDto o1, final DecisionGroupDto o2) {

      // when sorting "name" field has higher priority than "bpmnProcessId" field
      if (o1.getName() == null && o2.getName() == null) {
        return o1.getDecisionId().compareTo(o2.getDecisionId());
      }
      if (o1.getName() == null) {
        return 1;
      }
      if (o2.getName() == null) {
        return -1;
      }
      if (!o1.getName().equals(o2.getName())) {
        return o1.getName().compareTo(o2.getName());
      }
      return o1.getDecisionId().compareTo(o2.getDecisionId());
    }
  }
}
