/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.logstreams.log;

import io.zeebe.logstreams.storage.LogStorage;
import io.zeebe.util.sched.ActorScheduler;
import io.zeebe.util.sched.future.ActorFuture;

/** Builder pattern for the {@link LogStream} */
public interface LogStreamBuilder {

  /**
   * The actor scheduler to use for the {@link LogStream} and its child actors
   *
   * @param actorScheduler the scheduler to use
   * @return this builder
   */
  LogStreamBuilder withActorScheduler(ActorScheduler actorScheduler);

  /**
   * The maximum fragment size read from the shared write buffer; this should be aligned with the
   * maximum underlying storage block size.
   *
   * @param maxFragmentSize the maximum fragment size in bytes
   * @return this builder
   */
  LogStreamBuilder withMaxFragmentSize(int maxFragmentSize);

  /**
   * The underlying log storage to read from/write to.
   *
   * @param logStorage the underlying log storage
   * @return this builder
   */
  LogStreamBuilder withLogStorage(LogStorage logStorage);

  /**
   * The partition ID - primarily used for contextualizing the different log stream components
   *
   * @param partitionId the log stream's partition ID
   * @return this builder
   */
  LogStreamBuilder withPartitionId(int partitionId);

  /**
   * The node ID - to indicate on which node the log stream is running
   *
   * @param nodeId the node id
   * @return this builder
   */
  LogStreamBuilder withNodeId(int nodeId);

  /**
   * The log stream name - primarily used for contextualizing as well, e.g. loggers, actor name,
   * etc.
   *
   * @param logName the current log name
   * @return this builder
   */
  LogStreamBuilder withLogName(String logName);

  /**
   * Returns a future which, when completed, contains a log stream that can be read from/written to.
   *
   * @return a future which on complete contains the log stream
   */
  ActorFuture<LogStream> buildAsync();
}
