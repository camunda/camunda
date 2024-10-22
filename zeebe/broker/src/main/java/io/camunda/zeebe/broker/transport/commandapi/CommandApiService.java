/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.transport.commandapi;

import io.camunda.zeebe.engine.state.QueryService;
import io.camunda.zeebe.logstreams.log.LogStream;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.stream.api.CommandResponseWriter;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import java.util.function.Consumer;

public interface CommandApiService {

  CommandResponseWriter newCommandResponseWriter();

  Consumer<TypedRecord<?>> getOnProcessedListener(int partitionId);

  ActorFuture<Void> registerHandlers(
      final int partitionId, final LogStream logStream, final QueryService queryService);

  ActorFuture<Void> unregisterHandlers(final int partitionId);
}
