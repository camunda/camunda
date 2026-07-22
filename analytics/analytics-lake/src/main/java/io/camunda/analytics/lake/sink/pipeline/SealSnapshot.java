/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.sink.pipeline;

/**
 * The offset range and frontier the poll thread had observed at the exact moment it sealed a
 * file-boundary segment (TIME_DUE / SIZE_CAP / SHUTDOWN) — captured then and there so the flush
 * thread never has to read {@link SinkPipeline}'s "current" running offset/frontier fields later,
 * which could by then have moved past what this window's files actually contain (the poll thread
 * keeps ticking while the flush thread is still catching up). See {@link SinkPipeline} for where
 * this is produced and {@link FlushLoop} for where it's consumed.
 */
record SealSnapshot(long firstOffset, long lastOffset, long frontierMs) {}
