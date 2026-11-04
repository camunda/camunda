/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.configuration;

import java.time.Duration;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Configuration of the ECV tracking and commit, which runs on top of the system partition. */
public final class EcvCfg implements ConfigurationEntry {

  private EcvCommitMode commitMode = EcvCommitMode.AUTOMATIC;
  private Duration commitCheckInterval = Duration.ofSeconds(5);
  private Duration requestTimeout = Duration.ofSeconds(5);
  // null: every capability of the catalog; only for tests that simulate an older binary
  private @Nullable List<String> supportedCapabilities;

  /**
   * The commit mode. Only the mode of the system partition leader has an effect; a change takes
   * effect at the next restart.
   */
  public EcvCommitMode getCommitMode() {
    return commitMode;
  }

  public void setCommitMode(final EcvCommitMode commitMode) {
    this.commitMode = commitMode;
  }

  /** How often the system partition leader runs the commit checks in automatic mode. */
  public Duration getCommitCheckInterval() {
    return commitCheckInterval;
  }

  public void setCommitCheckInterval(final Duration commitCheckInterval) {
    this.commitCheckInterval = commitCheckInterval;
  }

  /** Timeout of a request between brokers, for example the progress query of a partition. */
  public Duration getRequestTimeout() {
    return requestTimeout;
  }

  public void setRequestTimeout(final Duration requestTimeout) {
    this.requestTimeout = requestTimeout;
  }

  /**
   * Test only: the names of the capabilities that this broker reports as supported, to simulate an
   * older binary. Null reports every capability of the catalog.
   */
  public @Nullable List<String> getSupportedCapabilities() {
    return supportedCapabilities;
  }

  public void setSupportedCapabilities(final @Nullable List<String> supportedCapabilities) {
    this.supportedCapabilities = supportedCapabilities;
  }

  @Override
  public String toString() {
    return "EcvCfg{commitMode=%s, commitCheckInterval=%s, requestTimeout=%s, supportedCapabilities=%s}"
        .formatted(commitMode, commitCheckInterval, requestTimeout, supportedCapabilities);
  }

  public enum EcvCommitMode {
    /** The system partition leader commits as soon as a commit is available and all checks pass. */
    AUTOMATIC,
    /** Only a commit request through the API commits. */
    MANUAL
  }
}
