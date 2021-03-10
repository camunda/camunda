/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.test.broker.protocol.brokerapi;

import io.zeebe.protocol.record.ValueType;
import io.zeebe.protocol.record.intent.JobIntent;
import java.util.function.Consumer;

public final class JobStubs {

  private final StubBrokerRule broker;

  public JobStubs(final StubBrokerRule broker) {
    this.broker = broker;
  }

  public void registerCompleteCommand() {
    registerCompleteCommand(r -> {});
  }

  public void registerCompleteCommand(final Consumer<ExecuteCommandResponseBuilder> modifier) {
    final ExecuteCommandResponseBuilder builder =
        broker
            .onExecuteCommandRequest(ValueType.JOB, JobIntent.COMPLETE)
            .respondWith()
            .event()
            .intent(JobIntent.COMPLETED)
            .key(r -> r.key())
            .value()
            .allOf((r) -> r.getCommand())
            .done();

    modifier.accept(builder);

    builder.register();
  }

  public void registerFailCommand() {
    registerFailCommand(r -> {});
  }

  public void registerFailCommand(final Consumer<ExecuteCommandResponseBuilder> modifier) {
    final ExecuteCommandResponseBuilder builder =
        broker
            .onExecuteCommandRequest(ValueType.JOB, JobIntent.FAIL)
            .respondWith()
            .event()
            .intent(JobIntent.FAILED)
            .key(r -> r.key())
            .value()
            .allOf((r) -> r.getCommand())
            .done();

    modifier.accept(builder);

    builder.register();
  }

  public void registerUpdateRetriesCommand() {
    registerUpdateRetriesCommand(b -> {});
  }

  public void registerUpdateRetriesCommand(final Consumer<ExecuteCommandResponseBuilder> modifier) {
    final ExecuteCommandResponseBuilder builder =
        broker
            .onExecuteCommandRequest(ValueType.JOB, JobIntent.UPDATE_RETRIES)
            .respondWith()
            .event()
            .intent(JobIntent.RETRIES_UPDATED)
            .key(r -> r.key())
            .value()
            .allOf((r) -> r.getCommand())
            .put("state", "RETRIES_UPDATED")
            .done();

    modifier.accept(builder);

    builder.register();
  }
}
