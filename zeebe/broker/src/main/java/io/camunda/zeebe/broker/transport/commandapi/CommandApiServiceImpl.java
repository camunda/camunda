/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.transport.commandapi;

import io.atomix.cluster.BrokerMemberId;
import io.camunda.cluster.PartitionId;
import io.camunda.zeebe.broker.Loggers;
import io.camunda.zeebe.broker.mailbox.LeaderPublisher;
import io.camunda.zeebe.broker.mailbox.MailboxCleanupLoop;
import io.camunda.zeebe.broker.mailbox.MailboxDrainLoop;
import io.camunda.zeebe.broker.mailbox.MailboxSetup;
import io.camunda.zeebe.broker.system.configuration.QueryApiCfg;
import io.camunda.zeebe.broker.system.monitoring.DiskSpaceUsageListener;
import io.camunda.zeebe.broker.transport.queryapi.QueryApiRequestHandler;
import io.camunda.zeebe.engine.state.QueryService;
import io.camunda.zeebe.logstreams.log.LogStream;
import io.camunda.zeebe.mailbox.api.MessageKind;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.scheduler.ActorSchedulingService;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.stream.api.CommandResponseWriter;
import io.camunda.zeebe.transport.RequestType;
import io.camunda.zeebe.transport.ServerTransport;
import java.time.Duration;
import java.util.Set;
import org.jspecify.annotations.Nullable;

