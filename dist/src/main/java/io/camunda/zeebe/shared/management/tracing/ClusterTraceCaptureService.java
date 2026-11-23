/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.shared.management.tracing;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.atomix.cluster.ClusterMembershipService;
import io.atomix.cluster.Member;
import io.atomix.cluster.MemberId;
import io.atomix.cluster.messaging.ClusterCommunicationService;
import io.camunda.zeebe.shared.management.tracing.LocalTraceCapture.LocalCaptureStatus;
import io.camunda.zeebe.util.VersionUtil;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Coordinates cluster-wide trace capture and export across all Atomix cluster members (brokers and
 * standalone gateways) over a single Atomix subject.
 *
 * <p>All JSON serialization, OTLP encoding, and gzip compression/decompression run off Netty I/O
 * threads and off Zeebe's {@code ActorScheduler}: responder handlers run on a small bounded daemon
 * executor, and the coordinator holds only compressed replies while awaiting remote RPCs before
 * decompressing one member at a time straight into the outgoing ZIP stream.
 */
@NullMarked
public final class ClusterTraceCaptureService implements AutoCloseable {

  static final String SUBJECT = "camunda-trace-capture";

  private static final Logger LOG = LoggerFactory.getLogger(ClusterTraceCaptureService.class);
  private static final Duration DEFAULT_RPC_TIMEOUT = Duration.ofSeconds(15);
  private static final int RPC_THREADS = 2;
  private static final int RPC_QUEUE_CAPACITY = 16;
  private static final byte FLAG_RAW = 0;
  private static final byte FLAG_GZIP = 1;
  private static final int DECOMPRESSION_META_HEADROOM_BYTES = 1024 * 1024;
  private static final int MIN_PER_NODE_EXPORT_BYTES = 64 * 1024;

  private static final ObjectMapper MAPPER =
      new ObjectMapper()
          .registerModule(new JavaTimeModule())
          .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
          .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
          .setSerializationInclusion(JsonInclude.Include.NON_NULL);

  private final LocalTraceCapture localCapture;
  private final ClusterMembershipService membershipService;
  private final ClusterCommunicationService communicationService;
  private final Clock wallClock;
  private final Duration rpcTimeout;
  private final int maxNodeExportBytes;
  private final int maxClusterExportBytes;
  private final ExecutorService rpcExecutor;
  private final AtomicBoolean exportInProgress = new AtomicBoolean(false);

  public ClusterTraceCaptureService(
      final LocalTraceCapture localCapture,
      final ClusterMembershipService membershipService,
      final ClusterCommunicationService communicationService) {
    this(
        localCapture,
        membershipService,
        communicationService,
        Clock.systemUTC(),
        DEFAULT_RPC_TIMEOUT,
        TraceCaptureConfig.MAX_NODE_EXPORT_BYTES,
        TraceCaptureConfig.MAX_CLUSTER_EXPORT_BYTES);
  }

  ClusterTraceCaptureService(
      final LocalTraceCapture localCapture,
      final ClusterMembershipService membershipService,
      final ClusterCommunicationService communicationService,
      final Clock wallClock,
      final Duration rpcTimeout,
      final int maxNodeExportBytes,
      final int maxClusterExportBytes) {
    this.localCapture = Objects.requireNonNull(localCapture, "localCapture");
    this.membershipService = Objects.requireNonNull(membershipService, "membershipService");
    this.communicationService =
        Objects.requireNonNull(communicationService, "communicationService");
    this.wallClock = Objects.requireNonNull(wallClock, "wallClock");
    this.rpcTimeout = Objects.requireNonNull(rpcTimeout, "rpcTimeout");
    this.maxNodeExportBytes = maxNodeExportBytes;
    this.maxClusterExportBytes = maxClusterExportBytes;

    final var threadCounter = new AtomicInteger(1);
    rpcExecutor =
        new ThreadPoolExecutor(
            RPC_THREADS,
            RPC_THREADS,
            0L,
            TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(RPC_QUEUE_CAPACITY),
            runnable -> {
              final var thread =
                  new Thread(runnable, "trace-capture-rpc-" + threadCounter.getAndIncrement());
              thread.setDaemon(true);
              return thread;
            });

    registerHandler();
  }

