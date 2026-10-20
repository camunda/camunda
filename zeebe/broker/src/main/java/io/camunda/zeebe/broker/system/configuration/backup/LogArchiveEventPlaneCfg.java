/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.configuration.backup;

import io.camunda.zeebe.broker.system.configuration.ConfigurationEntry;
import java.time.Duration;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Settings of the announcement of commit entries to the event plane, {@code
 * zeebe.broker.data.backup.logArchive.eventPlane.*}. The announcer only runs if {@link #getUrl()}
 * is set.
 */
@NullMarked
public class LogArchiveEventPlaneCfg implements ConfigurationEntry {

  private @Nullable String url;
  private @Nullable String clusterId;
  private @Nullable String username;
  private @Nullable String password;
  private int maxBatchEntries = 100;
  private Duration requestTimeout = Duration.ofSeconds(10);

  public @Nullable String getUrl() {
    return url;
  }

  public void setUrl(final @Nullable String url) {
    this.url = url;
  }

  /** The cluster id the event plane knows the cluster by; falls back to the cluster's own id. */
  public @Nullable String getClusterId() {
    return clusterId;
  }

  public void setClusterId(final @Nullable String clusterId) {
    this.clusterId = clusterId;
  }

  public @Nullable String getUsername() {
    return username;
  }

  public void setUsername(final @Nullable String username) {
    this.username = username;
  }

  public @Nullable String getPassword() {
    return password;
  }

  public void setPassword(final @Nullable String password) {
    this.password = password;
  }

  public int getMaxBatchEntries() {
    return maxBatchEntries;
  }

  public void setMaxBatchEntries(final int maxBatchEntries) {
    this.maxBatchEntries = maxBatchEntries;
  }

  public Duration getRequestTimeout() {
    return requestTimeout;
  }

  public void setRequestTimeout(final Duration requestTimeout) {
    this.requestTimeout = requestTimeout;
  }

  @Override
  public String toString() {
    return "LogArchiveEventPlaneCfg{url="
        + url
        + ", clusterId="
        + clusterId
        + ", username="
        + username
        + ", maxBatchEntries="
        + maxBatchEntries
        + ", requestTimeout="
        + requestTimeout
        + '}';
  }
}