public final class CommandApiServiceImpl extends Actor
    implements DiskSpaceUsageListener, CommandApiService {

  private static final Duration MAILBOX_WRITE_TIMEOUT = Duration.ofSeconds(10);

  private final PartitionId partitionId;
  private final ServerTransport serverTransport;
  private final CommandApiRequestHandler commandHandler;
  private final QueryApiRequestHandler queryHandler;
  private final ActorSchedulingService scheduler;
  private final @Nullable MailboxSetup mailboxSetup;
  private final String claimerId;
  private final @Nullable MailboxResponseRouter responseRouter;
  private final @Nullable LeaderPublisher leaderPublisher;
  private @Nullable MailboxDrainLoop requestDrain;
  private @Nullable MailboxCleanupLoop cleanup;
  private long leaderTerm;

  public CommandApiServiceImpl(
      final PartitionId partitionId,
      final ServerTransport serverTransport,
      final ActorSchedulingService scheduler,
      final QueryApiCfg queryApiCfg) {
    this(partitionId, serverTransport, scheduler, queryApiCfg, null, "");
  }

  /**
   * @param mailboxSetup if set, the leader of the partition also handles the requests that gateways
   *     left in the inbox of the partition, and sends their responses through the mailbox
   * @param claimerId identifies this node in the inbox. Not used without a mailbox.
   */
  public CommandApiServiceImpl(
      final PartitionId partitionId,
      final ServerTransport serverTransport,
      final ActorSchedulingService scheduler,
      final QueryApiCfg queryApiCfg,
      final @Nullable MailboxSetup mailboxSetup,
      final String claimerId) {
    this(partitionId, serverTransport, scheduler, queryApiCfg, mailboxSetup, claimerId, true);
  }

  /**
   * @param recordLeaders if the leader records itself in the mailbox store, so that gateways of
   *     other regions find it. Off in lease mode, where the lease rows tell who leads.
   */
  public CommandApiServiceImpl(
      final PartitionId partitionId,
      final ServerTransport serverTransport,
      final ActorSchedulingService scheduler,
      final QueryApiCfg queryApiCfg,
      final @Nullable MailboxSetup mailboxSetup,
      final String claimerId,
      final boolean recordLeaders) {
    super("CommandApiService", partitionId);
    this.partitionId = partitionId;
    this.serverTransport = serverTransport;
    this.scheduler = scheduler;
    this.mailboxSetup = mailboxSetup;
    this.claimerId = claimerId;
    commandHandler = new CommandApiRequestHandler(partitionId);
    queryHandler = new QueryApiRequestHandler(partitionId, queryApiCfg);
    responseRouter =
        mailboxSetup == null
            ? null
            : new MailboxResponseRouter(
                serverTransport,
                mailboxSetup.store(),
                mailboxSetup.senderExecutor(),
                mailboxSetup.config().outboxTtl(),
                System::nanoTime);
    leaderPublisher =
        recordLeaders ? newLeaderPublisher(partitionId, mailboxSetup, claimerId) : null;
  }

  /** A node without a zone is not in a multi-region deployment, so there is nothing to record. */
  private static @Nullable LeaderPublisher newLeaderPublisher(
      final PartitionId partitionId,
      final @Nullable MailboxSetup mailboxSetup,
      final String claimerId) {
    if (mailboxSetup == null) {
      return null;
    }
    final var zone = BrokerMemberId.from(claimerId).zone();
    return zone == null
        ? null
        : new LeaderPublisher(
            mailboxSetup.store(),
            mailboxSetup.senderExecutor(),
            partitionId.number(),
            claimerId,
            zone);
  }

  @Override
  protected void onActorStarting() {
    scheduler.submitActor(queryHandler);
    scheduler.submitActor(commandHandler);
  }

  @Override
  protected void onActorClosing() {
    unregisterHandlersActorless(partitionId.number());
    actor.runOnCompletion(
        commandHandler.closeAsync(),
        (ok, error) -> {
          if (error != null) {
            Loggers.TRANSPORT_LOGGER.error("Error closing command api request handler", error);
          }
        });
    actor.runOnCompletion(
        queryHandler.closeAsync(),
        (ok, error) -> {
          if (error != null) {
            Loggers.TRANSPORT_LOGGER.warn("Failed to close query API request handler", error);
          }
        });
  }

  @Override
  public CommandResponseWriter newCommandResponseWriter() {
    return new CommandResponseWriterImpl(responseRouter != null ? responseRouter : serverTransport);
  }

  @Override
  public void onRecovered(final int partitionId) {
    commandHandler.onRecovered(partitionId);
  }

  @Override
  public void onPaused(final int partitionId) {
    commandHandler.onPaused(partitionId);
  }

  @Override
  public void onResumed(final int partitionId) {
    commandHandler.onResumed(partitionId);
  }

  @Override
  public ActorFuture<Void> registerHandlers(
      final int partitionId, final LogStream logStream, final QueryService queryService) {
    return registerHandlers(partitionId, 0, logStream, queryService);
  }

  @Override
  public ActorFuture<Void> registerHandlers(
      final int partitionId,
      final long term,
      final LogStream logStream,
      final QueryService queryService) {
    return actor.call(
        () -> {
          // create the writer immediately so if the logStream is closed, this will throw an
          // exception immediately
          final var logStreamWriter = logStream.newLogStreamWriter();
          queryHandler.addPartition(partitionId, queryService);
          serverTransport.subscribe(this.partitionId, RequestType.QUERY, queryHandler);
          commandHandler.addPartition(partitionId, logStreamWriter);
          serverTransport.subscribe(this.partitionId, RequestType.COMMAND, commandHandler);
          startRequestDrain(term);
          publishLeader(term);
        });
  }

  @Override
  public ActorFuture<Void> unregisterHandlers(final int partitionId) {
    return actor.call(() -> unregisterHandlersActorless(partitionId));
  }

  @Override
  public ActorFuture<Void> deactivateHandlers(final int partitionId) {
    return actor.call(
        () -> {
          stopRequestDrain();
          commandHandler.removePartition(partitionId);
          queryHandler.removePartition(partitionId);
        });
  }

  private void publishLeader(final long term) {
    if (leaderPublisher != null) {
      leaderTerm = term;
      leaderPublisher.publish(term);
    }
  }

  private void withdrawLeader() {
    if (leaderPublisher != null && requestDrain != null) {
      leaderPublisher.withdraw(leaderTerm);
    }
  }

  /** Starts to handle the requests in the inbox. Only a leader, which has handlers, does this. */
  private void startRequestDrain(final long term) {
    if (mailboxSetup == null || responseRouter == null || requestDrain != null) {
      return;
    }
    responseRouter.startTerm(term);
    requestDrain =
        new MailboxDrainLoop(
            "mailbox-requests-" + partitionId.number(),
            mailboxSetup.store(),
            partitionId.number(),
            claimerId + "/client-request",
            Set.of(MessageKind.CLIENT_REQUEST),
            mailboxSetup.config(),
            new ClientRequestMailboxWriter(
                commandHandler, responseRouter, partitionId.number(), MAILBOX_WRITE_TIMEOUT));
    requestDrain.start();
    startCleanup();
  }

  /**
   * The cleanup is global, so the leader of one partition, the deployment partition, runs it for
   * the cluster.
   */
  private void startCleanup() {
    if (mailboxSetup == null
        || cleanup != null
        || partitionId.number() != Protocol.DEPLOYMENT_PARTITION) {
      return;
    }
    cleanup =
        new MailboxCleanupLoop("mailbox-cleanup", mailboxSetup.store(), mailboxSetup.config());
    cleanup.start();
  }

  /** Stops handling the requests in the inbox. The responses that are still due are lost. */
  private void stopRequestDrain() {
    withdrawLeader();
    if (requestDrain != null) {
      requestDrain.close();
      requestDrain = null;
    }
    if (cleanup != null) {
      cleanup.close();
      cleanup = null;
    }
    if (responseRouter != null) {
      responseRouter.clear();
    }
  }

  private void unregisterHandlersActorless(final int partitionId) {
    stopRequestDrain();
    commandHandler.removePartition(partitionId);
    queryHandler.removePartition(partitionId);
    serverTransport.unsubscribe(this.partitionId, RequestType.COMMAND);
    serverTransport.unsubscribe(this.partitionId, RequestType.QUERY);
  }

  @Override
  public void onDiskSpaceNotAvailable() {
    actor.run(commandHandler::onDiskSpaceNotAvailable);
  }

  @Override
  public void onDiskSpaceAvailable() {
    actor.run(commandHandler::onDiskSpaceAvailable);
  }
}
