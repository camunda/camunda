/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE (“USE”), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
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
package io.camunda.operate.entities;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Objects;

public class EventMetadataEntity {

  /** Job data. */
  private String jobType;

  private Integer jobRetries;
  private String jobWorker;
  private OffsetDateTime jobDeadline;
  private Map<String, String> jobCustomHeaders;
  private Long jobKey;

  /** Incident data. */
  private ErrorType incidentErrorType;

  private String incidentErrorMessage;

  /** Message data. */
  private String messageName;

  private String correlationKey;

  public String getJobType() {
    return jobType;
  }

  public EventMetadataEntity setJobType(final String jobType) {
    this.jobType = jobType;
    return this;
  }

  public Integer getJobRetries() {
    return jobRetries;
  }

  public EventMetadataEntity setJobRetries(final Integer jobRetries) {
    this.jobRetries = jobRetries;
    return this;
  }

  public String getJobWorker() {
    return jobWorker;
  }

  public EventMetadataEntity setJobWorker(final String jobWorker) {
    this.jobWorker = jobWorker;
    return this;
  }

  public OffsetDateTime getJobDeadline() {
    return jobDeadline;
  }

  public EventMetadataEntity setJobDeadline(final OffsetDateTime jobDeadline) {
    this.jobDeadline = jobDeadline;
    return this;
  }

  public Map<String, String> getJobCustomHeaders() {
    return jobCustomHeaders;
  }

  public EventMetadataEntity setJobCustomHeaders(final Map<String, String> jobCustomHeaders) {
    this.jobCustomHeaders = jobCustomHeaders;
    return this;
  }

  public Long getJobKey() {
    return jobKey;
  }

  public void setJobKey(final Long jobKey) {
    this.jobKey = jobKey;
  }

  public ErrorType getIncidentErrorType() {
    return incidentErrorType;
  }

  public void setIncidentErrorType(final ErrorType incidentErrorType) {
    this.incidentErrorType = incidentErrorType;
  }

  public String getIncidentErrorMessage() {
    return incidentErrorMessage;
  }

  public void setIncidentErrorMessage(final String incidentErrorMessage) {
    this.incidentErrorMessage = incidentErrorMessage;
  }

  public String getMessageName() {
    return messageName;
  }

  public EventMetadataEntity setMessageName(final String messageName) {
    this.messageName = messageName;
    return this;
  }

  public String getCorrelationKey() {
    return correlationKey;
  }

  public EventMetadataEntity setCorrelationKey(final String correlationKey) {
    this.correlationKey = correlationKey;
    return this;
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        jobType,
        jobRetries,
        jobWorker,
        jobDeadline,
        jobCustomHeaders,
        jobKey,
        incidentErrorType,
        incidentErrorMessage,
        messageName,
        correlationKey);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final EventMetadataEntity that = (EventMetadataEntity) o;
    return Objects.equals(jobType, that.jobType)
        && Objects.equals(jobRetries, that.jobRetries)
        && Objects.equals(jobWorker, that.jobWorker)
        && Objects.equals(jobDeadline, that.jobDeadline)
        && Objects.equals(jobCustomHeaders, that.jobCustomHeaders)
        && Objects.equals(jobKey, that.jobKey)
        && incidentErrorType == that.incidentErrorType
        && Objects.equals(incidentErrorMessage, that.incidentErrorMessage)
        && Objects.equals(messageName, that.messageName)
        && Objects.equals(correlationKey, that.correlationKey);
  }
}
