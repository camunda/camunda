/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.gateway.impl.stream;

import io.camunda.zeebe.gateway.impl.broker.cluster.BrokerTopologyListener;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.transport.stream.api.ClientStreamer;
import io.camunda.zeebe.util.CloseableSilently;

/** The main entry point for the client side of job streaming in the gateway. */
public interface JobStreamClient extends BrokerTopologyListener, CloseableSilently {

  /** Returns the underlying job streamer. */
  ClientStreamer<JobActivationProperties> streamer();

  /** Asynchronously starts the job stream client. */
  ActorFuture<Void> start();
}
