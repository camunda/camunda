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
import java.util.Map;

import org.camunda.operate.entities.EventMetadataEntity;

public class EventMetadataDto {

  /**
  * Job data.
  */
  private String jobType;
  private Integer jobRetries;
  private String jobWorker;
  private OffsetDateTime jobDeadline;
  private Map<String, Object> jobCustomHeaders;
  
  /**
  * Incident data.
  */
  private String incidentErrorType;
  private String incidentErrorMessage;
  private String jobKey;

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

  public OffsetDateTime getJobDeadline() {
    return jobDeadline;
  }

  public void setJobDeadline(OffsetDateTime jobDeadline) {
    this.jobDeadline = jobDeadline;
  }

  public Map<String, Object> getJobCustomHeaders() {
    return jobCustomHeaders;
  }

  public void setJobCustomHeaders(Map<String, Object> jobCustomHeaders) {
    this.jobCustomHeaders = jobCustomHeaders;
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

  public String getJobKey() {
    return jobKey;
  }

  public void setJobKey(String jobKey) {
    this.jobKey = jobKey;
  }

  public static EventMetadataDto createFrom(EventMetadataEntity eventMetadataEntity) {
    EventMetadataDto eventMetadata = new EventMetadataDto();
    eventMetadata.setIncidentErrorMessage(eventMetadataEntity.getIncidentErrorMessage());
    eventMetadata.setIncidentErrorType(eventMetadataEntity.getIncidentErrorType());
    eventMetadata.setJobCustomHeaders(eventMetadataEntity.getJobCustomHeaders());
    eventMetadata.setJobDeadline(eventMetadataEntity.getJobDeadline());
    eventMetadata.setJobKey(eventMetadataEntity.getJobKey());
    eventMetadata.setJobRetries(eventMetadataEntity.getJobRetries());
    eventMetadata.setJobType(eventMetadataEntity.getJobType());
    eventMetadata.setJobWorker(eventMetadataEntity.getJobWorker());
    return eventMetadata;
  }

}
