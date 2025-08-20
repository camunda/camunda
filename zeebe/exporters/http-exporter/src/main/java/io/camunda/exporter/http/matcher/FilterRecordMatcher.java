/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.http.matcher;

import io.camunda.zeebe.protocol.record.Record;
import java.util.List;

/**
 * FilterRecordMatcher is used to match records against a list of filters. It checks if the record's
 * value type and intent match any of the provided filters.
 */
public record FilterRecordMatcher(List<Filter> filters) {

  public boolean matches(final Record<?> record) {
    for (final Filter filter : filters) {
      if (filter.valueType() == record.getValueType()) {
        if (filter.intents().isEmpty()) {
          return true;
        } else {
          return filter.intents().contains(record.getIntent().name());
        }
      }
    }
    return false;
  }
}
