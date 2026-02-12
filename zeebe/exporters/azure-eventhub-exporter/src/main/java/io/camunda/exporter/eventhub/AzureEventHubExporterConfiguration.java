/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.eventhub;

import io.camunda.zeebe.exporter.filter.FilterConfiguration;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import java.util.Collections;
import java.util.List;

public class AzureEventHubExporterConfiguration implements FilterConfiguration {

  // Connection settings
  private String connectionString;
  private String eventHubName;

  // Batch settings
  private int maxBatchSize = 100;
  private long batchIntervalMs = 1000L;

  // Filter settings
  private final IndexConfiguration index = new IndexConfiguration();

  public String getConnectionString() {
    return connectionString;
  }

  public AzureEventHubExporterConfiguration setConnectionString(final String connectionString) {
    this.connectionString = connectionString;
    return this;
  }

  public String getEventHubName() {
    return eventHubName;
  }

  public AzureEventHubExporterConfiguration setEventHubName(final String eventHubName) {
    this.eventHubName = eventHubName;
    return this;
  }

  public int getMaxBatchSize() {
    return maxBatchSize;
  }

  public AzureEventHubExporterConfiguration setMaxBatchSize(final int maxBatchSize) {
    this.maxBatchSize = maxBatchSize;
    return this;
  }

  public long getBatchIntervalMs() {
    return batchIntervalMs;
  }

  public AzureEventHubExporterConfiguration setBatchIntervalMs(final long batchIntervalMs) {
    this.batchIntervalMs = batchIntervalMs;
    return this;
  }

  public void validate() {
    if (connectionString == null || connectionString.isEmpty()) {
      throw new IllegalArgumentException(
          "Azure Event Hub connection string must be configured");
    }
    if (eventHubName == null || eventHubName.isEmpty()) {
      throw new IllegalArgumentException("Azure Event Hub name must be configured");
    }
    if (maxBatchSize <= 0) {
      throw new IllegalArgumentException("Max batch size must be greater than 0");
    }
    if (batchIntervalMs <= 0) {
      throw new IllegalArgumentException("Batch interval must be greater than 0");
    }
  }

  @Override
  public boolean shouldIndexValueType(final ValueType valueType) {
    // Accept all value types by default
    return true;
  }

  @Override
  public boolean shouldIndexRequiredValueType(final ValueType valueType) {
    // No required value types filtering for this exporter
    return false;
  }

  @Override
  public boolean shouldIndexRecordType(final RecordType recordType) {
    return switch (recordType) {
      case EVENT -> index.event;
      case COMMAND -> index.command;
      case COMMAND_REJECTION -> index.rejection;
      default -> false;
    };
  }

  @Override
  public IndexConfig filterIndexConfig() {
    return index;
  }

  public static class IndexConfiguration implements IndexConfig {
    // Record type filters - default to exporting only events
    public boolean event = true;
    public boolean command = false;
    public boolean rejection = false;

    @Override
    public List<String> getVariableNameInclusionExact() {
      return Collections.emptyList();
    }

    @Override
    public List<String> getVariableNameInclusionStartWith() {
      return Collections.emptyList();
    }

    @Override
    public List<String> getVariableNameInclusionEndWith() {
      return Collections.emptyList();
    }

    @Override
    public List<String> getVariableNameExclusionExact() {
      return Collections.emptyList();
    }

    @Override
    public List<String> getVariableNameExclusionStartWith() {
      return Collections.emptyList();
    }

    @Override
    public List<String> getVariableNameExclusionEndWith() {
      return Collections.emptyList();
    }

    @Override
    public List<String> getVariableValueTypeInclusion() {
      return Collections.emptyList();
    }

    @Override
    public List<String> getVariableValueTypeExclusion() {
      return Collections.emptyList();
    }
  }
}
