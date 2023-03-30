/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.property;

public class ArchiverProperties {

  private static final int DEFAULT_ARCHIVER_THREADS_COUNT = 1;

  private boolean rolloverEnabled = true;

  private int threadsCount = DEFAULT_ARCHIVER_THREADS_COUNT;

  /**
   * This format will be used to create timed indices. It must correspond to rolloverInterval parameter.
   */
  private String rolloverDateFormat = "yyyy-MM-dd";
  private String elsRolloverDateFormat = "date";
  /**
   * Interval description for "date histogram" aggregation, which is used to group finished instances.
   * @see <a href="https://www.elastic.co/guide/en/elasticsearch/reference/current/search-aggregations-bucket-datehistogram-aggregation.html">Elasticsearch docs</a>
   */
  private String rolloverInterval = "1d";

  private int rolloverBatchSize = 100;

  private String waitPeriodBeforeArchiving = "1h";

  private boolean ilmEnabled = false; // default due to usage of curator

  private String ilmMinAgeForDeleteArchivedIndices = "30d";

  /**
   * In case archiver runs without delays, two subsequent runs may try to process the same process entities (because of Elasticsearch refresh behaviour).
   * In general, it's fine, but there are two side effects:
   * 1. We do the job, that is not needed anymore -> wasting CPU time
   * 2. Metrics will become incorrect, as it's not possible to distinguish such (duplicated) calls from normal ones.
   */
  private int delayBetweenRuns = 2000;

  public String getIlmMinAgeForDeleteArchivedIndices() {
    return ilmMinAgeForDeleteArchivedIndices;
  }
  public void setIlmMinAgeForDeleteArchivedIndices(String ilmMinAgeForDeleteArchivedIndices) {
    this.ilmMinAgeForDeleteArchivedIndices = ilmMinAgeForDeleteArchivedIndices;
  }

  public boolean isIlmEnabled() {
    return ilmEnabled;
  }

  public void setIlmEnabled(boolean ilmEnabled) {
    this.ilmEnabled = ilmEnabled;
  }

  public boolean isRolloverEnabled() {
    return rolloverEnabled;
  }

  public void setRolloverEnabled(boolean rolloverEnabled) {
    this.rolloverEnabled = rolloverEnabled;
  }

  public String getRolloverDateFormat() {
    return rolloverDateFormat;
  }

  public void setRolloverDateFormat(String rolloverDateFormat) {
    this.rolloverDateFormat = rolloverDateFormat;
  }

  public String getElsRolloverDateFormat() {
    return elsRolloverDateFormat;
  }

  public void setElsRolloverDateFormat(String elsRolloverDateFormat) {
    this.elsRolloverDateFormat = elsRolloverDateFormat;
  }

  public String getRolloverInterval() {
    return rolloverInterval;
  }

  public void setRolloverInterval(String rolloverInterval) {
    this.rolloverInterval = rolloverInterval;
  }

  public int getRolloverBatchSize() {
    return rolloverBatchSize;
  }

  public void setRolloverBatchSize(int rolloverBatchSize) {
    this.rolloverBatchSize = rolloverBatchSize;
  }

  public int getThreadsCount() {
    return threadsCount;
  }

  public void setThreadsCount(int threadsCount) {
    this.threadsCount = threadsCount;
  }

  public String getWaitPeriodBeforeArchiving() {
    return waitPeriodBeforeArchiving;
  }

  public void setWaitPeriodBeforeArchiving(String waitPeriodBeforeArchiving) {
    this.waitPeriodBeforeArchiving = waitPeriodBeforeArchiving;
  }

  public String getArchivingTimepoint() {
    return "now-" + waitPeriodBeforeArchiving;
  }

  public int getDelayBetweenRuns() {
    return delayBetweenRuns;
  }

  public void setDelayBetweenRuns(int delayBetweenRuns) {
    this.delayBetweenRuns = delayBetweenRuns;
  }
}
