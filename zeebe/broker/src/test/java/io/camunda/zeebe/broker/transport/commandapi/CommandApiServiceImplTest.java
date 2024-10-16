/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.transport.commandapi;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.broker.system.configuration.QueryApiCfg;
import io.camunda.zeebe.engine.state.QueryService;
import io.camunda.zeebe.logstreams.impl.flowcontrol.RateLimit;
import io.camunda.zeebe.logstreams.util.ListLogStorage;
import io.camunda.zeebe.logstreams.util.TestLogStream;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.testing.ControlledActorSchedulerExtension;
import io.camunda.zeebe.stream.api.StreamClock;
import io.camunda.zeebe.transport.ServerTransport;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@MockitoSettings(strictness = Strictness.STRICT_STUBS)
final class CommandApiServiceImplTest {
  @RegisterExtension
  private final ControlledActorSchedulerExtension scheduler =
      new ControlledActorSchedulerExtension();

  @Mock private ServerTransport transport;
  @Mock private QueryService queryService;

  @Test
  void shouldThrowException() {
    // given
    final var logStream =
        TestLogStream.builder()
            .withClock(StreamClock.system())
            .withMaxFragmentSize(1024 * 1024)
            .withLogStorage(new ListLogStorage())
            .withPartitionId(1)
            .withWriteRateLimit(RateLimit.disabled())
            .withLogName("test")
            .build();
    final var service =
        new CommandApiServiceImpl(transport, scheduler.getActorScheduler(), new QueryApiCfg());
    scheduler.submitActor(service);
    scheduler.workUntilDone();

    // when - `onBecomingLeader` enqueues a call to the underlying actor, but the log stream
    // is closed before the actor is scheduled (via workUntilDone), so an exception is thrown
    final ActorFuture<?> result = service.onBecomingLeader(1, 1, logStream, queryService);
    logStream.close();
    scheduler.workUntilDone();

    // then
    assertThat(result).failsWithin(Duration.ofSeconds(6));
  }
}
