/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query;

import java.util.ArrayList;
import java.util.List;

public class PageResultDto<T> {

  private String pagingState;
  private int limit;
  private List<T> entities = new ArrayList<>();

  public PageResultDto(final int limit) {
    this.limit = limit;
  }

  public PageResultDto(final String pagingState, final int limit, final List<T> entities) {
    if (entities == null) {
      throw new IllegalArgumentException("entities cannot be null");
    }

    this.pagingState = pagingState;
    this.limit = limit;
    this.entities = entities;
  }

  protected PageResultDto() {}

  public boolean isEmpty() {
    return entities.isEmpty();
  }

  public boolean isLastPage() {
    return pagingState == null;
  }

  public String getPagingState() {
    return pagingState;
  }

  public void setPagingState(final String pagingState) {
    this.pagingState = pagingState;
  }

  public int getLimit() {
    return limit;
  }

  public void setLimit(final int limit) {
    this.limit = limit;
  }

  public List<T> getEntities() {
    return entities;
  }

  public void setEntities(final List<T> entities) {
    if (entities == null) {
      throw new IllegalArgumentException("entities cannot be null");
    }

    this.entities = entities;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof PageResultDto;
  }

  @Override
  public int hashCode() {
    return org.apache.commons.lang3.builder.HashCodeBuilder.reflectionHashCode(this);
  }

  @Override
  public boolean equals(final Object o) {
    return org.apache.commons.lang3.builder.EqualsBuilder.reflectionEquals(this, o);
  }

  @Override
  public String toString() {
    return "PageResultDto(pagingState="
        + getPagingState()
        + ", limit="
        + getLimit()
        + ", entities="
        + getEntities()
        + ")";
  }
}
