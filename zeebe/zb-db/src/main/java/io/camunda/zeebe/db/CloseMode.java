/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db;

/** How much work a database may do when it is closed. */
public enum CloseMode {
  /** Persist everything still buffered before closing, so the files on disk are complete. */
  FLUSH,
  /**
   * Skip persisting buffered data when closing. Only suitable when the on-disk state is derived and
   * will be discarded or rebuilt from a snapshot anyway.
   */
  FAST
}
