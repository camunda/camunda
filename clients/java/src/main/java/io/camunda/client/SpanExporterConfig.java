package io.camunda.client;

import io.opentelemetry.exporter.logging.LoggingSpanExporter;
import io.opentelemetry.sdk.trace.export.SpanExporter;

public class SpanExporterConfig {
  //  public static SpanExporter otlpHttpSpanExporter(final String endpoint) {
  //    return OtlpHttpSpanExporter.builder()
  //        .setEndpoint(endpoint)
  //        .addHeader("api-key", "value")
  //        .setTimeout(Duration.ofSeconds(10))
  //        .build();
  //  }
  //
  //  public static SpanExporter otlpGrpcSpanExporter(final String endpoint) {
  //    return OtlpGrpcSpanExporter.builder()
  //        .setEndpoint(endpoint)
  //        .addHeader("api-key", "value")
  //        .setTimeout(Duration.ofSeconds(10))
  //        .build();
  //  }

  public static SpanExporter logginSpanExporter() {
    return LoggingSpanExporter.create();
  }

  //  public static SpanExporter otlpJsonLoggingSpanExporter() {
  //    return OtlpJsonLoggingSpanExporter.create();
  //  }
}
