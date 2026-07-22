/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.sink.pipeline;

import java.util.Map;

/**
 * The offset range, frontier, and Zeebe origin-position dedup watermarks the poll thread had
 * observed at the exact moment it sealed a file-boundary segment (TIME_DUE / SIZE_CAP / SHUTDOWN) —
 * captured then and there so the flush thread never has to read {@link SinkPipeline}'s "current"
 * running offset/frontier/watermark fields later, which could by then have moved past what this
 * window's files actually contain (the poll thread keeps ticking while the flush thread is still
 * catching up). See {@link SinkPipeline} for where this is produced and {@link FlushLoop} for where
 * it's consumed.
 *
 * @param zeebeWatermarks an immutable point-in-time copy of {@code
 *     io.camunda.analytics.lake.translate.LakeTranslator#watermarkSnapshot()} at seal time — see
 *     that method's own javadoc; carried onward via {@link
 *     io.camunda.analytics.lake.sink.Descriptor} for {@code DirectCommitSink} to stamp as {@code
 *     lake.zbpos.z*} properties
 */
record SealSnapshot(
    long firstOffset, long lastOffset, long frontierMs, Map<Integer, Long> zeebeWatermarks) {

  SealSnapshot {
    zeebeWatermarks = Map.copyOf(zeebeWatermarks);
  }
}
