package io.camunda.client;

import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;

public class SdkTracerProviderConfig {
  public static SdkTracerProvider create(final Resource resource) {
    return SdkTracerProvider.builder()
        .setResource(resource)
        .addSpanProcessor(SimpleSpanProcessor.create(SpanExporterConfig.logginSpanExporter()))
        .build();
  }
}
