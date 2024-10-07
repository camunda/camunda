/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.domain;

import io.camunda.search.filter.ProcessInstanceFilter;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.sort.ProcessInstanceSort;

public record ProcessInstanceDbFilter(
    ProcessInstanceFilter filter,
    ProcessInstanceSort sort,
    SearchQueryPage page
) {

  public ProcessInstanceDbFilter withProcessInstanceFilter(ProcessInstanceFilter filter) {
    return new ProcessInstanceDbFilter(filter, this.sort, this.page);
  }

  public ProcessInstanceDbFilter withProcessInstanceSort(ProcessInstanceSort sort) {
    return new ProcessInstanceDbFilter(this.filter, sort, this.page);
  }

  public ProcessInstanceDbFilter withPage(SearchQueryPage page) {
    return new ProcessInstanceDbFilter(this.filter, this.sort, page);
  }
}
