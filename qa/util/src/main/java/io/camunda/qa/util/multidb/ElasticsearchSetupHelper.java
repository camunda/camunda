/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.qa.util.multidb;

import io.camunda.webapps.schema.descriptors.IndexDescriptor;
import java.time.Duration;
import java.util.Collection;
import java.util.Map;

public class ElasticsearchSetupHelper extends ElasticOpenSearchSetupHelper {
  private static final Duration DEFAULT_INDICES_LIFECYCLE_POLL_INTERVAL = Duration.ofMinutes(10);

  public ElasticsearchSetupHelper(
      final String endpoint, final Collection<IndexDescriptor> expectedDescriptors) {
    super(endpoint, expectedDescriptors);
  }

  @Override
  protected Map<String, Object> getLifecyclePollIntervalSettings(final Duration pollInterval) {
    if (pollInterval.toSeconds() < 1) {
      throw new IllegalArgumentException(
          "Elasticsearch index lifecycle poll interval must be at least 1 second");
    }

    return Map.of(
        "persistent",
        Map.of("indices.lifecycle.poll_interval", String.format("%ds", pollInterval.toSeconds())));
  }

  @Override
  protected Map<String, Object> getResetLifecyclePollIntervalSettings() {
    return getLifecyclePollIntervalSettings(DEFAULT_INDICES_LIFECYCLE_POLL_INTERVAL);
  }
}
