/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.rest.dto.dmn.list;

import io.camunda.operate.webapp.rest.dto.PaginatedQuery;
import io.camunda.operate.webapp.rest.dto.listview.SortValuesWrapper;
import io.camunda.operate.webapp.rest.exception.InvalidRequestException;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public class DecisionInstanceListRequestDto extends PaginatedQuery<DecisionInstanceListRequestDto> {

  public static final String SORT_BY_ID = "id";
  public static final String SORT_BY_DECISION_NAME = "decisionName";
  public static final String SORT_BY_DECISION_VERSION = "decisionVersion";
  public static final String SORT_BY_EVALUATION_DATE = "evaluationDate";
  public static final String SORT_BY_PROCESS_INSTANCE_ID = "processInstanceId";

  public static final Set<String> VALID_SORT_BY_VALUES;
  static {
    VALID_SORT_BY_VALUES = new HashSet<>();
    VALID_SORT_BY_VALUES.add(SORT_BY_ID);
    VALID_SORT_BY_VALUES.add(SORT_BY_DECISION_NAME);
    VALID_SORT_BY_VALUES.add(SORT_BY_DECISION_VERSION);
    VALID_SORT_BY_VALUES.add(SORT_BY_EVALUATION_DATE);
    VALID_SORT_BY_VALUES.add(SORT_BY_PROCESS_INSTANCE_ID);
  }

  public DecisionInstanceListRequestDto() {
  }

  public DecisionInstanceListRequestDto(
      final DecisionInstanceListQueryDto query) {
    this.query = query;
  }

  private DecisionInstanceListQueryDto query;

  public DecisionInstanceListQueryDto getQuery() {
    return query;
  }

  public DecisionInstanceListRequestDto setQuery(
      final DecisionInstanceListQueryDto query) {
    this.query = query;
    return this;
  }

  @Override
  protected Set<String> getValidSortByValues() {
    return VALID_SORT_BY_VALUES;
  }

  @Override
  public DecisionInstanceListRequestDto setSearchAfterOrEqual(final SortValuesWrapper[] searchAfterOrEqual) {
    if (searchAfterOrEqual != null) {
      throw new InvalidRequestException("SearchAfterOrEqual is not supported.");
    }
    return this;
  }

  @Override
  public DecisionInstanceListRequestDto setSearchBeforeOrEqual(final SortValuesWrapper[] searchBeforeOrEqual) {
    if (searchBeforeOrEqual != null) {
      throw new InvalidRequestException("SearchBeforeOrEqual is not supported.");
    }
    return this;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }
    final DecisionInstanceListRequestDto that = (DecisionInstanceListRequestDto) o;
    return Objects.equals(query, that.query);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), query);
  }
}
