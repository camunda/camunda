/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.rest.dto;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class ListenerResponseDto {
  private List<ListenerDto> listeners = new ArrayList<>();
  private Long totalCount;

  public ListenerResponseDto() {}

  public ListenerResponseDto(final List listeners, final Long totalCount) {
    this.totalCount = totalCount;
    this.listeners = listeners;
  }

  public List<ListenerDto> getListeners() {
    return listeners;
  }

  public ListenerResponseDto setListeners(final List<ListenerDto> listeners) {
    this.listeners = listeners;
    return this;
  }

  public Long getTotalCount() {
    return totalCount;
  }

  public ListenerResponseDto setTotalCount(final Long totalCount) {
    this.totalCount = totalCount;
    return this;
  }

  @Override
  public int hashCode() {
    return Objects.hash(listeners, totalCount);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final ListenerResponseDto that = (ListenerResponseDto) o;
    return Objects.equals(listeners, that.listeners) && Objects.equals(totalCount, that.totalCount);
  }

  @Override
  public String toString() {
    return "ListenerResponseDto{" + "listeners=" + listeners + ", totalCount=" + totalCount + '}';
  }
}
