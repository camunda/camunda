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
import lombok.Data;
import lombok.NonNull;

@Data
public class PageResultDto<T> {

  private String pagingState;
  private int limit;
  @NonNull private List<T> entities = new ArrayList<>();

  public PageResultDto(final int limit) {
    this.limit = limit;
  }

  public PageResultDto(String pagingState, int limit, @NonNull List<T> entities) {
    this.pagingState = pagingState;
    this.limit = limit;
    this.entities = entities;
  }

  protected PageResultDto() {}

  public boolean isEmpty() {
    return this.entities.isEmpty();
  }

  public boolean isLastPage() {
    return pagingState == null;
  }
}
