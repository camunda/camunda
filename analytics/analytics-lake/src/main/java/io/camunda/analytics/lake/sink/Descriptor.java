/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.sink;

import java.util.List;

/**
 * The control-path message for one finished flush of one table: which files now exist and what
 * source range they cover. This is the unit the committer (or today's direct commit) turns into one
 * atomic Iceberg commit of files + offset stamp + frontier stamp — rows and position land together
 * or not at all.
 *
 * @param table Iceberg table name
 * @param sourcePartition event-bridge partition id these rows came from
 * @param files the flush's data files (primary day file(s) + optional spill file)
 * @param firstOffset first source offset covered by this flush (inclusive)
 * @param lastOffset last source offset covered (inclusive) — the offset stamp's new value
 * @param localFrontierMs this partition's frontier at flush time: min start of open instances, or
 *     the event-time watermark when none are open
 */
public record Descriptor(
    String table,
    int sourcePartition,
    List<DataFileResult> files,
    long firstOffset,
    long lastOffset,
    long localFrontierMs) {

  public Descriptor {
    files = List.copyOf(files);
  }
}
