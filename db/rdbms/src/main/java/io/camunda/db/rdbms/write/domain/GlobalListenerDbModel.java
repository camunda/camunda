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

// Note: this is not a record in order to be able to use <collection> to aggregate event types in
// the MyBatis mapping file
public class GlobalListenerDbModel {
  private String id;
  private String listenerId;
  private String type;
  private Integer retries;
  private List<String> eventTypes;
  private boolean afterNonGlobal;
  private Integer priority;
  private GlobalListenerSource source;
  private GlobalListenerType listenerType;

  public GlobalListenerDbModel(final String id) {
    this.id = id;
  }

  public GlobalListenerDbModel(
      final String id,
      final String listenerId,
      final String type,
      final Integer retries,
      final List<String> eventTypes,
      final boolean afterNonGlobal,
      final Integer priority,
      final GlobalListenerSource source,
      final GlobalListenerType listenerType) {
    this.id = id;
    this.listenerId = listenerId;
    this.type = type;
    this.retries = retries;
    this.eventTypes = eventTypes;
    this.afterNonGlobal = afterNonGlobal;
    this.priority = priority;
    this.source = source;
    this.listenerType = listenerType;
  }

  public String id() {
    return id;
  }

  public void id(final String id) {
    this.id = id;
  }

  public String listenerId() {
    return listenerId;
  }

  public void listenerId(final String listenerId) {
    this.listenerId = listenerId;
  }

  public String type() {
    return type;
  }

  public void type(final String type) {
    this.type = type;
  }

  public Integer retries() {
    return retries;
  }

  public void retries(final Integer retries) {
    this.retries = retries;
  }

  public List<String> eventTypes() {
    return eventTypes;
  }

  public void eventTypes(final List<String> eventTypes) {
    this.eventTypes = eventTypes;
  }

  public boolean afterNonGlobal() {
    return afterNonGlobal;
  }

  public void afterNonGlobal(final boolean afterNonGlobal) {
    this.afterNonGlobal = afterNonGlobal;
  }

  public Integer priority() {
    return priority;
  }

  public void priority(final Integer priority) {
    this.priority = priority;
  }

  public GlobalListenerSource source() {
    return source;
  }

  public void source(final GlobalListenerSource source) {
    this.source = source;
  }

  public GlobalListenerType listenerType() {
    return listenerType;
  }

  public void listenerType(final GlobalListenerType listenerType) {
    this.listenerType = listenerType;
  }

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
