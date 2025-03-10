/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.config.operate;

public class ImportProperties {

  public static final int DEFAULT_VARIABLE_SIZE_THRESHOLD = 8191;
  public static final int DEFAULT_IMPORT_POSITION_UPDATE_INTERVAL = 10000;
  private static final int DEFAULT_IMPORT_THREADS_COUNT = 3;
  private static final int DEFAULT_READER_THREADS_COUNT = 3;
  private static final int DEFAULT_IMPORT_QUEUE_SIZE = 3;
  private static final int DEFAULT_READER_BACKOFF = 5000;
  private static final int DEFAULT_SCHEDULER_BACKOFF = 5000;
  private static final int DEFAULT_FLOW_NODE_TREE_CACHE_SIZE = 1000;
  private static final int DEFAULT_MAX_EMPTY_RUNS = 10;
  private static final int DEFAULT_MINIMUM_EMPTY_BATCHES_FOR_COMPLETED_READER = 5;

  private int threadsCount = DEFAULT_IMPORT_THREADS_COUNT;

  // used for cases where sequences are not valid
  private boolean useOnlyPosition = false;

  private int readerThreadsCount = DEFAULT_READER_THREADS_COUNT;

  private int queueSize = DEFAULT_IMPORT_QUEUE_SIZE;

  private int readerBackoff = DEFAULT_READER_BACKOFF;

  /**
   * The property is not used anymore. Instead of a backoff, the records reader gets rescheduled
   * once the queue has capacity.
   */
  @Deprecated(since = "8.1.0")
  private int schedulerBackoff = DEFAULT_SCHEDULER_BACKOFF;

  private int flowNodeTreeCacheSize = DEFAULT_FLOW_NODE_TREE_CACHE_SIZE;

  private int importPositionUpdateInterval = DEFAULT_IMPORT_POSITION_UPDATE_INTERVAL;

  /** Indicates, whether loading of Zeebe data should start on startup. */
  private boolean startLoadingDataOnStartup = true;

  /** Variable size under which we won't store preview separately. */
  private int variableSizeThreshold = DEFAULT_VARIABLE_SIZE_THRESHOLD;

  /**
   * When we build hierarchies for flow node instances (e.g. subprocess -> task inside subprocess)
   * and for process instances parent instance -> child instance), we normally read data only from
   * runtime indices. But it may occur that data was partially archived already. In this case import
   * process will be stuck with errors "Unable to find parent tree path for flow node instance" or
   * "Unable to find parent tree path for parent instance". This parameter allows to read parent
   * instances from archived indices. Should not be set true forever for performance reasons.
   */
  private boolean readArchivedParents = false;

  /**
   * When reading parent flow node instance from Elastic, we retry with 2 seconds delay for the case
   * when parent was imported with the previous batch but Elastic did not yet refresh the indices.
   * This may degrade import performance (especially when parent data is lost and no retry will help
   * to find it). In this case, disable the retry by setting the parameter to false.
   */
  private boolean retryReadingParents = true;

  private int maxEmptyRuns = DEFAULT_MAX_EMPTY_RUNS;

  /**
   * When migrating from the old importers to the new CamundaExporter we need to ensure that all
   * previous data have been imported before we start consuming data on the CamundaExporter.
   */
  private int completedReaderMinEmptyBatches = DEFAULT_MINIMUM_EMPTY_BATCHES_FOR_COMPLETED_READER;

  public boolean isStartLoadingDataOnStartup() {
    return startLoadingDataOnStartup;
  }

  public void setStartLoadingDataOnStartup(final boolean startLoadingDataOnStartup) {
    this.startLoadingDataOnStartup = startLoadingDataOnStartup;
  }

  public int getThreadsCount() {
    return threadsCount;
  }

  public void setThreadsCount(final int threadsCount) {
    this.threadsCount = threadsCount;
  }

  public boolean isUseOnlyPosition() {
    return useOnlyPosition;
  }

  public ImportProperties setUseOnlyPosition(final boolean useOnlyPosition) {
    this.useOnlyPosition = useOnlyPosition;
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

  public void setQueueSize(final int queueSize) {
    this.queueSize = queueSize;
  }

  public int getReaderBackoff() {
    return readerBackoff;
  }

  public void setReaderBackoff(final int readerBackoff) {
    this.readerBackoff = readerBackoff;
  }

  public int getSchedulerBackoff() {
    return schedulerBackoff;
  }

  public void setSchedulerBackoff(final int schedulerBackoff) {
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

  public void setImportPositionUpdateInterval(final int importPositionUpdateInterval) {
    this.importPositionUpdateInterval = importPositionUpdateInterval;
  }

  public boolean isReadArchivedParents() {
    return readArchivedParents;
  }

  public ImportProperties setReadArchivedParents(final boolean readArchivedParents) {
    this.readArchivedParents = readArchivedParents;
    return this;
  }

  public boolean isRetryReadingParents() {
    return retryReadingParents;
  }

  public ImportProperties setRetryReadingParents(final boolean retryReadingParents) {
    this.retryReadingParents = retryReadingParents;
    return this;
  }

  public int getMaxEmptyRuns() {
    return maxEmptyRuns;
  }

  public ImportProperties setMaxEmptyRuns(final int maxEmptyRuns) {
    this.maxEmptyRuns = maxEmptyRuns;
    return this;
  }

  public int getCompletedReaderMinEmptyBatches() {
    return completedReaderMinEmptyBatches;
  }

  public ImportProperties setCompletedReaderMinEmptyBatches(
      final int completedReaderMinEmptyBatches) {
    this.completedReaderMinEmptyBatches = completedReaderMinEmptyBatches;
    return this;
  }
}
