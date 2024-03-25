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
package io.camunda.operate.webapp.rest.dto.metadata;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import io.camunda.operate.entities.EventEntity;
import io.camunda.operate.entities.EventMetadataEntity;
import io.camunda.operate.entities.FlowNodeType;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Objects;

@JsonInclude(Include.NON_NULL)
public class JobFlowNodeInstanceMetadataDto extends FlowNodeInstanceMetadataDto
    implements FlowNodeInstanceMetadata {

  private OffsetDateTime jobDeadline;

  /** Job data. */
  private String jobType;

  private Integer jobRetries;
  private String jobWorker;
  private Map<String, String> jobCustomHeaders;

  public JobFlowNodeInstanceMetadataDto(
      final String flowNodeId,
      final String flowNodeInstanceId,
      final FlowNodeType flowNodeType,
      final OffsetDateTime startDate,
      final OffsetDateTime endDate,
      final EventEntity event) {
    super(flowNodeId, flowNodeInstanceId, flowNodeType, startDate, endDate, event);
    final EventMetadataEntity eventMetadataEntity = event.getMetadata();
    if (eventMetadataEntity != null) {
      setJobCustomHeaders(eventMetadataEntity.getJobCustomHeaders())
          .setJobDeadline(eventMetadataEntity.getJobDeadline())
          .setJobRetries(eventMetadataEntity.getJobRetries())
          .setJobType(eventMetadataEntity.getJobType())
          .setJobWorker(eventMetadataEntity.getJobWorker());
    }
  }

  public JobFlowNodeInstanceMetadataDto() {
    super();
  }

  public String getJobType() {
    return jobType;
  }

  public JobFlowNodeInstanceMetadataDto setJobType(final String jobType) {
    this.jobType = jobType;
    return this;
  }

  public Integer getJobRetries() {
    return jobRetries;
  }

  public JobFlowNodeInstanceMetadataDto setJobRetries(final Integer jobRetries) {
    this.jobRetries = jobRetries;
    return this;
  }

  public String getJobWorker() {
    return jobWorker;
  }

  public JobFlowNodeInstanceMetadataDto setJobWorker(final String jobWorker) {
    this.jobWorker = jobWorker;
    return this;
  }

  public OffsetDateTime getJobDeadline() {
    return jobDeadline;
  }

  public JobFlowNodeInstanceMetadataDto setJobDeadline(final OffsetDateTime jobDeadline) {
    this.jobDeadline = jobDeadline;
    return this;
  }

  public Map<String, String> getJobCustomHeaders() {
    return jobCustomHeaders;
  }

  public JobFlowNodeInstanceMetadataDto setJobCustomHeaders(
      final Map<String, String> jobCustomHeaders) {
    this.jobCustomHeaders = jobCustomHeaders;
    return this;
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        super.hashCode(), jobDeadline, jobType, jobRetries, jobWorker, jobCustomHeaders);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }
    final JobFlowNodeInstanceMetadataDto that = (JobFlowNodeInstanceMetadataDto) o;
    return Objects.equals(jobDeadline, that.jobDeadline)
        && Objects.equals(jobType, that.jobType)
        && Objects.equals(jobRetries, that.jobRetries)
        && Objects.equals(jobWorker, that.jobWorker)
        && Objects.equals(jobCustomHeaders, that.jobCustomHeaders);
  }

  @Override
  public String toString() {
    return "JobFlowNodeInstanceMetadataDto{"
        + "jobDeadline="
        + jobDeadline
        + ", jobType='"
        + jobType
        + '\''
        + ", jobRetries="
        + jobRetries
        + ", jobWorker='"
        + jobWorker
        + '\''
        + ", jobCustomHeaders="
        + jobCustomHeaders
        + "} "
        + super.toString();
  }
}
