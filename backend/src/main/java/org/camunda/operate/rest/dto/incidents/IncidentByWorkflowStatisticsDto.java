/* Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.camunda.operate.rest.dto.incidents;

public class IncidentByWorkflowStatisticsDto implements Comparable {

  private String workflowId;

  private int version;

  private String name;

  private String bpmnProcessId;

  private String errorMessage;

  private long instancesWithActiveIncidentsCount;

  private Long activeInstancesCount;

  public IncidentByWorkflowStatisticsDto() {
  }

  public IncidentByWorkflowStatisticsDto(String workflowId, long instancesWithActiveIncidentsCount, long activeInstancesCount) {
    this.workflowId = workflowId;
    this.instancesWithActiveIncidentsCount = instancesWithActiveIncidentsCount;
    this.activeInstancesCount = activeInstancesCount;
  }

  public IncidentByWorkflowStatisticsDto(String workflowId, String errorMessage, long instancesWithActiveIncidentsCount) {
    this.workflowId = workflowId;
    this.errorMessage = errorMessage;
    this.instancesWithActiveIncidentsCount = instancesWithActiveIncidentsCount;
  }

  public String getWorkflowId() {
    return workflowId;
  }

  public void setWorkflowId(String workflowId) {
    this.workflowId = workflowId;
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

  public Long getActiveInstancesCount() {
    return activeInstancesCount;
  }

  public void setActiveInstancesCount(Long activeInstancesCount) {
    this.activeInstancesCount = activeInstancesCount;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;

    IncidentByWorkflowStatisticsDto that = (IncidentByWorkflowStatisticsDto) o;

    if (version != that.version)
      return false;
    if (instancesWithActiveIncidentsCount != that.instancesWithActiveIncidentsCount)
      return false;
    if (workflowId != null ? !workflowId.equals(that.workflowId) : that.workflowId != null)
      return false;
    if (name != null ? !name.equals(that.name) : that.name != null)
      return false;
    if (bpmnProcessId != null ? !bpmnProcessId.equals(that.bpmnProcessId) : that.bpmnProcessId != null)
      return false;
    if (errorMessage != null ? !errorMessage.equals(that.errorMessage) : that.errorMessage != null)
      return false;
    return activeInstancesCount != null ? activeInstancesCount.equals(that.activeInstancesCount) : that.activeInstancesCount == null;
  }

  @Override
  public int hashCode() {
    int result = workflowId != null ? workflowId.hashCode() : 0;
    result = 31 * result + version;
    result = 31 * result + (name != null ? name.hashCode() : 0);
    result = 31 * result + (bpmnProcessId != null ? bpmnProcessId.hashCode() : 0);
    result = 31 * result + (errorMessage != null ? errorMessage.hashCode() : 0);
    result = 31 * result + (int) (instancesWithActiveIncidentsCount ^ (instancesWithActiveIncidentsCount >>> 32));
    result = 31 * result + (activeInstancesCount != null ? activeInstancesCount.hashCode() : 0);
    return result;
  }

  @Override
  public int compareTo(Object o) {
    if (o == null || ! (o instanceof IncidentByWorkflowStatisticsDto)){
      return 1;
    }
    final IncidentByWorkflowStatisticsDto stat = (IncidentByWorkflowStatisticsDto) o;
    int compare = Long.compare(stat.getInstancesWithActiveIncidentsCount(), this.getInstancesWithActiveIncidentsCount());
    if (compare == 0) {
      compare = this.getWorkflowId().compareTo(stat.getWorkflowId());
    }
    return compare;
  }
}
