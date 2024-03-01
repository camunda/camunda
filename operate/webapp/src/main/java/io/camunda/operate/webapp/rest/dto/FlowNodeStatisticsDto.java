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
package io.camunda.operate.webapp.rest.dto;

public class FlowNodeStatisticsDto {

  private String activityId;

  private Long active = 0L;
  private Long canceled = 0L;
  private Long incidents = 0L;
  private Long completed = 0L;

  public FlowNodeStatisticsDto() {}

  public FlowNodeStatisticsDto(String activityId) {
    this.activityId = activityId;
  }

  public String getActivityId() {
    return activityId;
  }

  public FlowNodeStatisticsDto setActivityId(String activityId) {
    this.activityId = activityId;
    return this;
  }

  public Long getActive() {
    return active;
  }

  public FlowNodeStatisticsDto setActive(Long active) {
    this.active = active;
    return this;
  }

  public void addActive(Long active) {
    this.active += active;
  }

  public Long getCanceled() {
    return canceled;
  }

  public FlowNodeStatisticsDto setCanceled(Long canceled) {
    this.canceled = canceled;
    return this;
  }

  public void addCanceled(Long canceled) {
    this.canceled += canceled;
  }

  public Long getIncidents() {
    return incidents;
  }

  public FlowNodeStatisticsDto setIncidents(Long incidents) {
    this.incidents = incidents;
    return this;
  }

  public void addIncidents(Long incidents) {
    this.incidents += incidents;
  }

  public Long getCompleted() {
    return completed;
  }

  public FlowNodeStatisticsDto setCompleted(Long completed) {
    this.completed = completed;
    return this;
  }

  public void addCompleted(Long completed) {
    this.completed += completed;
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

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    FlowNodeStatisticsDto that = (FlowNodeStatisticsDto) o;

    if (activityId != null ? !activityId.equals(that.activityId) : that.activityId != null)
      return false;
    if (active != null ? !active.equals(that.active) : that.active != null) return false;
    if (canceled != null ? !canceled.equals(that.canceled) : that.canceled != null) return false;
    if (incidents != null ? !incidents.equals(that.incidents) : that.incidents != null)
      return false;
    return completed != null ? completed.equals(that.completed) : that.completed == null;
  }

  @Override
  public String toString() {
    return "FlowNodeStatisticsDto{"
        + "activityId='"
        + activityId
        + '\''
        + ", active="
        + active
        + ", canceled="
        + canceled
        + ", incidents="
        + incidents
        + ", completed="
        + completed
        + '}';
  }
}
