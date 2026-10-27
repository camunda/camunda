/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.exporter.stream;

/** The outcome of {@link ExporterContainer#exportRecord} for a single record. */
enum ExportOutcome {
  /** The record was exported (or skipped as already up to date) successfully. */
  EXPORTED,

  /** The export failed and should be retried against the same record. */
  RETRY,

  /**
   * The exporter reopened and had a mid-run replay request accepted, rewinding the shared log
   * reader. The current record is abandoned rather than retried immediately, so it - and everything
   * the exporter missed before it - is redelivered strictly in order once reading resumes from the
   * rewound position.
   */
  ABORT_REPLAY,
}
