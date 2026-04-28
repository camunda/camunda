/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.entities;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MessageSubscriptionEntity(
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
    String processDefinitionName,
    Integer processDefinitionVersion,
    Map<String, String> extensionProperties,
    String toolName,
    String inboundConnectorType,
    List<InputSpecItem> inputSpecification)
    implements TenantOwnedEntity {

  public MessageSubscriptionEntity {
    // Mutable collections are required: MyBatis hydrates collection-mapped fields (e.g. from a
    // <collection> result map or a LEFT JOIN) by calling .add() on the existing instance.
    // Immutable defaults (e.g. Map.of()) would cause UnsupportedOperationException at runtime.
    extensionProperties = extensionProperties != null ? extensionProperties : new HashMap<>();
    inputSpecification = inputSpecification != null ? inputSpecification : new ArrayList<>();
    // Pre-8.10 rows have no messageSubscriptionType stored; default them to PROCESS_EVENT.
    messageSubscriptionType =
        messageSubscriptionType != null
            ? messageSubscriptionType
            : MessageSubscriptionType.PROCESS_EVENT;
  }

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {
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
    private String processDefinitionName;
    private Integer processDefinitionVersion;
    private Map<String, String> extensionProperties;
    private String toolName;
    private String inboundConnectorType;
    private List<InputSpecItem> inputSpecification;

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

    public Builder extensionProperties(final Map<String, String> extensionProperties) {
      this.extensionProperties = extensionProperties;
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

    public Builder inputSpecification(final List<InputSpecItem> inputSpecification) {
      this.inputSpecification = inputSpecification;
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

    public MessageSubscriptionEntity build() {
      return new MessageSubscriptionEntity(
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
          processDefinitionName,
          processDefinitionVersion,
          extensionProperties,
          toolName,
          inboundConnectorType,
          inputSpecification);
    }
  }

  public enum MessageSubscriptionState {
    CORRELATED,
    CREATED,
    DELETED,
    MIGRATED
  }

  public enum MessageSubscriptionType {
    START_EVENT,
    PROCESS_EVENT
  }

  public record InputSpecItem(
      String name, String description, String type, boolean required, String schema) {}
}
