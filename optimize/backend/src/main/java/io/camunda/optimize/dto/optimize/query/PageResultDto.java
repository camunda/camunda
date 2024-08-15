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
    final int PRIME = 59;
    int result = 1;
    final Object $pagingState = getPagingState();
    result = result * PRIME + ($pagingState == null ? 43 : $pagingState.hashCode());
    result = result * PRIME + getLimit();
    final Object $entities = getEntities();
    result = result * PRIME + ($entities == null ? 43 : $entities.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof PageResultDto)) {
      return false;
    }
    final PageResultDto<?> other = (PageResultDto<?>) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    final Object this$pagingState = getPagingState();
    final Object other$pagingState = other.getPagingState();
    if (this$pagingState == null
        ? other$pagingState != null
        : !this$pagingState.equals(other$pagingState)) {
      return false;
    }
    if (getLimit() != other.getLimit()) {
      return false;
    }
    final Object this$entities = getEntities();
    final Object other$entities = other.getEntities();
    if (this$entities == null ? other$entities != null : !this$entities.equals(other$entities)) {
      return false;
    }
    return true;
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
