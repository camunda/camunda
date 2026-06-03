/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.sql.columns;

import io.camunda.search.entities.WaitStateEntity;

public enum WaitStateSearchColumn implements SearchColumn<WaitStateEntity> {
  ELEMENT_INSTANCE_KEY("elementInstanceKey"),
  PROCESS_INSTANCE_KEY("processInstanceKey"),
  ROOT_PROCESS_INSTANCE_KEY("rootProcessInstanceKey"),
  ELEMENT_ID("elementId");

  private final String property;

  WaitStateSearchColumn(final String property) {
    this.property = property;
  }

  @Override
  public String property() {
    return property;
  }

  @Override
  public Class<WaitStateEntity> getEntityClass() {
    return WaitStateEntity.class;
  }
}
