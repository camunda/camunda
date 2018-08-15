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

  private Long activeCount = 0L;
  private Long canceledCount = 0L;
  private Long incidentsCount = 0L;
  private Long finishedCount = 0L;

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

  public Long getActiveCount() {
    return activeCount;
  }

  public void setActiveCount(Long activeCount) {
    this.activeCount = activeCount;
  }

  public Long getCanceledCount() {
    return canceledCount;
  }

  public void setCanceledCount(Long canceledCount) {
    this.canceledCount = canceledCount;
  }

  public Long getIncidentsCount() {
    return incidentsCount;
  }

  public void setIncidentsCount(Long incidentsCount) {
    this.incidentsCount = incidentsCount;
  }

  public Long getFinishedCount() {
    return finishedCount;
  }

  public void setFinishedCount(Long finishedCount) {
    this.finishedCount = finishedCount;
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
    if (activeCount != null ? !activeCount.equals(that.activeCount) : that.activeCount != null)
      return false;
    if (canceledCount != null ? !canceledCount.equals(that.canceledCount) : that.canceledCount != null)
      return false;
    if (incidentsCount != null ? !incidentsCount.equals(that.incidentsCount) : that.incidentsCount != null)
      return false;
    return finishedCount != null ? finishedCount.equals(that.finishedCount) : that.finishedCount == null;
  }

  @Override
  public int hashCode() {
    int result = activityId != null ? activityId.hashCode() : 0;
    result = 31 * result + (activeCount != null ? activeCount.hashCode() : 0);
    result = 31 * result + (canceledCount != null ? canceledCount.hashCode() : 0);
    result = 31 * result + (incidentsCount != null ? incidentsCount.hashCode() : 0);
    result = 31 * result + (finishedCount != null ? finishedCount.hashCode() : 0);
    return result;
  }
}
