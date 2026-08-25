/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.domain;

import io.camunda.db.rdbms.write.util.MapSerializer;
import io.camunda.db.rdbms.write.util.TruncateUtil;
import io.camunda.search.entities.MessageSubscriptionEntity.MessageSubscriptionState;
import io.camunda.search.entities.MessageSubscriptionEntity.MessageSubscriptionType;
import io.camunda.util.ObjectBuilder;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

public record MessageSubscriptionDbModel(
    Long messageSubscriptionKey,
    String processDefinitionId,
    Long processDefinitionKey,
    Long processInstanceKey,
    Long rootProcessInstanceKey,
    String flowNodeId,
    Long flowNodeInstanceKey,
    MessageSubscriptionState messageSubscriptionState,
    MessageSubscriptionType messageSubscriptionType,
    OffsetDateTime dateTime,
    String messageName,
    String correlationKey,
    String tenantId,
    int partitionId,
    String processDefinitionName,
    Integer processDefinitionVersion,
    String serializedToolProperties,
    String toolName,
    String inboundConnectorType,
    String businessId)
    implements Copyable<MessageSubscriptionDbModel> {

  public Map<String, String> toolProperties() {
    return MapSerializer.deserialize(serializedToolProperties);
  }

  public MessageSubscriptionDbModel truncateToolFields(
      final int sizeLimit, final Integer byteLimit) {
    final var truncatedToolName = TruncateUtil.truncateValue(toolName, sizeLimit, byteLimit);
    final var truncatedInboundConnectorType =
        TruncateUtil.truncateValue(inboundConnectorType, sizeLimit, byteLimit);
    if (Objects.equals(truncatedToolName, toolName)
        && Objects.equals(truncatedInboundConnectorType, inboundConnectorType)) {
      return this;
    }

    return new MessageSubscriptionDbModel(
        messageSubscriptionKey,
        processDefinitionId,
        processDefinitionKey,
        processInstanceKey,
        rootProcessInstanceKey,
        flowNodeId,
        flowNodeInstanceKey,
        messageSubscriptionState,
        messageSubscriptionType,
        dateTime,
        messageName,
        correlationKey,
        tenantId,
        partitionId,
        processDefinitionName,
        processDefinitionVersion,
        serializedToolProperties,
        truncatedToolName,
        truncatedInboundConnectorType,
        businessId);
  }

  @Override
  public MessageSubscriptionDbModel copy(
      final Function<
              ObjectBuilder<MessageSubscriptionDbModel>, ObjectBuilder<MessageSubscriptionDbModel>>
          copyFunction) {
    return copyFunction.apply(toBuilder()).build();
  }

  public Builder toBuilder() {
    return new Builder(serializedToolProperties)
        .messageSubscriptionKey(messageSubscriptionKey)
        .processDefinitionId(processDefinitionId)
        .processDefinitionKey(processDefinitionKey)
        .processInstanceKey(processInstanceKey)
        .rootProcessInstanceKey(rootProcessInstanceKey)
        .flowNodeId(flowNodeId)
        .flowNodeInstanceKey(flowNodeInstanceKey)
        .messageSubscriptionState(messageSubscriptionState)
        .messageSubscriptionType(messageSubscriptionType)
        .dateTime(dateTime)
        .messageName(messageName)
        .correlationKey(correlationKey)
        .tenantId(tenantId)
        .partitionId(partitionId)
        .processDefinitionName(processDefinitionName)
        .processDefinitionVersion(processDefinitionVersion)
        .toolName(toolName)
        .inboundConnectorType(inboundConnectorType)
        .businessId(businessId);
  }

  public static class Builder implements ObjectBuilder<MessageSubscriptionDbModel> {
    private Long messageSubscriptionKey;
    private String processDefinitionId;
    private Long processDefinitionKey;
    private Long processInstanceKey;
    private Long rootProcessInstanceKey;
    private String flowNodeId;
    private Long flowNodeInstanceKey;
    private MessageSubscriptionState messageSubscriptionState;
    private MessageSubscriptionType messageSubscriptionType;
    private OffsetDateTime dateTime;
    private String messageName;
    private String correlationKey;
    private String tenantId;
    private int partitionId;
    private String processDefinitionName;
    private Integer processDefinitionVersion;
    private String serializedToolProperties;
    private String toolName;
    private String inboundConnectorType;
    private String businessId;

    public Builder() {}

    // Seeds the raw serialized column value for toBuilder()/copy(), so a copy that never touches
    // toolProperties() carries the original string through unparsed instead of round-tripping it
    // through Jackson. Package-private, not a public setter: a second public setter aliasing the
    // same field as toolProperties(Map) would let callers silently drop one write (e.g.
    // builder.serializedToolProperties(x).toolProperties(y) loses x).
    Builder(final String serializedToolProperties) {
      this.serializedToolProperties = serializedToolProperties;
    }

    public Builder messageSubscriptionKey(final Long messageSubscriptionKey) {
      this.messageSubscriptionKey = messageSubscriptionKey;
      return this;
    }

    public Builder processDefinitionId(final String processDefinitionId) {
      this.processDefinitionId = processDefinitionId;
      return this;
    }

    public Builder processDefinitionKey(final Long processDefinitionKey) {
      this.processDefinitionKey = processDefinitionKey;
      return this;
    }

    public Builder processInstanceKey(final Long processInstanceKey) {
      this.processInstanceKey = processInstanceKey;
      return this;
    }

    public Builder rootProcessInstanceKey(final Long rootProcessInstanceKey) {
      this.rootProcessInstanceKey = rootProcessInstanceKey;
      return this;
    }

    public Builder flowNodeId(final String flowNodeId) {
      this.flowNodeId = flowNodeId;
      return this;
    }

    public Builder flowNodeInstanceKey(final Long flowNodeInstanceKey) {
      this.flowNodeInstanceKey = flowNodeInstanceKey;
      return this;
    }

    public Builder messageSubscriptionState(
        final MessageSubscriptionState messageSubscriptionState) {
      this.messageSubscriptionState = messageSubscriptionState;
      return this;
    }

    public Builder messageSubscriptionType(final MessageSubscriptionType messageSubscriptionType) {
      this.messageSubscriptionType = messageSubscriptionType;
      return this;
    }

    public Builder processDefinitionName(final String processDefinitionName) {
      this.processDefinitionName = processDefinitionName;
      return this;
    }

    public Builder processDefinitionVersion(final Integer processDefinitionVersion) {
      this.processDefinitionVersion = processDefinitionVersion;
      return this;
    }

    public Builder toolProperties(final Map<String, String> toolProperties) {
      serializedToolProperties = MapSerializer.serialize(toolProperties);
      return this;
    }

    public Builder toolName(final String toolName) {
      this.toolName = toolName;
      return this;
    }

    public Builder inboundConnectorType(final String inboundConnectorType) {
      this.inboundConnectorType = inboundConnectorType;
      return this;
    }

    public Builder businessId(final String businessId) {
      this.businessId = businessId;
      return this;
    }

    public Builder dateTime(final OffsetDateTime dateTime) {
      this.dateTime = dateTime;
      return this;
    }

    public Builder messageName(final String messageName) {
      this.messageName = messageName;
      return this;
    }

    public Builder correlationKey(final String correlationKey) {
      this.correlationKey = correlationKey;
      return this;
    }

    public Builder tenantId(final String tenantId) {
      this.tenantId = tenantId;
      return this;
    }

    public Builder partitionId(final int partitionId) {
      this.partitionId = partitionId;
      return this;
    }

    @Override
    public MessageSubscriptionDbModel build() {
      return new MessageSubscriptionDbModel(
          messageSubscriptionKey,
          processDefinitionId,
          processDefinitionKey,
          processInstanceKey,
          rootProcessInstanceKey,
          flowNodeId,
          flowNodeInstanceKey,
          messageSubscriptionState,
          messageSubscriptionType,
          dateTime,
          messageName,
          correlationKey,
          tenantId,
          partitionId,
          processDefinitionName,
          processDefinitionVersion,
          serializedToolProperties,
          toolName,
          inboundConnectorType,
          businessId);
    }
  }
}
