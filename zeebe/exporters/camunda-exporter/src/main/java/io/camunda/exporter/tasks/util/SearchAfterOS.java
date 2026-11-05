/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.util;

import java.util.List;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch.core.SearchRequest.Builder;

public record SearchAfterOS(List<FieldValue> values) implements SearchAfter {
  public void apply(final Builder requestBuilder) {
    if (values != null && !values.isEmpty()) {
      requestBuilder.searchAfter(values);
    }
  }
}
