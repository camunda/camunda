/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.transport.commandapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.cluster.PartitionId;
import io.camunda.cluster.PhysicalTenantIds;
import io.camunda.zeebe.broker.mailbox.MailboxSetup;
import io.camunda.zeebe.broker.system.configuration.QueryApiCfg;
import io.camunda.zeebe.engine.state.QueryService;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerPublishMessageRequest;
import io.camunda.zeebe.logstreams.log.LogAppendEntry;
import io.camunda.zeebe.logstreams.log.LogStream;
import io.camunda.zeebe.logstreams.log.LogStreamWriter;
import io.camunda.zeebe.logstreams.log.WriteContext;
import io.camunda.zeebe.mailbox.InMemoryMailboxStore;
import io.camunda.zeebe.mailbox.api.MailboxConfig;
import io.camunda.zeebe.mailbox.api.MessageKind;
import io.camunda.zeebe.mailbox.api.NewInboxMessage;
import io.camunda.zeebe.mailbox.api.PartitionLeader;
import io.camunda.zeebe.scheduler.testing.ActorSchedulerExtension;
import io.camunda.zeebe.transport.ServerTransport;
import io.camunda.zeebe.util.Either;
import java.time.Duration;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

/** The leader of a partition handles the requests in the inbox, and only while it leads. */
final class CommandApiServiceImplMailboxTest {

  private static final PartitionId PARTITION =
      new PartitionId(PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID, 1);
  private static final MailboxConfig CONFIG =
      new MailboxConfig(
          Duration.ofMillis(5),
          Duration.ofMillis(20),
          Duration.ofMinutes(1),
          10,
          Duration.ofHours(1),
          Duration.ofHours(1));

  private static final MailboxConfig EXPIRING =
      new MailboxConfig(
          Duration.ofMillis(5),
          Duration.ofMillis(20),
          Duration.ofMinutes(1),
          10,
          Duration.ofMillis(100),
          Duration.ofMillis(100));

  @RegisterExtension final ActorSchedulerExtension scheduler = new ActorSchedulerExtension();

  private final InMemoryMailboxStore store = new InMemoryMailboxStore();
  private final LogStreamWriter logStreamWriter = mock(LogStreamWriter.class);
  private final LogStream logStream = mock(LogStream.class);
  private CommandApiServiceImpl service;

  @BeforeEach
  void setUp() {
    when(logStream.newLogStreamWriter()).thenReturn(logStreamWriter);
    when(logStreamWriter.canWriteEvents(anyInt(), anyInt())).thenReturn(true);
    when(logStreamWriter.tryWrite(any(WriteContext.class), any(LogAppendEntry.class)))
        .thenReturn(Either.right(1L));
    service =
        new CommandApiServiceImpl(
            PARTITION,
            mock(ServerTransport.class),
            scheduler.get(),
            new QueryApiCfg(),
            MailboxSetup.of(store, CONFIG, null),
            "0");
    scheduler.submitActor(service).join();
  }

  @Test
  void shouldHandleTheRequestsInTheInboxWhileLeading() {
    // given
    store.insert(request(1));

    // when
    service.registerHandlers(PARTITION.number(), logStream, mock(QueryService.class)).join();

    // then
    verify(logStreamWriter, timeout(5_000))
        .tryWrite(any(WriteContext.class), any(LogAppendEntry.class));
  }

  @Test
  void shouldNotHandleRequestsOfOtherKinds() {
    // given
    store.insert(
        new NewInboxMessage(
            PARTITION.number(),
            MessageKind.INTER_PARTITION_COMMAND,
            "partition-2",
            1,
            new byte[] {1}));

    // when
    service.registerHandlers(PARTITION.number(), logStream, mock(QueryService.class)).join();

    // then
    verify(logStreamWriter, after(500).never())
        .tryWrite(any(WriteContext.class), any(LogAppendEntry.class));
  }

  @Test
  void shouldStopHandlingRequestsWhenTheHandlersAreDeactivated() {
    // given
    service.registerHandlers(PARTITION.number(), logStream, mock(QueryService.class)).join();
    service.deactivateHandlers(PARTITION.number()).join();

    // when
    store.insert(request(1));

    // then
    verify(logStreamWriter, after(500).never())
        .tryWrite(any(WriteContext.class), any(LogAppendEntry.class));
  }

  @Test
  void shouldNotHandleRequestsWithoutAMailbox() {
    // given
    final var plain =
        new CommandApiServiceImpl(
            PARTITION, mock(ServerTransport.class), scheduler.get(), new QueryApiCfg());
    scheduler.submitActor(plain).join();
    store.insert(request(1));

    // when
    plain.registerHandlers(PARTITION.number(), logStream, mock(QueryService.class)).join();

    // then
    verify(logStreamWriter, never()).tryWrite(any(WriteContext.class), any(LogAppendEntry.class));
  }

  @Test
  void shouldRecordTheLeaderWhileLeading() {
    // given
    final var zoned = zonedService();

    // when
    zoned.registerHandlers(PARTITION.number(), 4, logStream, mock(QueryService.class)).join();

    // then
    await()
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(
            () ->
                assertThat(store.findLeader(PARTITION.number()))
                    .contains(new PartitionLeader(PARTITION.number(), "eu_0", "eu", 4)));
  }

