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
package io.camunda.client.impl.backpressure;

import io.camunda.zeebe.gateway.protocol.GatewayGrpc;
import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.MethodDescriptor.MethodType;
import io.grpc.Status;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;

/**
 * Applies the backpressure handling of the client to its gRPC calls: a unary call waits for the
 * concurrency limit, and is started again, with a delay, when the gateway answers it with {@code
 * RESOURCE_EXHAUSTED}.
 *
 * <p>The retry reuses the deadline of the call, so the call as a whole, waiting and retrying
 * included, does not take longer than the request timeout the caller chose.
 *
 * <p>Calls that are not unary, i.e. job activation and job streaming, are not touched: the job
 * worker backs off between polls on its own.
 */
public final class BackpressureClientInterceptor implements ClientInterceptor {

  /**
   * The calls that never wait for the concurrency limit: those that finish work that is already
   * running, and the topology request that probes the cluster.
   */
  private static final Set<String> UNGATED_METHODS =
      Collections.unmodifiableSet(
          new HashSet<>(
              Arrays.asList(
                  GatewayGrpc.getCompleteJobMethod().getFullMethodName(),
                  GatewayGrpc.getFailJobMethod().getFullMethodName(),
                  GatewayGrpc.getThrowErrorMethod().getFullMethodName(),
                  GatewayGrpc.getTopologyMethod().getFullMethodName())));

  private final BackpressureController controller;
  private final int maxAttempts;

  /**
   * @param controller the backpressure handling of the client
   * @param retry whether to retry rejected calls; false if the channel retries them already, which
   *     would multiply the attempts
   */
  public BackpressureClientInterceptor(
      final BackpressureController controller, final boolean retry) {
    this.controller = controller;
    maxAttempts = retry ? controller.settings().maxAttempts() : 1;
  }

