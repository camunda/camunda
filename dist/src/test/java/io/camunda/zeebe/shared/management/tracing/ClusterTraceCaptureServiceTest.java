/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.shared.management.tracing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.atomix.cluster.ClusterMembershipService;
import io.atomix.cluster.Member;
import io.atomix.cluster.MemberId;
import io.atomix.cluster.messaging.ClusterCommunicationService;
import io.atomix.cluster.messaging.MessagingException;
import io.camunda.zeebe.shared.management.tracing.ClusterTraceCaptureService.TraceCaptureBusyException;
import io.camunda.zeebe.shared.management.tracing.ClusterTraceCaptureService.TraceCaptureConflictException;
import io.camunda.zeebe.shared.management.tracing.ClusterTraceCaptureService.TraceCaptureUnavailableException;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import java.io.ByteArrayInputStream;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;

final class ClusterTraceCaptureServiceTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final Clock FIXED_CLOCK =
      Clock.fixed(Instant.parse("2026-11-23T12:00:00Z"), ZoneOffset.UTC);

  @Test
  void shouldCoordinateCaptureAndStreamZipExportAcrossClusterMembers() throws Exception {
    // given - two nodes connected via an in-memory ClusterCommunicationService bus
    final var bus = new InMemoryClusterBus();
    final var gatewayId = MemberId.from("gateway");
    final var brokerId = MemberId.from("0");
    final var members = Set.of(member(gatewayId), member(brokerId));

    try (final var gatewayCapture = attachedCapture();
        final var brokerCapture = attachedCapture();
        final var gatewayService =
            new ClusterTraceCaptureService(
                gatewayCapture,
                membership(gatewayId, members),
                bus.forMember(gatewayId),
                FIXED_CLOCK,
                Duration.ofSeconds(5),
                TraceCaptureConfig.MAX_NODE_EXPORT_BYTES,
                TraceCaptureConfig.MAX_CLUSTER_EXPORT_BYTES);
        final var brokerService =
            new ClusterTraceCaptureService(
                brokerCapture,
                membership(brokerId, members),
                bus.forMember(brokerId),
                FIXED_CLOCK,
                Duration.ofSeconds(5),
                TraceCaptureConfig.MAX_NODE_EXPORT_BYTES,
                TraceCaptureConfig.MAX_CLUSTER_EXPORT_BYTES);
        final var gatewayProvider = tracerProvider(gatewayCapture);
        final var brokerProvider = tracerProvider(brokerCapture)) {

      // when - start capture on gateway -> fans out to broker
      final var startStatus =
          gatewayService.startCapture(new TraceCaptureConfig(Duration.ofSeconds(60), 1.0, 50, 500));
      assertThat(startStatus.active()).isTrue();
      assertThat(startStatus.sdkEnabled()).isTrue();
      assertThat(startStatus.expectedNodes()).isEqualTo(2);
      assertThat(startStatus.respondedNodes()).isEqualTo(2);
      assertThat(startStatus.nodes()).containsOnlyKeys("0", "gateway");
      assertThat(startStatus.errors()).isEmpty();

      // starting a second capture while active is rejected with conflict
      assertThatThrownBy(() -> gatewayService.startCapture(TraceCaptureConfig.defaults()))
          .isInstanceOf(TraceCaptureConflictException.class);

      final var gwSpan = gatewayProvider.get("gateway").spanBuilder("gw-request").startSpan();
      final var gwTraceId = gwSpan.getSpanContext().getTraceId();
      gwSpan.end();

      brokerProvider.get("broker").spanBuilder("broker-request").startSpan().end();

      // then - cluster status aggregates counts across both nodes
      final var status = gatewayService.status();
      assertThat(status.totalCapturedSpans()).isEqualTo(2);
      assertThat(status.nodes().get("gateway").capturedSpans()).isEqualTo(1);
      assertThat(status.nodes().get("0").capturedSpans()).isEqualTo(1);

      // then - full ZIP export streams resourceSpans from both nodes plus rich metadata
      final var export = gatewayService.exportZip(null);
      assertThat(export.metadata().totalExportedSpans()).isEqualTo(2);
      assertThat(export.metadata().expectedNodes()).isEqualTo(2);
      assertThat(export.metadata().respondedNodes()).isEqualTo(2);
      assertThat(export.metadata().camundaVersion()).isNotBlank();
      assertThat(export.metadata().errors()).isEmpty();
      assertThat(export.metadata().nodes()).containsOnlyKeys("0", "gateway");
      assertThat(export.metadata().nodes().get("0").samplingProbability()).isEqualTo(1.0);

      final var entries = unzip(export.zipBytes());
      assertThat(entries).containsOnlyKeys("traces.otlp.json", "metadata.json");
      assertThat(MAPPER.readTree(entries.get("traces.otlp.json")).path("resourceSpans").size())
          .isEqualTo(2);

      // then - traceId-filtered export returns only the matching span
      final var filtered = gatewayService.exportZip(gwTraceId);
      assertThat(filtered.metadata().totalExportedSpans()).isEqualTo(1);
      assertThat(filtered.metadata().traceIdFilter()).isEqualTo(gwTraceId);

      // then - clear() stops and clears both nodes, allowing a fresh start
      final var cleared = gatewayService.clear();
      assertThat(cleared.active()).isFalse();
      assertThat(cleared.totalCapturedSpans()).isZero();
    }
  }

  @Test
  void shouldRoundTripFullPerNodeByteBudgetWithoutDecompressionFailure() throws Exception {
    // given - remote broker fills its entire 64 KiB export budget (regression test: no JSON string
    // double-escaping blow-up on wire framing)
    final var bus = new InMemoryClusterBus();
    final var gatewayId = MemberId.from("gateway");
    final var brokerId = MemberId.from("0");
    final var members = Set.of(member(gatewayId), member(brokerId));
    final int tightNodeCap = 64 * 1024;

    try (final var gatewayCapture = attachedCapture();
        final var brokerCapture = attachedCapture();
        final var gatewayService =
            new ClusterTraceCaptureService(
                gatewayCapture,
                membership(gatewayId, members),
                bus.forMember(gatewayId),
                FIXED_CLOCK,
                Duration.ofSeconds(5),
                tightNodeCap,
                tightNodeCap * 2);
        final var brokerService =
            new ClusterTraceCaptureService(
                brokerCapture,
                membership(brokerId, members),
                bus.forMember(brokerId),
                FIXED_CLOCK,
                Duration.ofSeconds(5),
                tightNodeCap,
                tightNodeCap * 2);
        final var brokerProvider = tracerProvider(brokerCapture)) {

      gatewayService.startCapture(new TraceCaptureConfig(Duration.ofSeconds(60), 1.0, 500, 1000));
      final var tracer = brokerProvider.get("broker");
      for (int i = 0; i < 400; i++) {
        tracer
            .spanBuilder("quoted-\"span\"-" + i)
            .setAttribute("zeebe.value_type", "PROCESS_INSTANCE_CREATION")
            .setAttribute("detail", "value-\"quoted\"-".repeat(20))
            .startSpan()
            .end();
      }

      // when
      final var export = gatewayService.exportZip(null);

      // then - remote broker export decodes cleanly with truncated=true and valid OTLP JSON
      assertThat(export.metadata().errors()).isEmpty();
      assertThat(export.metadata().truncated()).isTrue();
      assertThat(export.metadata().nodes().get("0").truncated()).isTrue();
      assertThat(export.metadata().totalExportedSpans()).isPositive();
      final var entries = unzip(export.zipBytes());
      assertThat(MAPPER.readTree(entries.get("traces.otlp.json")).path("resourceSpans").size())
          .isEqualTo(1);
    }
  }

  @Test
  void shouldRejectStartWhenTracingSdkIsDisabled() {
    // given - LocalTraceCapture was never attached to SdkTracerProvider
    final var bus = new InMemoryClusterBus();
    final var memberId = MemberId.from("0");
    try (final var capture = new LocalTraceCapture();
        final var service =
            new ClusterTraceCaptureService(
                capture,
                membership(memberId, Set.of(member(memberId))),
                bus.forMember(memberId),
                FIXED_CLOCK,
                Duration.ofSeconds(2),
                TraceCaptureConfig.MAX_NODE_EXPORT_BYTES,
                TraceCaptureConfig.MAX_CLUSTER_EXPORT_BYTES)) {

      // when / then
      assertThat(service.status().sdkEnabled()).isFalse();
      assertThatThrownBy(() -> service.startCapture(TraceCaptureConfig.defaults()))
          .isInstanceOf(TraceCaptureUnavailableException.class);
    }
  }

  @Test
  void shouldRejectConcurrentExportWithBusyException() throws Exception {
    // given - remote member blocks its export reply until released
    final var bus = new InMemoryClusterBus();
    final var exportStarted = new CountDownLatch(1);
    final var releaseExport = new CountDownLatch(1);
    final var gatewayId = MemberId.from("gateway");
    final var brokerId = MemberId.from("0");
    final var members = Set.of(member(gatewayId), member(brokerId));

    final var blockingBrokerBus = bus.forMember(brokerId);
    blockingBrokerBus.replyTo(
        ClusterTraceCaptureService.SUBJECT,
        bytes -> bytes,
        raw -> {
          exportStarted.countDown();
          try {
            releaseExport.await(5, TimeUnit.SECONDS);
          } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
          }
          return CompletableFuture.<byte[]>failedFuture(new IllegalStateException("released"));
        },
        bytes -> bytes);

    try (final var gatewayCapture = attachedCapture();
        final var gatewayService =
            new ClusterTraceCaptureService(
                gatewayCapture,
                membership(gatewayId, members),
                bus.forMember(gatewayId),
                FIXED_CLOCK,
                Duration.ofSeconds(5),
                TraceCaptureConfig.MAX_NODE_EXPORT_BYTES,
                TraceCaptureConfig.MAX_CLUSTER_EXPORT_BYTES)) {

      // when - first export is in flight
      final var firstExport = CompletableFuture.supplyAsync(() -> gatewayService.exportZip(null));
      assertThat(exportStarted.await(5, TimeUnit.SECONDS)).isTrue();

      // then - second concurrent export fails fast with TraceCaptureBusyException
      assertThatThrownBy(() -> gatewayService.exportZip(null))
          .isInstanceOf(TraceCaptureBusyException.class);

      releaseExport.countDown();
      firstExport.join();
    }
  }

  @Test
  void shouldReturnPartialExportWhenOneClusterMemberIsUnreachable() throws Exception {
    // given - broker-1 never registered handlers (simulates unreachable or older node)
    final var bus = new InMemoryClusterBus();
    final var gatewayId = MemberId.from("gateway");
    final var unreachableBrokerId = MemberId.from("1");
    final var members = Set.of(member(gatewayId), member(unreachableBrokerId));

    try (final var gatewayCapture = attachedCapture();
        final var gatewayService =
            new ClusterTraceCaptureService(
                gatewayCapture,
                membership(gatewayId, members),
                bus.forMember(gatewayId),
                FIXED_CLOCK,
                Duration.ofSeconds(2),
                TraceCaptureConfig.MAX_NODE_EXPORT_BYTES,
                TraceCaptureConfig.MAX_CLUSTER_EXPORT_BYTES);
        final var gatewayProvider = tracerProvider(gatewayCapture)) {

      // when
      final var startStatus =
          gatewayService.startCapture(new TraceCaptureConfig(Duration.ofSeconds(60), 1.0, 50, 500));
      assertThat(startStatus.nodes()).containsOnlyKeys("gateway");
      assertThat(startStatus.errors()).containsKey("1");

      gatewayProvider.get("gateway").spanBuilder("gateway-only").startSpan().end();

      final var export = gatewayService.exportZip(null);

      // then - export still succeeds with reachable node's spans and reports the failure
      assertThat(export.metadata().expectedNodes()).isEqualTo(2);
      assertThat(export.metadata().respondedNodes()).isEqualTo(1);
      assertThat(export.metadata().totalExportedSpans()).isEqualTo(1);
      assertThat(export.metadata().nodes()).containsOnlyKeys("gateway");
      assertThat(export.metadata().errors()).containsKey("1");
      final var entries = unzip(export.zipBytes());
      assertThat(MAPPER.readTree(entries.get("traces.otlp.json")).path("resourceSpans").size())
          .isEqualTo(1);
    }
  }

  @Test
  void shouldIsolateMalformedPeerExportPayloadIntoErrors() throws Exception {
    // given - remote peer returns a valid raw wire frame with invalid (non-array) OTLP bytes
    final var bus = new InMemoryClusterBus();
    final var gatewayId = MemberId.from("gateway");
    final var malformedBrokerId = MemberId.from("1");
    final var members = Set.of(member(gatewayId), member(malformedBrokerId));

    final byte[] metaJson =
        """
        {"memberId":"1","active":false,"sdkEnabled":true,"samplingProbability":0.0,"maxTracesPerSecond":0,"maxSpans":0,"capturedSpans":0,"evictedSpans":0,"exportedSpans":0,"truncated":false}
        """
            .trim()
            .getBytes(java.nio.charset.StandardCharsets.UTF_8);
    final byte[] corruptSpans =
        "not-a-json-array".getBytes(java.nio.charset.StandardCharsets.UTF_8);
    final var frame =
        java.nio.ByteBuffer.allocate(1 + Integer.BYTES + metaJson.length + corruptSpans.length)
            .put((byte) 0)
            .putInt(metaJson.length)
            .put(metaJson)
            .put(corruptSpans)
            .array();

    bus.forMember(malformedBrokerId)
        .replyTo(
            ClusterTraceCaptureService.SUBJECT,
            bytes -> bytes,
            raw -> CompletableFuture.completedFuture(frame),
            bytes -> bytes);

    try (final var gatewayCapture = attachedCapture();
        final var gatewayService =
            new ClusterTraceCaptureService(
                gatewayCapture,
                membership(gatewayId, members),
                bus.forMember(gatewayId),
                FIXED_CLOCK,
                Duration.ofSeconds(2),
                TraceCaptureConfig.MAX_NODE_EXPORT_BYTES,
                TraceCaptureConfig.MAX_CLUSTER_EXPORT_BYTES);
        final var gatewayProvider = tracerProvider(gatewayCapture)) {

      gatewayService.startCapture(new TraceCaptureConfig(Duration.ofSeconds(60), 1.0, 50, 500));
      gatewayProvider.get("gateway").spanBuilder("healthy-gateway-span").startSpan().end();

      // when
      final var export = gatewayService.exportZip(null);

      // then - malformed peer is recorded in errors without aborting healthy node's ZIP export
      assertThat(export.metadata().errors()).containsKey("1");
      assertThat(export.metadata().nodes()).containsOnlyKeys("gateway");
      assertThat(export.metadata().totalExportedSpans()).isEqualTo(1);
      final var entries = unzip(export.zipBytes());
      assertThat(MAPPER.readTree(entries.get("traces.otlp.json")).path("resourceSpans").size())
          .isEqualTo(1);
    }
  }

  @Test
  void shouldSubscribeAndUnsubscribeSingleSubjectOnClose() {
    // given
    final var bus = new InMemoryClusterBus();
    final var memberId = MemberId.from("0");
    try (final var capture = attachedCapture()) {
      final var service =
          new ClusterTraceCaptureService(
              capture,
              membership(memberId, Set.of(member(memberId))),
              bus.forMember(memberId),
              FIXED_CLOCK,
              Duration.ofSeconds(2),
              TraceCaptureConfig.MAX_NODE_EXPORT_BYTES,
              TraceCaptureConfig.MAX_CLUSTER_EXPORT_BYTES);

      assertThat(bus.subscribedSubjects(memberId))
          .containsExactly(ClusterTraceCaptureService.SUBJECT);

      // when
      service.close();

      // then
      assertThat(bus.subscribedSubjects(memberId)).isEmpty();
    }
  }

  private static LocalTraceCapture attachedCapture() {
    final var capture = new LocalTraceCapture();
    capture.markSdkAttached();
    return capture;
  }

  private static SdkTracerProvider tracerProvider(final LocalTraceCapture capture) {
    return SdkTracerProvider.builder()
        .setSampler(capture.createSampler(Sampler.parentBased(Sampler.alwaysOff())))
        .addSpanProcessor(capture.spanProcessor())
        .build();
  }

  private static Map<String, byte[]> unzip(final byte[] zipBytes) throws Exception {
    final var entries = new HashMap<String, byte[]>();
    try (final var zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
      ZipEntry entry;
      while ((entry = zis.getNextEntry()) != null) {
        entries.put(entry.getName(), zis.readAllBytes());
      }
    }
    return entries;
  }

  private static Member member(final MemberId id) {
    final var m = mock(Member.class);
    when(m.id()).thenReturn(id);
    return m;
  }

  private static ClusterMembershipService membership(
      final MemberId localId, final Set<Member> allMembers) {
    final var service = mock(ClusterMembershipService.class);
    final var local =
        allMembers.stream().filter(m -> m.id().equals(localId)).findFirst().orElseThrow();
    when(service.getLocalMember()).thenReturn(local);
    when(service.getMembers()).thenReturn(allMembers);
    return service;
  }

  /** Minimal in-memory ClusterCommunicationService exercising full byte[] encode/decode paths. */
  private static final class InMemoryClusterBus {

    private final Map<
            MemberId, Map<String, BiFunction<MemberId, byte[], CompletableFuture<byte[]>>>>
        handlers = new ConcurrentHashMap<>();

    Set<String> subscribedSubjects(final MemberId memberId) {
      return new HashSet<>(handlers.getOrDefault(memberId, Map.of()).keySet());
    }

    ClusterCommunicationService forMember(final MemberId localId) {
      handlers.computeIfAbsent(localId, ignored -> new ConcurrentHashMap<>());
      return new ClusterCommunicationService() {
        @Override
        public <M> void broadcast(
            final String subject,
            final M message,
            final Function<M, byte[]> encoder,
            final boolean reliable) {}

        @Override
        public <M> void multicast(
            final String subject,
            final M message,
            final Function<M, byte[]> encoder,
            final Set<MemberId> memberIds,
            final boolean reliable) {}

        @Override
        public <M> void unicast(
            final String subject,
            final M message,
            final Function<M, byte[]> encoder,
            final MemberId memberId,
            final boolean reliable) {}

        @Override
        public <M, R> CompletableFuture<R> send(
            final String subject,
            final M message,
            final Function<M, byte[]> encoder,
            final Function<byte[], R> decoder,
            final MemberId toMemberId,
            final Duration timeout) {
          final var memberHandlers = handlers.get(toMemberId);
          final var handler = memberHandlers != null ? memberHandlers.get(subject) : null;
          if (handler == null) {
            return CompletableFuture.failedFuture(new MessagingException.NoRemoteHandler(subject));
          }
          final byte[] encoded = encoder.apply(message);
          return handler.apply(localId, encoded).thenApply(decoder);
        }

        @Override
        public <M, R> void replyTo(
            final String subject,
            final Function<byte[], M> decoder,
            final Function<M, CompletableFuture<R>> handler,
            final Function<R, byte[]> encoder) {
          handlers
              .get(localId)
              .put(
                  subject,
                  (sender, payload) -> handler.apply(decoder.apply(payload)).thenApply(encoder));
        }

        @Override
        public <M> void consume(
            final String subject,
            final Function<byte[], M> decoder,
            final Consumer<M> handler,
            final Executor executor) {}

        @Override
        public <M> void consume(
            final String subject,
            final Function<byte[], M> decoder,
            final BiConsumer<MemberId, M> handler,
            final Executor executor) {}

        @Override
        public <M, R> void replyTo(
            final String subject,
            final Function<byte[], M> decoder,
            final BiFunction<MemberId, M, R> handler,
            final Function<R, byte[]> encoder,
            final Executor executor) {
          handlers
              .get(localId)
              .put(
                  subject,
                  (sender, payload) ->
                      CompletableFuture.supplyAsync(
                          () -> encoder.apply(handler.apply(sender, decoder.apply(payload))),
                          executor));
        }

        @Override
        public <M, R> void replyToAsync(
            final String subject,
            final Function<byte[], M> decoder,
            final Function<M, CompletableFuture<R>> handler,
            final Function<R, byte[]> encoder,
            final Executor executor) {
          handlers
              .get(localId)
              .put(
                  subject,
                  (sender, payload) -> handler.apply(decoder.apply(payload)).thenApply(encoder));
        }

        @Override
        public void unsubscribe(final String subject) {
          handlers.get(localId).remove(subject);
        }
      };
    }
  }
}
