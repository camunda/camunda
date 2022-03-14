/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
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

  public interface CustomQueryValidator<T> {
    void validate(Query<T> query) throws ValidationException;
  }

  private List<String> fields;

  public void validate(final Query<T> query, Class<T> queriedClass) throws ValidationException {
    validate(query, queriedClass, null);
  }

  public void validate(final Query<T> query, Class<T> queriedClass,
      CustomQueryValidator<T> customValidator) {
    retrieveFieldsFor(queriedClass);
    validateSorting(query.getSort(), fields);
    validatePaging(query);
    if (customValidator != null) {
      customValidator.validate(query);
    }
  }

  private void retrieveFieldsFor(final Class<T> queriedClass) {
    if (fields == null) {
      fields = Arrays.stream(
          queriedClass.getDeclaredFields()).map(
          Field::getName).collect(Collectors.toList());
    }
  }

  protected void validatePaging(final Query<T> query) {
    final int size = query.getSize();
    if (size <= 0 || size > 1_000) {
      throw new ClientException("size should be greater than zero and lesser than 1_000");
    }
    final Object[] searchAfter = query.getSearchAfter();
    if (searchAfter != null && searchAfter.length == 0) {
      throw new ValidationException("searchAfter should have a least 1 value");
    }
    if( query.getSort()!= null ) {
      final int sortSize = query.getSort().size();
      if (searchAfter != null && searchAfter.length != sortSize + 1) {
        throw new ValidationException(String.format("searchAfter should have a %s values", sortSize + 1));
      }
    }
  }

  protected void validateSorting(final List<Sort> sortSpecs, List<String> fields) {
    if (sortSpecs == null || sortSpecs.isEmpty()) {
      return;
    }
    final List<String> givenFields = CollectionUtil.withoutNulls(
        sortSpecs.stream().map(Sort::getField)
            .collect(Collectors.toList()));
    if (givenFields.isEmpty()) {
      throw new ValidationException("No 'field' given in sort. Example: \"sort\": [{\"field\":\"name\",\"order\": \"ASC\"}] ");
    }
    List<String> invalidSortSpecs = getInvalidFields(fields, givenFields);
    if (!invalidSortSpecs.isEmpty()) {
      throw new ValidationException(String.format("Sort has invalid field(s): %s",
          String.join(", ", invalidSortSpecs)));
    }
  }

  private List<String> getInvalidFields(final List<String> availableFields,
      final List<String> givenFields) {
    return givenFields.stream().filter(field -> !availableFields.contains(field))
        .collect(Collectors.toList());
  }

}
