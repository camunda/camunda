/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.logarchive.processing;

import io.camunda.zeebe.backup.logarchive.model.HandoverJson;
import io.camunda.zeebe.backup.logarchive.model.TimelineFile;
import org.jspecify.annotations.Nullable;

/**
 * Immutable description of what to write to the bucket for one event. {@code root} and {@code seal}
 * are null when not due.
 *
 * <p>Requests are delivered in seq order. Recovery requests (see {@link
 * LogArchiveRecordsProcessor#onRecovered}) are the exception to the "one request per commit entry"
 * rule:
 *
 * <ul>
 *   <li>a republished outbox row has a commit entry and neither root nor seal;
 *   <li>a root-only request has {@code seq} set to the seq the root was built for, a null {@code
 *       commitEntryJson} and a root, and optionally a seal;
 *   <li>a seal-only request has {@code seq = -1}, a null {@code commitEntryJson} and a seal.
 * </ul>
 *
 * @param timelineId the timeline the request belongs to
 * @param seq the seq of the commit entry, or of the root for a root-only request, or -1
 * @param commitEntryJson the bytes of {@code commits/<seq>.json}, null for root-only and seal-only
 *     requests
 * @param rootJson the bytes of {@code root/<seq>.json}. After writing it and {@code _latest.json},
 *     the publisher writes {@code CONFIRM_PUBLISHED(seq)}
 * @param seal the index chunk to write, followed by {@code SEAL_INDEX}
 * @param handover set if the commit entry is the {@code HANDOVER} one: the publisher then also
 *     writes the handover file to the store of the old backups, before the root
 * @param timeline set if the commit entry is the {@code TIMELINE_STARTED} one: the publisher then
 *     also writes the timeline's registry file {@code timeline.json}, after the commit entry
 */
public record PublishRequest(
    String timelineId,
    long seq,
    byte @Nullable [] commitEntryJson,
    byte @Nullable [] rootJson,
    @Nullable SealRequest seal,
    @Nullable HandoverJson handover,
    @Nullable TimelineFile timeline) {

  public PublishRequest(
      final String timelineId,
      final long seq,
      final byte @Nullable [] commitEntryJson,
      final byte @Nullable [] rootJson,
      final @Nullable SealRequest seal,
      final @Nullable HandoverJson handover) {
    this(timelineId, seq, commitEntryJson, rootJson, seal, handover, null);
  }

  public PublishRequest(
      final String timelineId,
      final long seq,
      final byte @Nullable [] commitEntryJson,
      final byte @Nullable [] rootJson,
      final @Nullable SealRequest seal) {
    this(timelineId, seq, commitEntryJson, rootJson, seal, null, null);
  }
}
