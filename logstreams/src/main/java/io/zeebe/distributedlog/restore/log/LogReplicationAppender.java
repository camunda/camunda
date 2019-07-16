/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.distributedlog.restore.log;

@FunctionalInterface
public interface LogReplicationAppender {

  /**
   * Appends a block of complete, serialized {@link io.zeebe.logstreams.log.LoggedEvent} to a log
   * stream, and updates the commit position to {@code commitPosition}.
   *
   * @param commitPosition the position of the last event in the {@code blockBuffer}
   * @param blockBuffer the buffer containing a block of log entries to be written into storage
   * @return the address at which the block has been written or error status code
   */
  long append(long commitPosition, byte[] blockBuffer);
}
