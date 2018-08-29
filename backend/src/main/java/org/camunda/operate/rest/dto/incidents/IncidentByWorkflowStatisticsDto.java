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

  private long instancesWithActiveIncidentsCount;

  private long activeInstancesCount;

  public IncidentByWorkflowStatisticsDto() {
  }

  public IncidentByWorkflowStatisticsDto(String workflowId, long instancesWithActiveIncidentsCount, long activeInstancesCount) {
    this.workflowId = workflowId;
    this.instancesWithActiveIncidentsCount = instancesWithActiveIncidentsCount;
    this.activeInstancesCount = activeInstancesCount;
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
    if (activeInstancesCount != that.activeInstancesCount)
      return false;
    if (workflowId != null ? !workflowId.equals(that.workflowId) : that.workflowId != null)
      return false;
    return name != null ? name.equals(that.name) : that.name == null;
  }

  @Override
  public int hashCode() {
    int result = workflowId != null ? workflowId.hashCode() : 0;
    result = 31 * result + version;
    result = 31 * result + (name != null ? name.hashCode() : 0);
    result = 31 * result + (int) (instancesWithActiveIncidentsCount ^ (instancesWithActiveIncidentsCount >>> 32));
    result = 31 * result + (int) (activeInstancesCount ^ (activeInstancesCount >>> 32));
    return result;
  }

  @Override
  public int compareTo(Object o) {
    if (o == null || ! (o instanceof IncidentByWorkflowStatisticsDto)){
      return 1;
    }
    return Long.compare(((IncidentByWorkflowStatisticsDto) o).getInstancesWithActiveIncidentsCount(), this.getInstancesWithActiveIncidentsCount());
  }
}
