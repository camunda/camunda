/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.distributedlog.restore.log;

public interface LogReplicationRequest {

  /** @return true if the response must include {@link #getFromPosition()} */
  boolean includeFromPosition();

  /** @return the position to replication from (exclusive) */
  long getFromPosition();

  /** @return upper bound position to replicate until (inclusive) */
  long getToPosition();
}
