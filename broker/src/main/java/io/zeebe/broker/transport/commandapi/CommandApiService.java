/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.transport.commandapi;

import static io.zeebe.broker.Broker.LOG;
import static io.zeebe.broker.Broker.actorNamePattern;

import io.zeebe.broker.Loggers;
import io.zeebe.broker.PartitionListener;
import io.zeebe.broker.transport.backpressure.PartitionAwareRequestLimiter;
import io.zeebe.broker.transport.backpressure.RequestLimiter;
import io.zeebe.engine.processor.CommandResponseWriter;
import io.zeebe.engine.processor.TypedRecord;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.protocol.impl.encoding.BrokerInfo;
import io.zeebe.protocol.record.RecordType;
import io.zeebe.protocol.record.intent.Intent;
import io.zeebe.transport.RequestSubscription;
import io.zeebe.transport.ServerOutput;
import io.zeebe.transport.ServerResponse;
import io.zeebe.util.sched.Actor;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import org.agrona.collections.Int2ObjectHashMap;
import org.agrona.collections.IntHashSet;
import org.agrona.collections.Long2ObjectHashMap;
import org.agrona.concurrent.UnsafeBuffer;

public final class CommandApiService extends Actor implements PartitionListener {

  private final PartitionAwareRequestLimiter limiter;
  private final RequestSubscription requestSubscription;
  private final CommandApiMessageHandler atomixMessageHandler;
  private final AtomixResponse atomixOutput;
  private final IntHashSet leadPartitions = new IntHashSet();
  private final Int2ObjectHashMap<Long2ObjectHashMap<CompletableFuture<byte[]>>>
      partitionsRequestMap = new Int2ObjectHashMap<>();
  private final AtomicLong requestCount = new AtomicLong(0);
  private final BrokerInfo localMember;

  public CommandApiService(
      final RequestSubscription requestSubscription,
      final BrokerInfo localMember,
      final PartitionAwareRequestLimiter limiter) {
    this.requestSubscription = requestSubscription;
    this.limiter = limiter;
    atomixOutput = new AtomixResponse();
    atomixMessageHandler = new CommandApiMessageHandler();
    this.localMember = localMember;
  }

  @Override
  public String getName() {
    return actorNamePattern(localMember, "CommandApiService");
  }

  @Override
  protected void onActorClosing() {
    for (final Integer leadPartition : leadPartitions) {
      removeForPartitionId(leadPartition);
    }
    leadPartitions.clear();
  }

  @Override
  public void onBecomingFollower(
      final int partitionId, final long term, final LogStream logStream) {
    actor.call(
        () -> {
          atomixMessageHandler.removePartition(logStream);
          cleanLeadingPartition(partitionId);
        });
  }

  @Override
  public void onBecomingLeader(final int partitionId, final long term, final LogStream logStream) {
    actor.call(
        () -> {
          leadPartitions.add(partitionId);
          limiter.addPartition(partitionId);

          logStream
              .newLogStreamRecordWriter()
              .onComplete(
                  (recordWriter, error) -> {
                    if (error == null) {

                      final var requestLimiter = this.limiter.getLimiter(partitionId);
                      partitionsRequestMap.put(partitionId, new Long2ObjectHashMap<>());
                      atomixMessageHandler.addPartition(partitionId, recordWriter, requestLimiter);
                      requestSubscription.subscribe(
                          partitionId, request -> handleAtomixRequest(request, partitionId));

                    } else {
                      // TODO https://github.com/zeebe-io/zeebe/issues/3499
                      // the best would be to return a future onBecomingLeader
                      // when one of these futures failed we need to stop the partition installation
                      // and
                      // step down
                      // because then otherwise we are not correctly installed
                      Loggers.SYSTEM_LOGGER.error(
                          "Error on retrieving write buffer from log stream {}",
                          partitionId,
                          error);
                    }
                  });
        });
  }

  private void cleanLeadingPartition(final int partitionId) {
    leadPartitions.remove(partitionId);
    removeForPartitionId(partitionId);
  }

  private void removeForPartitionId(final int partitionId) {
    limiter.removePartition(partitionId);
    requestSubscription.unsubscribe(partitionId);
    final var requestMap = partitionsRequestMap.remove(partitionId);
    if (requestMap != null) {
      requestMap.clear();
    }
  }

  private CompletableFuture<byte[]> handleAtomixRequest(final byte[] bytes, final int partitionId) {
    final var completableFuture = new CompletableFuture<byte[]>();
    actor.call(
        () -> {
          final var requestId = requestCount.getAndIncrement();
          final var requestMap = partitionsRequestMap.get(partitionId);
          if (requestMap == null) {
            final var errorMsg =
                String.format(
                    "Node is not leader for partition %d, but subscribed to that partition.",
                    partitionId);
            completableFuture.completeExceptionally(new IllegalStateException(errorMsg));
            return;
          }

          requestMap.put(requestId, completableFuture);
          atomixMessageHandler.onRequest(
              atomixOutput, partitionId, new UnsafeBuffer(bytes), 0, bytes.length, requestId);
        });

    return completableFuture;
  }

  public CommandResponseWriter newCommandResponseWriter() {
    return new CommandResponseWriterImpl(atomixOutput);
  }

  public Consumer<TypedRecord> getOnProcessedListener(final int partitionId) {
    final RequestLimiter<Intent> partitionLimiter = limiter.getLimiter(partitionId);
    return typedRecord -> {
      if (typedRecord.getRecordType() == RecordType.COMMAND && typedRecord.hasRequestMetadata()) {
        partitionLimiter.onResponse(typedRecord.getRequestStreamId(), typedRecord.getRequestId());
      }
    };
  }

  private final class AtomixResponse implements ServerOutput {

    @Override
    public boolean sendResponse(final ServerResponse response) {
      // called from processing actor
      final var requestId = response.getRequestId();
      final var streamId = response.getStreamId();
      final var length = response.getLength();
      final var bytes = new byte[length];
      final var unsafeBuffer = new UnsafeBuffer(bytes);
      response.write(unsafeBuffer, 0);

      actor.run(
          () -> {
            final var requestMap = partitionsRequestMap.get(streamId);
            if (requestMap == null) {
              LOG.error(
                  "Node is no longer leader for partition {}, tried to send an response.",
                  streamId);
              return;
            }

            final var completableFuture = requestMap.remove(requestId);
            if (completableFuture != null) {
              completableFuture.complete(bytes);
            }
          });

      return true;
    }
  }
}
