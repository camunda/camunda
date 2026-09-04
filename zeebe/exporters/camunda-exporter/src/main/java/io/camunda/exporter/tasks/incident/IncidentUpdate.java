/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.incident;

import io.camunda.webapps.schema.descriptors.template.IncidentTemplate;
import io.camunda.webapps.schema.entities.incident.IncidentState;
import java.util.HashMap;
import java.util.Map;

public record IncidentUpdate(String id, String index, IncidentState state, String treePath)
    implements IncidentTaskUpdate {

  @Override
  public Map<String, Object> doc() {
    final Map<String, Object> fields = new HashMap<>();
    fields.put(IncidentTemplate.STATE, state);
    if (treePath != null) {
      fields.put(IncidentTemplate.TREE_PATH, treePath);
    }
    return fields;
  }

  public static Builder id(final String id) {
    return new Builder(id);
  }

  public static final class Builder extends IncidentTaskUpdate.Builder<IncidentUpdate, Builder> {
    private IncidentState state;
    private String treePath;

    private Builder(final String id) {
      super(id);
    }

    public Builder state(final IncidentState state) {
      this.state = state;
      return this;
    }

    public Builder treePath(final String treePath) {
      this.treePath = treePath;
      return this;
    }

    @Override
    public IncidentUpdate build() {
      return new IncidentUpdate(id, index, state, treePath);
    }
  }
}
