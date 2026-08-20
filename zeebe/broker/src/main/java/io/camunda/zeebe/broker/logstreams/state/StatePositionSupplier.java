/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.logstreams.state;

/** Supplies various positions from the persisted state. */
public interface StatePositionSupplier {

  /** Returns the lowest position across all exporters. */
  long getLowestExportedPosition();

  /** Returns the highest position across all exporters. */
  long getHighestExportedPosition();

  /** Returns the highest backup position. */
  long getHighestBackupPosition();
}
