/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.schema.entities.messagesubscription;

import io.camunda.webapps.schema.entities.ExporterEntity;
import io.camunda.webapps.schema.entities.PartitionedEntity;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import java.time.OffsetDateTime;
import java.util.Objects;

public class MessageSubscriptionEntity
    implements ExporterEntity<MessageSubscriptionEntity>,
        PartitionedEntity<MessageSubscriptionEntity>,
        TenantOwned {

  private String id;
  private long key;
  private int partitionId;

  /** Process data. */
  private Long processDefinitionKey;

  private Long processInstanceKey;
  private String bpmnProcessId;

  /** Activity data. */
  private String flowNodeId;

  private Long flowNodeInstanceKey;

  private MessageSubscriptionState messageSubscriptionState;
  private OffsetDateTime dateTime;

  private MessageSubscriptionMetadataEntity metadata;

  private String tenantId = DEFAULT_TENANT_IDENTIFIER;

  private Long positionProcessMessageSubscription;

  /**
   * @deprecated since 8.9
   */
  @Deprecated private EventSourceType eventSourceType;

  /**
   * @deprecated since 8.9
   */
  @Deprecated private Long position;

  /**
   * @deprecated since 8.9
   */
  @Deprecated private Long positionIncident;

  /**
   * @deprecated since 8.9
   */
  @Deprecated private Long positionJob;

  @Override
  public String getId() {
    return id;
  }

  @Override
  public MessageSubscriptionEntity setId(final String id) {
    this.id = id;
    return this;
  }

  public long getKey() {
    return key;
  }

  public MessageSubscriptionEntity setKey(final long key) {
    this.key = key;
    return this;
  }

  @Override
  public int getPartitionId() {
    return partitionId;
  }

  @Override
  public MessageSubscriptionEntity setPartitionId(final int partitionId) {
    this.partitionId = partitionId;
    return this;
  }

  public Long getProcessDefinitionKey() {
    return processDefinitionKey;
  }

  public MessageSubscriptionEntity setProcessDefinitionKey(final Long processDefinitionKey) {
    this.processDefinitionKey = processDefinitionKey;
    return this;
  }

  public Long getProcessInstanceKey() {
    return processInstanceKey;
  }

  public MessageSubscriptionEntity setProcessInstanceKey(final Long processInstanceKey) {
    this.processInstanceKey = processInstanceKey;
    return this;
  }

  public String getBpmnProcessId() {
    return bpmnProcessId;
  }

  public MessageSubscriptionEntity setBpmnProcessId(final String bpmnProcessId) {
    this.bpmnProcessId = bpmnProcessId;
    return this;
  }

  public String getFlowNodeId() {
    return flowNodeId;
  }

  public MessageSubscriptionEntity setFlowNodeId(final String flowNodeId) {
    this.flowNodeId = flowNodeId;
    return this;
  }

  public Long getFlowNodeInstanceKey() {
    return flowNodeInstanceKey;
  }

  public MessageSubscriptionEntity setFlowNodeInstanceKey(final Long flowNodeInstanceKey) {
    this.flowNodeInstanceKey = flowNodeInstanceKey;
    return this;
  }

  public MessageSubscriptionState getMessageSubscriptionState() {
    return messageSubscriptionState;
  }

  public MessageSubscriptionEntity setMessageSubscriptionState(
      final MessageSubscriptionState messageSubscriptionState) {
    this.messageSubscriptionState = messageSubscriptionState;
    return this;
  }

  public OffsetDateTime getDateTime() {
    return dateTime;
  }

  public MessageSubscriptionEntity setDateTime(final OffsetDateTime dateTime) {
    this.dateTime = dateTime;
    return this;
  }

  public MessageSubscriptionMetadataEntity getMetadata() {
    return metadata;
  }

  public MessageSubscriptionEntity setMetadata(final MessageSubscriptionMetadataEntity metadata) {
    this.metadata = metadata;
    return this;
  }

  @Override
  public String getTenantId() {
    return tenantId;
  }

  public MessageSubscriptionEntity setTenantId(final String tenantId) {
    this.tenantId = tenantId;
    return this;
  }

  public Long getPositionProcessMessageSubscription() {
    return positionProcessMessageSubscription;
  }

  public MessageSubscriptionEntity setPositionProcessMessageSubscription(
      final Long positionProcessMessageSubscription) {
    this.positionProcessMessageSubscription = positionProcessMessageSubscription;
    return this;
  }

  /**
   * @deprecated since 8.9
   */
  @Deprecated
  public EventSourceType getEventSourceType() {
    return eventSourceType;
  }

  /**
   * @deprecated since 8.9
   */
  @Deprecated
  public MessageSubscriptionEntity setEventSourceType(final EventSourceType eventSourceType) {
    this.eventSourceType = eventSourceType;
    return this;
  }

  /**
   * @deprecated since 8.9
   */
  @Deprecated
  public Long getPosition() {
    return position;
  }

  /**
   * @deprecated since 8.9
   */
  @Deprecated
  public MessageSubscriptionEntity setPosition(final Long position) {
    this.position = position;
    return this;
  }

  /**
   * @deprecated since 8.9
   */
  @Deprecated
  public Long getPositionIncident() {
    return positionIncident;
  }

  /**
   * @deprecated since 8.9
   */
  @Deprecated
  public MessageSubscriptionEntity setPositionIncident(final Long positionIncident) {
    this.positionIncident = positionIncident;
    return this;
  }

  /**
   * @deprecated since 8.9
   */
  @Deprecated
  public Long getPositionJob() {
    return positionJob;
  }

  /**
   * @deprecated since 8.9
   */
  @Deprecated
  public MessageSubscriptionEntity setPositionJob(final Long positionJob) {
    this.positionJob = positionJob;
    return this;
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        id,
        key,
        partitionId,
        processDefinitionKey,
        processInstanceKey,
        bpmnProcessId,
        flowNodeId,
        flowNodeInstanceKey,
        eventSourceType,
        messageSubscriptionState,
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
    final MessageSubscriptionEntity that = (MessageSubscriptionEntity) o;
    return Objects.equals(id, that.id)
        && key == that.key
        && partitionId == that.partitionId
        && Objects.equals(processDefinitionKey, that.processDefinitionKey)
        && Objects.equals(processInstanceKey, that.processInstanceKey)
        && Objects.equals(bpmnProcessId, that.bpmnProcessId)
        && Objects.equals(flowNodeId, that.flowNodeId)
        && Objects.equals(flowNodeInstanceKey, that.flowNodeInstanceKey)
        && eventSourceType == that.eventSourceType
        && messageSubscriptionState == that.messageSubscriptionState
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
