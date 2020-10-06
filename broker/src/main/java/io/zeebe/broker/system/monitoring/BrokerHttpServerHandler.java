/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.system.monitoring;

import static io.zeebe.util.ObjectWriterFactory.getDefaultJsonObjectWriter;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.exporter.common.TextFormat;
import io.zeebe.broker.Loggers;
import io.zeebe.broker.system.management.BrokerAdminService;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

public final class BrokerHttpServerHandler extends ChannelInboundHandlerAdapter {

  private static final String BROKER_READY_STATUS_URI = "/ready";
  private static final String METRICS_URI = "/metrics";
  private static final String BROKER_HEALTH_STATUS_URI = "/health";
  private static final String PARTITION_STATUS_URI = "/partitions";
  private static final String PARTITION_PAUSE_PROCESSING_URI = "/partitions/pauseProcessing";
  private static final String PARTITION_RESUME_PROCESSING_URI = "/partitions/resumeProcessing";
  private static final String PARTITION_TAKE_SNAPSHOT_URI = "/partitions/takeSnapshot";
  private static final String PARTITION_PREPARE_UPGRADE_URI = "/partitions/prepareUpgrade";

  private final CollectorRegistry metricsRegistry;
  private final BrokerHealthCheckService brokerHealthCheckService;
  private final BrokerAdminService brokerAdminService;

  public BrokerHttpServerHandler(
      final CollectorRegistry metricsRegistry,
      final BrokerHealthCheckService brokerHealthCheckService,
      final BrokerAdminService brokerAdminService) {
    this.metricsRegistry = metricsRegistry;
    this.brokerHealthCheckService = brokerHealthCheckService;
    this.brokerAdminService = brokerAdminService;
  }

  @Override
  public void channelRead(final ChannelHandlerContext ctx, final Object msg) throws Exception {
    if (!(msg instanceof HttpRequest)) {
      super.channelRead(ctx, msg);
      return;
    }

    final HttpRequest request = (HttpRequest) msg;

    if (!request.decoderResult().isSuccess()) {
      ctx.writeAndFlush(
          new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.BAD_REQUEST));
      return;
    }

    final QueryStringDecoder queryStringDecoder = new QueryStringDecoder(request.uri());

