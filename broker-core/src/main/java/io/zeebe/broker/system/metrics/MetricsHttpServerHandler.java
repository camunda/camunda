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
package io.zeebe.broker.system.metrics;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.HttpVersion;
import io.zeebe.util.metrics.MetricsManager;
import java.time.Instant;
import org.agrona.ExpandableDirectByteBuffer;
import org.agrona.MutableDirectBuffer;

public class MetricsHttpServerHandler extends ChannelInboundHandlerAdapter {

  private final MutableDirectBuffer metricsBuffer = new ExpandableDirectByteBuffer();
  private final MetricsManager metricsManager;

  public MetricsHttpServerHandler(MetricsManager metricsManager) {
    this.metricsManager = metricsManager;
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

    final int length = metricsManager.dump(metricsBuffer, 0, Instant.now().toEpochMilli());

    final DefaultFullHttpResponse response =
        new DefaultFullHttpResponse(
            HttpVersion.HTTP_1_1,
            HttpResponseStatus.OK,
            Unpooled.copiedBuffer(metricsBuffer.byteBuffer()).slice(0, length));

    response.headers().set(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.TEXT_PLAIN);
    response.headers().set(HttpHeaderNames.CONTENT_LENGTH, length);
    HttpUtil.setKeepAlive(response, HttpUtil.isKeepAlive(request));

    ctx.writeAndFlush(response);
  }
}
