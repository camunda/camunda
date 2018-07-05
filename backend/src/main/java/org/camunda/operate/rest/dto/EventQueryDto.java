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
package org.camunda.operate.rest.dto;

public class EventQueryDto {

  private String workflowInstanceId;

  public EventQueryDto() {
  }

  public EventQueryDto(String workflowInstanceId) {
    this.workflowInstanceId = workflowInstanceId;
  }

  public EventQueryDto(String workflowInstanceId, String activityInstanceId) {
    this.workflowInstanceId = workflowInstanceId;
    this.activityInstanceId = activityInstanceId;
  }

  private String activityInstanceId;

  public String getWorkflowInstanceId() {
    return workflowInstanceId;
  }

  public void setWorkflowInstanceId(String workflowInstanceId) {
    this.workflowInstanceId = workflowInstanceId;
  }

  public String getActivityInstanceId() {
    return activityInstanceId;
  }

  public void setActivityInstanceId(String activityInstanceId) {
    this.activityInstanceId = activityInstanceId;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;

    EventQueryDto that = (EventQueryDto) o;

    if (workflowInstanceId != null ? !workflowInstanceId.equals(that.workflowInstanceId) : that.workflowInstanceId != null)
      return false;
    return activityInstanceId != null ? activityInstanceId.equals(that.activityInstanceId) : that.activityInstanceId == null;
  }

  @Override
  public int hashCode() {
    int result = workflowInstanceId != null ? workflowInstanceId.hashCode() : 0;
    result = 31 * result + (activityInstanceId != null ? activityInstanceId.hashCode() : 0);
    return result;
  }
}
