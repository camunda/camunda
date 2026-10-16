/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.scim.mapper;

import com.unboundid.scim2.common.exceptions.BadRequestException;
import com.unboundid.scim2.common.filters.Filter;
import com.unboundid.scim2.common.filters.FilterType;
import java.util.Locale;
import java.util.Optional;

/** Parses the {@code filter} query parameter; only {@code userName eq "<value>"} is supported. */
public final class ScimUserFilter {

  private ScimUserFilter() {}

  public static Optional<String> parseUserNameEquals(final String filterExpression)
      throws BadRequestException {
    if (filterExpression == null || filterExpression.isBlank()) {
      return Optional.empty();
    }
    final Filter filter = Filter.fromString(filterExpression);
    final var path = filter.getAttributePath();
    if (filter.getFilterType() != FilterType.EQUAL
        || path == null
        || path.size() != 1
        || path.getSchemaUrn() != null
        || path.getElement(0).getValueFilter() != null
        || !"username".equals(path.getElement(0).getAttribute().toLowerCase(Locale.ROOT))
        || !filter.getComparisonValue().isString()) {
      throw BadRequestException.invalidFilter(
          "Only filters of the form 'userName eq \"<value>\"' are supported");
    }
    return Optional.of(filter.getComparisonValue().asString());
  }
}
