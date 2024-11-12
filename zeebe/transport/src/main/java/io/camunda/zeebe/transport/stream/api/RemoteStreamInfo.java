/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.transport.stream.api;

import io.atomix.cluster.MemberId;
import java.util.Collection;
import java.util.UUID;
import org.agrona.DirectBuffer;

/** Represents non-aggregated metadata */
public interface RemoteStreamInfo<M> {

  /** Returns the list of possible payload consumers */
  Collection<RemoteStreamId> consumers();

  /** The stream's type identifier, used for aggregation */
  DirectBuffer streamType();

  /** The stream's metadata, used for aggregation and by consumers of the API */
  M metadata();

  /** A logical representation of a globally unique stream ID */
  interface RemoteStreamId {

    /** The stream's ID on as known by the receiver */
    UUID streamId();

    /** The target receiver for any messages pushed onto the stream */
    MemberId receiver();
  }
}
