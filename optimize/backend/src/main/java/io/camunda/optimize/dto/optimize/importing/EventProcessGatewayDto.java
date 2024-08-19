/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.importing;

import java.util.List;

public class EventProcessGatewayDto {

  private final String id;
  private final String type;
  private final List<String> previousNodeIds;
  private final List<String> nextNodeIds;

  EventProcessGatewayDto(
      final String id,
      final String type,
      final List<String> previousNodeIds,
      final List<String> nextNodeIds) {
    this.id = id;
    this.type = type;
    this.previousNodeIds = previousNodeIds;
    this.nextNodeIds = nextNodeIds;
  }

  public String getId() {
    return id;
  }

  public String getType() {
    return type;
  }

  public List<String> getPreviousNodeIds() {
    return previousNodeIds;
  }

  public List<String> getNextNodeIds() {
    return nextNodeIds;
  }

  public static EventProcessGatewayDtoBuilder builder() {
    return new EventProcessGatewayDtoBuilder();
  }

  public static class EventProcessGatewayDtoBuilder {

    private String id;
    private String type;
    private List<String> previousNodeIds;
    private List<String> nextNodeIds;

    EventProcessGatewayDtoBuilder() {}

    public EventProcessGatewayDtoBuilder id(final String id) {
      this.id = id;
      return this;
    }

    public EventProcessGatewayDtoBuilder type(final String type) {
      this.type = type;
      return this;
    }

    public EventProcessGatewayDtoBuilder previousNodeIds(final List<String> previousNodeIds) {
      this.previousNodeIds = previousNodeIds;
      return this;
    }

    public EventProcessGatewayDtoBuilder nextNodeIds(final List<String> nextNodeIds) {
      this.nextNodeIds = nextNodeIds;
      return this;
    }

    public EventProcessGatewayDto build() {
      return new EventProcessGatewayDto(id, type, previousNodeIds, nextNodeIds);
    }

    @Override
    public String toString() {
      return "EventProcessGatewayDto.EventProcessGatewayDtoBuilder(id="
          + id
          + ", type="
          + type
          + ", previousNodeIds="
          + previousNodeIds
          + ", nextNodeIds="
          + nextNodeIds
          + ")";
    }
  }
}
