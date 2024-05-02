/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.api.v1.entities;

import io.camunda.operate.util.CollectionUtil;
import io.camunda.operate.webapp.api.v1.entities.Query.Sort;
import io.camunda.operate.webapp.api.v1.exceptions.ClientException;
import io.camunda.operate.webapp.api.v1.exceptions.ValidationException;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class QueryValidator<T> {

  public static final int MAX_QUERY_SIZE = 1000;
  private List<String> fields;

  public void validate(final Query<T> query, final Class<T> queriedClass)
      throws ValidationException {
    validate(query, queriedClass, null);
  }

  public void validate(
      final Query<T> query,
      final Class<T> queriedClass,
      final CustomQueryValidator<T> customValidator) {
    retrieveFieldsFor(queriedClass);
    validateSorting(query.getSort(), fields);
    validatePaging(query);
    if (customValidator != null) {
      customValidator.validate(query);
    }
  }

  private void retrieveFieldsFor(final Class<T> queriedClass) {
    if (fields == null) {
      fields =
          Arrays.stream(queriedClass.getDeclaredFields())
              .map(Field::getName)
              .collect(Collectors.toList());
    }
  }

  protected void validatePaging(final Query<T> query) {
    final int size = query.getSize();
    if (size <= 0 || size > MAX_QUERY_SIZE) {
      throw new ClientException(
          "size should be greater than zero and equal or less than " + MAX_QUERY_SIZE);
    }
    final Object[] searchAfter = query.getSearchAfter();
    if (searchAfter != null && searchAfter.length == 0) {
      throw new ValidationException("searchAfter should have a least 1 value");
    }
    if (query.getSort() != null) {
      final int sortSize = query.getSort().size();
      if (searchAfter != null && searchAfter.length != sortSize + 1) {
        throw new ValidationException(
            String.format("searchAfter should have a %s values", sortSize + 1));
      }
    }
  }

  protected void validateSorting(final List<Sort> sortSpecs, final List<String> fields) {
    if (sortSpecs == null || sortSpecs.isEmpty()) {
      return;
    }
    final List<String> givenFields =
        CollectionUtil.withoutNulls(
            sortSpecs.stream().map(Sort::getField).collect(Collectors.toList()));
    if (givenFields.isEmpty()) {
      throw new ValidationException(
          "No 'field' given in sort. Example: \"sort\": [{\"field\":\"name\",\"order\": \"ASC\"}] ");
    }
    final List<String> invalidSortSpecs = getInvalidFields(fields, givenFields);
    if (!invalidSortSpecs.isEmpty()) {
      throw new ValidationException(
          String.format("Sort has invalid field(s): %s", String.join(", ", invalidSortSpecs)));
    }
  }

  private List<String> getInvalidFields(
      final List<String> availableFields, final List<String> givenFields) {
    return givenFields.stream()
        .filter(field -> !availableFields.contains(field))
        .collect(Collectors.toList());
  }

  public interface CustomQueryValidator<T> {
    void validate(Query<T> query) throws ValidationException;
  }
}
