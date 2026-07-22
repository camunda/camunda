/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.sink;

/**
 * Where descriptors go — the parked seam between the sink and durability. Today: a direct-commit
 * implementation adapting the existing writer path (atomic commit of files + re-stamp of ALL
 * partitions' {@code lake.offset.*} summary properties — the carry-forward rule is load-bearing and
 * test-guarded). Later: a control-topic producer feeding the coordinator/committer, without any
 * other sink class changing.
 *
 * <p>Called on the flush thread; implementations may block it (that is the flush thread's job) but
 * must be retry-safe: a redelivered descriptor after a crash must not double-register files
 * (offset-range idempotence).
 */
public interface DescriptorSink {

  void accept(Descriptor descriptor);
}
