/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.schema.config;

import io.camunda.zeebe.util.retry.RetryConfiguration;
import java.time.Duration;

public class SchemaManagerConfiguration {

  private boolean isCreateSchema = true;
  private SchemaManagerRetryConfiguration retry = new SchemaManagerRetryConfiguration();

  public boolean isCreateSchema() {
    return isCreateSchema;
  }

  public void setCreateSchema(final boolean isCreateSchema) {
    this.isCreateSchema = isCreateSchema;
  }

  public SchemaManagerRetryConfiguration getRetry() {
    return retry;
  }

  public void setRetry(final SchemaManagerRetryConfiguration retry) {
    this.retry = retry;
  }

  public static class SchemaManagerRetryConfiguration extends RetryConfiguration {

    public static final Duration DEFAULT_MIN_RETRY_DELAY = Duration.ofMillis(500);
    public static final Duration DEFAULT_MAX_RETRY_DELAY = Duration.ofSeconds(10);
    public static final int DEFAULT_MAX_RETRIES = 10;

    @Override
    public int defaultMaxRetries() {
      return DEFAULT_MAX_RETRIES;
    }

    @Override
    public Duration defaultMinRetryDelay() {
      return DEFAULT_MIN_RETRY_DELAY;
    }

    @Override
    public Duration defaultMaxRetryDelay() {
      return DEFAULT_MAX_RETRY_DELAY;
    }
  }
}
