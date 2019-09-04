/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.system.monitoring;

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
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class BrokerHttpServerHandler extends ChannelInboundHandlerAdapter {

  private static final String BROKER_READY_STATUS_URI = "/ready";
  private static final String METRICS_URI = "/metrics";

  private final CollectorRegistry metricsRegistry;
  private BrokerHealthCheckService brokerHealthCheckService;

  public BrokerHttpServerHandler(
      CollectorRegistry metricsRegistry, BrokerHealthCheckService brokerHealthCheckService) {
    this.metricsRegistry = metricsRegistry;
    this.brokerHealthCheckService = brokerHealthCheckService;
  }

  @Override
  public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
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

    if (request.method() != HttpMethod.GET) {
      ctx.writeAndFlush(
          new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.METHOD_NOT_ALLOWED));
      return;
    }

    final QueryStringDecoder queryStringDecoder = new QueryStringDecoder(request.uri());

    final DefaultFullHttpResponse response;
    if (BROKER_READY_STATUS_URI.equals(queryStringDecoder.path())) {
      response = getReadyStatus();
    } else if (METRICS_URI.equals(queryStringDecoder.path())) {
      response = getMetrics(queryStringDecoder);
    } else {
      response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.NOT_FOUND);
    }

    ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
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

  private DefaultFullHttpResponse getMetrics(QueryStringDecoder queryStringDecoder) {
    final ByteBuf buf = Unpooled.buffer();
    try (ByteBufOutputStream os = new ByteBufOutputStream(buf);
        OutputStreamWriter writer = new OutputStreamWriter(os); ) {
      TextFormat.write004(
          writer, metricsRegistry.filteredMetricFamilySamples(metricsFilter(queryStringDecoder)));
    } catch (IOException e) {
      Loggers.SYSTEM_LOGGER.warn("Failed to respond to metrics request", e);
      return new DefaultFullHttpResponse(
          HttpVersion.HTTP_1_1, HttpResponseStatus.INTERNAL_SERVER_ERROR);
    }

    final DefaultFullHttpResponse response =
        new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, buf);
    response.headers().set(HttpHeaderNames.CONTENT_TYPE, TextFormat.CONTENT_TYPE_004);

    return response;
  }

  private static Set<String> metricsFilter(QueryStringDecoder queryStringDecoder) {
    final List<String> names = queryStringDecoder.parameters().get("name[]");
    if (names != null && !names.isEmpty()) {
      return new HashSet<>(names);
    } else {
      return Collections.emptySet();
    }
  }
}
