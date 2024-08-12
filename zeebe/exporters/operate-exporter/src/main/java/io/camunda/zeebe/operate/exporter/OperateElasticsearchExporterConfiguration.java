/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.operate.exporter;

import io.camunda.zeebe.operate.exporter.schema.ElasticSearchProperties;

public record OperateElasticsearchExporterConfiguration(
    final BulkConfiguration bulk, final ElasticSearchProperties elasticSearch) {

  public static class BulkConfiguration {
    // delay before forced flush
    public final int delay = 5;
    // bulk size before flush
    public final int size = 1_000;
    // memory limit of the bulk in bytes before flush
    public final int memoryLimit = 10 * 1024 * 1024;

    @Override
    public String toString() {
      return "BulkConfiguration{"
          + "delay="
          + delay
          + ", size="
          + size
          + ", memoryLimit="
          + memoryLimit
          + '}';
    }
  }
}
