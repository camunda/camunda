/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.configuration;

import io.camunda.zeebe.protocol.record.value.ProtectionMode;
import java.util.List;
import java.util.Set;

/**
 * Data protection configuration for the variable redaction proof of concept (product-hub #3805).
 */
public record DataProtectionCfg(List<String> patterns, Set<ProtectionMode> modes)
    implements ConfigurationEntry {

  public static final List<String> DEFAULT_PATTERNS = List.of("sensitive_.*");
  public static final Set<ProtectionMode> DEFAULT_MODES = Set.of(ProtectionMode.REDACT);

  public DataProtectionCfg {
    patterns = (patterns == null || patterns.isEmpty()) ? DEFAULT_PATTERNS : List.copyOf(patterns);
    modes = (modes == null || modes.isEmpty()) ? DEFAULT_MODES : Set.copyOf(modes);
    ProtectionMode.validateCombination(modes);
  }

  public static DataProtectionCfg defaultDataProtectionCfg() {
    return new DataProtectionCfg(null, null);
  }
}
