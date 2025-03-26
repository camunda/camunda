/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.client.impl.search.request;

import io.camunda.client.protocol.rest.SortOrderEnum;

public class SearchRequestSort {

  private String field;
  private SortOrderEnum order = SortOrderEnum.ASC;

  public SearchRequestSort() {}

  public SearchRequestSort field(final String field) {
    this.field = field;
    return this;
  }

  public String getField() {
    return field;
  }

  public void setField(final String field) {
    this.field = field;
  }

  public SearchRequestSort order(final SortOrderEnum order) {
    this.order = order;
    return this;
  }

  public SortOrderEnum getOrder() {
    return order;
  }

  public void setOrder(final SortOrderEnum order) {
    this.order = order;
  }
}
