/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.domain;

import io.camunda.search.entities.GlobalListenerSource;
import io.camunda.search.entities.GlobalListenerType;
import io.camunda.util.GlobalListenerUtil;
import io.camunda.util.ObjectBuilder;
import java.util.List;

public record GlobalListenerDbModel(
    String id,
    String listenerId,
    String type,
    Integer retries,
    List<String> eventTypes,
    boolean afterNonGlobal,
    Integer priority,
    GlobalListenerSource source,
    GlobalListenerType listenerType) {

  public static class GlobalListenerDbModelBuilder implements ObjectBuilder<GlobalListenerDbModel> {
    private String listenerId;
    private String type;
    private Integer retries;
    private List<String> eventTypes;
    private boolean afterNonGlobal;
    private Integer priority;
    private GlobalListenerSource source;
    private GlobalListenerType listenerType;

    public GlobalListenerDbModelBuilder listenerId(final String listenerId) {
      this.listenerId = listenerId;
      return this;
    }

    public GlobalListenerDbModelBuilder type(final String type) {
      this.type = type;
      return this;
    }

    public GlobalListenerDbModelBuilder retries(final Integer retries) {
      this.retries = retries;
      return this;
    }

    public GlobalListenerDbModelBuilder eventTypes(final List<String> eventTypes) {
      this.eventTypes = eventTypes;
      return this;
    }

    public GlobalListenerDbModelBuilder afterNonGlobal(final boolean afterNonGlobal) {
      this.afterNonGlobal = afterNonGlobal;
      return this;
    }

    public GlobalListenerDbModelBuilder priority(final Integer priority) {
      this.priority = priority;
      return this;
    }

    public GlobalListenerDbModelBuilder source(final GlobalListenerSource source) {
      this.source = source;
      return this;
    }

    public GlobalListenerDbModelBuilder listenerType(final GlobalListenerType listenerType) {
      this.listenerType = listenerType;
      return this;
    }

    @Override
    public GlobalListenerDbModel build() {
      return new GlobalListenerDbModel(
          GlobalListenerUtil.generateId(listenerId, listenerType),
          listenerId,
          type,
          retries,
          eventTypes,
          afterNonGlobal,
          priority,
          source,
          listenerType);
    }
  }
}
