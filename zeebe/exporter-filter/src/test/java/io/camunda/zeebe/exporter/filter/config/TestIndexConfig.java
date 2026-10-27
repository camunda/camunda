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
  private boolean exportLocalVariablesEnabled = true;

  // Local variable name/type filters
  private List<String> localVariableNameInclusionExact = List.of();
  private List<String> localVariableNameInclusionStartWith = List.of();
  private List<String> localVariableNameInclusionEndWith = List.of();
  private List<String> localVariableNameExclusionExact = List.of();
  private List<String> localVariableNameExclusionStartWith = List.of();
  private List<String> localVariableNameExclusionEndWith = List.of();
  private List<String> localVariableValueTypeInclusion = List.of();
  private List<String> localVariableValueTypeExclusion = List.of();

  // Root variable name/type filters
  private List<String> rootVariableNameInclusionExact = List.of();
  private List<String> rootVariableNameInclusionStartWith = List.of();
  private List<String> rootVariableNameInclusionEndWith = List.of();
  private List<String> rootVariableNameExclusionExact = List.of();
  private List<String> rootVariableNameExclusionStartWith = List.of();
  private List<String> rootVariableNameExclusionEndWith = List.of();
  private List<String> rootVariableValueTypeInclusion = List.of();
  private List<String> rootVariableValueTypeExclusion = List.of();

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

  public TestIndexConfig withExportLocalVariablesEnabled(
      final boolean exportLocalVariablesEnabled) {
    this.exportLocalVariablesEnabled = exportLocalVariablesEnabled;
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

  public TestIndexConfig withLocalVariableNameInclusionExact(final List<String> names) {
    localVariableNameInclusionExact = names != null ? names : List.of();
    return this;
  }

  public TestIndexConfig withLocalVariableNameInclusionStartWith(final List<String> prefixes) {
    localVariableNameInclusionStartWith = prefixes != null ? prefixes : List.of();
    return this;
  }

  public TestIndexConfig withLocalVariableNameInclusionEndWith(final List<String> suffixes) {
    localVariableNameInclusionEndWith = suffixes != null ? suffixes : List.of();
    return this;
  }

  public TestIndexConfig withLocalVariableNameExclusionExact(final List<String> names) {
    localVariableNameExclusionExact = names != null ? names : List.of();
    return this;
  }

  public TestIndexConfig withLocalVariableNameExclusionStartWith(final List<String> prefixes) {
    localVariableNameExclusionStartWith = prefixes != null ? prefixes : List.of();
    return this;
  }

  public TestIndexConfig withLocalVariableNameExclusionEndWith(final List<String> suffixes) {
    localVariableNameExclusionEndWith = suffixes != null ? suffixes : List.of();
    return this;
  }

  public TestIndexConfig withLocalVariableValueTypeInclusion(final List<String> types) {
    localVariableValueTypeInclusion = types != null ? types : List.of();
    return this;
  }

  public TestIndexConfig withLocalVariableValueTypeExclusion(final List<String> types) {
    localVariableValueTypeExclusion = types != null ? types : List.of();
    return this;
  }

  public TestIndexConfig withRootVariableNameInclusionExact(final List<String> names) {
    rootVariableNameInclusionExact = names != null ? names : List.of();
    return this;
  }

  public TestIndexConfig withRootVariableNameInclusionStartWith(final List<String> prefixes) {
    rootVariableNameInclusionStartWith = prefixes != null ? prefixes : List.of();
    return this;
  }

  public TestIndexConfig withRootVariableNameInclusionEndWith(final List<String> suffixes) {
    rootVariableNameInclusionEndWith = suffixes != null ? suffixes : List.of();
    return this;
  }

  public TestIndexConfig withRootVariableNameExclusionExact(final List<String> names) {
    rootVariableNameExclusionExact = names != null ? names : List.of();
    return this;
  }

  public TestIndexConfig withRootVariableNameExclusionStartWith(final List<String> prefixes) {
    rootVariableNameExclusionStartWith = prefixes != null ? prefixes : List.of();
    return this;
  }

  public TestIndexConfig withRootVariableNameExclusionEndWith(final List<String> suffixes) {
    rootVariableNameExclusionEndWith = suffixes != null ? suffixes : List.of();
    return this;
  }

  public TestIndexConfig withRootVariableValueTypeInclusion(final List<String> types) {
    rootVariableValueTypeInclusion = types != null ? types : List.of();
    return this;
  }

  public TestIndexConfig withRootVariableValueTypeExclusion(final List<String> types) {
    rootVariableValueTypeExclusion = types != null ? types : List.of();
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

  @Override
  public boolean isExportLocalVariablesEnabled() {
    return exportLocalVariablesEnabled;
  }

  @Override
  public List<String> getLocalVariableNameInclusionExact() {
    return localVariableNameInclusionExact;
  }

  @Override
  public List<String> getLocalVariableNameInclusionStartWith() {
    return localVariableNameInclusionStartWith;
  }

  @Override
  public List<String> getLocalVariableNameInclusionEndWith() {
    return localVariableNameInclusionEndWith;
  }

  @Override
  public List<String> getLocalVariableNameExclusionExact() {
    return localVariableNameExclusionExact;
  }

  @Override
  public List<String> getLocalVariableNameExclusionStartWith() {
    return localVariableNameExclusionStartWith;
  }

  @Override
  public List<String> getLocalVariableNameExclusionEndWith() {
    return localVariableNameExclusionEndWith;
  }

  @Override
  public List<String> getLocalVariableValueTypeInclusion() {
    return localVariableValueTypeInclusion;
  }

  @Override
  public List<String> getLocalVariableValueTypeExclusion() {
    return localVariableValueTypeExclusion;
  }

  @Override
  public List<String> getRootVariableNameInclusionExact() {
    return rootVariableNameInclusionExact;
  }

  @Override
  public List<String> getRootVariableNameInclusionStartWith() {
    return rootVariableNameInclusionStartWith;
  }

  @Override
  public List<String> getRootVariableNameInclusionEndWith() {
    return rootVariableNameInclusionEndWith;
  }

  @Override
  public List<String> getRootVariableNameExclusionExact() {
    return rootVariableNameExclusionExact;
  }

  @Override
  public List<String> getRootVariableNameExclusionStartWith() {
    return rootVariableNameExclusionStartWith;
  }

  @Override
  public List<String> getRootVariableNameExclusionEndWith() {
    return rootVariableNameExclusionEndWith;
  }

  @Override
  public List<String> getRootVariableValueTypeInclusion() {
    return rootVariableValueTypeInclusion;
  }

  @Override
  public List<String> getRootVariableValueTypeExclusion() {
    return rootVariableValueTypeExclusion;
  }
}
