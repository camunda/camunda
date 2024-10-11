/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.process.test.impl.client;

import java.util.List;

public class SearchProcessInstanceResponseDto {

  private List<ProcessInstanceDto> items;
  private long total;

  public List<ProcessInstanceDto> getItems() {
    return items;
  }

  public void setItems(final List<ProcessInstanceDto> items) {
    this.items = items;
  }

  public long getTotal() {
    return total;
  }

  public void setTotal(final long total) {
    this.total = total;
  }
}
