/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service.stream;

import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.scheduler.ConcurrencyControl;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.transport.stream.api.ClientStreamConsumer;
import io.camunda.zeebe.transport.stream.api.ClientStreamId;
import io.camunda.zeebe.transport.stream.api.ClientStreamer;
import io.camunda.zeebe.util.VisibleForTesting;
import java.nio.ByteBuffer;
import java.util.concurrent.Executor;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StreamProcessInstanceHandler extends Actor {

  static Logger LOGGER = LoggerFactory.getLogger(StreamProcessInstanceHandler.class);

  private final ClientStreamer<ProcessInstanceProperties> jobStreamer;

  public StreamProcessInstanceHandler(final ClientStreamer<ProcessInstanceProperties> jobStreamer) {
    this.jobStreamer = jobStreamer;
  }

  public void handle(final long processInstanceKey, final ProcessInstanceEmitter emitter) {
    handle(new ProcessInstanceProperties(processInstanceKey), emitter);
  }

  public void handle(
      final ProcessInstanceProperties processInstanceProperties,
      final ProcessInstanceEmitter emitter) {
    final var byteBuffer = ByteBuffer.allocateDirect(Long.BYTES);
    final var streamType = new UnsafeBuffer(byteBuffer);
    streamType.putLong(0, processInstanceProperties.getProcessInstanceKey());
    final var consumer = new ProcessInstanceStreamConsumer(actor, emitter);
    final var cleaner = new AsyncProcessInstanceStreamRemover(jobStreamer, actor);

    actor.run(
        () ->
            actor.runOnCompletion(
                jobStreamer.add(streamType, processInstanceProperties, consumer),
                (streamId, error) -> onStreamAdded(cleaner, streamId, error)));
  }

  private void onStreamAdded(
      final AsyncProcessInstanceStreamRemover cleaner,
      final ClientStreamId streamId,
      final Throwable error) {
    cleaner.streamId(streamId);
  }

  record ProcessInstanceStreamConsumer(ConcurrencyControl executor, ProcessInstanceEmitter emitter)
      implements ClientStreamConsumer {

    @Override
    public ActorFuture<Void> push(final DirectBuffer payload) {
      final var result = new CompletableActorFuture<Void>();
      try {
        executor.run(() -> handlePushedProcessInstanceRecord(payload, result));
      } catch (final Exception e) {
        // only possible failure here is that the actor is not running, so close the stream
        // preemptively
        result.completeExceptionally(e);
      }

      return result;
    }

    private void handlePushedProcessInstanceRecord(
        final DirectBuffer payload, final CompletableActorFuture<Void> result) {
      final var deserializedProcessInstance = new ProcessInstanceRecord();

      // fail push on serialization errors, but no need to close the client stream
      try {
        deserializedProcessInstance.wrap(payload);
        LOGGER.info("Handle Pushed Process Instance Record {}", deserializedProcessInstance);
        emitter.send(deserializedProcessInstance);
      } catch (final Exception e) {
        result.completeExceptionally(e);
        return;
      }
      result.complete(null);
    }
  }

  @VisibleForTesting("Allow unit testing behavior")
  static final class AsyncProcessInstanceStreamRemover implements Runnable {
    private final ClientStreamer<ProcessInstanceProperties> jobStreamer;
    private final Executor executor;

    private boolean isRemoved;
    private ClientStreamId streamId;

    @VisibleForTesting("Allow unit testing behavior")
    AsyncProcessInstanceStreamRemover(
        final ClientStreamer<ProcessInstanceProperties> jobStreamer, final Executor executor) {
      this.jobStreamer = jobStreamer;
      this.executor = executor;
    }

    @Override
    public void run() {
      executor.execute(this::remove);
    }

    @VisibleForTesting("Allow unit testing behavior")
    void streamId(final ClientStreamId streamId) {
      executor.execute(() -> setStreamId(streamId));
    }

    private void remove() {
      isRemoved = true;

      if (streamId != null) {
        jobStreamer.remove(streamId);
      }
    }

    private void setStreamId(final ClientStreamId streamId) {
      if (isRemoved) {
        jobStreamer.remove(streamId);
        return;
      }

      this.streamId = streamId;
    }
  }
}
