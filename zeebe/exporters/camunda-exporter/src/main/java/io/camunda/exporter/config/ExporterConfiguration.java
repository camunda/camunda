/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.config;

public class ExporterConfiguration {
  public final BulkConfiguration bulk = new BulkConfiguration();
  public final ElasticsearchProperties elasticsearch = new ElasticsearchProperties();

  @Override
  public String toString() {
    return "ExporterConfiguration{" + "bulk=" + bulk + ", elasticsearch=" + elasticsearch + '}';
  }

  public static class BulkConfiguration {
    // delay before forced flush
    private int delay = 5;
    // bulk size before flush
    private int size = 1_000;
    // memory limit of the bulk in bytes before flush
    private int memoryLimit = 10 * 1024 * 1024;

    public int getDelay() {
      return delay;
    }

    public void setDelay(final int delay) {
      this.delay = delay;
    }

    public int getSize() {
      return size;
    }

    public void setSize(final int size) {
      this.size = size;
    }

    public int getMemoryLimit() {
      return memoryLimit;
    }

    public void setMemoryLimit(final int memoryLimit) {
      this.memoryLimit = memoryLimit;
    }

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
