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

public class ActivityStatisticsDto {

  private String activityId;

  private Long active = 0L;
  private Long canceled = 0L;
  private Long incidents = 0L;
  private Long completed = 0L;

  public ActivityStatisticsDto() {
  }

  public ActivityStatisticsDto(String activityId) {
    this.activityId = activityId;
  }

  public String getActivityId() {
    return activityId;
  }

  public void setActivityId(String activityId) {
    this.activityId = activityId;
  }

  public Long getActive() {
    return active;
  }

  public void setActive(Long active) {
    this.active = active;
  }

  public Long getCanceled() {
    return canceled;
  }

  public void setCanceled(Long canceled) {
    this.canceled = canceled;
  }

  public Long getIncidents() {
    return incidents;
  }

  public void setIncidents(Long incidents) {
    this.incidents = incidents;
  }

  public Long getCompleted() {
    return completed;
  }

  public void setCompleted(Long completed) {
    this.completed = completed;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;

    ActivityStatisticsDto that = (ActivityStatisticsDto) o;

    if (activityId != null ? !activityId.equals(that.activityId) : that.activityId != null)
      return false;
    if (active != null ? !active.equals(that.active) : that.active != null)
      return false;
    if (canceled != null ? !canceled.equals(that.canceled) : that.canceled != null)
      return false;
    if (incidents != null ? !incidents.equals(that.incidents) : that.incidents != null)
      return false;
    return completed != null ? completed.equals(that.completed) : that.completed == null;
  }

  @Override
  public int hashCode() {
    int result = activityId != null ? activityId.hashCode() : 0;
    result = 31 * result + (active != null ? active.hashCode() : 0);
    result = 31 * result + (canceled != null ? canceled.hashCode() : 0);
    result = 31 * result + (incidents != null ? incidents.hashCode() : 0);
    result = 31 * result + (completed != null ? completed.hashCode() : 0);
    return result;
  }
}
