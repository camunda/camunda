/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.client.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.atomix.cluster.BrokerMemberId;
import io.camunda.cluster.PhysicalTenantIds;
import io.camunda.zeebe.broker.client.api.BrokerClientRequestMetrics;
import io.camunda.zeebe.broker.client.api.dto.BrokerRequest;
import io.camunda.zeebe.broker.client.api.dto.BrokerResponse;
import io.camunda.zeebe.broker.client.api.mailbox.GatewayMailboxSetup;
import io.camunda.zeebe.broker.client.impl.mailbox.GatewayMailbox;
import io.camunda.zeebe.mailbox.InMemoryMailboxStore;
import io.camunda.zeebe.mailbox.api.GatewayId;
import io.camunda.zeebe.mailbox.api.MailboxConfig;
import io.camunda.zeebe.mailbox.api.OutboxResponse;
import io.camunda.zeebe.mailbox.api.PartitionLeader;
import io.camunda.zeebe.scheduler.ActorScheduler;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.transport.ClientRequest;
import io.camunda.zeebe.transport.ClientTransport;
import io.camunda.zeebe.transport.RequestType;
import io.camunda.zeebe.util.buffer.BufferWriter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * A request goes through the mailbox only if the topology shows no leader and the store has one.
 */
final class BrokerRequestManagerMailboxTest {

  private static final int PARTITION = 1;
  private static final String GATEWAY = "gateway-1";
  private static final byte[] NETWORK_RESPONSE = {1};
  private static final byte[] MAILBOX_RESPONSE = {9};
  private static final MailboxConfig CONFIG =
      new MailboxConfig(
          Duration.ofMillis(5),
          Duration.ofMillis(20),
          Duration.ofMinutes(1),
          10,
          Duration.ofHours(1),
          Duration.ofHours(1));

  private final ActorScheduler scheduler = ActorScheduler.newActorScheduler().build();
  private final InMemoryMailboxStore store = new InMemoryMailboxStore();
  private final RecordingTransport transport = new RecordingTransport();
  private final GatewayMailbox mailbox =
      new GatewayMailbox(new GatewayMailboxSetup(store, CONFIG, GATEWAY, Runnable::run));
  private BrokerRequestManager manager;

  @BeforeEach
  void setUp() {
    scheduler.start();
    mailbox.start();
  }

  @AfterEach
  void tearDown() {
    mailbox.close();
    scheduler.stop();
  }

  @Test
  void shouldSendThroughTheMailboxWhenTheTopologyShowsNoLeaderAndTheStoreHasOne() throws Exception {
    // given
    start(new TestTopologyManager().addPartition(PARTITION, null), mailbox);
    store.putLeader(new PartitionLeader(PARTITION, "us_0", "us", 1));

    // when
    final var response = manager.sendRequest(request(PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID));
    final var inboxMessage =
        await()
            .atMost(Duration.ofSeconds(5))
            .until(
                () -> store.claim(PARTITION, "leader", 10, Duration.ofMinutes(1)),
                m -> !m.isEmpty())
            .get(0);
    store.putResponse(
        new OutboxResponse(new GatewayId(GATEWAY), inboxMessage.requestId(), MAILBOX_RESPONSE));

    // then
    assertThat(inboxMessage.payload()).containsExactly(5, 6, 7);
    assertThat(response.get().getResponse()).isEqualTo(MAILBOX_RESPONSE);
    assertThat(transport.sent.get()).isZero();
  }

  @Test
  void shouldSendOverTheNetworkWhenTheTopologyShowsTheLeader() throws Exception {
    // given
    start(new TestTopologyManager().addPartition(PARTITION, BrokerMemberId.from(0)), mailbox);
    store.putLeader(new PartitionLeader(PARTITION, "us_0", "us", 1));

    // when
    final var response = manager.sendRequest(request(PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID));

    // then
    assertThat(response.get().getResponse()).isEqualTo(NETWORK_RESPONSE);
    assertThat(store.claim(PARTITION, "leader", 10, Duration.ofMinutes(1))).isEmpty();
  }

  @Test
  void shouldSendOverTheNetworkWhenTheStoreHasNoLeader() throws Exception {
    // given
    start(new TestTopologyManager().addPartition(PARTITION, null), mailbox);

    // when
    final var response = manager.sendRequest(request(PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID));

    // then
    assertThat(response.get().getResponse()).isEqualTo(NETWORK_RESPONSE);
    assertThat(store.claim(PARTITION, "leader", 10, Duration.ofMinutes(1))).isEmpty();
  }

