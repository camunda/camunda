/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.property;

public class ImportProperties {

  private static final int DEFAULT_IMPORT_THREADS_COUNT = 3;

  private static final int DEFAULT_POST_IMPORT_THREADS_COUNT = 1;

  private static final int DEFAULT_READER_THREADS_COUNT = 3;

  private static final int DEFAULT_IMPORT_QUEUE_SIZE = 3;

  private static final int DEFAULT_READER_BACKOFF = 5000;

  private static final int DEFAULT_SCHEDULER_BACKOFF = 5000;

  private static final int DEFAULT_FLOW_NODE_TREE_CACHE_SIZE = 1000;

  public static final int DEFAULT_VARIABLE_SIZE_THRESHOLD = 8191;

  public static final int DEFAULT_IMPORT_POSITION_UPDATE_INTERVAL = 10000;

  private static final int DEFAULT_MAX_EMPTY_RUNS = 10;

  private int threadsCount = DEFAULT_IMPORT_THREADS_COUNT;

  private int postImportThreadsCount = DEFAULT_POST_IMPORT_THREADS_COUNT;

  //is here for testing purposes
  private boolean postImportEnabled = true;

  private boolean postImporterIgnoreMissingData = false;

  private int readerThreadsCount = DEFAULT_READER_THREADS_COUNT;

  private int queueSize = DEFAULT_IMPORT_QUEUE_SIZE;

  private int readerBackoff = DEFAULT_READER_BACKOFF;

  /**
   * The property is not used anymore. Instead of a backoff,
   * the records reader gets rescheduled once the queue has capacity.
   */
  @Deprecated(since = "8.1.0")
  private int schedulerBackoff = DEFAULT_SCHEDULER_BACKOFF;

  private int flowNodeTreeCacheSize = DEFAULT_FLOW_NODE_TREE_CACHE_SIZE;

  private int importPositionUpdateInterval = DEFAULT_IMPORT_POSITION_UPDATE_INTERVAL;

  /**
   * Indicates, whether loading of Zeebe data should start on startup.
   */
  private boolean startLoadingDataOnStartup = true;

  /**
   * Variable size under which we won't store preview separately.
   */
  private int variableSizeThreshold = DEFAULT_VARIABLE_SIZE_THRESHOLD;

  /**
   * When we build hierarchies for flow node instances (e.g. subprocess -> task inside subprocess) and for process instances
   * parent instance -> child instance), we normally read data only from runtime indices. But it may occur that data was partially archived already.
   * In this case import process will be stuck with errors "Unable to find parent tree path for flow node instance" or
   * "Unable to find parent tree path for parent instance". This parameter allows to read parent instances from archived indices.
   * Should not be set true forever for performance reasons.
   */
  private boolean readArchivedParents = false;
  private int maxEmptyRuns = DEFAULT_MAX_EMPTY_RUNS;

  public boolean isStartLoadingDataOnStartup() {
    return startLoadingDataOnStartup;
  }

  public void setStartLoadingDataOnStartup(boolean startLoadingDataOnStartup) {
    this.startLoadingDataOnStartup = startLoadingDataOnStartup;
  }

  public int getThreadsCount() {
    return threadsCount;
  }

  public void setThreadsCount(int threadsCount) {
    this.threadsCount = threadsCount;
  }

  public int getPostImportThreadsCount() {
    return postImportThreadsCount;
  }

  public ImportProperties setPostImportThreadsCount(final int postImportThreadsCount) {
    this.postImportThreadsCount = postImportThreadsCount;
    return this;
  }

  public boolean isPostImportEnabled() {
    return postImportEnabled;
  }

  public ImportProperties setPostImportEnabled(boolean postImportEnabled) {
    this.postImportEnabled = postImportEnabled;
    return this;
  }

  public boolean isPostImporterIgnoreMissingData() {
    return postImporterIgnoreMissingData;
  }

  public ImportProperties setPostImporterIgnoreMissingData(boolean postImporterIgnoreMissingData) {
    this.postImporterIgnoreMissingData = postImporterIgnoreMissingData;
    return this;
  }

  public int getReaderThreadsCount() {
    return readerThreadsCount;
  }

  public ImportProperties setReaderThreadsCount(final int readerThreadsCount) {
    this.readerThreadsCount = readerThreadsCount;
    return this;
  }

  public int getQueueSize() {
    return queueSize;
  }

  public void setQueueSize(int queueSize) {
    this.queueSize = queueSize;
  }

  public int getReaderBackoff() {
    return readerBackoff;
  }

  public void setReaderBackoff(int readerBackoff) {
    this.readerBackoff = readerBackoff;
  }

  public int getSchedulerBackoff() {
    return schedulerBackoff;
  }

  public void setSchedulerBackoff(int schedulerBackoff) {
    this.schedulerBackoff = schedulerBackoff;
  }

  public int getFlowNodeTreeCacheSize() {
    return flowNodeTreeCacheSize;
  }

  public void setFlowNodeTreeCacheSize(final int flowNodeTreeCacheSize) {
    this.flowNodeTreeCacheSize = flowNodeTreeCacheSize;
  }

  public int getVariableSizeThreshold() {
    return variableSizeThreshold;
  }

  public ImportProperties setVariableSizeThreshold(final int variableSizeThreshold) {
    this.variableSizeThreshold = variableSizeThreshold;
    return this;
  }

  public int getImportPositionUpdateInterval() {
    return importPositionUpdateInterval;
  }

  public void setImportPositionUpdateInterval(int importPositionUpdateInterval) {
    this.importPositionUpdateInterval = importPositionUpdateInterval;
  }

  public boolean isReadArchivedParents() {
    return readArchivedParents;
  }

  public ImportProperties setReadArchivedParents(boolean readArchivedParents) {
    this.readArchivedParents = readArchivedParents;
    return this;
  }

  public int getMaxEmptyRuns() {
    return maxEmptyRuns;
  }

  public ImportProperties setMaxEmptyRuns(int maxEmptyRuns) {
    this.maxEmptyRuns = maxEmptyRuns;
    return this;
  }
}
