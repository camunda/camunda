/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service.search.result;

import java.util.ArrayList;
import java.util.List;

public interface QueryResultConfig {

  List<FieldFilter> getFieldFilters();

  abstract class AbstractBuilder<T> {

    protected final List<FieldFilter> fieldFilters = new ArrayList<>();

    protected FieldFilter currentFieldFilter;

    protected abstract T self();

    protected T addInclusion(final boolean include) {
      if (currentFieldFilter != null) {
        final var field = currentFieldFilter.field();
        final var newFieldFilter = new FieldFilter(field, include);
        fieldFilters.add(newFieldFilter);
        currentFieldFilter = null;
      }
      // else if not set, then noop
      return self();
    }

    public T include() {
      return addInclusion(true);
    }

    public T exclude() {
      return addInclusion(false);
    }
  }

  record FieldFilter(String field, Boolean include) {}
}
