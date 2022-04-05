/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.rest.dto;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import io.camunda.operate.entities.ProcessEntity;
import io.swagger.annotations.ApiModel;

@ApiModel(value="Process group object", description = "Group of processes with the same bpmnProcessId with all versions included")
public class ProcessGroupDto {

  private String bpmnProcessId;

  private String name;

  private List<ProcessDto> processes;

  public String getBpmnProcessId() {
    return bpmnProcessId;
  }

  public void setBpmnProcessId(String bpmnProcessId) {
    this.bpmnProcessId = bpmnProcessId;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public List<ProcessDto> getProcesses() {
    return processes;
  }

  public void setProcesses(List<ProcessDto> processes) {
    this.processes = processes;
  }

  public static List<ProcessGroupDto> createFrom(Map<String, List<ProcessEntity>> processesGrouped) {
    List<ProcessGroupDto> groups = new ArrayList<>();
    processesGrouped.entrySet().stream().forEach(groupEntry -> {
        ProcessGroupDto groupDto = new ProcessGroupDto();
        groupDto.setBpmnProcessId(groupEntry.getKey());
        groupDto.setName(groupEntry.getValue().get(0).getName());
        groupDto.setProcesses(DtoCreator.create(groupEntry.getValue(), ProcessDto.class));
        groups.add(groupDto);
      }
    );
    groups.sort(new ProcessGroupDto.ProcessGroupComparator());
    return groups;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;

    ProcessGroupDto that = (ProcessGroupDto) o;

    return bpmnProcessId != null ? bpmnProcessId.equals(that.bpmnProcessId) : that.bpmnProcessId == null;
  }

  @Override
  public int hashCode() {
    return bpmnProcessId != null ? bpmnProcessId.hashCode() : 0;
  }

  public static class ProcessGroupComparator implements Comparator<ProcessGroupDto> {
    @Override
    public int compare(ProcessGroupDto o1, ProcessGroupDto o2) {

      //when sorting "name" field has higher priority than "bpmnProcessId" field
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
