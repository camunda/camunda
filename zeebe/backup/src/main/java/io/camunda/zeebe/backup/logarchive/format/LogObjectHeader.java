/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.logarchive.format;

/**
 * @param term the writer's term, frames may carry lower terms
 * @param firstPosition lowest position of the first application frame, -1 if none
 * @param lastPosition highest position of the last application frame, -1 if none
 * @param headerLength total length of the header in bytes, i.e. the offset of the first frame
 */
public record LogObjectHeader(
    String timelineId,
    int partitionId,
    long term,
    long firstIndex,
    long lastIndex,
    long firstPosition,
    long lastPosition,
    int entryCount,
    int headerLength) {}
