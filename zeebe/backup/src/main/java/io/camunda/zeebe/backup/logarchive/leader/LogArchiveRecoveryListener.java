/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.logarchive.leader;

import io.camunda.zeebe.backup.logarchive.state.LogArchiveMeta;

/**
 * Receives the outcome of the {@link LogArchivePublisher}'s recovery check (spec section 8.4). The
 * archiver and the snapshot uploader write nothing to the bucket before it arrives.
 */
@FunctionalInterface
public interface LogArchiveRecoveryListener {

  /**
   * Called once per leadership, after the publisher checked the bucket against the recovered state.
   * Called on the publisher's actor; implementations must hop to their own actor.
   *
   * @param meta the recovered meta (a copy owned by the receiver)
   * @param bucketAhead true if the bucket already holds the commit entry for {@code meta.nextSeq}
   *     of {@code meta.timelineId}, i.e. the state was rolled back behind the bucket. Nothing is
   *     published to that timeline any more; the archiver forks a new timeline instead.
   */
  void onRecoveryChecked(LogArchiveMeta meta, boolean bucketAhead);
}
