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

import io.camunda.operate.entities.EventEntity;
import io.camunda.operate.entities.FlowNodeType;
import java.time.OffsetDateTime;
import java.util.Objects;

public class FlowNodeInstanceMetadataDto implements FlowNodeInstanceMetadata {
  private String flowNodeId;

  private String flowNodeInstanceId;
  private FlowNodeType flowNodeType;
  private OffsetDateTime startDate;
  private OffsetDateTime endDate;
  private String eventId;

  private String messageName;
  private String correlationKey;

  public FlowNodeInstanceMetadataDto(
      final String flowNodeId,
      final String flowNodeInstanceId,
      final FlowNodeType flowNodeType,
      final OffsetDateTime startDate,
      final OffsetDateTime endDate,
      final EventEntity event) {
    this.flowNodeId = flowNodeId;
    this.flowNodeInstanceId = flowNodeInstanceId;
    this.flowNodeType = flowNodeType;
    this.startDate = startDate;
    this.endDate = endDate;
    eventId = event.getId();
    final var eventMetadata = event.getMetadata();
    if (eventMetadata != null) {
      messageName = eventMetadata.getMessageName();
      correlationKey = eventMetadata.getCorrelationKey();
    }
  }

  public FlowNodeInstanceMetadataDto() {}

  @Override
  public FlowNodeType getFlowNodeType() {
    return flowNodeType;
  }

  @Override
  public FlowNodeInstanceMetadataDto setFlowNodeType(final FlowNodeType flowNodeType) {
    this.flowNodeType = flowNodeType;
    return this;
  }

  @Override
  public String getFlowNodeInstanceId() {
    return flowNodeInstanceId;
  }

  @Override
  public FlowNodeInstanceMetadataDto setFlowNodeInstanceId(final String flowNodeInstanceId) {
    this.flowNodeInstanceId = flowNodeInstanceId;
    return this;
  }

  @Override
  public String getFlowNodeId() {
    return flowNodeId;
  }

  @Override
  public FlowNodeInstanceMetadataDto setFlowNodeId(final String flowNodeId) {
    this.flowNodeId = flowNodeId;
    return this;
  }

  @Override
  public OffsetDateTime getStartDate() {
    return startDate;
  }

  @Override
  public FlowNodeInstanceMetadataDto setStartDate(final OffsetDateTime startDate) {
    this.startDate = startDate;
    return this;
  }

  @Override
  public OffsetDateTime getEndDate() {
    return endDate;
  }

  @Override
  public FlowNodeInstanceMetadataDto setEndDate(final OffsetDateTime endDate) {
    this.endDate = endDate;
    return this;
  }

  @Override
  public String getEventId() {
    return eventId;
  }

  @Override
  public FlowNodeInstanceMetadataDto setEventId(final String eventId) {
    this.eventId = eventId;
    return this;
  }

  @Override
  public String getMessageName() {
    return messageName;
  }

  @Override
  public FlowNodeInstanceMetadataDto setMessageName(final String messageName) {
    this.messageName = messageName;
    return this;
  }

  @Override
  public String getCorrelationKey() {
    return correlationKey;
  }

  @Override
  public FlowNodeInstanceMetadataDto setCorrelationKey(final String correlationKey) {
    this.correlationKey = correlationKey;
    return this;
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        flowNodeId,
        flowNodeInstanceId,
        flowNodeType,
        startDate,
        endDate,
        eventId,
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
    final FlowNodeInstanceMetadataDto that = (FlowNodeInstanceMetadataDto) o;
    return Objects.equals(flowNodeId, that.flowNodeId)
        && Objects.equals(flowNodeInstanceId, that.flowNodeInstanceId)
        && flowNodeType == that.flowNodeType
        && Objects.equals(startDate, that.startDate)
        && Objects.equals(endDate, that.endDate)
        && Objects.equals(eventId, that.eventId)
        && Objects.equals(messageName, that.messageName)
        && Objects.equals(correlationKey, that.correlationKey);
  }

  @Override
  public String toString() {
    return "FlowNodeInstanceMetadataDto{"
        + "flowNodeId='"
        + flowNodeId
        + '\''
        + ", flowNodeInstanceId='"
        + flowNodeInstanceId
        + '\''
        + ", flowNodeType="
        + flowNodeType
        + ", startDate="
        + startDate
        + ", endDate="
        + endDate
        + ", eventId='"
        + eventId
        + '\''
        + ", messageName='"
        + messageName
        + '\''
        + ", correlationKey='"
        + correlationKey
        + '\''
        + '}';
  }
}
