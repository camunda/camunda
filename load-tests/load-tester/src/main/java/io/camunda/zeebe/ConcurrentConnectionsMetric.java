/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe;

import com.google.common.base.Suppliers;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.hc.client5.http.async.AsyncExecCallback;
import org.apache.hc.client5.http.async.AsyncExecChain;
import org.apache.hc.client5.http.async.AsyncExecChain.Scope;
import org.apache.hc.client5.http.async.AsyncExecChainHandler;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.nio.AsyncDataConsumer;
import org.apache.hc.core5.http.nio.AsyncEntityProducer;

public class ConcurrentConnectionsMetric implements AsyncExecChainHandler {
  private final AtomicInteger concurrentConnections = new AtomicInteger();

  public ConcurrentConnectionsMetric(final MeterRegistry registry) {
    Gauge.builder("starter.http.concurrent.connections", concurrentConnections, AtomicInteger::get)
        .description("Number of concurrent HTTP connections to the broker")
        .register(registry);
  }

  @Override
  public void execute(
      final HttpRequest request,
      final AsyncEntityProducer entityProducer,
      final Scope scope,
      final AsyncExecChain chain,
      final AsyncExecCallback asyncExecCallback)
      throws HttpException, IOException {
    concurrentConnections.incrementAndGet();
    final var done = Suppliers.memoize(concurrentConnections::decrementAndGet);
    chain.proceed(
        request,
        entityProducer,
        scope,
        new AsyncExecCallback() {
          @Override
          public AsyncDataConsumer handleResponse(
              final HttpResponse response, final EntityDetails entityDetails)
              throws HttpException, IOException {
            return asyncExecCallback.handleResponse(response, entityDetails);
          }

          @Override
          public void handleInformationResponse(final HttpResponse response)
              throws HttpException, IOException {
            asyncExecCallback.handleInformationResponse(response);
          }

          @Override
          public void completed() {
            done.get();
            asyncExecCallback.completed();
          }

          @Override
          public void failed(final Exception cause) {
            done.get();
            asyncExecCallback.failed(cause);
          }
        });
  }
}