  public ClusterCaptureStatus startCapture(final TraceCaptureConfig config) {
    Objects.requireNonNull(config, "config");
    if (!localCapture.isSdkAttached()) {
      throw new TraceCaptureUnavailableException(
          "OpenTelemetry tracing SDK is disabled on this node");
    }
    // Atomically install the local capture window before fanning out so concurrent POST requests
    // on the same coordinator cannot race past the conflict check.
    final var localStatus = localCapture.startIfInactive(config);
    if (localStatus == null) {
      throw new TraceCaptureConflictException(
          "A trace capture is already active; stop and clear it with DELETE /actuator/traces first");
    }

    final var request =
        new RpcRequest(
            Action.START,
            config.duration().toMillis(),
            config.samplingProbability(),
            config.maxTracesPerSecond(),
            config.maxSpansPerNode(),
            null,
            0);
    LOG.info(
        "Starting cluster-wide trace capture: duration={}, samplingProbability={}, maxTracesPerSecond={}, maxSpansPerNode={}",
        config.duration(),
        config.samplingProbability(),
        config.maxTracesPerSecond(),
        config.maxSpansPerNode());

    return fanOutStatus(request, () -> toNodeStatus(localMemberId().id(), localStatus, null, null));
  }

  public ClusterCaptureStatus clear() {
    LOG.info("Stopping and clearing cluster-wide trace capture buffers");
    final var request = new RpcRequest(Action.CLEAR, 0L, 0.0, 0, 0, null, 0);
    return fanOutStatus(
        request, () -> toNodeStatus(localMemberId().id(), localCapture.clear(), null, null));
  }

  public ClusterCaptureStatus status() {
    final var request = new RpcRequest(Action.STATUS, 0L, 0.0, 0, 0, null, 0);
    return fanOutStatus(
        request, () -> toNodeStatus(localMemberId().id(), localCapture.status(), null, null));
  }

  /**
   * Collects captured spans from all cluster members and returns a support-ready ZIP archive
   * containing {@code traces.otlp.json} and {@code metadata.json}. Only one export runs at a time
   * per coordinator node to bound heap usage.
   */
  public ClusterTraceExport exportZip(final @Nullable String traceId) {
    if (!exportInProgress.compareAndSet(false, true)) {
      throw new TraceCaptureBusyException(
          "A trace export is already in progress on this node; retry shortly");
    }
    try {
      return doExportZip(traceId);
    } finally {
      exportInProgress.set(false);
    }
  }

