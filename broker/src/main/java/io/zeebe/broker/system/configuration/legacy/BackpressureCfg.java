/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.system.configuration.legacy;

@Deprecated(since = "0.23.0-alpha1")
/* Kept in order to be able to offer a migration path for old configuration.
 * It is not yet clear whether we intent to offer a migration path for old configurations.
 * This class might be moved or removed on short notice.
 */
public final class BackpressureCfg implements ConfigurationEntry {

  private boolean enabled = true;
  private boolean useWindowed = true;
  private String algorithm = "vegas";

  public boolean isEnabled() {
    return enabled;
  }

  public BackpressureCfg setEnabled(final boolean enabled) {
    this.enabled = enabled;
    return this;
  }

  public boolean useWindowed() {
    return useWindowed;
  }

  public BackpressureCfg setUseWindowed(final boolean useWindowed) {
    this.useWindowed = useWindowed;
    return this;
  }

  public LimitAlgorithm getAlgorithm() {
    return LimitAlgorithm.valueOf(algorithm.toUpperCase());
  }

  public BackpressureCfg setAlgorithm(final String algorithm) {
    this.algorithm = algorithm;
    return this;
  }

  public enum LimitAlgorithm {
    VEGAS,
    GRADIENT,
    GRADIENT2
  }
}
