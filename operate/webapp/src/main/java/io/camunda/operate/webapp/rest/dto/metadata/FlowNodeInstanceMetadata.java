/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.rest.dto.metadata;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.As;
import io.camunda.webapps.schema.entities.flownode.FlowNodeType;
import java.time.OffsetDateTime;

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = As.PROPERTY,
    property = "flowNodeType",
    defaultImpl = FlowNodeInstanceMetadataDto.class)
@JsonSubTypes({
  @JsonSubTypes.Type(value = UserTaskInstanceMetadataDto.class, name = "USER_TASK"),
  @JsonSubTypes.Type(value = ServiceTaskInstanceMetadataDto.class, name = "SERVICE_TASK"),
  @JsonSubTypes.Type(
      value = BusinessRuleTaskInstanceMetadataDto.class,
      name = "BUSINESS_RULE_TASK"),
  @JsonSubTypes.Type(value = CallActivityInstanceMetadataDto.class, name = "CALL_ACTIVITY")
})
@JsonInclude(Include.NON_NULL)
public interface FlowNodeInstanceMetadata {
  FlowNodeType getFlowNodeType();

  FlowNodeInstanceMetadata setFlowNodeType(final FlowNodeType flowNodeType);

  String getFlowNodeInstanceId();

  FlowNodeInstanceMetadata setFlowNodeInstanceId(final String flowNodeInstanceId);

  String getFlowNodeId();

  FlowNodeInstanceMetadata setFlowNodeId(final String flowNodeId);

  OffsetDateTime getStartDate();

  FlowNodeInstanceMetadata setStartDate(final OffsetDateTime startDate);

  OffsetDateTime getEndDate();

  FlowNodeInstanceMetadata setEndDate(final OffsetDateTime endDate);

  String getEventId();

  FlowNodeInstanceMetadata setEventId(final String eventId);

  String getMessageName();

  FlowNodeInstanceMetadataDto setMessageName(final String messageName);

  String getCorrelationKey();

  FlowNodeInstanceMetadataDto setCorrelationKey(final String correlationKey);
}