  private ClusterTraceExport doExportZip(final @Nullable String traceId) {
    final var localId = localMemberId();
    final var members = sortedMembers();
    final int perNodeBudget =
        Math.min(
            maxNodeExportBytes,
            Math.max(
                MIN_PER_NODE_EXPORT_BYTES, maxClusterExportBytes / Math.max(1, members.size())));

    final var request = new RpcRequest(Action.EXPORT, 0L, 0.0, 0, 0, traceId, perNodeBudget);
    final byte[] requestBytes = encodeJson(request);

    // 1. Dispatch all remote RPCs first with raw pass-through decoder so Netty I/O threads never
    // decompress or parse JSON.
    final var remoteFutures = new TreeMap<String, CompletableFuture<RawResult>>();
    for (final var member : members) {
      final var memberId = member.id();
      if (memberId.equals(localId)) {
        continue;
      }
      final var future =
          communicationService
              .send(SUBJECT, requestBytes, bytes -> bytes, bytes -> bytes, memberId, rpcTimeout)
              .handle((wireBytes, error) -> new RawResult(wireBytes, error));
      remoteFutures.put(memberId.id(), future);
    }

    // 2. Serialize the local node while remote RPCs are in flight.
    @Nullable WireReply localReply = null;
    @Nullable String localError = null;
    try {
      localReply = buildLocalExportReply(request);
    } catch (final RuntimeException e) {
      localError = rootCauseMessage(e);
      LOG.warn("Failed to export local traces on member {}: {}", localId, localError);
    }

    // 3. Await all remote compressed replies on the caller thread.
    if (!remoteFutures.isEmpty()) {
      CompletableFuture.allOf(remoteFutures.values().toArray(CompletableFuture[]::new)).join();
    }

    // 4. Stream one node at a time into the ZIP archive so the coordinator never holds all
    // decompressed nodes in memory simultaneously.
    final var nodes = new TreeMap<String, NodeStatus>();
    final var errors = new TreeMap<String, String>();
    final var zipOut = new ByteArrayOutputStream(64 * 1024);

    try (final var zip = new ZipOutputStream(zipOut, StandardCharsets.UTF_8)) {
      zip.putNextEntry(new ZipEntry("traces.otlp.json"));
      zip.write(OtlpJsonTraceSerializer.OTLP_PREFIX);
      boolean hasResourceSpans = false;

      for (final var member : members) {
        final var memberKey = member.id().id();
        final WireReply reply;
        if (member.id().equals(localId)) {
          if (localError != null || localReply == null) {
            errors.put(memberKey, localError != null ? localError : "Local export failed");
            continue;
          }
          reply = localReply;
        } else {
          final var future = remoteFutures.get(memberKey);
          if (future == null) {
            continue;
          }
          final var raw = future.join();
          if (raw.error() != null) {
            final var message = rootCauseMessage(raw.error());
            LOG.warn("Failed to export traces from member {}: {}", memberKey, message);
            errors.put(memberKey, message);
            continue;
          }
          final var wireBytes = raw.wireBytes();
          if (wireBytes == null) {
            errors.put(memberKey, "Empty response from member");
            continue;
          }
          try {
            reply = decodeWireReply(wireBytes, perNodeBudget);
            OtlpJsonTraceSerializer.validateResourceSpansArray(reply.rawResourceSpans());
          } catch (final RuntimeException e) {
            final var message = rootCauseMessage(e);
            LOG.warn("Failed to decode trace export from member {}: {}", memberKey, message);
            errors.put(memberKey, message);
            continue;
          }
        }

        nodes.put(memberKey, reply.status());
        hasResourceSpans =
            OtlpJsonTraceSerializer.writeResourceSpansElements(
                zip, reply.rawResourceSpans(), hasResourceSpans);
      }

      zip.write(OtlpJsonTraceSerializer.OTLP_SUFFIX);
      zip.closeEntry();

      int totalExported = 0;
      long totalEvicted = 0;
      boolean anyTruncated = false;
      for (final var node : nodes.values()) {
        if (node.exportedSpans() != null) {
          totalExported += node.exportedSpans();
        }
        totalEvicted += node.evictedSpans();
        if (Boolean.TRUE.equals(node.truncated())) {
          anyTruncated = true;
        }
      }

      final var metadata =
          new ExportMetadata(
              VersionUtil.getVersion(),
              wallClock.instant(),
              localId.id(),
              traceId,
              members.size(),
              nodes.size(),
              totalExported,
              totalEvicted,
              anyTruncated,
              Map.copyOf(nodes),
              Map.copyOf(errors));
      final byte[] metadataJson = encodePrettyJson(metadata);

      zip.putNextEntry(new ZipEntry("metadata.json"));
      zip.write(metadataJson);
      zip.closeEntry();

      zip.finish();
      return new ClusterTraceExport(zipOut.toByteArray(), metadata);
    } catch (final IOException e) {
      throw new UncheckedIOException("Failed to stream trace export ZIP bundle", e);
    }
  }

  @Override
  public void close() {
    communicationService.unsubscribe(SUBJECT);
    rpcExecutor.shutdownNow();
  }

  private void registerHandler() {
    communicationService.replyTo(SUBJECT, bytes -> bytes, this::handleRpcAsync, bytes -> bytes);
  }

  private CompletableFuture<byte[]> handleRpcAsync(final byte[] rawRequest) {
    final var future = new CompletableFuture<byte[]>();
    try {
      rpcExecutor.execute(
          () -> {
            try {
              final var req = decodeJson(rawRequest, RpcRequest.class);
              final byte[] response = handleRpc(req);
              future.complete(response);
            } catch (final Throwable t) {
              future.completeExceptionally(t);
            }
          });
    } catch (final RejectedExecutionException e) {
      future.completeExceptionally(
          new IllegalStateException("Trace capture RPC executor is saturated", e));
    }
    return future;
  }

