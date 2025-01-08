/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest;

import io.camunda.zeebe.gateway.protocol.rest.SortOrderEnum;

public class SearchQuerySortRequest {

  private String field;
  private SortOrderEnum order = SortOrderEnum.ASC;

  public SearchQuerySortRequest() {}

  public SearchQuerySortRequest field(String field) {
    this.field = field;
    return this;
  }

  public String getField() {
    return field;
  }

  public void setField(String field) {
    this.field = field;
  }

  public SearchQuerySortRequest order(SortOrderEnum order) {
    this.order = order;
    return this;
  }

  public SortOrderEnum getOrder() {
    return order;
  }

  public void setOrder(SortOrderEnum order) {
    this.order = order;
  }
}
