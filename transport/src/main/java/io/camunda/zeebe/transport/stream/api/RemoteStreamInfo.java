/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.transport.stream.api;

import io.atomix.cluster.MemberId;
import java.util.Collection;
import java.util.UUID;
import org.agrona.DirectBuffer;

/** Represents non-aggregated metadata */
public interface RemoteStreamInfo<M> {
  Collection<RemoteStreamId> consumers();

  DirectBuffer streamType();

  M metadata();

  interface RemoteStreamId {
    UUID streamId();

    MemberId receiver();
  }
}
