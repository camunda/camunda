/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.examples;

import io.camunda.client.CamundaClient;
import io.camunda.zeebe.model.bpmn.Bpmn;
import java.net.URI;
import java.util.List;
import java.util.Map;

/**
 * Continuously starts instances of a timer process tagged with a {@code region} variable and a
 * region-dependent {@code delay}, so each instance takes a region-correlated amount of wall-clock
 * time. The analytics pipeline captures the {@code region} variable and derives an execution-time
 * fact per instance, letting a report group execution time (avg/max) by region.
 *
 * <p>The process is {@code start -> intermediate timer catch (duration = delay) -> end}: instances
 * complete on their own once the timer fires — no job worker needed.
 *
 * <pre>
 *   java io.camunda.eventbridge.examples.DeployAndRunRegionDemo [processId]
 *   -Dcamunda.rest=http://localhost:8088 -DpauseMs=700
 * </pre>
 */
public final class DeployAndRunRegionDemo {

  private DeployAndRunRegionDemo() {}

  /** A region and the rotation of instance durations (seconds) started for it. */
  private record Region(String name, double[] delaysSeconds) {}

  public static void main(final String[] args) throws InterruptedException {
    final var restAddress = System.getProperty("camunda.rest", "http://localhost:8088");
    final var processId = args.length > 0 ? args[0] : "region-exec-time-demo";
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

      client
          .newDeployResourceCommand()
          .addProcessModel(
              Bpmn.createExecutableProcess(processId)
                  .startEvent()
                  .intermediateCatchEvent("wait", c -> c.timerWithDurationExpression("delay"))
                  .endEvent()
                  .done(),
              processId + ".bpmn")
          .send()
          .join();
      System.out.println(
          "Deployed timer process '"
              + processId
              + "'; starting instances continuously (Ctrl-C to stop)");

      long started = 0;
      for (int i = 0; ; i++) {
        final Region region = regions.get(i % regions.size());
        final double[] profile = region.delaysSeconds();
        final double secs = profile[(i / regions.size()) % profile.length];
        client
            .newCreateInstanceCommand()
            .bpmnProcessId(processId)
            .latestVersion()
            .variables(Map.of("region", region.name(), "delay", "PT" + secs + "S"))
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
