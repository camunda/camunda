/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.layered;

/**
 * The durable side of persist rounds: creates atomic {@link PersistBatch}es and reads back the
 * recovery anchor on startup.
 */
public interface PersistSink {

  /** A fresh, empty batch. The caller owns committing and closing it. */
  PersistBatch newBatch();

  /**
   * The recovery anchor most recently committed through a batch's {@link
   * PersistBatch#putAnchor(long)}, or {@code -1} if none was ever committed. Recovery replays the
   * log from the position after this.
   */
  long readAnchor();
}
