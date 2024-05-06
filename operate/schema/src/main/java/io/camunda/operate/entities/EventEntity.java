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

import static io.camunda.operate.schema.indices.IndexDescriptor.DEFAULT_TENANT_ID;

import java.time.OffsetDateTime;
import java.util.Objects;

public class EventEntity extends OperateZeebeEntity<EventEntity> {

  /** Process data. */
  private Long processDefinitionKey;

  private Long processInstanceKey;
  private String bpmnProcessId;

  /** Activity data. */
  private String flowNodeId;

  private Long flowNodeInstanceKey;

  /** Event data. */
  private EventSourceType eventSourceType;

  private EventType eventType;
  private OffsetDateTime dateTime;

  /** Metadata */
  private EventMetadataEntity metadata;

  private String tenantId = DEFAULT_TENANT_ID;

  private Long position;
  private Long positionIncident;
  private Long positionProcessMessageSubscription;
  private Long positionJob;

  public Long getProcessDefinitionKey() {
    return processDefinitionKey;
  }

  public EventEntity setProcessDefinitionKey(final Long processDefinitionKey) {
    this.processDefinitionKey = processDefinitionKey;
    return this;
  }

  public Long getProcessInstanceKey() {
    return processInstanceKey;
  }

  public EventEntity setProcessInstanceKey(final Long processInstanceKey) {
    this.processInstanceKey = processInstanceKey;
    return this;
  }

  public String getBpmnProcessId() {
    return bpmnProcessId;
  }

  public EventEntity setBpmnProcessId(final String bpmnProcessId) {
    this.bpmnProcessId = bpmnProcessId;
    return this;
  }

  public String getFlowNodeId() {
    return flowNodeId;
  }

  public EventEntity setFlowNodeId(final String flowNodeId) {
    this.flowNodeId = flowNodeId;
    return this;
  }

  public Long getFlowNodeInstanceKey() {
    return flowNodeInstanceKey;
  }

  public EventEntity setFlowNodeInstanceKey(final Long flowNodeInstanceKey) {
    this.flowNodeInstanceKey = flowNodeInstanceKey;
    return this;
  }

  public EventSourceType getEventSourceType() {
    return eventSourceType;
  }

  public EventEntity setEventSourceType(final EventSourceType eventSourceType) {
    this.eventSourceType = eventSourceType;
    return this;
  }

  public EventType getEventType() {
    return eventType;
  }

  public EventEntity setEventType(final EventType eventType) {
    this.eventType = eventType;
    return this;
  }

  public OffsetDateTime getDateTime() {
    return dateTime;
  }

  public EventEntity setDateTime(final OffsetDateTime dateTime) {
    this.dateTime = dateTime;
    return this;
  }

  public EventMetadataEntity getMetadata() {
    return metadata;
  }

  public EventEntity setMetadata(final EventMetadataEntity metadata) {
    this.metadata = metadata;
    return this;
  }

  public String getTenantId() {
    return tenantId;
  }

  public EventEntity setTenantId(final String tenantId) {
    this.tenantId = tenantId;
    return this;
  }

  public Long getPosition() {
    return position;
  }

  public EventEntity setPosition(final Long position) {
    this.position = position;
    return this;
  }

  public Long getPositionIncident() {
    return positionIncident;
  }

  public EventEntity setPositionIncident(final Long positionIncident) {
    this.positionIncident = positionIncident;
    return this;
  }

  public Long getPositionProcessMessageSubscription() {
    return positionProcessMessageSubscription;
  }

  public EventEntity setPositionProcessMessageSubscription(
      final Long positionProcessMessageSubscription) {
    this.positionProcessMessageSubscription = positionProcessMessageSubscription;
    return this;
  }

  public Long getPositionJob() {
    return positionJob;
  }

  public EventEntity setPositionJob(final Long positionJob) {
    this.positionJob = positionJob;
    return this;
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        super.hashCode(),
        processDefinitionKey,
        processInstanceKey,
        bpmnProcessId,
        flowNodeId,
        flowNodeInstanceKey,
        eventSourceType,
        eventType,
        dateTime,
        metadata,
        tenantId,
        position,
        positionIncident,
        positionProcessMessageSubscription,
        positionJob);
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
    final EventEntity that = (EventEntity) o;
    return Objects.equals(processDefinitionKey, that.processDefinitionKey)
        && Objects.equals(processInstanceKey, that.processInstanceKey)
        && Objects.equals(bpmnProcessId, that.bpmnProcessId)
        && Objects.equals(flowNodeId, that.flowNodeId)
        && Objects.equals(flowNodeInstanceKey, that.flowNodeInstanceKey)
        && eventSourceType == that.eventSourceType
        && eventType == that.eventType
        && Objects.equals(dateTime, that.dateTime)
        && Objects.equals(metadata, that.metadata)
        && Objects.equals(tenantId, that.tenantId)
        && Objects.equals(position, that.position)
        && Objects.equals(positionIncident, that.positionIncident)
        && Objects.equals(
            positionProcessMessageSubscription, that.positionProcessMessageSubscription)
        && Objects.equals(positionJob, that.positionJob);
  }
}
