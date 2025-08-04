/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import io.camunda.configuration.UnifiedConfigurationHelper.BackwardsCompatibilityMode;
import java.util.Set;

public class Interceptor extends BaseExternalCodeConfiguration {
  private static final String PREFIX = "camunda.api.grpc.interceptors.";
  private static final String LEGACY_INTERCEPTOR_PROPERTY = "zeebe.gateway.interceptors.";

  @Override
  public String getId(final int index) {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        PREFIX + index + ".id",
        id,
        String.class,
        BackwardsCompatibilityMode.SUPPORTED,
        Set.of(LEGACY_INTERCEPTOR_PROPERTY + index + ".id"));
  }

  @Override
  public String getJarPath(final int index) {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        PREFIX + index + ".jar-path",
        jarPath,
        String.class,
        BackwardsCompatibilityMode.SUPPORTED,
        Set.of(LEGACY_INTERCEPTOR_PROPERTY + index + ".jarPath"));
  }

  @Override
  public String getClassName(final int index) {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        PREFIX + index + ".class-name",
        className,
        String.class,
        BackwardsCompatibilityMode.SUPPORTED,
        Set.of(LEGACY_INTERCEPTOR_PROPERTY + index + ".className"));
  }
}
