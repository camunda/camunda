/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.filter.config;

import io.camunda.zeebe.exporter.filter.CommonFilterConfiguration;
import java.util.List;

/**
 * Minimal {@link CommonFilterConfiguration.IndexConfig} implementation for tests. All variable-name
 * and variable-type filters are left as {@code null} to avoid influencing the value-type tests.
 */
final class TestIndexConfig implements CommonFilterConfiguration.IndexConfig {

  @Override
  public List<String> getVariableNameInclusionExact() {
    return null;
  }

  @Override
  public List<String> getVariableNameInclusionStartWith() {
    return null;
  }

  @Override
  public List<String> getVariableNameInclusionEndWith() {
    return null;
  }

  @Override
  public List<String> getVariableNameExclusionExact() {
    return null;
  }

  @Override
  public List<String> getVariableNameExclusionStartWith() {
    return null;
  }

  @Override
  public List<String> getVariableNameExclusionEndWith() {
    return null;
  }

  @Override
  public List<String> getVariableValueTypeInclusion() {
    return null;
  }

  @Override
  public List<String> getVariableValueTypeExclusion() {
    return null;
  }

  @Override
  public boolean isOptimizeModeEnabled() {
    return false;
  }
}
