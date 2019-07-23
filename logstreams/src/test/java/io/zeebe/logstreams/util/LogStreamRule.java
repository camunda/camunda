/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.logstreams.util;

import static io.zeebe.logstreams.impl.service.LogStreamServiceNames.distributedLogPartitionServiceName;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import io.zeebe.distributedlog.DistributedLogstreamService;
import io.zeebe.distributedlog.impl.DefaultDistributedLogstreamService;
import io.zeebe.distributedlog.impl.DistributedLogstreamPartition;
import io.zeebe.logstreams.LogStreams;
import io.zeebe.logstreams.impl.LogStreamBuilder;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.servicecontainer.ServiceContainer;
import io.zeebe.servicecontainer.impl.ServiceContainerImpl;
import io.zeebe.util.sched.ActorScheduler;
import io.zeebe.util.sched.clock.ControlledActorClock;
import io.zeebe.util.sched.testing.ActorSchedulerRule;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import org.junit.rules.ExternalResource;
import org.junit.rules.TemporaryFolder;
import org.mockito.internal.util.reflection.FieldSetter;
import org.mockito.stubbing.Answer;

public class LogStreamRule extends ExternalResource {
  private final TemporaryFolder temporaryFolder;
  private final ControlledActorClock clock = new ControlledActorClock();
  private final Consumer<LogStreamBuilder> streamBuilder;
  private ActorScheduler actorScheduler;
  private ServiceContainer serviceContainer;
  private LogStream logStream;
  private DistributedLogstreamService distributedLogImpl;
  private LogStreamBuilder builder;
  private ActorSchedulerRule actorSchedulerRule;

  public LogStreamRule(final TemporaryFolder temporaryFolder) {
    this(temporaryFolder, b -> {});
  }

  public LogStreamRule(
      final TemporaryFolder temporaryFolder, final Consumer<LogStreamBuilder> streamBuilder) {
    this.temporaryFolder = temporaryFolder;
    this.streamBuilder = streamBuilder;
  }

  @Override
  protected void before() {
    actorSchedulerRule = new ActorSchedulerRule(clock);
    actorSchedulerRule.before();
    startLogStream();
  }

  @Override
  protected void after() {
    stopLogStream();

    actorSchedulerRule.after();
  }

  public void startLogStream() {
    actorScheduler = actorSchedulerRule.get();

    serviceContainer = new ServiceContainerImpl(actorScheduler);
    serviceContainer.start();

    builder =
        LogStreams.createFsLogStream(0)
            .logDirectory(temporaryFolder.getRoot().getAbsolutePath())
            .serviceContainer(serviceContainer);

    // apply additional configs
    streamBuilder.accept(builder);

    openLogStream();
  }

  public void stopLogStream() {
    if (logStream != null) {
      logStream.close();
    }

    try {
      serviceContainer.close(5, TimeUnit.SECONDS);
    } catch (final TimeoutException | ExecutionException | InterruptedException e) {
      e.printStackTrace();
    }
  }

  private void openDistributedLog() {
    final DistributedLogstreamPartition mockDistLog = mock(DistributedLogstreamPartition.class);
    distributedLogImpl = new DefaultDistributedLogstreamService();

    final String nodeId = "0";
    try {
      FieldSetter.setField(
          distributedLogImpl,
          DefaultDistributedLogstreamService.class.getDeclaredField("logStream"),
          logStream);

      FieldSetter.setField(
          distributedLogImpl,
          DefaultDistributedLogstreamService.class.getDeclaredField("logStorage"),
          logStream.getLogStorage());

      FieldSetter.setField(
          distributedLogImpl,
          DefaultDistributedLogstreamService.class.getDeclaredField("currentLeader"),
          nodeId);

    } catch (NoSuchFieldException e) {
      e.printStackTrace();
    }

    doAnswer(
            (Answer<CompletableFuture<Long>>)
                invocation -> {
                  final Object[] arguments = invocation.getArguments();
                  if (arguments != null
                      && arguments.length > 1
                      && arguments[0] != null
                      && arguments[1] != null) {
                    final byte[] bytes = (byte[]) arguments[0];
                    final long pos = (long) arguments[1];
                    return CompletableFuture.completedFuture(
                        distributedLogImpl.append(nodeId, pos, bytes));
                  }
                  return null;
                })
        .when(mockDistLog)
        .asyncAppend(any(), anyLong());

    serviceContainer
        .createService(distributedLogPartitionServiceName(builder.getLogName()), () -> mockDistLog)
        .install()
        .join();
  }

  public void openLogStream() {
    logStream = builder.build().join();
    openDistributedLog();
    logStream.openAppender().join();
  }

  public LogStream getLogStream() {
    return logStream;
  }

  public long getCommitPosition() {
    return logStream.getCommitPosition();
  }

  public void setCommitPosition(final long position) {
    logStream.setCommitPosition(position);
  }

  public ControlledActorClock getClock() {
    return clock;
  }

  public ActorScheduler getActorScheduler() {
    return actorScheduler;
  }

  public ServiceContainer getServiceContainer() {
    return serviceContainer;
  }
}
