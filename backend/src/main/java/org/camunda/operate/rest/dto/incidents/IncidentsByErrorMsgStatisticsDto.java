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

public class IncidentsByErrorMsgStatisticsDto {

  private String errorMessage;

  private long instancesWithErrorCount;

  @JsonDeserialize(as = TreeSet.class)    //for tests
  private Set<IncidentByWorkflowStatisticsDto> workflows = new TreeSet<>();

  public IncidentsByErrorMsgStatisticsDto() {
  }

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

  public Set<IncidentByWorkflowStatisticsDto> getWorkflows() {
    return workflows;
  }

  public void setWorkflows(Set<IncidentByWorkflowStatisticsDto> workflows) {
    this.workflows = workflows;
  }

  public void recordInstancesCount(long count) {
    this.instancesWithErrorCount += count;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;

    IncidentsByErrorMsgStatisticsDto that = (IncidentsByErrorMsgStatisticsDto) o;

    if (instancesWithErrorCount != that.instancesWithErrorCount)
      return false;
    if (errorMessage != null ? !errorMessage.equals(that.errorMessage) : that.errorMessage != null)
      return false;
    return workflows != null ? workflows.equals(that.workflows) : that.workflows == null;
  }

  @Override
  public int hashCode() {
    int result = errorMessage != null ? errorMessage.hashCode() : 0;
    result = 31 * result + (int) (instancesWithErrorCount ^ (instancesWithErrorCount >>> 32));
    result = 31 * result + (workflows != null ? workflows.hashCode() : 0);
    return result;
  }
}