  @Override
  public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
      final MethodDescriptor<ReqT, RespT> method, final CallOptions options, final Channel next) {
    if (method.getType() != MethodType.UNARY) {
      return next.newCall(method, options);
    }

    final BackpressureScope scope =
        UNGATED_METHODS.contains(method.getFullMethodName())
            ? BackpressureScope.UNGATED
            : BackpressureScope.MANAGED;
    final BackpressureCall call =
        controller.newCall(scope, timeoutMillis(options.getDeadline()), maxAttempts);
    if (call == null) {
      return next.newCall(method, options);
    }
    return new UnaryCall<>(call, method, options, next);
  }

  private static long timeoutMillis(final io.grpc.Deadline deadline) {
    return deadline == null ? 0 : Math.max(1, deadline.timeRemaining(TimeUnit.MILLISECONDS));
  }

  private static Status statusOf(final Throwable error) {
    final Throwable cause =
        error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
    if (cause instanceof BackpressureTimeoutException) {
      return Status.DEADLINE_EXCEEDED.withDescription(cause.getMessage()).withCause(cause);
    }
    if (cause instanceof BackpressureQueueFullException) {
      return Status.RESOURCE_EXHAUSTED.withDescription(cause.getMessage()).withCause(cause);
    }
    return Status.UNAVAILABLE.withDescription(cause.getMessage()).withCause(cause);
  }

  /**
   * A unary call that is started once it is its turn, and again if the gateway rejects it because
   * of backpressure.
   *
   * <p>The call collects what the caller hands it (listener, headers, request, number of requested
   * responses) and replays it on every attempt. It relies on the way a unary stub drives a call:
   * start, request, send the message and half-close, one after the other.
   *
   * <p>An attempt is only repeated as long as it did not deliver a response, so the caller never
   * sees the response of one attempt and the status of another.
   */
  private static final class UnaryCall<ReqT, RespT> extends ClientCall<ReqT, RespT> {

    private final BackpressureCall backpressure;
    private final MethodDescriptor<ReqT, RespT> method;
    private final CallOptions options;
    private final Channel next;
    private final Object lock = new Object();

    // everything below is guarded by lock, unless noted otherwise
    private Listener<RespT> listener; // written once by start, before anything else happens
    private Metadata headers; // same
    private ReqT message; // same, by sendMessage
    private Boolean messageCompression;
    private int requested;
    private ClientCall<ReqT, RespT> active;
    private Metadata pendingHeaders;
    private boolean delivered;
    private boolean cancelled;
    private boolean closed;

    private UnaryCall(
        final BackpressureCall backpressure,
        final MethodDescriptor<ReqT, RespT> method,
        final CallOptions options,
        final Channel next) {
      this.backpressure = backpressure;
      this.method = method;
      this.options = options;
      this.next = next;
    }

    @Override
    public void start(final Listener<RespT> responseListener, final Metadata requestHeaders) {
      listener = responseListener;
      headers = requestHeaders;
    }

    @Override
    public void request(final int numMessages) {
      final ClientCall<ReqT, RespT> current;
      synchronized (lock) {
        requested = (int) Math.min(Integer.MAX_VALUE, (long) requested + numMessages);
        current = active;
      }
      if (current != null) {
        current.request(numMessages);
      }
    }

    @Override
    public void sendMessage(final ReqT requestMessage) {
      message = requestMessage;
    }

    @Override
    public void setMessageCompression(final boolean enabled) {
      synchronized (lock) {
        messageCompression = enabled;
        if (active != null) {
          active.setMessageCompression(enabled);
        }
      }
    }

    @Override
    public void halfClose() {
      // the request is complete, so it is time to wait for the turn of the call
      backpressure
          .acquire()
          .whenComplete(
              (ignored, error) -> {
                if (error == null) {
                  startAttempt();
                } else if (!(error instanceof CancellationException)) {
                  finish(statusOf(error), new Metadata());
                }
              });
    }

    @Override
    public void cancel(final String cancelMessage, final Throwable cause) {
      final ClientCall<ReqT, RespT> current;
      synchronized (lock) {
        if (cancelled || closed) {
          return;
        }
        cancelled = true;
        current = active;
      }
      if (current != null) {
        // the attempt reports the cancellation, which then ends this call
        current.cancel(cancelMessage, cause);
      } else {
        // waiting for the turn, or for the delay between two attempts
        finish(Status.CANCELLED.withDescription(cancelMessage).withCause(cause), new Metadata());
      }
    }

    @Override
    public boolean isReady() {
      synchronized (lock) {
        return active != null && active.isReady();
      }
    }

    private void startAttempt() {
      synchronized (lock) {
        if (cancelled || closed) {
          return;
        }
      }

      final ClientCall<ReqT, RespT> call = next.newCall(method, options);
      final int replayRequested;
      final Boolean compression;
      synchronized (lock) {
        if (cancelled || closed) {
          return;
        }
        active = call;
        delivered = false;
        pendingHeaders = null;
        replayRequested = requested;
        compression = messageCompression;
      }

      // the interceptors below add to the headers, so every attempt starts from a fresh copy
      final Metadata attemptHeaders = new Metadata();
      attemptHeaders.merge(headers);
      try {
        call.start(new AttemptListener(call), attemptHeaders);
        if (compression != null) {
          call.setMessageCompression(compression);
        }
        if (replayRequested > 0) {
          call.request(replayRequested);
        }
        call.sendMessage(message);
        call.halfClose();
      } catch (final RuntimeException e) {
        // the attempt may or may not report its end, so this call ends here
        final Status status;
        synchronized (lock) {
          if (active == call) {
            active = null;
          }
          status =
              cancelled
                  ? Status.CANCELLED.withDescription("Call was cancelled")
                  : Status.INTERNAL.withDescription("Failed to start the call").withCause(e);
        }
        try {
          call.cancel("Failed to start the call", e);
        } catch (final RuntimeException ignored) {
          // nothing more can be done for the attempt
        }
        finish(status, new Metadata());
      }
    }

    private void onAttemptClosed(
        final ClientCall<ReqT, RespT> call, final Status status, final Metadata trailers) {
      final boolean mayRetry;
      synchronized (lock) {
        if (call != active || closed) {
          return;
        }
        active = null;
        mayRetry = !delivered && !cancelled;
      }

      if (status.isOk()) {
        backpressure.onSuccess();
        finish(status, trailers);
        return;
      }

      if (mayRetry) {
        final long delay = backpressure.onFailure(BackpressureSignals.classifyGrpc(status), 0);
        if (delay >= 0) {
          backpressure.schedule(this::startNextAttempt, delay);
          return;
        }
      }
      finish(status, trailers);
    }

    private void startNextAttempt() {
      try {
        startAttempt();
      } catch (final RuntimeException e) {
        finish(
            Status.INTERNAL.withDescription("Failed to retry the call").withCause(e),
            new Metadata());
      }
    }

    private void finish(final Status status, final Metadata trailers) {
      final Metadata held;
      synchronized (lock) {
        if (closed) {
          return;
        }
        closed = true;
        held = pendingHeaders;
        pendingHeaders = null;
      }
      backpressure.release();
      if (held != null) {
        listener.onHeaders(held);
      }
      listener.onClose(status, trailers);
    }

    private final class AttemptListener extends Listener<RespT> {
      private final ClientCall<ReqT, RespT> call;

      private AttemptListener(final ClientCall<ReqT, RespT> call) {
        this.call = call;
      }

      @Override
      public void onHeaders(final Metadata responseHeaders) {
        // held back until it is clear that the attempt is the one that is shown to the caller
        synchronized (lock) {
          if (call == active && !closed) {
            pendingHeaders = responseHeaders;
          }
        }
      }

      @Override
      public void onMessage(final RespT response) {
        final Metadata held;
        synchronized (lock) {
          if (call != active || closed) {
            return;
          }
          delivered = true;
          held = pendingHeaders;
          pendingHeaders = null;
        }
        if (held != null) {
          listener.onHeaders(held);
        }
        listener.onMessage(response);
      }

      @Override
      public void onReady() {
        synchronized (lock) {
          if (call != active || closed) {
            return;
          }
        }
        listener.onReady();
      }

      @Override
      public void onClose(final Status status, final Metadata trailers) {
        onAttemptClosed(call, status, trailers);
      }
    }
  }
}
