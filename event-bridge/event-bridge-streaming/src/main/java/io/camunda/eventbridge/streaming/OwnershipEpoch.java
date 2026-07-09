/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.streaming;

/**
 * The coordinator fencing token under which a {@link Task} currently holds its partition — the same
 * member epoch the coordinator validates offset commits against, surfaced to the task so its
 * <em>data writes</em> can be fenced too. It is derived from the group epoch, so it is comparable
 * across members and strictly higher for any later owner of the partition: a task that stamps
 * {@code (epoch, offset)} onto an external write lets the target store reject a fenced zombie's
 * stale overwrite with one monotone comparison — the write-side counterpart of the offset-commit
 * fence.
 *
 * <p>Read it at each commit barrier, not once: the epoch changes when the member is fenced and
 * rejoins. {@code 0} before the first join; {@code -1} after this member has been fenced (a fenced
 * member's stamps always lose).
 */
@FunctionalInterface
public interface OwnershipEpoch {

  long current();
}
