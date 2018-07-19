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
import org.camunda.operate.entities.EventMetadataEntity;
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

  private EventMetadataDto metadata;

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

  public EventMetadataDto getMetadata() {
    return metadata;
  }

  public void setMetadata(EventMetadataDto metadata) {
    this.metadata = metadata;
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
    eventDto.setPayload(eventEntity.getPayload());
    eventDto.setWorkflowId(eventEntity.getWorkflowId());
    eventDto.setWorkflowInstanceId(eventEntity.getWorkflowInstanceId());

    EventMetadataEntity eventMetadataEntity = eventEntity.getMetadata();
    if (eventMetadataEntity != null) {
      EventMetadataDto eventMetadataDto = EventMetadataDto.createFrom(eventMetadataEntity);
      eventDto.setMetadata(eventMetadataDto);
    }

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

}
