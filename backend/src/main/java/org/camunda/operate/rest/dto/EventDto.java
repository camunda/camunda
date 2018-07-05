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

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import org.camunda.operate.entities.EventEntity;
import org.camunda.operate.entities.EventSourceType;
import org.camunda.operate.entities.EventType;

public class EventDto {

  private String id;

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
   * Job data.
   */
  private String jobType;
  private Integer jobRetries;
  private String jobWorker;

  /**
   * Incident data.
   */
  private String incidentErrorType;
  private String incidentErrorMessage;

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

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

  public String getBpmnProcessId() {
    return bpmnProcessId;
  }

  public void setBpmnProcessId(String bpmnProcessId) {
    this.bpmnProcessId = bpmnProcessId;
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

  public String getJobType() {
    return jobType;
  }

  public void setJobType(String jobType) {
    this.jobType = jobType;
  }

  public Integer getJobRetries() {
    return jobRetries;
  }

  public void setJobRetries(Integer jobRetries) {
    this.jobRetries = jobRetries;
  }

  public String getJobWorker() {
    return jobWorker;
  }

  public void setJobWorker(String jobWorker) {
    this.jobWorker = jobWorker;
  }

  public String getIncidentErrorType() {
    return incidentErrorType;
  }

  public void setIncidentErrorType(String incidentErrorType) {
    this.incidentErrorType = incidentErrorType;
  }

  public String getIncidentErrorMessage() {
    return incidentErrorMessage;
  }

  public void setIncidentErrorMessage(String incidentErrorMessage) {
    this.incidentErrorMessage = incidentErrorMessage;
  }

  public static EventDto createFrom(EventEntity eventEntity) {
    EventDto eventDto = new EventDto();
    eventDto.setId(eventEntity.getId());
    eventDto.setActivityId(eventEntity.getActivityId());
    eventDto.setActivityInstanceId(eventEntity.getActivityInstanceId());
    eventDto.setBpmnProcessId(eventEntity.getBpmnProcessId());
    eventDto.setDateTime(eventEntity.getDateTime());
    eventDto.setEventSourceType(eventEntity.getEventSourceType());
    eventDto.setEventType(eventEntity.getEventType());
    eventDto.setIncidentErrorMessage(eventEntity.getIncidentErrorMessage());
    eventDto.setIncidentErrorType(eventEntity.getIncidentErrorType());
    eventDto.setJobRetries(eventEntity.getJobRetries());
    eventDto.setJobType(eventEntity.getJobType());
    eventDto.setJobWorker(eventEntity.getJobWorker());
    eventDto.setPayload(eventEntity.getPayload());
    eventDto.setWorkflowId(eventEntity.getWorkflowId());
    eventDto.setWorkflowInstanceId(eventEntity.getWorkflowInstanceId());
    return eventDto;
  }

  public static List<EventDto> createFrom(List<EventEntity> eventEntities) {
    List<EventDto> result = new ArrayList<>();
    if (eventEntities != null) {
      for (EventEntity eventEntity: eventEntities) {
        if (eventEntity != null) {
          result.add(createFrom(eventEntity));
        }
      }
    }
    return result;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;

    EventDto eventDto = (EventDto) o;

    if (id != null ? !id.equals(eventDto.id) : eventDto.id != null)
      return false;
    if (workflowId != null ? !workflowId.equals(eventDto.workflowId) : eventDto.workflowId != null)
      return false;
    if (workflowInstanceId != null ? !workflowInstanceId.equals(eventDto.workflowInstanceId) : eventDto.workflowInstanceId != null)
      return false;
    if (bpmnProcessId != null ? !bpmnProcessId.equals(eventDto.bpmnProcessId) : eventDto.bpmnProcessId != null)
      return false;
    if (activityId != null ? !activityId.equals(eventDto.activityId) : eventDto.activityId != null)
      return false;
    if (activityInstanceId != null ? !activityInstanceId.equals(eventDto.activityInstanceId) : eventDto.activityInstanceId != null)
      return false;
    if (eventSourceType != eventDto.eventSourceType)
      return false;
    if (eventType != eventDto.eventType)
      return false;
    if (dateTime != null ? !dateTime.equals(eventDto.dateTime) : eventDto.dateTime != null)
      return false;
    if (payload != null ? !payload.equals(eventDto.payload) : eventDto.payload != null)
      return false;
    if (jobType != null ? !jobType.equals(eventDto.jobType) : eventDto.jobType != null)
      return false;
    if (jobRetries != null ? !jobRetries.equals(eventDto.jobRetries) : eventDto.jobRetries != null)
      return false;
    if (jobWorker != null ? !jobWorker.equals(eventDto.jobWorker) : eventDto.jobWorker != null)
      return false;
    if (incidentErrorType != null ? !incidentErrorType.equals(eventDto.incidentErrorType) : eventDto.incidentErrorType != null)
      return false;
    return incidentErrorMessage != null ? incidentErrorMessage.equals(eventDto.incidentErrorMessage) : eventDto.incidentErrorMessage == null;
  }

  @Override
  public int hashCode() {
    int result = id != null ? id.hashCode() : 0;
    result = 31 * result + (workflowId != null ? workflowId.hashCode() : 0);
    result = 31 * result + (workflowInstanceId != null ? workflowInstanceId.hashCode() : 0);
    result = 31 * result + (bpmnProcessId != null ? bpmnProcessId.hashCode() : 0);
    result = 31 * result + (activityId != null ? activityId.hashCode() : 0);
    result = 31 * result + (activityInstanceId != null ? activityInstanceId.hashCode() : 0);
    result = 31 * result + (eventSourceType != null ? eventSourceType.hashCode() : 0);
    result = 31 * result + (eventType != null ? eventType.hashCode() : 0);
    result = 31 * result + (dateTime != null ? dateTime.hashCode() : 0);
    result = 31 * result + (payload != null ? payload.hashCode() : 0);
    result = 31 * result + (jobType != null ? jobType.hashCode() : 0);
    result = 31 * result + (jobRetries != null ? jobRetries.hashCode() : 0);
    result = 31 * result + (jobWorker != null ? jobWorker.hashCode() : 0);
    result = 31 * result + (incidentErrorType != null ? incidentErrorType.hashCode() : 0);
    result = 31 * result + (incidentErrorMessage != null ? incidentErrorMessage.hashCode() : 0);
    return result;
  }
}
