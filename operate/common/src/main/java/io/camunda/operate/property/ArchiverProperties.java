/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.property;

public class ArchiverProperties {

  private static final int DEFAULT_ARCHIVER_THREADS_COUNT = 1;
  private static final int DEFAULT_ROLLOVER_BATCH_SIZE = 100;
  private static final int DEFAULT_ARCHIVE_BY_ID_ROLLOVER_BATCH_SIZE = 500;

  private boolean rolloverEnabled = true;

  private int threadsCount = DEFAULT_ARCHIVER_THREADS_COUNT;

  /**
   * This format will be used to create timed indices. It must correspond to rolloverInterval
   * parameter.
   */
  private String rolloverDateFormat = "yyyy-MM-dd";

  private String elsRolloverDateFormat = "date";

  /**
   * Interval description for "date histogram" aggregation, which is used to group finished
   * instances.
   *
   * @see <a
   *     href="https://www.elastic.co/guide/en/elasticsearch/reference/current/search-aggregations-bucket-datehistogram-aggregation.html">Elasticsearch
   *     docs</a>
   */
  private String rolloverInterval = "1d";

  private Integer rolloverBatchSize;

  private String waitPeriodBeforeArchiving = "1h";

  private boolean ilmEnabled = false; // default due to usage of curator

  private String ilmMinAgeForDeleteArchivedIndices = "30d";

  /**
   * In case archiver runs without delays, two subsequent runs may try to process the same process
   * entities (because of Elasticsearch refresh behaviour). In general, it's fine, but there are two
   * side effects: 1. We do the job, that is not needed anymore -> wasting CPU time 2. Metrics will
   * become incorrect, as it's not possible to distinguish such (duplicated) calls from normal ones.
   */
  private int delayBetweenRuns = 2000;

  private boolean archiveByIdEnabled = false;

  private int archiveByIdBatchSize = 500;

  private int archiveByIdMaxRetryAttempts = 3;

  private int archiveByIdRetryDelayMs = 1000;

  public boolean isArchiveByIdEnabled() {
    return archiveByIdEnabled;
  }

  public void setArchiveByIdEnabled(final boolean archiveByIdEnabled) {
    this.archiveByIdEnabled = archiveByIdEnabled;
  }

  public int getArchiveByIdBatchSize() {
    return archiveByIdBatchSize;
  }

  public void setArchiveByIdBatchSize(final int archiveByIdBatchSize) {
    this.archiveByIdBatchSize = archiveByIdBatchSize;
  }

  public int getArchiveByIdMaxRetryAttempts() {
    return archiveByIdMaxRetryAttempts;
  }

  public void setArchiveByIdMaxRetryAttempts(final int archiveByIdMaxRetryAttempts) {
    this.archiveByIdMaxRetryAttempts = archiveByIdMaxRetryAttempts;
  }

  public int getArchiveByIdRetryDelayMs() {
    return archiveByIdRetryDelayMs;
  }

  public void setArchiveByIdRetryDelayMs(final int archiveByIdRetryDelayMs) {
    this.archiveByIdRetryDelayMs = archiveByIdRetryDelayMs;
  }

  public String getIlmMinAgeForDeleteArchivedIndices() {
    return ilmMinAgeForDeleteArchivedIndices;
  }

  public void setIlmMinAgeForDeleteArchivedIndices(final String ilmMinAgeForDeleteArchivedIndices) {
    this.ilmMinAgeForDeleteArchivedIndices = ilmMinAgeForDeleteArchivedIndices;
  }

  public boolean isIlmEnabled() {
    return ilmEnabled;
  }

  public void setIlmEnabled(final boolean ilmEnabled) {
    this.ilmEnabled = ilmEnabled;
  }

  public boolean isRolloverEnabled() {
    return rolloverEnabled;
  }

  public void setRolloverEnabled(final boolean rolloverEnabled) {
    this.rolloverEnabled = rolloverEnabled;
  }

  public String getRolloverDateFormat() {
    return rolloverDateFormat;
  }

  public void setRolloverDateFormat(final String rolloverDateFormat) {
    this.rolloverDateFormat = rolloverDateFormat;
  }

  public String getElsRolloverDateFormat() {
    return elsRolloverDateFormat;
  }

  public void setElsRolloverDateFormat(final String elsRolloverDateFormat) {
    this.elsRolloverDateFormat = elsRolloverDateFormat;
  }

  public String getRolloverInterval() {
    return rolloverInterval;
  }

  public void setRolloverInterval(final String rolloverInterval) {
    this.rolloverInterval = rolloverInterval;
  }

  public int getRolloverBatchSize() {
    if (rolloverBatchSize == null) {
      return archiveByIdEnabled
          ? DEFAULT_ARCHIVE_BY_ID_ROLLOVER_BATCH_SIZE
          : DEFAULT_ROLLOVER_BATCH_SIZE;
    }
    return rolloverBatchSize;
  }

  public void setRolloverBatchSize(final int rolloverBatchSize) {
    this.rolloverBatchSize = rolloverBatchSize;
  }

  public int getThreadsCount() {
    return threadsCount;
  }

  public void setThreadsCount(final int threadsCount) {
    this.threadsCount = threadsCount;
  }

  public String getWaitPeriodBeforeArchiving() {
    return waitPeriodBeforeArchiving;
  }

  public void setWaitPeriodBeforeArchiving(final String waitPeriodBeforeArchiving) {
    this.waitPeriodBeforeArchiving = waitPeriodBeforeArchiving;
  }

  public String getArchivingTimepoint() {
    return "now-" + waitPeriodBeforeArchiving;
  }

  public int getDelayBetweenRuns() {
    return delayBetweenRuns;
  }

  public void setDelayBetweenRuns(final int delayBetweenRuns) {
    this.delayBetweenRuns = delayBetweenRuns;
  }
}
