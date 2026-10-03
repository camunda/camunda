/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import io.camunda.zeebe.protocol.record.value.ProtectionMode;
import java.util.List;

/**
 * Configuration for declaring sensitive process variables and the protection they receive on
 * export, proof of concept (product-hub #3805).
 *
 * <p>Maps to the {@code camunda.data.protection} property namespace.
 */
public class Protection {

  /**
   * Regular expressions matched against a variable's full name. A variable whose name matches any
   * of these patterns receives the configured {@link #modes}.
   */
  private List<String> patterns = List.of("sensitive_.*");

  /**
   * The protection modes applied to every variable matched by {@link #patterns}. {@code REDACT}
   * replaces the value with the redaction marker before any exporter receives it; {@code MASK} and
   * {@code ENCRYPT} are declarable but not yet enforced by any read or export path.
   */
  private List<ProtectionMode> modes = List.of(ProtectionMode.REDACT);

  public List<String> getPatterns() {
    return patterns;
  }

  public void setPatterns(final List<String> patterns) {
    this.patterns = patterns;
  }

  public List<ProtectionMode> getModes() {
    return modes;
  }

  public void setModes(final List<ProtectionMode> modes) {
    this.modes = modes;
  }
}
