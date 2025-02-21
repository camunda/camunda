/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.atomix.cluster.messaging.impl;

<<<<<<< HEAD
=======
import static io.atomix.cluster.messaging.impl.NettyDnsMetricsDoc.*;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Meter.MeterProvider;
import io.micrometer.core.instrument.MeterRegistry;
>>>>>>> 07f812b2 (fix: NettyDnsMetrics use a MeterProvider instead of a HashMap to avoid concurrency issues)
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
>>>>>>> 07f812b2 (fix: NettyDnsMetrics use a MeterProvider instead of a HashMap to avoid concurrency issues)

final class NettyDnsMetrics implements DnsQueryLifecycleObserver {
<<<<<<< HEAD
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
=======

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
>>>>>>> 07f812b2 (fix: NettyDnsMetrics use a MeterProvider instead of a HashMap to avoid concurrency issues)

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
<<<<<<< HEAD
    FAILED.labels(code.toString()).inc();
=======
    failed.withTag(NettyDnsKeyName.CODE.asString(), code.toString()).increment();
>>>>>>> 07f812b2 (fix: NettyDnsMetrics use a MeterProvider instead of a HashMap to avoid concurrency issues)
    return this;
  }

  @Override
  public void queryFailed(final Throwable cause) {
    ERROR.inc();
  }

  @Override
  public void querySucceed() {
<<<<<<< HEAD
    SUCCESS.inc();
=======
    succeded.increment();
>>>>>>> 07f812b2 (fix: NettyDnsMetrics use a MeterProvider instead of a HashMap to avoid concurrency issues)
  }
}
