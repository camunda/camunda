/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.impl.stream;

import io.camunda.zeebe.broker.client.api.BrokerTopologyListener;
import io.camunda.zeebe.protocol.impl.stream.job.JobActivationProperties;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.transport.stream.api.ClientStream;
import io.camunda.zeebe.transport.stream.api.ClientStreamer;
import io.camunda.zeebe.util.CloseableSilently;
import java.util.Collection;

/** The main entry point for the client side of job streaming in the gateway. */
public interface JobStreamClient extends BrokerTopologyListener, CloseableSilently {

  /** Returns the underlying job streamer. */
  ClientStreamer<JobActivationProperties> streamer();

  /** Asynchronously starts the job stream client. */
  ActorFuture<Void> start();

  /** Returns the list of registered job streams */
  ActorFuture<Collection<ClientStream<JobActivationProperties>>> list();
}
