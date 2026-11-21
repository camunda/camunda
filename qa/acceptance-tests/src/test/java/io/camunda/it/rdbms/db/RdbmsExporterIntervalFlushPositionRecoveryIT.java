/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db;

import org.junit.jupiter.api.Tag;

/**
 * Runs the scenarios of {@link RdbmsExporterPositionRecoveryIT} with the default flush interval, so
 * that the periodic flush, not a record-triggered flush, detects the position mismatch and the
 * exporter must reopen from a scheduled task.
 */
@Tag("rdbms")
class RdbmsExporterIntervalFlushPositionRecoveryIT extends RdbmsExporterPositionRecoveryIT {

  @Override
  String flushInterval() {
    return "PT0.5S";
  }
}
