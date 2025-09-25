package io.camunda.client;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.exporter.zipkin.ZipkinSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.semconv.ServiceAttributes;

public class OpenTelemetrySdkConfig {
  public static OpenTelemetry create() {
    final Resource resource =
        Resource.create(Attributes.of(ServiceAttributes.SERVICE_NAME, "client-java"));

    final ZipkinSpanExporter zipkinSpanExporter =
        ZipkinSpanExporter.builder().setEndpoint("http://localhost:9411/api/v2/spans").build();

    final SdkTracerProvider sdkTracerProvider =
        SdkTracerProvider.builder()
            .setResource(resource)
            .addSpanProcessor(BatchSpanProcessor.builder(zipkinSpanExporter).build())
            .build();

    return OpenTelemetrySdk.builder()
        .setTracerProvider(sdkTracerProvider)
        .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
        .buildAndRegisterGlobal();
  }
}
