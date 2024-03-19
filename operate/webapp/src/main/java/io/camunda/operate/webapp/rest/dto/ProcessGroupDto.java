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
package io.camunda.operate.webapp.rest.dto;

import io.camunda.operate.entities.ProcessEntity;
import io.camunda.operate.store.ProcessStore;
import io.camunda.operate.webapp.security.identity.PermissionsService;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
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
      Map<ProcessStore.ProcessKey, List<ProcessEntity>> processesGrouped) {
    return createFrom(processesGrouped, null);
  }

  public static List<ProcessGroupDto> createFrom(
      Map<ProcessStore.ProcessKey, List<ProcessEntity>> processesGrouped,
      PermissionsService permissionsService) {
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
                  permissionsService == null
                      ? new HashSet<>()
                      : permissionsService.getProcessDefinitionPermission(
                          process0.getBpmnProcessId()));
              groupDto.setProcesses(DtoCreator.create(group, ProcessDto.class));
              groups.add(groupDto);
            });
    groups.sort(new ProcessGroupDto.ProcessGroupComparator());
    return groups;
  }

  public String getBpmnProcessId() {
    return bpmnProcessId;
  }

  public void setBpmnProcessId(String bpmnProcessId) {
    this.bpmnProcessId = bpmnProcessId;
  }

  public String getTenantId() {
    return tenantId;
  }

  public ProcessGroupDto setTenantId(String tenantId) {
    this.tenantId = tenantId;
    return this;
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

  public List<ProcessDto> getProcesses() {
    return processes;
  }

  public void setProcesses(List<ProcessDto> processes) {
    this.processes = processes;
  }

  @Override
  public int hashCode() {
    return bpmnProcessId != null ? bpmnProcessId.hashCode() : 0;
  }

  @Override
  public boolean equals(Object o) {
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
    public int compare(ProcessGroupDto o1, ProcessGroupDto o2) {

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
