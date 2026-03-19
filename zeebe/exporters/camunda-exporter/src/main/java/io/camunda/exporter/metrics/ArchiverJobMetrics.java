/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.metrics;

import static io.camunda.exporter.metrics.CamundaArchiverMetrics.ARCHIVE_OPERATION_STAGE_TAG_COPY;
import static io.camunda.exporter.metrics.CamundaArchiverMetrics.ARCHIVE_OPERATION_STAGE_TAG_DELETE;
import static io.camunda.exporter.metrics.CamundaArchiverMetrics.ARCHIVE_OPERATION_STAGE_TAG_READ;

import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.Timer.Sample;

/**
 * Wrapper for CamundaArchiverMetrics along with the jobName. This simply call the respective method
 * in CamundaArchiverMetrics with the specified jobName.
 */
public class ArchiverJobMetrics {

  final String jobName;
  final CamundaArchiverMetrics archiverMetrics;

  public ArchiverJobMetrics(final String jobName, final CamundaArchiverMetrics archiverMetrics) {
    this.jobName = jobName;
    this.archiverMetrics = archiverMetrics;
  }

  public void measureArchivingSuccessDuration(final Sample timer) {
    archiverMetrics.measureArchivingSuccessDuration(jobName, timer);
  }

  public void measureArchivingFailedDuration(final Sample timer) {
    archiverMetrics.measureArchivingFailedDuration(jobName, timer);
  }

  public void measureArchivingBatchSize(final int batchSize) {
    archiverMetrics.measureArchivingBatchSize(jobName, batchSize);
  }

  public void measureArchivedInstanceCount(final int batchSize) {
    archiverMetrics.measureArchivedInstanceCount(jobName, batchSize);
  }

  public void measureArchiverRequestSearchDuration(final Timer.Sample sample) {
    archiverMetrics.measureArchiverSearch(sample);
  }

  public void measureArchiverReindexDuration(final Sample timer) {
    archiverMetrics.measureArchiverReindex(timer);
  }

  public void measureArchiverDeleteDuration(final Sample timer) {
    archiverMetrics.measureArchiverDelete(timer);
  }

  public void measureArchiverReadSuccess(
      final String sourceIdx, final Sample timer, final Long noOfDocs) {
    archiverMetrics.measureSuccessfulArchiverStageMetrics(
        jobName, sourceIdx, ARCHIVE_OPERATION_STAGE_TAG_READ, timer, noOfDocs);
  }

  public void measureArchiverReadFailure(
      final String sourceIdx, final Sample timer, final Long noOfDocs, final Throwable throwable) {
    archiverMetrics.measureFailedArchiverStageMetrics(
        jobName, sourceIdx, ARCHIVE_OPERATION_STAGE_TAG_READ, timer, noOfDocs, throwable);
  }

  public void measureArchiverCopySuccess(
      final String sourceIdx, final Sample timer, final Long noOfDocs) {
    archiverMetrics.measureSuccessfulArchiverStageMetrics(
        jobName, sourceIdx, ARCHIVE_OPERATION_STAGE_TAG_COPY, timer, noOfDocs);
  }

  public void measureArchiverCopyFailure(
      final String sourceIdx, final Sample timer, final Long noOfDocs, final Throwable throwable) {
    archiverMetrics.measureFailedArchiverStageMetrics(
        jobName, sourceIdx, ARCHIVE_OPERATION_STAGE_TAG_COPY, timer, noOfDocs, throwable);
  }

  public void measureArchiverDeleteSuccess(
      final String sourceIdx, final Sample timer, final Long noOfDocs) {
    archiverMetrics.measureSuccessfulArchiverStageMetrics(
        jobName, sourceIdx, ARCHIVE_OPERATION_STAGE_TAG_DELETE, timer, noOfDocs);
  }

  public void measureArchiverDeleteFailure(
      final String sourceIdx, final Sample timer, final Long noOfDocs, final Throwable throwable) {
    archiverMetrics.measureFailedArchiverStageMetrics(
        jobName, sourceIdx, ARCHIVE_OPERATION_STAGE_TAG_DELETE, timer, noOfDocs, throwable);
  }
}
