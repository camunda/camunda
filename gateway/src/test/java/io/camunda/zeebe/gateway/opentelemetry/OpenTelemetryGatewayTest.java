/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.gateway.opentelemetry;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.stop.Stop.stopQuietly;

import com.google.protobuf.InvalidProtocolBufferException;
import io.camunda.zeebe.gateway.api.util.GatewayTest;
import io.camunda.zeebe.gateway.protocol.GatewayGrpc;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.TopologyRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.TopologyResponse;
import io.camunda.zeebe.test.util.opentelemetry.OpenTelemetryTests;
import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceRequest;
import io.opentelemetry.proto.trace.v1.Span;
import io.opentelemetry.proto.trace.v1.Span.SpanKind;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.model.Body;
import org.mockserver.model.HttpRequest;

@Category(OpenTelemetryTests.class)
public class OpenTelemetryGatewayTest extends GatewayTest {

  private static ClientAndServer mockServer;

  @BeforeClass
  public static void beforeClass() throws Exception {
    final String mockServerPortProperty = System.getProperty("mockserver.http.port");
    if (mockServerPortProperty == null) {
      throw new RuntimeException("Cannot find mockserver.http.port system property.");
    }
    mockServer = ClientAndServer.startClientAndServer(Integer.parseInt(mockServerPortProperty));
    mockServer.when(request()).respond(response().withStatusCode(200));
  }

  @AfterClass
  public static void afterClass() throws Exception {
    stopQuietly(mockServer);
  }

  @Test
  public void shouldReturnClientAndServerGrpcTraces() {
    final TopologyResponse topologyResponse = client.topology(TopologyRequest.newBuilder().build());

    assertThat(topologyResponse).isNotNull();
    await()
        .atMost(30, SECONDS)
        .untilAsserted(
            () -> {
              final HttpRequest[] httpRequests = mockServer.retrieveRecordedRequests(request());
              final List<Span> spans = extractSpansFromRequests(httpRequests);

              assertThat(spans)
                  .isNotEmpty()
                  .matches(
                      assertedSpans ->
                          assertedSpans.stream()
                              .allMatch(
                                  span ->
                                      span.getName()
                                          .equals(
                                              String.format(
                                                  "%s/Topology", GatewayGrpc.SERVICE_NAME))),
                      String.format(
                          "Should contains only %s named spans",
                          String.format("%s/Topology", GatewayGrpc.SERVICE_NAME)))
                  .matches(
                      assertedSpans ->
                          assertedSpans.stream()
                              .allMatch(
                                  span -> {
                                    final SpanKind spanKind = span.getKind();
                                    return spanKind == SpanKind.SPAN_KIND_CLIENT
                                        || spanKind == SpanKind.SPAN_KIND_SERVER;
                                  }),
                      "Should contains the client and the server spans");
            });
  }

  private List<Span> extractSpansFromRequests(final HttpRequest[] requests) {
    return Arrays.stream(requests)
        .map(HttpRequest::getBody)
        .flatMap(body -> getExportTraceServiceRequest(body).stream())
        .flatMap(r -> r.getResourceSpansList().stream())
        .flatMap(r -> r.getInstrumentationLibrarySpansList().stream())
        .flatMap(r -> r.getSpansList().stream())
        .collect(Collectors.toList());
  }

  private Optional<ExportTraceServiceRequest> getExportTraceServiceRequest(final Body<?> body) {
    try {
      return Optional.ofNullable(ExportTraceServiceRequest.parseFrom(body.getRawBytes()));
    } catch (final InvalidProtocolBufferException e) {
      return Optional.empty();
    }
  }
}
