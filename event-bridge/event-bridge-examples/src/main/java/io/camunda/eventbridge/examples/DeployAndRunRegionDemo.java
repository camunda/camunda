/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.examples;

import io.camunda.client.CamundaClient;
import java.net.URI;
import java.util.List;
import java.util.Map;

/**
 * Continuously starts instances of the {@code region-exec-time-demo} process (a laid-out BPMN model
 * with two timer events, deployed from {@code region-exec-time-demo.bpmn}), each tagged with a
 * {@code region} variable and timer durations. The "process order" timer is region-dependent
 * ({@code delay}); the "review order" timer ({@code reviewDelay}) is shorter — so per-element
 * execution times differ, which the heatmap renders on the diagram. Instances complete on their own
 * once the timers fire — no job worker needed.
 *
 * <pre>
 *   java io.camunda.eventbridge.examples.DeployAndRunRegionDemo
 *   -Dcamunda.rest=http://localhost:8088 -DpauseMs=700
 * </pre>
 */
public final class DeployAndRunRegionDemo {

  private static final String PROCESS_ID = "region-exec-time-demo";
  private static final String RESOURCE = "region-exec-time-demo.bpmn";

  private DeployAndRunRegionDemo() {}

  /** A region and the rotation of "process order" durations (seconds) started for it. */
  private record Region(String name, double[] delaysSeconds) {}

  public static void main(final String[] args) throws InterruptedException {
    final var restAddress = System.getProperty("camunda.rest", "http://localhost:8088");
    final long pauseMs = Long.getLong("pauseMs", 700L);

    // distinct duration profiles so avg and max differ per region in the report
    final List<Region> regions =
        List.of(
            new Region("EU", new double[] {0.5, 1.0, 1.5}),
            new Region("US", new double[] {1.0, 2.0, 3.0}),
            new Region("APAC", new double[] {2.0, 3.0, 4.0}));

    try (final var client =
        CamundaClient.newClientBuilder()
            .restAddress(URI.create(restAddress))
            .preferRestOverGrpc(true)
            .build()) {

      client.newDeployResourceCommand().addResourceFromClasspath(RESOURCE).send().join();
      System.out.println(
          "Deployed '" + PROCESS_ID + "'; starting instances continuously (Ctrl-C to stop)");

      long started = 0;
      for (int i = 0; ; i++) {
        final Region region = regions.get(i % regions.size());
        final double[] profile = region.delaysSeconds();
        final double processSecs = profile[(i / regions.size()) % profile.length];
        final double reviewSecs = 0.3 + (i % 3) * 0.2; // 0.3 / 0.5 / 0.7s — the shorter step
        client
            .newCreateInstanceCommand()
            .bpmnProcessId(PROCESS_ID)
            .latestVersion()
            .variables(
                Map.of(
                    "region", region.name(),
                    "delay", "PT" + processSecs + "S",
                    "reviewDelay", "PT" + reviewSecs + "S"))
            .send()
            .join();
        started++;
        if (started % 10 == 0) {
          System.out.println("started " + started + " instances…");
        }
        Thread.sleep(pauseMs);
      }
    }
  }
}
