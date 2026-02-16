/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.filter.config;

import io.camunda.zeebe.exporter.filter.FilterConfiguration;
import java.util.List;

/**
 * Minimal {@link FilterConfiguration.IndexConfig} implementation for tests.
 *
 * <p>By default, all lists are empty (no name-based rules). Tests can override individual lists via
 * the fluent setters.
 */
public final class TestIndexConfig implements FilterConfiguration.IndexConfig {

  private List<String> inclusionExact = List.of();
  private List<String> inclusionStartWith = List.of();
  private List<String> inclusionEndWith = List.of();

  private List<String> exclusionExact = List.of();
  private List<String> exclusionStartWith = List.of();
  private List<String> exclusionEndWith = List.of();

  private List<String> variableValueTypeInclusion = List.of();
  private List<String> variableValueTypeExclusion = List.of();

  private List<String> bpmnProcessIdInclusion = List.of();
  private List<String> bpmnProcessIdExclusion = List.of();

  private boolean optimizeModeEnabled = false;

  // --- fluent setters -------------------------------------------------------

  public TestIndexConfig withVariableNameInclusionExact(final List<String> names) {
    inclusionExact = names != null ? names : List.of();
    return this;
  }

  public TestIndexConfig withVariableNameInclusionStartWith(final List<String> prefixes) {
    inclusionStartWith = prefixes != null ? prefixes : List.of();
    return this;
  }

  public TestIndexConfig withVariableNameInclusionEndWith(final List<String> suffixes) {
    inclusionEndWith = suffixes != null ? suffixes : List.of();
    return this;
  }

  public TestIndexConfig withVariableNameExclusionExact(final List<String> names) {
    exclusionExact = names != null ? names : List.of();
    return this;
  }

  public TestIndexConfig withVariableNameExclusionStartWith(final List<String> prefixes) {
    exclusionStartWith = prefixes != null ? prefixes : List.of();
    return this;
  }

  public TestIndexConfig withVariableNameExclusionEndWith(final List<String> suffixes) {
    exclusionEndWith = suffixes != null ? suffixes : List.of();
    return this;
  }

  public TestIndexConfig withVariableValueTypeInclusion(final List<String> typeInclusion) {
    variableValueTypeInclusion = typeInclusion != null ? typeInclusion : List.of();
    return this;
  }

  public TestIndexConfig withVariableValueTypeExclusion(final List<String> typeExclusion) {
    variableValueTypeExclusion = typeExclusion != null ? typeExclusion : List.of();
    return this;
  }

  public TestIndexConfig withOptimizeModeEnabled(final boolean optimizeModeEnabled) {
    this.optimizeModeEnabled = optimizeModeEnabled;
    return this;
  }

  public TestIndexConfig withBpmnProcessIdInclusion(final List<String> bpmnProcessIds) {
    bpmnProcessIdInclusion = bpmnProcessIds != null ? bpmnProcessIds : List.of();
    return this;
  }

  public TestIndexConfig withBpmnProcessIdExclusion(final List<String> bpmnProcessIds) {
    bpmnProcessIdExclusion = bpmnProcessIds != null ? bpmnProcessIds : List.of();
    return this;
  }

  // --- FilterConfiguration.IndexConfig -------------------------------------

  @Override
  public List<String> getVariableNameInclusionExact() {
    return inclusionExact;
  }

  @Override
  public List<String> getVariableNameInclusionStartWith() {
    return inclusionStartWith;
  }

  @Override
  public List<String> getVariableNameInclusionEndWith() {
    return inclusionEndWith;
  }

  @Override
  public List<String> getVariableNameExclusionExact() {
    return exclusionExact;
  }

  @Override
  public List<String> getVariableNameExclusionStartWith() {
    return exclusionStartWith;
  }

  @Override
  public List<String> getVariableNameExclusionEndWith() {
    return exclusionEndWith;
  }

  @Override
  public List<String> getVariableValueTypeInclusion() {
    return variableValueTypeInclusion;
  }

  @Override
  public List<String> getVariableValueTypeExclusion() {
    return variableValueTypeExclusion;
  }

  @Override
  public List<String> getBpmnProcessIdInclusion() {
    return bpmnProcessIdInclusion;
  }

  @Override
  public List<String> getBpmnProcessIdExclusion() {
    return bpmnProcessIdExclusion;
  }

  @Override
  public boolean isOptimizeModeEnabled() {
    return optimizeModeEnabled;
  }
}
