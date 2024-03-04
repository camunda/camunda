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
package io.camunda.operate.webapp.rest.dto.incidents;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.util.Comparator;
import java.util.Set;
import java.util.TreeSet;

public class IncidentsByErrorMsgStatisticsDto {

  public static final Comparator<IncidentsByErrorMsgStatisticsDto> COMPARATOR =
      new IncidentsByErrorMsgStatisticsDtoComparator();

  private String errorMessage;

  private long instancesWithErrorCount;

  @JsonDeserialize(as = TreeSet.class) // for tests
  private Set<IncidentByProcessStatisticsDto> processes = new TreeSet<>();

  public IncidentsByErrorMsgStatisticsDto() {}

  public IncidentsByErrorMsgStatisticsDto(String errorMessage) {
    this.errorMessage = errorMessage;
  }

  public String getErrorMessage() {
    return errorMessage;
  }

  public void setErrorMessage(String errorMessage) {
    this.errorMessage = errorMessage;
  }

  public long getInstancesWithErrorCount() {
    return instancesWithErrorCount;
  }

  public void setInstancesWithErrorCount(long instancesWithErrorCount) {
    this.instancesWithErrorCount = instancesWithErrorCount;
  }

  public Set<IncidentByProcessStatisticsDto> getProcesses() {
    return processes;
  }

  public void setProcesses(Set<IncidentByProcessStatisticsDto> processes) {
    this.processes = processes;
  }

  public void recordInstancesCount(long count) {
    this.instancesWithErrorCount += count;
  }

  @Override
  public int hashCode() {
    int result = errorMessage != null ? errorMessage.hashCode() : 0;
    result = 31 * result + (int) (instancesWithErrorCount ^ (instancesWithErrorCount >>> 32));
    result = 31 * result + (processes != null ? processes.hashCode() : 0);
    return result;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    IncidentsByErrorMsgStatisticsDto that = (IncidentsByErrorMsgStatisticsDto) o;

    if (instancesWithErrorCount != that.instancesWithErrorCount) return false;
    if (errorMessage != null ? !errorMessage.equals(that.errorMessage) : that.errorMessage != null)
      return false;
    return processes != null ? processes.equals(that.processes) : that.processes == null;
  }

  public static class IncidentsByErrorMsgStatisticsDtoComparator
      implements Comparator<IncidentsByErrorMsgStatisticsDto> {

    @Override
    public int compare(IncidentsByErrorMsgStatisticsDto o1, IncidentsByErrorMsgStatisticsDto o2) {
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
      int result = Long.compare(o2.getInstancesWithErrorCount(), o1.getInstancesWithErrorCount());
      if (result == 0) {
        result = o1.getErrorMessage().compareTo(o2.getErrorMessage());
      }
      return result;
    }
  }
}
