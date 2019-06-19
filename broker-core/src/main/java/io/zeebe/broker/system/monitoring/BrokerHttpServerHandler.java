/*
 * Zeebe Broker Core
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.zeebe.broker.system.monitoring;

import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;

public class BrokerHttpServerHandler extends ChannelInboundHandlerAdapter {

  private static final String BROKER_READY_STATUS_URI = "/ready";
  private BrokerHealthCheckService brokerHealthCheckService;

  public BrokerHttpServerHandler(BrokerHealthCheckService brokerHealthCheckService) {
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

    final DefaultFullHttpResponse response;
    if (request.uri().equals(BROKER_READY_STATUS_URI)) {
      response = getReadyStatus();
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
}
