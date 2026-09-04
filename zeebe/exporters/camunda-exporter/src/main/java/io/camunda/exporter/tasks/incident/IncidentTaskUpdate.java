/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.incident;

import java.util.Map;

sealed interface IncidentTaskUpdate
    permits IncidentUpdate, ListViewInstanceUpdate, FlowNodeInstanceUpdate {
  String id();

  String index();

  default String routing() {
    return null;
  }

  Map<String, Object> doc();

  abstract class Builder<T extends IncidentTaskUpdate, B extends Builder<T, B>> {
    protected final String id;
    protected String index;

    Builder(final String id) {
      this.id = id;
    }

    @SuppressWarnings("unchecked")
    public B index(final String index) {
      this.index = index;
      return (B) this;
    }

    public abstract T build();
  }
}