  @Test
  void shouldRemoveTheLeaderWhenTheHandlersAreDeactivated() {
    // given
    final var zoned = zonedService();
    zoned.registerHandlers(PARTITION.number(), 4, logStream, mock(QueryService.class)).join();
    await()
        .atMost(Duration.ofSeconds(5))
        .until(() -> store.findLeader(PARTITION.number()).isPresent());

    // when
    zoned.deactivateHandlers(PARTITION.number()).join();

    // then
    await()
        .atMost(Duration.ofSeconds(5))
        .until(() -> store.findLeader(PARTITION.number()).isEmpty());
  }

  @Test
  void shouldNotRecordTheLeaderOfANodeWithoutAZone() {
    // when - the service of setUp has the member id "0", which has no zone
    service.registerHandlers(PARTITION.number(), 4, logStream, mock(QueryService.class)).join();

    // then
    await()
        .during(Duration.ofMillis(300))
        .atMost(Duration.ofSeconds(5))
        .until(() -> store.findLeader(PARTITION.number()).isEmpty());
  }

  @Test
  void shouldNotRecordTheLeaderInLeaseMode() {
    // given - in lease mode the lease rows tell who leads
    final var leaseMode =
        new CommandApiServiceImpl(
            PARTITION,
            mock(ServerTransport.class),
            scheduler.get(),
            new QueryApiCfg(),
            MailboxSetup.of(store, CONFIG, null),
            "eu_0",
            false);
    scheduler.submitActor(leaseMode).join();

    // when
    leaseMode.registerHandlers(PARTITION.number(), 4, logStream, mock(QueryService.class)).join();

    // then
    await()
        .during(Duration.ofMillis(300))
        .atMost(Duration.ofSeconds(5))
        .until(() -> store.findLeader(PARTITION.number()).isEmpty());
  }

  @Test
  void shouldCleanUpTheMailboxWhileLeadingTheDeploymentPartition() {
    // given
    final var cleaning = serviceOf(PARTITION);
    final var old = commandFor(PARTITION.number());
    store.insert(old);

    // when
    cleaning.registerHandlers(PARTITION.number(), logStream, mock(QueryService.class)).join();

    // then - the message is gone once its key can be inserted again
    await().atMost(Duration.ofSeconds(10)).until(() -> store.insert(old));
  }

  @Test
  void shouldNotCleanUpTheMailboxOnOtherPartitions() {
    // given
    final var other = new PartitionId(PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID, 2);
    final var notCleaning = serviceOf(other);
    final var old = commandFor(other.number());
    store.insert(old);

    // when
    notCleaning.registerHandlers(other.number(), logStream, mock(QueryService.class)).join();

    // then - the message stays, so its key cannot be inserted again
    await()
        .during(Duration.ofSeconds(2))
        .atMost(Duration.ofSeconds(5))
        .until(() -> !store.insert(old));
  }

  @Test
  void shouldStopCleaningUpWhenTheHandlersAreDeactivated() {
    // given
    final var cleaning = serviceOf(PARTITION);
    cleaning.registerHandlers(PARTITION.number(), logStream, mock(QueryService.class)).join();
    cleaning.deactivateHandlers(PARTITION.number()).join();
    final var old = commandFor(PARTITION.number());
    store.insert(old);

    // then
    await()
        .during(Duration.ofSeconds(2))
        .atMost(Duration.ofSeconds(5))
        .until(() -> !store.insert(old));
  }

  private CommandApiServiceImpl serviceOf(final PartitionId partition) {
    final var created =
        new CommandApiServiceImpl(
            partition,
            mock(ServerTransport.class),
            scheduler.get(),
            new QueryApiCfg(),
            MailboxSetup.of(store, EXPIRING, null),
            "0");
    scheduler.submitActor(created).join();
    return created;
  }

  /** A message that no consumer of this service takes, which expires soon. */
  private static NewInboxMessage commandFor(final int partition) {
    return new NewInboxMessage(
        partition, MessageKind.INTER_PARTITION_COMMAND, "partition-9", 1, new byte[] {1});
  }

  private CommandApiServiceImpl zonedService() {
    final var zoned =
        new CommandApiServiceImpl(
            PARTITION,
            mock(ServerTransport.class),
            scheduler.get(),
            new QueryApiCfg(),
            MailboxSetup.of(store, CONFIG, null),
            "eu_0");
    scheduler.submitActor(zoned).join();
    return zoned;
  }

  private static NewInboxMessage request(final long requestId) {
    final var request =
        new BrokerPublishMessageRequest("test", "1").setMessageId("1").setTimeToLive(0);
    request.serializeValue();
    final var bytes = new byte[request.getLength()];
    request.write(new UnsafeBuffer(bytes), 0);
    return new NewInboxMessage(
        PARTITION.number(), MessageKind.CLIENT_REQUEST, "gateway-1", requestId, bytes);
  }
}
