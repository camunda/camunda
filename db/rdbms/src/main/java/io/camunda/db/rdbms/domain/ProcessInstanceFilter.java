/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.domain;

import java.util.Collection;
import java.util.List;

public record ProcessInstanceFilter(
    String bpmnProcessId, VariableFilter variable, Paging paging, List<SortFieldEntry> sortFields) {

  public record VariableFilter(String name, Collection<String> values) {}

  public enum ProcessInstanceSortField implements SortField {
    PROCESS_INSTANCE_KEY("PROCESS_INSTANCE_KEY"),
    START_DATE("START_DATE");

    private final String fieldName;

    ProcessInstanceSortField(final String fieldName) {
      this.fieldName = fieldName;
    }

    @Override
    public String getFieldName() {
      return this.fieldName;
    }
  }
}
