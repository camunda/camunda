/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.logstreams.state;

public interface SnapshotChunk {

  /** @return the lower bound snapshot position, identifies the corresponding snapshot */
  long getSnapshotPosition();

  /** @return the total count of snapshot chunks, which correspond to the same snapshot */
  int getTotalCount();

  /** @return the name of the current chunk (e.g. file name) */
  String getChunkName();

  /** @return the checksum of the content, can be use to verify the integrity of the content */
  long getChecksum();

  /** @return the content of the current chunk */
  byte[] getContent();
}
