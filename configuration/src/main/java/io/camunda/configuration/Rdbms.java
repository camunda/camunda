/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import io.camunda.db.rdbms.write.RdbmsWriterConfig;
import io.camunda.exporter.rdbms.ExporterConfiguration;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

public class Rdbms extends SecondaryStorageDatabase<RdbmsHistory> {

  /** Endpoint for the database configured as secondary storage. */
  private String url;

  /** Username for the database configured as secondary storage. */
  private String username;

  /** Password for the database configured as secondary storage. */
  private String password;

  private String prefix;

  /** The interval at which the exporters execution queue is flushed. */
  private String flushInterval = ExporterConfiguration.DEFAULT_FLUSH_INTERVAL.toString();

  /** The maximum size of the exporters execution queue before it is flushed to the database. */
  private Integer queueSize = RdbmsWriterConfig.DEFAULT_QUEUE_SIZE;

  /** Process definition cache configuration. Defines the size of the process definition cache. */
  private RdbmsCache processCache;

  /** Batch operation cache configuration. Defines the size of the batch operation cache. */
  private RdbmsCache batchOperationCache;

  /**
   * If true, batch operation items are exported to the database when the batch operation is created
   * (status = ACTIVE). If false, the items are created on demand when they have been processed.
   * When set to true, this ensures that the items are available when the batch operation is
   * created, but it may lead to a delay in the creation of the batch operation if there are many
   * items to create.
   */
  private boolean exportBatchOperationItemsOnCreation =
      RdbmsWriterConfig.DEFAULT_EXPORT_BATCH_OPERATION_ITEMS_ON_CREATION;

  /**
   * The number of batch operation items to insert in a single batched SQL when creating the items
   * for a batch operation. This is only relevant when exportBatchOperationItemsOnCreation is set to
   * true.
   */
  private int batchOperationItemInsertBlockSize =
      RdbmsWriterConfig.DEFAULT_BATCH_OPERATION_ITEM_INSERT_BLOCK_SIZE;

  @NestedConfigurationProperty private RdbmsHistory history = new RdbmsHistory();

  public String getPrefix() {
    return prefix;
  }

  public void setPrefix(final String prefix) {
    this.prefix = prefix;
  }

  public String getFlushInterval() {
    return flushInterval;
  }

  public void setFlushInterval(final String flushInterval) {
    this.flushInterval = flushInterval;
  }

  public Integer getQueueSize() {
    return queueSize;
  }

  public void setQueueSize(final Integer queueSize) {
    this.queueSize = queueSize;
  }

  public RdbmsCache getProcessCache() {
    return processCache;
  }

  public void setProcessCache(final RdbmsCache processCache) {
    this.processCache = processCache;
  }

  public RdbmsCache getBatchOperationCache() {
    return batchOperationCache;
  }

  public void setBatchOperationCache(final RdbmsCache batchOperationCache) {
    this.batchOperationCache = batchOperationCache;
  }

  public boolean isExportBatchOperationItemsOnCreation() {
    return exportBatchOperationItemsOnCreation;
  }

  public void setExportBatchOperationItemsOnCreation(
      final boolean exportBatchOperationItemsOnCreation) {
    this.exportBatchOperationItemsOnCreation = exportBatchOperationItemsOnCreation;
  }

  public int getBatchOperationItemInsertBlockSize() {
    return batchOperationItemInsertBlockSize;
  }

  public void setBatchOperationItemInsertBlockSize(final int batchOperationItemInsertBlockSize) {
    this.batchOperationItemInsertBlockSize = batchOperationItemInsertBlockSize;
  }

  @Override
  public String getUrl() {
    return url;
  }

  @Override
  public void setUrl(final String url) {
    this.url = url;
  }

  @Override
  public String getUsername() {
    return username;
  }

  @Override
  public void setUsername(final String username) {
    this.username = username;
  }

  @Override
  public String getPassword() {
    return password;
  }

  @Override
  public void setPassword(final String password) {
    this.password = password;
  }

  @Override
  public RdbmsHistory getHistory() {
    return history;
  }

  @Override
  public void setHistory(final RdbmsHistory history) {
    this.history = history;
  }

  @Override
  public String databaseName() {
    return "rdbms";
  }
}
