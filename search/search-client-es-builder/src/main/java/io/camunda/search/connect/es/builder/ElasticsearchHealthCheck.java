/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.connect.es.builder;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * Immutable configuration for performing an Elasticsearch cluster health check with retry behavior.
 * Uses {@link RetryOperation} internally to retry on {@link IOException} and {@link
 * ElasticsearchException}.
 *
 * <p>Defaults: {@value #DEFAULT_MAX_RETRIES} retries with a {@value #DEFAULT_DELAY_SECONDS}s delay
 * between attempts.
 *
 * <p>Usage example:
 *
 * <pre>{@code
 * boolean healthy = ElasticsearchHealthCheck.builder()
 *     .client(esClient)
 *     .expectedClusterName("my-cluster")
 *     .maxRetries(100)
 *     .delaySeconds(5)
 *     .build()
 *     .check();
 * }</pre>
 */
public final class ElasticsearchHealthCheck {

  /** Default number of retry attempts. */
  public static final int DEFAULT_MAX_RETRIES = 50;

  /** Default delay in seconds between retry attempts. */
  public static final int DEFAULT_DELAY_SECONDS = 3;

  private final ElasticsearchClient client;
  private final String expectedClusterName;
  private final int maxRetries;
  private final int delaySeconds;

  private ElasticsearchHealthCheck(
      final ElasticsearchClient client,
      final String expectedClusterName,
      final int maxRetries,
      final int delaySeconds) {
    this.client = client;
    this.expectedClusterName = expectedClusterName;
    this.maxRetries = maxRetries;
    this.delaySeconds = delaySeconds;
  }

  /** Creates a new {@link Builder} for configuring an Elasticsearch health check. */
  public static Builder builder() {
    return new Builder();
  }

  /** Returns the Elasticsearch client used for the health check. */
  public ElasticsearchClient getClient() {
    return client;
  }

  /** Returns the expected cluster name to validate against. */
  public String getExpectedClusterName() {
    return expectedClusterName;
  }

  /** Returns the maximum number of retry attempts. */
  public int getMaxRetries() {
    return maxRetries;
  }

  /** Returns the delay in seconds between retry attempts. */
  public int getDelaySeconds() {
    return delaySeconds;
  }

  /**
   * Executes the health check using {@link RetryOperation}.
   *
   * @return {@code true} if the cluster is reachable and the cluster name matches
   * @throws ElasticsearchClientBuilderException if all retries are exhausted
   */
  public boolean check() {
    try {
      return RetryOperation.<Boolean>newBuilder()
          .noOfRetry(maxRetries)
          .retryOn(IOException.class, ElasticsearchException.class)
          .delayInterval(delaySeconds, TimeUnit.SECONDS)
          .message(String.format("Connect to Elasticsearch cluster [%s]", expectedClusterName))
          .retryConsumer(
              () -> {
                final var healthResponse = client.cluster().health();
                return healthResponse.clusterName().equals(expectedClusterName);
              })
          .retryPredicate(result -> !result)
          .build()
          .retry();
    } catch (final Exception e) {
      throw new ElasticsearchClientBuilderException(
          "Health check failed for Elasticsearch cluster [" + expectedClusterName + "]", e);
    }
  }

  /** Builder for creating an immutable {@link ElasticsearchHealthCheck} instance. */
  public static final class Builder {

    private ElasticsearchClient client;
    private String expectedClusterName;
    private int maxRetries = DEFAULT_MAX_RETRIES;
    private int delaySeconds = DEFAULT_DELAY_SECONDS;

    private Builder() {}

    /** Sets the Elasticsearch client to check. */
    public Builder client(final ElasticsearchClient client) {
      this.client = client;
      return this;
    }

    /** Sets the expected cluster name to validate against. */
    public Builder expectedClusterName(final String expectedClusterName) {
      this.expectedClusterName = expectedClusterName;
      return this;
    }

    /** Sets the maximum number of retry attempts. */
    public Builder maxRetries(final int maxRetries) {
      this.maxRetries = maxRetries;
      return this;
    }

    /** Sets the delay in seconds between retry attempts. */
    public Builder delaySeconds(final int delaySeconds) {
      this.delaySeconds = delaySeconds;
      return this;
    }

    /**
     * Builds an immutable {@link ElasticsearchHealthCheck} instance.
     *
     * @throws ElasticsearchClientBuilderException if client or expectedClusterName is not set
     */
    public ElasticsearchHealthCheck build() {
      if (client == null) {
        throw new ElasticsearchClientBuilderException("ElasticsearchHealthCheck requires a client");
      }
      if (expectedClusterName == null || expectedClusterName.isEmpty()) {
        throw new ElasticsearchClientBuilderException(
            "ElasticsearchHealthCheck requires an expectedClusterName");
      }
      return new ElasticsearchHealthCheck(client, expectedClusterName, maxRetries, delaySeconds);
    }
  }
}
