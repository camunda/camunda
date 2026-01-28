package io.camunda.zeebe.telemetry;

import io.grpc.*;
import io.grpc.ServerCall.Listener;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;

public final class OpenTelemetryGatewayInterceptor implements ServerInterceptor {

  private final Tracer tracer;

  public OpenTelemetryGatewayInterceptor() {
    super();
    tracer = GlobalOpenTelemetry.getTracer("io.camunda.zeebe.gateway.grpc");
  }

  @Override
  public <ReqT, RespT> Listener<ReqT> interceptCall(
      final ServerCall<ReqT, RespT> call,
      final Metadata headers,
      final ServerCallHandler<ReqT, RespT> next) {

    // Extract parent context from incoming metadata if you use W3C or B3 propagation
    final Context parentContext = Context.current(); // or use TextMapPropagator here

    final Span span =
        tracer
            .spanBuilder(call.getMethodDescriptor().getFullMethodName())
            .setSpanKind(SpanKind.SERVER)
            .setParent(parentContext)
            .startSpan();

    span.setAttribute("rpc.system", "grpc");
    span.setAttribute("rpc.service", call.getMethodDescriptor().getServiceName());
    span.setAttribute("rpc.method", call.getMethodDescriptor().getBareMethodName());

    final Context ctxWithSpan = parentContext.with(span);

    try (final Scope ignored = ctxWithSpan.makeCurrent()) {
      final Listener<ReqT> delegate = next.startCall(call, headers);
      return new ForwardingServerCallListener.SimpleForwardingServerCallListener<>(delegate) {

        @Override
        public void onMessage(final ReqT message) {
          // Optionally add attributes per message
          super.onMessage(message);
        }

        @Override
        public void onHalfClose() {
          super.onHalfClose();
        }

        @Override
        public void onCancel() {
          span.setAttribute("rpc.grpc.cancelled", true);
          span.end();
          super.onCancel();
        }

        @Override
        public void onComplete() {
          span.end();
          super.onComplete();
        }
      };
    }
  }
}
