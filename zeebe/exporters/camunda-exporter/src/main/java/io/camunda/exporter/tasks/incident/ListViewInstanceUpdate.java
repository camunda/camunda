/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.incident;

import io.camunda.webapps.schema.descriptors.template.ListViewTemplate;
import java.util.Map;
import java.util.Objects;

public record ListViewInstanceUpdate(String id, String index, String routing, boolean hasIncident)
    implements IncidentTaskUpdate {

  @Override
  public Map<String, Object> doc() {
    return Map.of(ListViewTemplate.INCIDENT, hasIncident);
  }

  public static Builder id(final String id) {
    return new Builder(id);
  }

  public static final class Builder
      extends IncidentTaskUpdate.Builder<ListViewInstanceUpdate, Builder> {
    private String routing;
    private Boolean hasIncident;

    private Builder(final String id) {
      super(id);
    }

    public Builder routing(final String routing) {
      this.routing = routing;
      return this;
    }

    public Builder hasIncident(final boolean hasIncident) {
      this.hasIncident = hasIncident;
      return this;
    }

    @Override
    public ListViewInstanceUpdate build() {
      return new ListViewInstanceUpdate(id, index, routing, Objects.requireNonNull(hasIncident));
    }
  }
}
