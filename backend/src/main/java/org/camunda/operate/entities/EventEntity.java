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
package org.camunda.operate.entities;

import java.time.OffsetDateTime;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnore;

public class EventEntity extends OperateEntity {

  /**
   * Workflow data.
   */
  private String workflowId;
  private String workflowInstanceId;
  private String bpmnProcessId;

  /**
   * Activity data.
   */
  private String activityId;
  private String activityInstanceId;

  /**
   * Event data.
   */
  private EventSourceType eventSourceType;
  private EventType eventType;
  private OffsetDateTime dateTime;
  private String payload;

  /**
   * Metadata
   */
  private EventMetadataEntity metadata;

  public String getWorkflowId() {
    return workflowId;
  }

  public void setWorkflowId(String workflowId) {
    this.workflowId = workflowId;
  }

  public String getWorkflowInstanceId() {
    return workflowInstanceId;
  }

  public void setWorkflowInstanceId(String workflowInstanceId) {
    this.workflowInstanceId = workflowInstanceId;
  }

  public String getActivityId() {
    return activityId;
  }

  public void setActivityId(String activityId) {
    this.activityId = activityId;
  }

  public String getActivityInstanceId() {
    return activityInstanceId;
  }

  public void setActivityInstanceId(String activityInstanceId) {
    this.activityInstanceId = activityInstanceId;
  }

  public EventSourceType getEventSourceType() {
    return eventSourceType;
  }

  public void setEventSourceType(EventSourceType eventSourceType) {
    this.eventSourceType = eventSourceType;
  }

  public EventType getEventType() {
    return eventType;
  }

  public void setEventType(EventType eventType) {
    this.eventType = eventType;
  }

  public String getBpmnProcessId() {
    return bpmnProcessId;
  }

  public void setBpmnProcessId(String bpmnProcessId) {
    this.bpmnProcessId = bpmnProcessId;
  }

  public OffsetDateTime getDateTime() {
    return dateTime;
  }

  public void setDateTime(OffsetDateTime dateTime) {
    this.dateTime = dateTime;
  }

  public String getPayload() {
    return payload;
  }

  public void setPayload(String payload) {
    this.payload = payload;
  }

  public EventMetadataEntity getMetadata() {
    return metadata;
  }

  public void setMetadata(EventMetadataEntity metadata) {
    this.metadata = metadata;
  }

  @Override
  @JsonIgnore
  public Integer getPartitionId() {
    return super.getPartitionId();
  }

  @Override
  @JsonIgnore
  public long getPosition() {
    return super.getPosition();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;
    if (!super.equals(o))
      return false;

    EventEntity that = (EventEntity) o;

    if (workflowId != null ? !workflowId.equals(that.workflowId) : that.workflowId != null)
      return false;
    if (workflowInstanceId != null ? !workflowInstanceId.equals(that.workflowInstanceId) : that.workflowInstanceId != null)
      return false;
    if (bpmnProcessId != null ? !bpmnProcessId.equals(that.bpmnProcessId) : that.bpmnProcessId != null)
      return false;
    if (activityId != null ? !activityId.equals(that.activityId) : that.activityId != null)
      return false;
    if (activityInstanceId != null ? !activityInstanceId.equals(that.activityInstanceId) : that.activityInstanceId != null)
      return false;
    if (eventSourceType != that.eventSourceType)
      return false;
    if (eventType != that.eventType)
      return false;
    if (dateTime != null ? !dateTime.equals(that.dateTime) : that.dateTime != null)
      return false;

    return payload != null ? !payload.equals(that.payload) : that.payload != null;
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + (workflowId != null ? workflowId.hashCode() : 0);
    result = 31 * result + (workflowInstanceId != null ? workflowInstanceId.hashCode() : 0);
    result = 31 * result + (bpmnProcessId != null ? bpmnProcessId.hashCode() : 0);
    result = 31 * result + (activityId != null ? activityId.hashCode() : 0);
    result = 31 * result + (activityInstanceId != null ? activityInstanceId.hashCode() : 0);
    result = 31 * result + (eventSourceType != null ? eventSourceType.hashCode() : 0);
    result = 31 * result + (eventType != null ? eventType.hashCode() : 0);
    result = 31 * result + (dateTime != null ? dateTime.hashCode() : 0);
    result = 31 * result + (payload != null ? payload.hashCode() : 0);
    return result;
  }
}