  private byte[] handleRpc(final RpcRequest req) {
    final var localId = localMemberId().id();
    return switch (req.action()) {
      case START -> {
        final LocalCaptureStatus localStatus;
        if (!localCapture.isSdkAttached()) {
          localStatus = localCapture.status();
        } else {
          final var config =
              new TraceCaptureConfig(
                  Duration.ofMillis(req.durationMs()),
                  req.samplingProbability(),
                  req.maxTracesPerSecond(),
                  req.maxSpansPerNode());
          localStatus = localCapture.start(config);
        }
        final var status = toNodeStatus(localId, localStatus, null, null);
        yield encodeWireReply(new WireReply(status, new byte[0]), false);
      }
      case CLEAR -> {
        final var status = toNodeStatus(localId, localCapture.clear(), null, null);
        yield encodeWireReply(new WireReply(status, new byte[0]), false);
      }
      case STATUS -> {
        final var status = toNodeStatus(localId, localCapture.status(), null, null);
        yield encodeWireReply(new WireReply(status, new byte[0]), false);
      }
      case EXPORT -> encodeWireReply(buildLocalExportReply(req), true);
    };
  }

  private WireReply buildLocalExportReply(final RpcRequest req) {
    final var localId = localMemberId().id();
    final var snapshot = localCapture.snapshot(req.traceId());
    final int byteLimit = Math.min(Math.max(req.maxBytes(), 1024), maxNodeExportBytes);
    final var serialized =
        OtlpJsonTraceSerializer.serializeResourceSpansArray(snapshot.spans(), localId, byteLimit);
    final var status =
        toNodeStatus(
            localId, snapshot.status(), serialized.exportedSpans(), serialized.truncated());
    return new WireReply(status, serialized.resourceSpansArrayJson());
  }

  private ClusterCaptureStatus fanOutStatus(
      final RpcRequest request, final Supplier<NodeStatus> localAction) {
    final var localId = localMemberId();
    final var members = sortedMembers();
    final byte[] requestBytes = encodeJson(request);

    // Dispatch remote RPCs first, then run local action while remote RPCs are in flight.
    final var remoteFutures = new TreeMap<String, CompletableFuture<RawResult>>();
    for (final var member : members) {
      final var memberId = member.id();
      if (memberId.equals(localId)) {
        continue;
      }
      final var future =
          communicationService
              .send(SUBJECT, requestBytes, bytes -> bytes, bytes -> bytes, memberId, rpcTimeout)
              .handle((wireBytes, error) -> new RawResult(wireBytes, error));
      remoteFutures.put(memberId.id(), future);
    }

    final var nodes = new TreeMap<String, NodeStatus>();
    final var errors = new TreeMap<String, String>();

    try {
      nodes.put(localId.id(), localAction.get());
    } catch (final RuntimeException e) {
      final var message = rootCauseMessage(e);
      LOG.warn(
          "Local trace capture action {} failed on member {}: {}",
          request.action(),
          localId,
          message);
      errors.put(localId.id(), message);
    }

    if (!remoteFutures.isEmpty()) {
      CompletableFuture.allOf(remoteFutures.values().toArray(CompletableFuture[]::new)).join();
    }

    for (final var entry : remoteFutures.entrySet()) {
      final var memberKey = entry.getKey();
      final var raw = entry.getValue().join();
      final var wireBytes = raw.wireBytes();
      if (raw.error() != null) {
        final var message = rootCauseMessage(raw.error());
        LOG.warn(
            "Trace capture RPC {} to member {} failed: {}", request.action(), memberKey, message);
        errors.put(memberKey, message);
      } else if (wireBytes == null) {
        errors.put(memberKey, "Empty response from member");
      } else {
        try {
          nodes.put(memberKey, decodeWireReply(wireBytes, 0).status());
        } catch (final RuntimeException e) {
          final var message = rootCauseMessage(e);
          LOG.warn("Failed to decode trace capture status from member {}: {}", memberKey, message);
          errors.put(memberKey, message);
        }
      }
    }

    boolean anyActive = false;
    boolean anySdkEnabled = false;
    int totalCaptured = 0;
    long totalEvicted = 0;
    for (final var node : nodes.values()) {
      anyActive |= node.active();
      anySdkEnabled |= node.sdkEnabled();
      totalCaptured += node.capturedSpans();
      totalEvicted += node.evictedSpans();
    }

    return new ClusterCaptureStatus(
        anyActive,
        anySdkEnabled,
        members.size(),
        nodes.size(),
        totalCaptured,
        totalEvicted,
        Map.copyOf(nodes),
        Map.copyOf(errors));
  }

