/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.transport.stream.api;

import io.atomix.cluster.MemberId;
import java.util.Set;
import org.agrona.DirectBuffer;

public interface ClientStream<M> {
  ClientStreamId streamId();

  DirectBuffer streamType();

  M metadata();

  Set<MemberId> liveConnections();
}
