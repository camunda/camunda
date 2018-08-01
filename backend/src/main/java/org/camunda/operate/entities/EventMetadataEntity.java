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

public class EventMetadataEntity {

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
  private String jobId;

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

  public String getJobId() {
    return jobId;
  }

  public void setJobId(String jobId) {
    this.jobId = jobId;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;

    EventMetadataEntity that = (EventMetadataEntity) o;

    if (jobType != null ? !jobType.equals(that.jobType) : that.jobType != null)
      return false;
    if (jobRetries != null ? !jobRetries.equals(that.jobRetries) : that.jobRetries != null)
      return false;
    if (jobWorker != null ? !jobWorker.equals(that.jobWorker) : that.jobWorker != null)
      return false;
    if (jobDeadline != null ? !jobDeadline.equals(that.jobDeadline) : that.jobDeadline != null)
      return false;
    if (jobCustomHeaders != null ? !jobCustomHeaders.equals(that.jobCustomHeaders) : that.jobCustomHeaders != null)
      return false;
    if (incidentErrorType != null ? !incidentErrorType.equals(that.incidentErrorType) : that.incidentErrorType != null)
      return false;
    if (incidentErrorMessage != null ? !incidentErrorMessage.equals(that.incidentErrorMessage) : that.incidentErrorMessage != null)
      return false;
    return jobId != null ? jobId.equals(that.jobId) : that.jobId == null;
  }

  @Override
  public int hashCode() {
    int result = jobType != null ? jobType.hashCode() : 0;
    result = 31 * result + (jobRetries != null ? jobRetries.hashCode() : 0);
    result = 31 * result + (jobWorker != null ? jobWorker.hashCode() : 0);
    result = 31 * result + (jobDeadline != null ? jobDeadline.hashCode() : 0);
    result = 31 * result + (jobCustomHeaders != null ? jobCustomHeaders.hashCode() : 0);
    result = 31 * result + (incidentErrorType != null ? incidentErrorType.hashCode() : 0);
    result = 31 * result + (incidentErrorMessage != null ? incidentErrorMessage.hashCode() : 0);
    result = 31 * result + (jobId != null ? jobId.hashCode() : 0);
    return result;
  }
}
