/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.filter;

import io.camunda.zeebe.util.SemanticVersion;

/**
 * Marks a filter as only applicable from a given broker/record version onward.
 *
 * <p>When used in an {@link ExportRecordFilterChain}, filters implementing this interface are
 * applied only if the record's broker version is greater than or equal to {@link
 * #minRecordBrokerVersion()}. For older records, the filter is skipped and treated as if it had
 * accepted the record.
 */
@FunctionalInterface
public interface RecordVersionFilter {

  /**
   * The minimum broker/record version (e.g. {@code "8.9.0"}) from which this filter should be
   * applied.
   */
  SemanticVersion minRecordBrokerVersion();
}
