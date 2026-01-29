package io.camunda.zeebe.telemetry;

import io.grpc.*;
import io.grpc.ServerCall.Listener;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.semconv.ServiceAttributes;

public final class OpenTelemetryGatewayInterceptor implements ServerInterceptor {

  private final Tracer tracer;

  public OpenTelemetryGatewayInterceptor() {
    super();
    tracer =
        createOpenTelemetry("http://localhost:4317", "grpcGatewayInterceptor")
            .getTracer("io.camunda.zeebe.gateway.grpc");
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

  public static OpenTelemetry createOpenTelemetry(final String exporter, final String serviceName) {
    final Resource resource =
        Resource.create(Attributes.of(ServiceAttributes.SERVICE_NAME, serviceName));

    final OtlpGrpcSpanExporter otlpExporter =
        OtlpGrpcSpanExporter.builder()
            .setEndpoint(exporter) // Set your OTLP endpoint here
            .build();

    final SdkTracerProvider sdkTracerProvider =
        SdkTracerProvider.builder()
            .setResource(resource)
            .addSpanProcessor(SimpleSpanProcessor.create(otlpExporter))
            .build();

    return OpenTelemetrySdk.builder()
        .setTracerProvider(sdkTracerProvider)
        .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
        .build();
  }
}
