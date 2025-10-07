/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.client.impl;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ForwardingClientCall;
import io.grpc.ForwardingClientCallListener;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.Status;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;

public class OpenTelemetryInterceptor implements ClientInterceptor {

  private final Tracer tracer;

  public OpenTelemetryInterceptor(final OpenTelemetry openTelemetry) {
    tracer = openTelemetry.getTracer("io.camunda.client.grpc");
  }

  @Override
  public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
      final MethodDescriptor<ReqT, RespT> methodDescriptor, final CallOptions callOptions,
      final Channel channel) {
    final Span span = tracer.spanBuilder(methodDescriptor.getBareMethodName())
        .setSpanKind(SpanKind.CLIENT)
        .setParent(Context.current())
        .startSpan();

    // Create the call with tracing context
    try (final Scope scope = span.makeCurrent()) {
      final ClientCall<ReqT, RespT> call = channel.newCall(methodDescriptor, callOptions);
      return new TracingClientCall<>(call, span);
    }
  }

  private static class TracingClientCall<ReqT, RespT>
      extends ForwardingClientCall.SimpleForwardingClientCall<ReqT, RespT> {

    private final Span span;

    TracingClientCall(final ClientCall<ReqT, RespT> delegate, final Span span) {
      super(delegate);
      this.span = span;
    }

    @Override
    public void start(final Listener<RespT> responseListener, final Metadata headers) {
      final Listener<RespT> tracingListener = new TracingClientCallListener<>(responseListener, span);
      super.start(tracingListener, headers);
    }

    @Override
    public void sendMessage(final ReqT message) {
      super.sendMessage(message);
    }

    @Override
    public void cancel(final String message, final Throwable cause) {
      if (cause != null) {
        span.recordException(cause);
      }
      span.end();
      super.cancel(message, cause);
    }

    @Override
    public void halfClose() {
      super.halfClose();
    }
  }

  /**
   * Forwarding client call listener that handles response events and span completion.
   */
  private static class TracingClientCallListener<RespT>
      extends ForwardingClientCallListener.SimpleForwardingClientCallListener<RespT> {

    private final Span span;

    TracingClientCallListener(final ClientCall.Listener<RespT> delegate, final Span span) {
      super(delegate);
      this.span = span;
    }

    @Override
    public void onMessage(final RespT message) {
      super.onMessage(message);
    }

    @Override
    public void onHeaders(final Metadata headers) {
      super.onHeaders(headers);
    }

    @Override
    public void onClose(final Status status, final Metadata trailers) {
      try {
        if (status.isOk()) {
          span.setStatus(StatusCode.OK);
        } else {
          span.setStatus(StatusCode.ERROR, status.getDescription());
          if (status.getCause() != null) {
            span.recordException(status.getCause());
          }
        }
      } finally {
        span.end();
        super.onClose(status, trailers);
      }
    }

    @Override
    public void onReady() {
      super.onReady();
    }
  }
}
