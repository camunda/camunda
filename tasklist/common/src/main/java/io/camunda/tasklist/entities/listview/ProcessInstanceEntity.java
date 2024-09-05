/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.entities.listview;

import com.fasterxml.jackson.annotation.JsonInclude;

public class ProcessInstanceEntity {
  @JsonInclude(JsonInclude.Include.NON_NULL)
  private String id;

  public String getId() {
    return id;
  }

  public ProcessInstanceEntity setId(final String id) {
    this.id = id;
    return this;
  }
}
