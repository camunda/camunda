/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.management;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.ImmutableMap;
import io.camunda.zeebe.util.health.HealthIssue;
import io.camunda.zeebe.util.health.HealthReport;
import io.camunda.zeebe.util.health.HealthStatus;
import java.time.Instant;
import org.junit.Test;

public class HealthTreeTest {
  @Test
  public void unhealthyComponentsArePropagatedUpwards() {
    final var root =
        new HealthReport(
            "A",
            HealthStatus.HEALTHY,
            null,
            ImmutableMap.of(
                "B",
                new HealthReport(
                    "B",
                    HealthStatus.UNHEALTHY,
                    HealthIssue.of("unavailable", Instant.ofEpochMilli(123123L)),
                    ImmutableMap.of())));
    final var tree = HealthTree.fromHealthReport(root);
    assertThat(tree.status()).isEqualTo(HealthStatus.UNHEALTHY);
  }
}
