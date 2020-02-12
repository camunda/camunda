/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.transport.commandapi;

import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.Tracer.SpanBuilder;
import io.opentracing.propagation.Format.Builtin;
import io.zeebe.broker.Loggers;
import io.zeebe.protocol.impl.tracing.SbeTracingAdapter;
import java.nio.BufferOverflowException;
import java.nio.BufferUnderflowException;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;
import org.agrona.DirectBuffer;

public class DefaultCommandTracer implements CommandTracer {
  private final Tracer tracer;
  private final ConcurrentMap<Id, Span> spans;

  public DefaultCommandTracer(final Tracer tracer) {
    this.tracer = tracer;
    this.spans = new ConcurrentHashMap<>();
  }

  @Override
  public Span start(final DirectBuffer serialized, final int partitionId, final long requestId) {
    final var adapter = new SbeTracingAdapter(serialized);
    final var builder =
        tracer
            .buildSpan("io.zeebe.engine.processing")
            .withTag("span.kind", "server")
            .withTag("component", "io.zeebe.broker")
            .withTag("message_bus.destination", partitionId)
            .withTag("io.zeebe.partitionId", partitionId);

    final var context = extractContext(adapter);
    if (context != null) {
      builder.asChildOf(context);
    }

    final var span = builder.start();
    spans.put(new Id(partitionId, requestId), span);
    return span;
  }

  @Override
  public void finish(final int partitionId, final long requestId, final Consumer<Span> spanConsumer) {
    final var span = spans.get(new Id(partitionId, requestId));

    if (span != null) {
      spanConsumer.accept(span);
      span.finish();
    } else {
      // at the moment it's fine that we have no spans available
      Loggers.TRANSPORT_LOGGER.debug(
          "No such span for stream {} and request {}", partitionId, requestId);
    }
  }

  private SpanContext extractContext(final SbeTracingAdapter adapter) {
    if (adapter.extractionBuffer().capacity() > 0) {
      try {
        return tracer.extract(Builtin.BINARY, adapter);
      } catch (final BufferOverflowException | BufferUnderflowException e) {
        Loggers.TRANSPORT_LOGGER.debug(
            "Failed to extract tracing context from non-zero span context", e);
        return null;
      }
    }

    return null;
  }

  private static final class Id {
    private final int partitionId;
    private final long requestId;

    private Id(final int partitionId, final long requestId) {
      this.partitionId = partitionId;
      this.requestId = requestId;
    }

    @Override
    public int hashCode() {
      return Objects.hash(partitionId, requestId);
    }

    @Override
    public boolean equals(final Object other) {
      if (this == other) {
        return true;
      }

      if (other == null || getClass() != other.getClass()) {
        return false;
      }

      final Id id = (Id) other;
      return partitionId == id.partitionId && requestId == id.requestId;
    }
  }
}
