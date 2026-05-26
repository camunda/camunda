/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.message;

/**
 * Lifecycle states of an entry in the cross-partition message-start dedup state on {@code P_B}.
 *
 * <p>{@link #ACTIVE} entries deduplicate against the holder PI's lifetime: any retry of the same
 * {@code (processDefinitionKey, messageKey)} ask re-replies {@code STARTED} with the cached PI key.
 * {@link #TOMBSTONE} entries carry a deletion deadline; they continue to re-reply {@code STARTED}
 * until the deadline passes, closing the race where {@code P_K} retries after the holder has
 * completed but before the dedup entry has been fully removed. After the deadline a fresh ask is
 * treated as a cache miss and re-evaluated against current state.
 */
public enum MessageStartProcessInstanceDedupStatus {
  ACTIVE,
  TOMBSTONE
}
