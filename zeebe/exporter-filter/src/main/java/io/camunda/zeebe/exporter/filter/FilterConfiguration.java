/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.filter;

import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import java.util.List;

/**
 * Configuration for exporter record filtering.
 *
 * <p>Implementations define which {@link RecordType} and {@link ValueType} should be indexed by an
 * exporter. This provides a shared, backend-agnostic configuration that can be used by both the
 * Elasticsearch and OpenSearch exporters to decide which records to export, while leaving the
 * actual indexing mechanics to each exporter.
 */
public interface FilterConfiguration {

  boolean shouldIndexValueType(ValueType valueType);

  boolean shouldIndexRequiredValueType(ValueType valueType);

  boolean shouldIndexRecordType(RecordType recordType);

  IndexConfig filterIndexConfig();

  interface IndexConfig {

    // Global variable filters
    List<String> getVariableNameInclusionExact();

    List<String> getVariableNameInclusionStartWith();

    List<String> getVariableNameInclusionEndWith();

    List<String> getVariableNameExclusionExact();

    List<String> getVariableNameExclusionStartWith();

    List<String> getVariableNameExclusionEndWith();

    List<String> getVariableValueTypeInclusion();

    List<String> getVariableValueTypeExclusion();

    // Root variable filters
    default List<String> getRootVariableNameInclusionExact() {
      return List.of();
    }

    default List<String> getRootVariableNameInclusionStartWith() {
      return List.of();
    }

    default List<String> getRootVariableNameInclusionEndWith() {
      return List.of();
    }

    default List<String> getRootVariableNameExclusionExact() {
      return List.of();
    }

    default List<String> getRootVariableNameExclusionStartWith() {
      return List.of();
    }

    default List<String> getRootVariableNameExclusionEndWith() {
      return List.of();
    }

    default List<String> getRootVariableValueTypeInclusion() {
      return List.of();
    }

    default List<String> getRootVariableValueTypeExclusion() {
      return List.of();
    }

    // Local variable filters
    default List<String> getLocalVariableNameInclusionExact() {
      return List.of();
    }

    default List<String> getLocalVariableNameInclusionStartWith() {
      return List.of();
    }

    default List<String> getLocalVariableNameInclusionEndWith() {
      return List.of();
    }

    default List<String> getLocalVariableNameExclusionExact() {
      return List.of();
    }

    default List<String> getLocalVariableNameExclusionStartWith() {
      return List.of();
    }

    default List<String> getLocalVariableNameExclusionEndWith() {
      return List.of();
    }

    default List<String> getLocalVariableValueTypeInclusion() {
      return List.of();
    }

    default List<String> getLocalVariableValueTypeExclusion() {
      return List.of();
    }

    // Filter local variables
    default boolean isExportLocalVariablesEnabled() {
      return true;
    }

    // BPMN process filters
    List<String> getBpmnProcessIdInclusion();

    List<String> getBpmnProcessIdExclusion();

    // Optimize mode
    boolean isOptimizeModeEnabled();
  }
}
