/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.query.sorting;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Optional;

@AllArgsConstructor(access = AccessLevel.PUBLIC)
@NoArgsConstructor(access = AccessLevel.PUBLIC)
@Data
public class ReportSortingDto {
  public static final String SORT_BY_KEY = "key";
  public static final String SORT_BY_VALUE = "value";
  public static final String SORT_BY_LABEL = "label";

  private String by;
  private SortOrder order;

  public Optional<String> getBy() {
    return Optional.ofNullable(by);
  }

  public Optional<SortOrder> getOrder() {
    return Optional.ofNullable(order);
  }

}
