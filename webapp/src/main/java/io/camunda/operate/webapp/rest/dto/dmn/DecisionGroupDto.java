/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.rest.dto.dmn;

import io.camunda.operate.entities.dmn.definition.DecisionDefinitionEntity;
import io.camunda.operate.webapp.rest.dto.DtoCreator;
import io.camunda.operate.webapp.security.identity.PermissionsService;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Schema(name = "Decision group object", description = "Group of decisions with the same decisionId with all versions included")
public class DecisionGroupDto {

  private String decisionId;

  private String name;

  private Set<String> permissions;

  private List<DecisionDto> decisions;

  public String getDecisionId() {
    return decisionId;
  }

  public void setDecisionId(String decisionId) {
    this.decisionId = decisionId;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public Set<String> getPermissions() {
    return permissions;
  }

  public void setPermissions(Set<String> permissions) {
    this.permissions = permissions;
  }

  public List<DecisionDto> getDecisions() {
    return decisions;
  }

  public void setDecisions(List<DecisionDto> decisions) {
    this.decisions = decisions;
  }

  public static List<DecisionGroupDto> createFrom(Map<String, List<DecisionDefinitionEntity>> decisionsGrouped) {
    return createFrom(decisionsGrouped, null);
  }

  public static List<DecisionGroupDto> createFrom(Map<String, List<DecisionDefinitionEntity>> decisionsGrouped, PermissionsService permissionsService) {
    List<DecisionGroupDto> groups = new ArrayList<>();
    decisionsGrouped.entrySet().stream().forEach(groupEntry -> {
        DecisionGroupDto groupDto = new DecisionGroupDto();
        groupDto.setDecisionId(groupEntry.getKey());
        groupDto.setName(groupEntry.getValue().get(0).getName());
        groupDto.setPermissions(permissionsService == null ? new HashSet<>() : permissionsService.getDecisionDefinitionPermission(groupEntry.getKey()));
        groupDto.setDecisions(DtoCreator.create(groupEntry.getValue(), DecisionDto.class));
        groups.add(groupDto);
      }
    );
    groups.sort(new DecisionGroupComparator());
    return groups;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;

    DecisionGroupDto that = (DecisionGroupDto) o;

    return decisionId != null ? decisionId.equals(that.decisionId) : that.decisionId == null;
  }

  @Override
  public int hashCode() {
    return decisionId != null ? decisionId.hashCode() : 0;
  }

  public static class DecisionGroupComparator implements Comparator<DecisionGroupDto> {
    @Override
    public int compare(DecisionGroupDto o1, DecisionGroupDto o2) {

      //when sorting "name" field has higher priority than "bpmnProcessId" field
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
