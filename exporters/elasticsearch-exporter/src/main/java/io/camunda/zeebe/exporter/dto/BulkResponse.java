/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.exporter.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Collections;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public final class BulkResponse {

  private boolean errors;
  private List<BulkItem> items = Collections.emptyList();

  public List<BulkItem> getItems() {
    return items;
  }

  public void setItems(final List<BulkItem> items) {
    this.items = items;
  }

  public void setErrors(final boolean errors) {
    this.errors = errors;
  }

  public boolean hasErrors() {
    return errors;
  }
}