  /**
   * Wire frame: {@code [1-byte compression flag][payload]} where payload (uncompressed) is {@code
   * [4-byte big-endian metaLen][metaJson UTF-8][raw resourceSpans UTF-8]}. Raw OTLP bytes are never
   * JSON-escaped inside a string wrapper.
   */
  private byte[] encodeWireReply(final WireReply reply, final boolean compress) {
    final byte[] metaJson = encodeJson(reply.status());
    final byte[] rawSpans = reply.rawResourceSpans();
    final int framedLen = Integer.BYTES + metaJson.length + rawSpans.length;
    final byte[] framed = new byte[framedLen];
    ByteBuffer.wrap(framed).putInt(metaJson.length);
    System.arraycopy(metaJson, 0, framed, Integer.BYTES, metaJson.length);
    System.arraycopy(rawSpans, 0, framed, Integer.BYTES + metaJson.length, rawSpans.length);

    if (!compress) {
      final byte[] out = new byte[1 + framedLen];
      out[0] = FLAG_RAW;
      System.arraycopy(framed, 0, out, 1, framedLen);
      return out;
    }

    final var out = new ByteArrayOutputStream(Math.min(framedLen / 4 + 256, 256 * 1024));
    out.write(FLAG_GZIP);
    try (final var gzip = new GZIPOutputStream(out)) {
      gzip.write(framed);
    } catch (final IOException e) {
      throw new UncheckedIOException("Failed to compress trace capture reply", e);
    }
    return out.toByteArray();
  }

  private WireReply decodeWireReply(final byte[] wireBytes, final int expectedMaxSpansBytes) {
    if (wireBytes.length < 1 + Integer.BYTES) {
      throw new IllegalArgumentException(
          "Truncated trace capture reply (" + wireBytes.length + " bytes)");
    }
    final byte flag = wireBytes[0];
    final byte[] framed;
    if (flag == FLAG_RAW) {
      framed = new byte[wireBytes.length - 1];
      System.arraycopy(wireBytes, 1, framed, 0, framed.length);
    } else if (flag == FLAG_GZIP) {
      final int clampedSpansLimit =
          Math.min(Math.max(0, expectedMaxSpansBytes), maxNodeExportBytes);
      final int maxDecompressed = clampedSpansLimit + DECOMPRESSION_META_HEADROOM_BYTES;
      framed = gunzipBounded(wireBytes, 1, wireBytes.length - 1, maxDecompressed);
    } else {
      throw new IllegalArgumentException(
          "Unsupported trace capture reply compression flag: " + flag);
    }

    if (framed.length < Integer.BYTES) {
      throw new IllegalArgumentException("Truncated trace capture reply frame");
    }
    final int metaLen = ByteBuffer.wrap(framed, 0, Integer.BYTES).getInt();
    if (metaLen <= 0 || metaLen > framed.length - Integer.BYTES) {
      throw new IllegalArgumentException("Invalid trace capture metadata length: " + metaLen);
    }
    final var metaBytes = new byte[metaLen];
    System.arraycopy(framed, Integer.BYTES, metaBytes, 0, metaLen);
    final var status = decodeJson(metaBytes, NodeStatus.class);

    final int spansOffset = Integer.BYTES + metaLen;
    final int spansLen = framed.length - spansOffset;
    final byte[] rawSpans = new byte[spansLen];
    if (spansLen > 0) {
      System.arraycopy(framed, spansOffset, rawSpans, 0, spansLen);
    }
    return new WireReply(status, rawSpans);
  }

  private static byte[] gunzipBounded(
      final byte[] input, final int offset, final int length, final int maxBytes) {
    final var out = new ByteArrayOutputStream();
    try (final var gzip = new GZIPInputStream(new ByteArrayInputStream(input, offset, length))) {
      final var buf = new byte[8192];
      int read;
      while ((read = gzip.read(buf)) != -1) {
        if (out.size() + read > maxBytes) {
          throw new IllegalStateException(
              "Decompressed trace export reply exceeded limit of " + maxBytes + " bytes");
        }
        out.write(buf, 0, read);
      }
    } catch (final IOException e) {
      throw new UncheckedIOException("Failed to decompress trace capture reply", e);
    }
    return out.toByteArray();
  }

