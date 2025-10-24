/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.rest.dto;

import io.camunda.operate.store.ProcessStore;
import io.camunda.operate.webapp.security.permission.PermissionsService;
import io.camunda.webapps.schema.entities.ProcessEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Schema(
    name = "Process group object",
    description = "Group of processes with the same bpmnProcessId with all versions included")
public class ProcessGroupDto {

  private String bpmnProcessId;

  private String tenantId;

  private String name;

  private Set<String> permissions;

  private List<ProcessDto> processes;

  public static List<ProcessGroupDto> createFrom(
      final Map<ProcessStore.ProcessKey, List<ProcessEntity>> processesGrouped) {
    return createFrom(processesGrouped, null);
  }

  public static List<ProcessGroupDto> createFrom(
      final Map<ProcessStore.ProcessKey, List<ProcessEntity>> processesGrouped,
      final PermissionsService permissionsService) {
    final List<ProcessGroupDto> groups = new ArrayList<>();
    processesGrouped.values().stream()
        .forEach(
            group -> {
              final ProcessGroupDto groupDto = new ProcessGroupDto();
              final ProcessEntity process0 = group.get(0);
              groupDto.setBpmnProcessId(process0.getBpmnProcessId());
              groupDto.setTenantId(process0.getTenantId());
              groupDto.setName(process0.getName());
              groupDto.setPermissions(
                  permissionsService.getProcessDefinitionPermissions(process0.getBpmnProcessId()));
              groupDto.setProcesses(DtoCreator.create(group, ProcessDto.class));
              groups.add(groupDto);
            });
    groups.sort(new ProcessGroupDto.ProcessGroupComparator());
    return groups;
  }

  public String getBpmnProcessId() {
    return bpmnProcessId;
  }

  public void setBpmnProcessId(final String bpmnProcessId) {
    this.bpmnProcessId = bpmnProcessId;
  }

  public String getTenantId() {
    return tenantId;
  }

  public ProcessGroupDto setTenantId(final String tenantId) {
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

  public List<ProcessDto> getProcesses() {
    return processes;
  }

  public void setProcesses(final List<ProcessDto> processes) {
    this.processes = processes;
  }

  @Override
  public int hashCode() {
    return bpmnProcessId != null ? bpmnProcessId.hashCode() : 0;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    final ProcessGroupDto that = (ProcessGroupDto) o;

    return bpmnProcessId != null
        ? bpmnProcessId.equals(that.bpmnProcessId)
        : that.bpmnProcessId == null;
  }

  public static class ProcessGroupComparator implements Comparator<ProcessGroupDto> {
    @Override
    public int compare(final ProcessGroupDto o1, final ProcessGroupDto o2) {

      // when sorting "name" field has higher priority than "bpmnProcessId" field
      if (o1.getName() == null && o2.getName() == null) {
        return o1.getBpmnProcessId().compareTo(o2.getBpmnProcessId());
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
      return o1.getBpmnProcessId().compareTo(o2.getBpmnProcessId());
    }
  }
}
