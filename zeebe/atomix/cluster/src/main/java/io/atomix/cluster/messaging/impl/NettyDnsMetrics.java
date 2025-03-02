/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.atomix.cluster.messaging.impl;

import static io.atomix.cluster.messaging.impl.NettyDnsMetricsDoc.*;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Meter.MeterProvider;
import io.micrometer.core.instrument.MeterRegistry;
import io.netty.channel.ChannelFuture;
import io.netty.handler.codec.dns.DnsQuestion;
import io.netty.handler.codec.dns.DnsResponseCode;
import io.netty.resolver.dns.DnsQueryLifecycleObserver;
import java.net.InetSocketAddress;
import java.util.List;

final class NettyDnsMetrics implements DnsQueryLifecycleObserver {

  private final Counter error;
  private final Counter written;
  private final Counter succeded;

  /** indexed by {@link DnsResponseCode#intValue()} */
  private final MeterProvider<Counter> failed;

  NettyDnsMetrics(final MeterRegistry registry) {
    error = Counter.builder(ERROR.getName()).description(ERROR.getDescription()).register(registry);
    written =
        Counter.builder(WRITTEN.getName()).description(WRITTEN.getDescription()).register(registry);
    succeded =
        Counter.builder(SUCCESS.getName()).description(SUCCESS.getDescription()).register(registry);
    failed =
        Counter.builder(FAILED.getName())
            .description(FAILED.getDescription())
            .withRegistry(registry);
  }

  @Override
  public void queryWritten(final InetSocketAddress dnsServerAddress, final ChannelFuture future) {
    written.increment();
  }

  @Override
  public void queryCancelled(final int queriesRemaining) {}

  @Override
  public DnsQueryLifecycleObserver queryRedirected(final List<InetSocketAddress> nameServers) {
    return this;
  }

  @Override
  public DnsQueryLifecycleObserver queryCNAMEd(final DnsQuestion cnameQuestion) {
    return this;
  }

  @Override
  public DnsQueryLifecycleObserver queryNoAnswer(final DnsResponseCode code) {
    failed.withTag(NettyDnsKeyName.CODE.asString(), code.toString()).increment();
    return this;
  }

  @Override
  public void queryFailed(final Throwable cause) {
    error.increment();
  }

  @Override
  public void querySucceed() {
    succeded.increment();
  }
}