    if (!isRequestValid(request, queryStringDecoder.path())) {
      ctx.writeAndFlush(
          new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.METHOD_NOT_ALLOWED));
      return;
    }

    final DefaultFullHttpResponse response;
    if (BROKER_READY_STATUS_URI.equals(queryStringDecoder.path())) {
      response = getReadyStatus();
    } else if (METRICS_URI.equals(queryStringDecoder.path())) {
      response = getMetrics(queryStringDecoder);
    } else if (BROKER_HEALTH_STATUS_URI.equals(queryStringDecoder.path())) {
      response = getHealthStatus();
    } else if (PARTITION_STATUS_URI.equals(queryStringDecoder.path())) {
      response = withExceptionHandling(this::getPartitionStatus);
    } else if (PARTITION_PAUSE_PROCESSING_URI.equals(queryStringDecoder.path())) {
      response = withExceptionHandling(this::pauseProcessing);
    } else if (PARTITION_RESUME_PROCESSING_URI.equals(queryStringDecoder.path())) {
      response = withExceptionHandling(this::resumeProcessing);
    } else if (PARTITION_TAKE_SNAPSHOT_URI.equals(queryStringDecoder.path())) {
      response = withExceptionHandling(this::takeSnapshot);
    } else if (PARTITION_PREPARE_UPGRADE_URI.equals(queryStringDecoder.path())) {
      response = withExceptionHandling(this::prepareUpgrade);
    } else {
      response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.NOT_FOUND);
    }

    ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
  }

  private boolean isRequestValid(final HttpRequest request, final String queryPath) {
    return (request.method() == HttpMethod.GET
            && (BROKER_READY_STATUS_URI.equals(queryPath)
                || METRICS_URI.equals(queryPath)
                || BROKER_HEALTH_STATUS_URI.equals(queryPath)
                || PARTITION_STATUS_URI.equals(queryPath)))
        || (request.method() == HttpMethod.POST
            && (PARTITION_PAUSE_PROCESSING_URI.equals(queryPath)
                || PARTITION_RESUME_PROCESSING_URI.equals(queryPath)
                || PARTITION_PREPARE_UPGRADE_URI.equals(queryPath)
                || PARTITION_TAKE_SNAPSHOT_URI.equals(queryPath)));
  }

  private DefaultFullHttpResponse getReadyStatus() {
    final boolean brokerReady = brokerHealthCheckService.isBrokerReady();
    final DefaultFullHttpResponse response;
    if (brokerReady) {
      response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.NO_CONTENT);
    } else {
      response =
          new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.SERVICE_UNAVAILABLE);
    }
    return response;
  }

  private DefaultFullHttpResponse getHealthStatus() {
    final boolean brokerHealthy = brokerHealthCheckService.isBrokerHealthy();
    final DefaultFullHttpResponse response;
    if (brokerHealthy) {
      response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.NO_CONTENT);
    } else {
      response =
          new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.SERVICE_UNAVAILABLE);
    }
    return response;
  }

  private DefaultFullHttpResponse getPartitionStatus() {
    final var partitionStatus = brokerAdminService.getPartitionStatus();

    final String jsonResponse;
    try {
      jsonResponse = getDefaultJsonObjectWriter().writeValueAsString(partitionStatus);
    } catch (final JsonProcessingException e) {
      Loggers.SYSTEM_LOGGER.warn("Failed to respond to partitionStatus request", e);
      return new DefaultFullHttpResponse(
          HttpVersion.HTTP_1_1, HttpResponseStatus.INTERNAL_SERVER_ERROR);
    }

    final DefaultFullHttpResponse response =
        new DefaultFullHttpResponse(
            HttpVersion.HTTP_1_1,
            HttpResponseStatus.OK,
            Unpooled.wrappedBuffer(jsonResponse.getBytes()));
    response.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json");

    return response;
  }

  private DefaultFullHttpResponse pauseProcessing() {
    brokerAdminService.pauseStreamProcessing();
    return getPartitionStatus();
  }

  private DefaultFullHttpResponse resumeProcessing() {
    brokerAdminService.resumeStreamProcessing();
    return getPartitionStatus();
  }

  private DefaultFullHttpResponse takeSnapshot() {
    brokerAdminService.takeSnapshot();
    return getPartitionStatus();
  }

  private DefaultFullHttpResponse prepareUpgrade() {
    brokerAdminService.prepareForUpgrade();
    return getPartitionStatus();
  }

  private DefaultFullHttpResponse withExceptionHandling(
      final Supplier<DefaultFullHttpResponse> requestHandler) {
    try {
      return requestHandler.get();
    } catch (final Exception e) {
      return new DefaultFullHttpResponse(
          HttpVersion.HTTP_1_1, HttpResponseStatus.INTERNAL_SERVER_ERROR);
    }
  }

  private DefaultFullHttpResponse getMetrics(final QueryStringDecoder queryStringDecoder) {
    final ByteBuf buf = Unpooled.buffer();
    try (final ByteBufOutputStream os = new ByteBufOutputStream(buf);
        final OutputStreamWriter writer = new OutputStreamWriter(os); ) {
      TextFormat.write004(
          writer, metricsRegistry.filteredMetricFamilySamples(metricsFilter(queryStringDecoder)));
    } catch (final IOException e) {
      Loggers.SYSTEM_LOGGER.warn("Failed to respond to metrics request", e);
      return new DefaultFullHttpResponse(
          HttpVersion.HTTP_1_1, HttpResponseStatus.INTERNAL_SERVER_ERROR);
    }

    final DefaultFullHttpResponse response =
        new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, buf);
    response.headers().set(HttpHeaderNames.CONTENT_TYPE, TextFormat.CONTENT_TYPE_004);

    return response;
  }

  private static Set<String> metricsFilter(final QueryStringDecoder queryStringDecoder) {
    final List<String> names = queryStringDecoder.parameters().get("name[]");
    if (names != null && !names.isEmpty()) {
      return new HashSet<>(names);
    } else {
      return Collections.emptySet();
    }
  }
}