  private List<Member> sortedMembers() {
    final var members = new ArrayList<>(membershipService.getMembers());
    final var local = membershipService.getLocalMember();
    if (members.stream().noneMatch(m -> m.id().equals(local.id()))) {
      members.add(local);
    }
    members.sort(Comparator.comparing(member -> member.id().id()));
    return members;
  }

  private MemberId localMemberId() {
    return membershipService.getLocalMember().id();
  }

  private static NodeStatus toNodeStatus(
      final String memberId,
      final LocalCaptureStatus local,
      final @Nullable Integer exportedSpans,
      final @Nullable Boolean truncated) {
    return new NodeStatus(
        memberId,
        local.active(),
        local.sdkEnabled(),
        local.startedAt(),
        local.expiresAt(),
        local.retentionExpiresAt(),
        local.samplingProbability(),
        local.maxTracesPerSecond(),
        local.maxSpans(),
        local.capturedSpans(),
        local.evictedSpans(),
        exportedSpans,
        truncated);
  }

  private static byte[] encodeJson(final Object value) {
    try {
      return MAPPER.writeValueAsBytes(value);
    } catch (final JsonProcessingException e) {
      throw new UncheckedIOException("Failed to serialize trace capture message", e);
    }
  }

  private static byte[] encodePrettyJson(final Object value) {
    try {
      return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsBytes(value);
    } catch (final JsonProcessingException e) {
      throw new UncheckedIOException("Failed to serialize trace export metadata", e);
    }
  }

  private static <T> T decodeJson(final byte[] bytes, final Class<T> type) {
    try {
      return MAPPER.readValue(bytes, type);
    } catch (final IOException e) {
      throw new UncheckedIOException("Failed to deserialize " + type.getSimpleName(), e);
    }
  }

  private static String rootCauseMessage(final Throwable error) {
    Throwable current = error;
    while (current instanceof CompletionException && current.getCause() != null) {
      current = current.getCause();
    }
    final var message = current.getMessage();
    return message != null && !message.isBlank()
        ? current.getClass().getSimpleName() + ": " + message
        : current.getClass().getSimpleName();
  }

  enum Action {
    START,
    CLEAR,
    STATUS,
    EXPORT
  }

  record RpcRequest(
      Action action,
      long durationMs,
      double samplingProbability,
      int maxTracesPerSecond,
      int maxSpansPerNode,
      @Nullable String traceId,
      int maxBytes) {}

  private record WireReply(NodeStatus status, byte[] rawResourceSpans) {}

  private record RawResult(byte @Nullable [] wireBytes, @Nullable Throwable error) {}

  public record NodeStatus(
      String memberId,
      boolean active,
      boolean sdkEnabled,
      @Nullable Instant startedAt,
      @Nullable Instant expiresAt,
      @Nullable Instant retentionExpiresAt,
      double samplingProbability,
      int maxTracesPerSecond,
      int maxSpans,
      int capturedSpans,
      long evictedSpans,
      @Nullable Integer exportedSpans,
      @Nullable Boolean truncated) {}

  public record ClusterCaptureStatus(
      boolean active,
      boolean sdkEnabled,
      int expectedNodes,
      int respondedNodes,
      int totalCapturedSpans,
      long totalEvictedSpans,
      Map<String, NodeStatus> nodes,
      Map<String, String> errors) {}

  public record ExportMetadata(
      String camundaVersion,
      Instant exportedAt,
      String coordinatorMemberId,
      @Nullable String traceIdFilter,
      int expectedNodes,
      int respondedNodes,
      int totalExportedSpans,
      long totalEvictedSpans,
      boolean truncated,
      Map<String, NodeStatus> nodes,
      Map<String, String> errors) {}

  public record ClusterTraceExport(byte[] zipBytes, ExportMetadata metadata) {}

  public static final class TraceCaptureUnavailableException extends IllegalStateException {
    public TraceCaptureUnavailableException(final String message) {
      super(message);
    }
  }

  public static final class TraceCaptureConflictException extends IllegalStateException {
    public TraceCaptureConflictException(final String message) {
      super(message);
    }
  }

  public static final class TraceCaptureBusyException extends IllegalStateException {
    public TraceCaptureBusyException(final String message) {
      super(message);
    }
  }
}
