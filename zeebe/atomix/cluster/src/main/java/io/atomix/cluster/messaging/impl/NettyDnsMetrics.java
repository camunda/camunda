/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.atomix.cluster.messaging.impl;

import io.netty.channel.ChannelFuture;
import io.netty.handler.codec.dns.DnsQuestion;
import io.netty.handler.codec.dns.DnsResponseCode;
import io.netty.resolver.dns.DnsQueryLifecycleObserver;
import io.prometheus.client.Counter;
import java.net.InetSocketAddress;
import java.util.List;
<<<<<<< HEAD
=======
import net.jcip.annotations.ThreadSafe;
import org.agrona.collections.Int2ObjectHashMap;
>>>>>>> 444c8eee (fix: make MessagingMetricsImpl ThreadSafe)

@ThreadSafe
final class NettyDnsMetrics implements DnsQueryLifecycleObserver {
  private static final Counter ERROR =
      Counter.build()
          .help("Counts how often DNS queries fail with an error")
          .namespace("zeebe")
          .subsystem("dns")
          .name("error")
          .register();
  private static final Counter FAILED =
      Counter.build()
          .help("Counts how often DNS queries return an unsuccessful answer")
          .namespace("zeebe")
          .subsystem("dns")
          .name("failed")
          .labelNames("code")
          .register();
  private static final Counter WRITTEN =
      Counter.build()
          .help("Counts how often DNS queries are written")
          .namespace("zeebe")
          .subsystem("dns")
          .name("written")
          .register();
  private static final Counter SUCCESS =
      Counter.build()
          .help("Counts how often DNS queries are successful")
          .namespace("zeebe")
          .subsystem("dns")
          .name("success")
          .register();

  @Override
  public void queryWritten(final InetSocketAddress dnsServerAddress, final ChannelFuture future) {
    WRITTEN.inc();
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
    FAILED.labels(code.toString()).inc();
    return this;
  }

  @Override
  public void queryFailed(final Throwable cause) {
    ERROR.inc();
  }

  @Override
  public void querySucceed() {
    SUCCESS.inc();
  }
}
