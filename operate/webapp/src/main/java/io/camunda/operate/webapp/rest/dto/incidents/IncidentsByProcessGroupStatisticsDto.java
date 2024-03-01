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

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.util.Comparator;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

public class IncidentsByProcessGroupStatisticsDto {

  public static final Comparator<IncidentsByProcessGroupStatisticsDto> COMPARATOR =
      new IncidentsByProcessGroupStatisticsDtoComparator();

  private String bpmnProcessId;

  private String tenantId;

  private String processName;

  private long instancesWithActiveIncidentsCount;

  private long activeInstancesCount;

  @JsonDeserialize(as = TreeSet.class) // for tests
  private Set<IncidentByProcessStatisticsDto> processes =
      new TreeSet<>(IncidentByProcessStatisticsDto.COMPARATOR);

  public String getBpmnProcessId() {
    return bpmnProcessId;
  }

  public void setBpmnProcessId(String bpmnProcessId) {
    this.bpmnProcessId = bpmnProcessId;
  }

  public String getTenantId() {
    return tenantId;
  }

  public IncidentsByProcessGroupStatisticsDto setTenantId(String tenantId) {
    this.tenantId = tenantId;
    return this;
  }

  public String getProcessName() {
    return processName;
  }

  public void setProcessName(String processName) {
    this.processName = processName;
  }

  public long getInstancesWithActiveIncidentsCount() {
    return instancesWithActiveIncidentsCount;
  }

  public void setInstancesWithActiveIncidentsCount(long instancesWithActiveIncidentsCount) {
    this.instancesWithActiveIncidentsCount = instancesWithActiveIncidentsCount;
  }

  public long getActiveInstancesCount() {
    return activeInstancesCount;
  }

  public void setActiveInstancesCount(long activeInstancesCount) {
    this.activeInstancesCount = activeInstancesCount;
  }

  public Set<IncidentByProcessStatisticsDto> getProcesses() {
    return processes;
  }

  public void setProcesses(Set<IncidentByProcessStatisticsDto> processes) {
    this.processes = processes;
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        bpmnProcessId,
        tenantId,
        processName,
        instancesWithActiveIncidentsCount,
        activeInstancesCount,
        processes);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    IncidentsByProcessGroupStatisticsDto that = (IncidentsByProcessGroupStatisticsDto) o;
    return instancesWithActiveIncidentsCount == that.instancesWithActiveIncidentsCount
        && activeInstancesCount == that.activeInstancesCount
        && Objects.equals(bpmnProcessId, that.bpmnProcessId)
        && Objects.equals(tenantId, that.tenantId)
        && Objects.equals(processName, that.processName)
        && Objects.equals(processes, that.processes);
  }

  public static class IncidentsByProcessGroupStatisticsDtoComparator
      implements Comparator<IncidentsByProcessGroupStatisticsDto> {
    @Override
    public int compare(
        IncidentsByProcessGroupStatisticsDto o1, IncidentsByProcessGroupStatisticsDto o2) {
      if (o1 == null) {
        if (o2 == null) {
          return 0;
        } else {
          return 1;
        }
      }
      if (o2 == null) {
        return -1;
      }
      if (o1.equals(o2)) {
        return 0;
      }
      int result =
          Long.compare(
              o2.getInstancesWithActiveIncidentsCount(), o1.getInstancesWithActiveIncidentsCount());
      if (result == 0) {
        result = Long.compare(o2.getActiveInstancesCount(), o1.getActiveInstancesCount());
        if (result == 0) {
          result = o1.getBpmnProcessId().compareTo(o2.getBpmnProcessId());
          if (result == 0) {
            result = o1.getTenantId().compareTo(o2.getTenantId());
          }
        }
      }
      return result;
    }
  }
}
