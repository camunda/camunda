/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.schema.entities;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

public class AlertDefinitionEntity extends AbstractExporterEntity<AlertDefinitionEntity> {

  @JsonInclude(JsonInclude.Include.NON_NULL)
  private Channel channel;

  @JsonInclude(JsonInclude.Include.NON_NULL)
  private List<Filter> filters;

  public Channel getChannel() {
    return channel;
  }

  public void setChannel(final Channel channel) {
    this.channel = channel;
  }

  public List<Filter> getFilters() {
    return filters;
  }

  public void setFilters(final List<Filter> filters) {
    this.filters = filters;
  }

  public record Channel(String type, String value) {}

  public record Filter(String processDefinitionKey) {}
}
