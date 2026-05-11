/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.configuration;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

/**
 * Broker-side configuration for the {@code camunda.secret.*} FEEL namespace.
 *
 * <p>PoC scope: only the {@code env} provider is supported, which reads secrets from process
 * environment variables. The {@code enabled} flag controls whether the broker attempts to resolve
 * secrets at all — when disabled, every lookup yields {@code Optional#empty()}, which means worker
 * requests for {@code camunda.secret.X} via {@code fetchVariables} will raise a "secret not
 * defined" incident regardless of whether the env var is set.
 */
public final class SecretsCfg implements ConfigurationEntry {

  /** Identifier for the environment-variable–backed secret store. */
  public static final String PROVIDER_ENV = "env";

  private boolean enabled = true;
  private String provider = PROVIDER_ENV;

  @Override
  public void init(final BrokerCfg globalConfig, final String brokerBase) {
    // no-op for the PoC; reserved for future provider-specific initialization
  }

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(final boolean enabled) {
    this.enabled = enabled;
  }

  public String getProvider() {
    return provider;
  }

  public void setProvider(final String provider) {
    this.provider = provider;
  }

  @Override
  public String toString() {
    return ToStringBuilder.reflectionToString(this, ToStringStyle.SHORT_PREFIX_STYLE);
  }
}
