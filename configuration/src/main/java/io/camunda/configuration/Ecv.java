/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import io.camunda.zeebe.broker.system.configuration.EcvCfg.EcvCommitMode;
import java.time.Duration;
import java.util.List;
import org.jspecify.annotations.Nullable;

public class Ecv {

  /**
   * The ECV commit mode: AUTOMATIC commits as soon as all commit checks pass, MANUAL waits for a
   * commit request through the API. Only the mode of the system partition leader has an effect. A
   * change takes effect at the next restart.
   */
  private EcvCommitMode commitMode = EcvCommitMode.AUTOMATIC;

  /** How often the system partition leader runs the commit checks in automatic mode. */
  private Duration commitCheckInterval = Duration.ofSeconds(5);

  /** Timeout of an ECV request between brokers. */
  private Duration requestTimeout = Duration.ofSeconds(5);

  /**
   * Test only: the names of the capabilities that this broker reports as supported, to simulate an
   * older binary. By default, every capability of the binary.
   */
  private @Nullable List<String> supportedCapabilities;

  public EcvCommitMode getCommitMode() {
    return commitMode;
  }

  public void setCommitMode(final EcvCommitMode commitMode) {
    this.commitMode = commitMode;
  }

  public Duration getCommitCheckInterval() {
    return commitCheckInterval;
  }

  public void setCommitCheckInterval(final Duration commitCheckInterval) {
    this.commitCheckInterval = commitCheckInterval;
  }

  public Duration getRequestTimeout() {
    return requestTimeout;
  }

  public void setRequestTimeout(final Duration requestTimeout) {
    this.requestTimeout = requestTimeout;
  }

  public @Nullable List<String> getSupportedCapabilities() {
    return supportedCapabilities;
  }

  public void setSupportedCapabilities(final @Nullable List<String> supportedCapabilities) {
    this.supportedCapabilities = supportedCapabilities;
  }
}
