/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.domain;

import io.camunda.search.filter.ProcessInstanceFilter;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.sort.ProcessInstanceSort;

public record ProcessInstanceDbFilter(
    ProcessInstanceFilter filter, ProcessInstanceSort sort, SearchQueryPage page) {

  public ProcessInstanceDbFilter withProcessInstanceFilter(final ProcessInstanceFilter filter) {
    return new ProcessInstanceDbFilter(filter, sort, page);
  }

  public ProcessInstanceDbFilter withProcessInstanceSort(final ProcessInstanceSort sort) {
    return new ProcessInstanceDbFilter(filter, sort, page);
  }

  public ProcessInstanceDbFilter withPage(final SearchQueryPage page) {
    return new ProcessInstanceDbFilter(filter, sort, page);
  }
}
