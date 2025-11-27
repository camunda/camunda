/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.identity.config;

public class EntitiesProperties {

  private static final String DEFAULT_PATTERN = "[^a-z0-9_@.-]";

  private NormalizationConfig defaults = new NormalizationConfig(true, DEFAULT_PATTERN);
  private NormalizationConfig role = new NormalizationConfig();
  private NormalizationConfig group = new NormalizationConfig();
  private NormalizationConfig user = new NormalizationConfig();
  private NormalizationConfig mappingRule = new NormalizationConfig();

  public NormalizationConfig getDefaults() {
    return defaults;
  }

  public void setDefaults(final NormalizationConfig defaults) {
    this.defaults = defaults;
  }

  public NormalizationConfig getRole() {
    return role;
  }

  public void setRole(final NormalizationConfig role) {
    this.role = role;
  }

  public NormalizationConfig getGroup() {
    return group;
  }

  public void setGroup(final NormalizationConfig group) {
    this.group = group;
  }

  public NormalizationConfig getUser() {
    return user;
  }

  public void setUser(final NormalizationConfig user) {
    this.user = user;
  }

  public NormalizationConfig getMappingRule() {
    return mappingRule;
  }

  public void setMappingRule(final NormalizationConfig mappingRule) {
    this.mappingRule = mappingRule;
  }

  /**
   * Gets the effective normalization configuration for a specific entity type. Entity-specific
   * settings override the defaults.
   */
  public NormalizationConfig getEffectiveConfig(final EntityType entityType) {
    final NormalizationConfig specific =
        switch (entityType) {
          case ROLE -> role;
          case GROUP -> group;
          case USER -> user;
          case MAPPING_RULE -> mappingRule;
        };

    return specific.mergeWithDefaults(defaults);
  }

  /** Configuration for ID normalization behavior. Supports inheritance from defaults. */
  public static class NormalizationConfig {
    private Boolean lowercase;
    private String pattern;

    public NormalizationConfig() {}

    public NormalizationConfig(final boolean lowercase, final String pattern) {
      this.lowercase = lowercase;
      this.pattern = pattern;
    }

    public Boolean getLowercase() {
      return lowercase;
    }

    public void setLowercase(final Boolean lowercase) {
      this.lowercase = lowercase;
    }

    public String getPattern() {
      return pattern;
    }

    public void setPattern(final String pattern) {
      this.pattern = pattern;
    }

    /** Merges this configuration with defaults. Specific settings override defaults. */
    public NormalizationConfig mergeWithDefaults(final NormalizationConfig defaults) {
      return new NormalizationConfig(
          lowercase != null ? lowercase : defaults.lowercase,
          pattern != null ? pattern : defaults.pattern);
    }

    public boolean isLowercaseEnabled() {
      return lowercase != null ? lowercase : true; // Default fallback
    }

    public String getEffectivePattern() {
      return pattern != null ? pattern : DEFAULT_PATTERN; // Default fallback
    }
  }

  public enum EntityType {
    ROLE,
    GROUP,
    USER,
    MAPPING_RULE
  }
}