  @Test
  void shouldSendOverTheNetworkWithoutAMailbox() throws Exception {
    // given
    start(new TestTopologyManager().addPartition(PARTITION, null), null);
    store.putLeader(new PartitionLeader(PARTITION, "us_0", "us", 1));

    // when
    final var response = manager.sendRequest(request(PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID));

    // then
    assertThat(response.get().getResponse()).isEqualTo(NETWORK_RESPONSE);
    assertThat(store.claim(PARTITION, "leader", 10, Duration.ofMinutes(1))).isEmpty();
  }

  @Test
  void shouldSendOverTheNetworkForOtherPhysicalTenants() throws Exception {
    // given
    final var tenant = "other-tenant";
    start(new TestTopologyManager().addPartition(tenant, PARTITION, null), mailbox);
    store.putLeader(new PartitionLeader(PARTITION, "us_0", "us", 1));

    // when
    final var response = manager.sendRequest(request(tenant));

    // then
    assertThat(response.get().getResponse()).isEqualTo(NETWORK_RESPONSE);
    assertThat(store.claim(PARTITION, "leader", 10, Duration.ofMinutes(1))).isEmpty();
  }

  private void start(
      final TestTopologyManager topology, final @Nullable GatewayMailbox gatewayMailbox) {
    manager =
        new BrokerRequestManager(
            transport,
            topology,
            new RoundRobinDispatchStrategy(),
            Duration.ofSeconds(30),
            new BrokerClientRequestMetrics(new SimpleMeterRegistry()),
            gatewayMailbox);
    scheduler.submitActor(manager).join();
  }

  private static TestRequest request(final String partitionGroup) {
    final var request = new TestRequest();
    request.setPartitionGroup(partitionGroup);
    return request;
  }

  /** Answers every request with {@link #NETWORK_RESPONSE} and counts them. */
  private static final class RecordingTransport implements ClientTransport {
    private final AtomicInteger sent = new AtomicInteger();

    @Override
    public ActorFuture<DirectBuffer> sendRequestWithRetry(
        final Supplier<@Nullable String> nodeAddressSupplier,
        final Predicate<DirectBuffer> responseValidator,
        final ClientRequest clientRequest,
        final Duration timeout) {
      return answer();
    }

    @Override
    public ActorFuture<DirectBuffer> sendRequest(
        final Supplier<@Nullable String> nodeAddressSupplier,
        final ClientRequest clientRequest,
        final Duration timeout) {
      return answer();
    }

    private ActorFuture<DirectBuffer> answer() {
      sent.incrementAndGet();
      return CompletableActorFuture.completed(new UnsafeBuffer(NETWORK_RESPONSE));
    }

    @Override
    public void close() {}
  }

  /** A request to partition 1 whose response is the bytes that came back. */
  private static final class TestRequest extends BrokerRequest<byte[]> {

    TestRequest() {
      super(0, 0);
    }

    @Override
    public void setPartitionId(final int partitionId) {}

    @Override
    public int getPartitionId() {
      return PARTITION;
    }

    @Override
    public RequestType getRequestType() {
      return RequestType.COMMAND;
    }

    @Override
    public boolean addressesSpecificPartition() {
      return true;
    }

    @Override
    public boolean requiresPartitionId() {
      return false;
    }

    @Override
    public @Nullable BufferWriter getRequestWriter() {
      return null;
    }

    @Override
    protected void setSerializedValue(final DirectBuffer buffer) {}

    @Override
    protected void wrapResponse(final DirectBuffer buffer) {}

    @Override
    protected BrokerResponse<byte[]> readResponse() {
      throw new UnsupportedOperationException();
    }

    @Override
    protected byte[] toResponseDto(final DirectBuffer buffer) {
      throw new UnsupportedOperationException();
    }

    @Override
    public BrokerResponse<byte[]> getResponse(final DirectBuffer responseBuffer) {
      final var bytes = new byte[responseBuffer.capacity()];
      responseBuffer.getBytes(0, bytes);
      return new BrokerResponse<>(bytes);
    }

    @Override
    public String getType() {
      return "test";
    }

    @Override
    public int getLength() {
      return 3;
    }

    @Override
    public int write(final MutableDirectBuffer buffer, final int offset) {
      buffer.putBytes(offset, new byte[] {5, 6, 7});
      return 3;
    }
  }
}
