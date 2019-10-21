/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.logstreams.delete;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.zeebe.broker.engine.EngineServiceNames;
import io.zeebe.broker.exporter.ExporterManagerService;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.servicecontainer.Injector;
import io.zeebe.servicecontainer.ServiceStartContext;
import io.zeebe.servicecontainer.testing.ServiceContainerRule;
import io.zeebe.util.sched.Actor;
import io.zeebe.util.sched.future.CompletableActorFuture;
import io.zeebe.util.sched.testing.ActorSchedulerRule;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.mockito.Mock;

public class LeaderLogStreamDeletionTest {
  private static final int PARTITION_ID = 0;
  private static final long POSITION_TO_DELETE = 6L;
  private static final long ADDRESS_TO_DELETE = 55L;
  @Mock ExporterManagerService mockExporterManagerService;
  @Mock LogStream mockLogStream = mock(LogStream.class);
  private final ActorSchedulerRule actorScheduler = new ActorSchedulerRule();
  private final ServiceContainerRule serviceContainer = new ServiceContainerRule(actorScheduler);
  @Rule public RuleChain chain = RuleChain.outerRule(actorScheduler).around(serviceContainer);
  private LeaderLogStreamDeletionService deletionService;

  @Before
  public void setup() {
    deletionService = new LeaderLogStreamDeletionService(mockLogStream);
    serviceContainer
        .get()
        .createService(
            EngineServiceNames.leaderLogStreamDeletionService(PARTITION_ID), deletionService)
        .install()
        .join();
    final Injector<ExporterManagerService> exporterManagerInjector =
        deletionService.getExporterManagerInjector();

    mockExporterManagerService = mock(ExporterManagerService.class);
    exporterManagerInjector.inject(mockExporterManagerService);
    deletionService.start(mock(ServiceStartContext.class));
  }

  @Test
  public void shouldDeleteWithDelayedExporter() {
    // given
    when(mockExporterManagerService.getLowestExporterPosition())
        .thenReturn(CompletableActorFuture.completed(POSITION_TO_DELETE));

    // when
    actorScheduler
        .submitActor(
            new Actor() {
              @Override
              protected void onActorStarted() {
                deletionService.delete(POSITION_TO_DELETE + 2);
              }
            })
        .join();

    // then
    verify(mockLogStream, timeout(1000)).delete(POSITION_TO_DELETE);
  }

  @Test
  public void shouldDeleteWithNoExporter() {
    // given
    when(mockExporterManagerService.getLowestExporterPosition())
        .thenReturn(CompletableActorFuture.completed(Long.MAX_VALUE));

    // when
    actorScheduler
        .submitActor(
            new Actor() {
              @Override
              protected void onActorStarted() {
                deletionService.delete(POSITION_TO_DELETE);
              }
            })
        .join();

    // then
    verify(mockLogStream, timeout(1000)).delete(POSITION_TO_DELETE);
  }

  @Test
  public void shouldNotDeleteOnNegativePosition() {
    // given
    when(mockExporterManagerService.getLowestExporterPosition())
        .thenReturn(CompletableActorFuture.completed(Long.MAX_VALUE));

    // when
    actorScheduler
        .submitActor(
            new Actor() {
              @Override
              protected void onActorStarted() {
                deletionService.delete(-1);
              }
            })
        .join();

    // then
    verify(mockLogStream, never()).delete(POSITION_TO_DELETE);
  }
}
