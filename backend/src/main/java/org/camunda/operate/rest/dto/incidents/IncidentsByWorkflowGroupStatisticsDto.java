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

import java.util.Set;
import java.util.TreeSet;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

public class IncidentsByWorkflowGroupStatisticsDto {

  private String bpmnProcessId;

  private String workflowName;

  private long instancesWithActiveIncidentsCount;

  private long activeInstancesCount;

  @JsonDeserialize(as = TreeSet.class)    //for tests
  private Set<IncidentByWorkflowStatisticsDto> workflows = new TreeSet<>();

  public String getBpmnProcessId() {
    return bpmnProcessId;
  }

  public void setBpmnProcessId(String bpmnProcessId) {
    this.bpmnProcessId = bpmnProcessId;
  }

  public String getWorkflowName() {
    return workflowName;
  }

  public void setWorkflowName(String workflowName) {
    this.workflowName = workflowName;
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

  public Set<IncidentByWorkflowStatisticsDto> getWorkflows() {
    return workflows;
  }

  public void setWorkflows(Set<IncidentByWorkflowStatisticsDto> workflows) {
    this.workflows = workflows;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;

    IncidentsByWorkflowGroupStatisticsDto that = (IncidentsByWorkflowGroupStatisticsDto) o;

    if (instancesWithActiveIncidentsCount != that.instancesWithActiveIncidentsCount)
      return false;
    if (activeInstancesCount != that.activeInstancesCount)
      return false;
    if (bpmnProcessId != null ? !bpmnProcessId.equals(that.bpmnProcessId) : that.bpmnProcessId != null)
      return false;
    if (workflowName != null ? !workflowName.equals(that.workflowName) : that.workflowName != null)
      return false;
    return workflows != null ? workflows.equals(that.workflows) : that.workflows == null;
  }

  @Override
  public int hashCode() {
    int result = bpmnProcessId != null ? bpmnProcessId.hashCode() : 0;
    result = 31 * result + (workflowName != null ? workflowName.hashCode() : 0);
    result = 31 * result + (int) (instancesWithActiveIncidentsCount ^ (instancesWithActiveIncidentsCount >>> 32));
    result = 31 * result + (int) (activeInstancesCount ^ (activeInstancesCount >>> 32));
    result = 31 * result + (workflows != null ? workflows.hashCode() : 0);
    return result;
  }
}
