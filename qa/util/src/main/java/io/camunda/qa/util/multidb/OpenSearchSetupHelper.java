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

public class OpenSearchSetupHelper extends ElasticOpenSearchSetupHelper {
  private static final Duration DEFAULT_INDEX_STATE_MGMT_JOB_INTERVAL = Duration.ofMinutes(5);
  private static final String DEFAULT_INDEX_STATE_MGMT_JITTER = "0.6";

  public OpenSearchSetupHelper(
      final String endpoint, final Collection<IndexDescriptor> expectedDescriptors) {
    super(endpoint, expectedDescriptors);
  }

  @Override
  protected Map<String, Object> getLifecyclePollIntervalSettings(final Duration pollInterval) {
    // set a very low jitter to make sure the job runs quickly after the interval
    return getLifecycleJobInterval(pollInterval, "0.001");
  }

  @Override
  protected Map<String, Object> getResetLifecyclePollIntervalSettings() {
    return getLifecycleJobInterval(
        DEFAULT_INDEX_STATE_MGMT_JOB_INTERVAL, DEFAULT_INDEX_STATE_MGMT_JITTER);
  }

  private Map<String, Object> getLifecycleJobInterval(
      final Duration jobInterval, final String jitter) {
    if (jobInterval.toMinutes() < 1) {
      throw new IllegalArgumentException(
          "OpenSearch index state management job interval must be at least 1 minute");
    }

    return Map.of(
        "persistent",
        Map.of(
            "plugins.index_state_management.history.enabled",
            "false",
            "plugins.index_state_management.job_interval",
            String.valueOf(jobInterval.toMinutes()),
            "plugins.index_state_management.jitter",
            jitter));
  }
}
