/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.jobstream;

import io.atomix.cluster.MemberId;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.agrona.DirectBuffer;

public final class StreamRegistry {
  private final ConcurrentMap<DirectBuffer, Set<JobStream>> streams = new ConcurrentHashMap<>();

  public void add(
      final DirectBuffer streamType,
      final long streamId,
      final MemberId recipient,
      final DirectBuffer metadata) {}

  interface JobStream {}
}
