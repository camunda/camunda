/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.inmemory;

import io.camunda.security.configuration.SecurityConfiguration;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.inmemory.InMemoryDbFactory;
import io.camunda.zeebe.engine.Engine;
import io.camunda.zeebe.engine.EngineConfiguration;
import io.camunda.zeebe.engine.processing.EngineProcessors;
import io.camunda.zeebe.engine.processing.message.command.SubscriptionCommandSender;
import io.camunda.zeebe.engine.processing.streamprocessor.JobStreamer;
import io.camunda.zeebe.logstreams.impl.storage.InMemoryLogStorage;
import io.camunda.zeebe.logstreams.log.LogStream;
import io.camunda.zeebe.logstreams.storage.LogStorage;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.scheduler.ActorScheduler;
import io.camunda.zeebe.scheduler.ActorSchedulingService;
import io.camunda.zeebe.scheduler.clock.ActorClock;
import io.camunda.zeebe.scheduler.clock.ControlledActorClock;
import io.camunda.zeebe.stream.api.CommandResponseWriter;
import io.camunda.zeebe.stream.api.InterPartitionCommandSender;
import io.camunda.zeebe.stream.impl.ControllableStreamClockImpl;
import io.camunda.zeebe.stream.impl.StreamProcessor;
import io.camunda.zeebe.util.FeatureFlags;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.InstantSource;
import java.util.Collections;

public class InMemoryEngineFactory {

  private static final int PARTITION_COUNT = 1;
  private static final int PARTITION_ID = 1;

  public static InMemoryEngine create() {

    final ControlledActorClock clock = new ControlledActorClock();
    final ActorScheduler scheduler = createActorScheduler(clock);

    final LogStorage logStorage = new InMemoryLogStorage();
    final LogStream logStream = createLogStream(logStorage, clock);
    final ZeebeDb<ZbColumnFamilies> database = new InMemoryDbFactory<ZbColumnFamilies>().createDb();

    final InterPartitionCommandSender commandSender =
        new SinglePartitionCommandSender(logStream.newLogStreamWriter());

    final Engine engine = createEngine(commandSender);
    final NoopCommandResponseWriter commandResponseWriter = new NoopCommandResponseWriter();

    final StreamProcessor streamProcessor =
        createStreamProcessor(
            logStream, database, scheduler, commandResponseWriter, commandSender, clock, engine);

    final InMemoryEngineMonitor engineMonitor = new InMemoryEngineMonitor(streamProcessor);
    logStorage.addCommitListener(engineMonitor);

    final Runnable startup =
        () -> {
          scheduler.start();
          // we may need to wait until the stream processor is opened
          streamProcessor.openAsync(false);
        };

    final Runnable shutdown =
        () -> {
          try {
            streamProcessor.close();
            database.close();
            logStream.close();
            scheduler.stop();
          } catch (final Exception ignore) {
            // all fine
          }
        };

    return new InMemoryEngine(startup, shutdown, logStream, engineMonitor);
  }

  private static LogStream createLogStream(final LogStorage logStorage, final InstantSource clock) {
    return LogStream.builder()
        .withPartitionId(PARTITION_ID)
        .withLogStorage(logStorage)
        .withClock(clock)
        .build();
  }

  private static Engine createEngine(final InterPartitionCommandSender commandSender) {
    return new Engine(
        context ->
            EngineProcessors.createEngineProcessors(
                context,
                PARTITION_COUNT,
                new SubscriptionCommandSender(context.getPartitionId(), commandSender),
                commandSender,
                FeatureFlags.createDefault(),
                JobStreamer.noop()),
        new EngineConfiguration(),
        new SecurityConfiguration());
  }

  private static ActorScheduler createActorScheduler(final ActorClock clock) {
    return ActorScheduler.newActorScheduler().setActorClock(clock).build();
  }

  private static StreamProcessor createStreamProcessor(
      final LogStream logStream,
      final ZeebeDb<ZbColumnFamilies> database,
      final ActorSchedulingService scheduler,
      final CommandResponseWriter commandResponseWriter,
      final InterPartitionCommandSender commandSender,
      final InstantSource clock,
      final Engine engine) {
    return StreamProcessor.builder()
        .logStream(logStream)
        .zeebeDb(database)
        .commandResponseWriter(commandResponseWriter)
        .partitionCommandSender(commandSender)
        .recordProcessors(Collections.singletonList(engine))
        .actorSchedulingService(scheduler)
        .clock(new ControllableStreamClockImpl(clock))
        .meterRegistry(new SimpleMeterRegistry())
        .build();
  }
}
