/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
import io.camunda.client.CamundaClient;
import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Load for the broker on LazyFS: publishes messages with a payload from several threads until
 * killed, printing how many were acknowledged by the broker. Usage: LoadGen <grpc host:port>
 * <threads> <payloadBytes>
 */
public final class LoadGen {
  public static void main(final String[] args) throws Exception {
    final var address = args[0];
    final int threads = Integer.parseInt(args[1]);
    final var payload = "x".repeat(Integer.parseInt(args[2]));
    final var acked = new AtomicLong();
    final var failed = new AtomicLong();
    try (final CamundaClient client =
        CamundaClient.newClientBuilder()
            .grpcAddress(URI.create("http://" + address))
            .restAddress(URI.create("http://localhost:8080"))
            .preferRestOverGrpc(false)
            .defaultRequestTimeout(Duration.ofSeconds(10))
            .build()) {
      for (int t = 0; t < threads; t++) {
        final int id = t;
        Thread.ofPlatform()
            .start(
                () -> {
                  long i = 0;
                  while (true) {
                    try {
                      client
                          .newPublishMessageCommand()
                          .messageName("msg")
                          .correlationKey("t" + id + "-" + (i++ % 1000))
                          .timeToLive(Duration.ofSeconds(Long.getLong("ttl", 600)))
                          .variables(Map.of("payload", payload))
                          .send()
                          .join();
                      acked.incrementAndGet();
                    } catch (final Exception e) {
                      failed.incrementAndGet();
                      try {
                        Thread.sleep(200);
                      } catch (final InterruptedException ie) {
                        return;
                      }
                    }
                  }
                });
      }
      while (true) {
        Thread.sleep(2000);
        System.out.println("acked=" + acked.get() + " failed=" + failed.get());
      }
    }
  }
}
