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

import java.util.Comparator;
import java.util.Objects;

public class IncidentByProcessStatisticsDto implements Comparable<IncidentByProcessStatisticsDto> {

  public static final Comparator<IncidentByProcessStatisticsDto> COMPARATOR =
      new IncidentByProcessStatisticsDtoComparator();

  private String processId;

  private int version;

  private String name;

  private String bpmnProcessId;

  private String tenantId;

  private String errorMessage;

  private long instancesWithActiveIncidentsCount;

  private long activeInstancesCount;

  public IncidentByProcessStatisticsDto() {}

  public IncidentByProcessStatisticsDto(
      String processId, long instancesWithActiveIncidentsCount, long activeInstancesCount) {
    this.processId = processId;
    this.instancesWithActiveIncidentsCount = instancesWithActiveIncidentsCount;
    this.activeInstancesCount = activeInstancesCount;
  }

  public IncidentByProcessStatisticsDto(
      String processId, String errorMessage, long instancesWithActiveIncidentsCount) {
    this.processId = processId;
    this.errorMessage = errorMessage;
    this.instancesWithActiveIncidentsCount = instancesWithActiveIncidentsCount;
  }

  public String getProcessId() {
    return processId;
  }

  public void setProcessId(String processId) {
    this.processId = processId;
  }

  public int getVersion() {
    return version;
  }

  public void setVersion(int version) {
    this.version = version;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
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

  public IncidentByProcessStatisticsDto setTenantId(String tenantId) {
    this.tenantId = tenantId;
    return this;
  }

  public String getErrorMessage() {
    return errorMessage;
  }

  public void setErrorMessage(String errorMessage) {
    this.errorMessage = errorMessage;
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

  @Override
  public int hashCode() {
    return Objects.hash(
        processId,
        version,
        name,
        bpmnProcessId,
        tenantId,
        errorMessage,
        instancesWithActiveIncidentsCount,
        activeInstancesCount);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    IncidentByProcessStatisticsDto that = (IncidentByProcessStatisticsDto) o;
    return version == that.version
        && instancesWithActiveIncidentsCount == that.instancesWithActiveIncidentsCount
        && activeInstancesCount == that.activeInstancesCount
        && Objects.equals(processId, that.processId)
        && Objects.equals(name, that.name)
        && Objects.equals(bpmnProcessId, that.bpmnProcessId)
        && Objects.equals(tenantId, that.tenantId)
        && Objects.equals(errorMessage, that.errorMessage);
  }

  @Override
  public int compareTo(IncidentByProcessStatisticsDto o) {
    return COMPARATOR.compare(this, o);
  }

  public static class IncidentByProcessStatisticsDtoComparator
      implements Comparator<IncidentByProcessStatisticsDto> {

    @Override
    public int compare(IncidentByProcessStatisticsDto o1, IncidentByProcessStatisticsDto o2) {
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
          result =
              emptyStringWhenNull(o1.getBpmnProcessId())
                  .compareTo(emptyStringWhenNull(o2.getBpmnProcessId()));
          if (result == 0) {
            result =
                emptyStringWhenNull(o1.getTenantId())
                    .compareTo(emptyStringWhenNull(o2.getTenantId()));
            if (result == 0) {
              result = Integer.compare(o1.getVersion(), o2.getVersion());
            }
          }
        }
      }
      return result;
    }

    private String emptyStringWhenNull(String aString) {
      return aString == null ? "" : aString;
    }
  }
}
