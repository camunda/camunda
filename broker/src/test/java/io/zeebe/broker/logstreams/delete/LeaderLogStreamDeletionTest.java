/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.logstreams.delete;

public class LeaderLogStreamDeletionTest {
  //  private static final int PARTITION_ID = 0;
  //  private static final long POSITION_TO_DELETE = 6L;
  //  private static final long ADDRESS_TO_DELETE = 55L;
  //  @Mock ExporterDirectorService mockExporterDirectorService;
  //  @Mock LogStream mockLogStream = mock(LogStream.class);
  //  private final ActorSchedulerRule actorScheduler = new ActorSchedulerRule();
  //  private final ServiceContainerRule serviceContainer = new
  // ServiceContainerRule(actorScheduler);
  //  @Rule public RuleChain chain = RuleChain.outerRule(actorScheduler).around(serviceContainer);
  //  private LeaderLogStreamDeletionService deletionService;
  //
  //  @Before
  //  public void setup() {
  //    deletionService = new LeaderLogStreamDeletionService(mockLogStream);
  //    serviceContainer
  //        .get()
  //        .createService(
  //            EngineServiceNames.leaderLogStreamDeletionService(PARTITION_ID), deletionService)
  //        .install()
  //        .join();
  //    final Injector<ExporterDirectorService> exporterManagerInjector =
  //        deletionService.getExporterDirectorInjector();
  //
  //    mockExporterDirectorService = mock(ExporterDirectorService.class);
  //    exporterManagerInjector.inject(mockExporterDirectorService);
  //    deletionService.start(mock(ServiceStartContext.class));
  //  }
  //
  //  @Test
  //  public void shouldDeleteWithDelayedExporter() {
  //    // given
  //    final var snapshot = mockSnapshot(POSITION_TO_DELETE + 2);
  //    when(mockExporterDirectorService.getLowestExporterPosition())
  //        .thenReturn(CompletableActorFuture.completed(POSITION_TO_DELETE));
  //
  //    // when
  //    actorScheduler
  //        .submitActor(
  //            new Actor() {
  //              @Override
  //              protected void onActorStarted() {
  //                deletionService.onSnapshotDeleted(snapshot);
  //              }
  //            })
  //        .join();
  //
  //    // then
  //    verify(mockLogStream, timeout(1000)).delete(POSITION_TO_DELETE);
  //  }
  //
  //  @Test
  //  public void shouldDeleteWithNoExporter() {
  //    // given
  //    final var snapshot = mockSnapshot(POSITION_TO_DELETE);
  //    when(mockExporterDirectorService.getLowestExporterPosition())
  //        .thenReturn(CompletableActorFuture.completed(Long.MAX_VALUE));
  //
  //    // when
  //    actorScheduler
  //        .submitActor(
  //            new Actor() {
  //              @Override
  //              protected void onActorStarted() {
  //                deletionService.onSnapshotDeleted(snapshot);
  //              }
  //            })
  //        .join();
  //
  //    // then
  //    verify(mockLogStream, timeout(1000)).delete(POSITION_TO_DELETE);
  //  }
  //
  //  @Test
  //  public void shouldNotDeleteOnNegativePosition() {
  //    // given
  //    final var snapshot = mockSnapshot(-1);
  //    when(mockExporterDirectorService.getLowestExporterPosition())
  //        .thenReturn(CompletableActorFuture.completed(Long.MAX_VALUE));
  //
  //    // when
  //    actorScheduler
  //        .submitActor(
  //            new Actor() {
  //              @Override
  //              protected void onActorStarted() {
  //                deletionService.onSnapshotDeleted(snapshot);
  //              }
  //            })
  //        .join();
  //
  //    // then
  //    verify(mockLogStream, never()).delete(POSITION_TO_DELETE);
  //  }
  //
  //  private Snapshot mockSnapshot(final long position) {
  //    final var snapshot = mock(Snapshot.class);
  //    when(snapshot.getPosition()).thenReturn(position);
  //    return snapshot;
  //  }
}
