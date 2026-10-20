/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.eventplane.service;

import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * What {@code GET /v1/groups/{group}} answers, as JSON:
 *
 * <pre>{@code
 * {"group":"g","workers":[{"worker":"w-…","beat":42}],
 *  "cursors":[{"log":"c/t/p","history":"…","index":1,"position":1,"worker":"w-…",
 *              "newestIndex":40,"lag":39}]}
 * }</pre>
 *
 * @param group the consumer group
 * @param workers the workers that have a heartbeat object, by name
 * @param cursors the cursors, by log
 */
public record GroupStatus(String group, List<Worker> workers, List<Cursor> cursors) {

  public GroupStatus {
    workers = List.copyOf(workers);
    cursors = List.copyOf(cursors);
  }

  /**
   * A worker's heartbeat object. The worker is live while {@code beat} keeps changing, timed on the
   * reader's clock.
   *
   * @param worker the worker's name
   * @param beat how many times the worker rewrote its heartbeat
   */
  public record Worker(String worker, long beat) {}

  /**
   * A group's cursor in one log, and how far behind the log's newest segment it is.
   *
   * @param log the log, as {@code {cluster}/{tenant}/{partition}}
   * @param history the history that the cursor is in
   * @param index the Raft index that the cursor is at
   * @param position the record position that the cursor is at
   * @param worker the worker that holds the log, if the cursor says
   * @param newestIndex the last index of the newest segment of the cursor's history that the
   *     service knows of, from a notice or a listing, or null if it knows of none
   * @param lag how many indexes the cursor is behind {@code newestIndex}, 0 or more
   */
  public record Cursor(
      String log,
      UUID history,
      long index,
      long position,
      @Nullable String worker,
      @Nullable Long newestIndex,
      long lag) {}
}
