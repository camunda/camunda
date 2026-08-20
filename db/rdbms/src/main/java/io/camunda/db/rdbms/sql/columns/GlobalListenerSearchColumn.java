/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.sql.columns;

import io.camunda.search.entities.GlobalListenerEntity;

public enum GlobalListenerSearchColumn implements SearchColumn<GlobalListenerEntity> {
  ID("id"),
  LISTENER_ID("listenerId"),
  TYPE("type"),
  RETRIES("retries"),
  AFTER_NON_GLOBAL("afterNonGlobal"),
  PRIORITY("priority"),
  SOURCE("source"),
  LISTENER_TYPE("listenerType");
  private final String property;

  GlobalListenerSearchColumn(final String property) {
    this.property = property;
  }

  @Override
  public String property() {
    return property;
  }

  @Override
  public Class<GlobalListenerEntity> getEntityClass() {
    return GlobalListenerEntity.class;
  }
}
