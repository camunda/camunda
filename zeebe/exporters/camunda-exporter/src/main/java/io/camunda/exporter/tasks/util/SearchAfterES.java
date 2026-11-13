/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.util;

import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch.core.SearchRequest.Builder;
import java.util.List;

public record SearchAfterES(List<FieldValue> values) implements SearchAfter {
  public void apply(final Builder requestBuilder) {
    if (values != null && !values.isEmpty()) {
      requestBuilder.searchAfter(values);
    }
  }
}
