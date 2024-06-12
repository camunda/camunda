/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.operate.exporter;

import io.camunda.operate.property.OperateElasticsearchProperties;

public class OperateElasticsearchExporterConfiguration {

  public final BulkConfiguration bulk = new BulkConfiguration();

  public final OperateElasticsearchProperties elasticsearch = new OperateElasticsearchProperties();

  @Override
  public String toString() {
    return "OperateElasticsearchExporterConfiguration{"
        + "bulk="
        + bulk
        + ", elasticsearch="
        + elasticsearch
        + '}';
  }

  public static class BulkConfiguration {
    // delay before forced flush
    public int delay = 5;
    // bulk size before flush
    public int size = 1_000;
    // memory limit of the bulk in bytes before flush
    public int memoryLimit = 10 * 1024 * 1024;

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
