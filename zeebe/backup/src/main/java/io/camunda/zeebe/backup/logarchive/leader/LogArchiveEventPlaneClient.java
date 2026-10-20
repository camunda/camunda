/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.logarchive.leader;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Transport to the event plane (spec section 19.1). One call announces a batch of consecutive
 * commit entries of one timeline of one partition.
 */
public interface LogArchiveEventPlaneClient {

  /**
   * Sends the entries, each the raw bytes of a {@code commits/<seq>.json} object, ascending and
   * consecutive. The future fails on connection errors and timeouts; any answer of the event plane
   * completes it with its status.
   */
  CompletableFuture<Answer> send(int partitionId, String timelineId, List<byte[]> entries);

  /**
   * The event plane's answer.
   *
   * @param status the HTTP status
   * @param nextSeq the seq the event plane expects next (200 and 409), or -1 if the answer has none
   */
  record Answer(int status, long nextSeq) {}
}
