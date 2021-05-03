/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.gateway.api.job;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.gateway.api.util.GatewayTest;
import io.camunda.zeebe.gateway.impl.configuration.GatewayCfg;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.ActivateJobsRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.ActivateJobsResponse;
import java.time.Duration;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import org.junit.Test;

public final class NonLongPollingActivateJobsTest extends GatewayTest {

  public NonLongPollingActivateJobsTest() {
    super(getConfig());
  }

  private static GatewayCfg getConfig() {
    final var config = new GatewayCfg();
    config.getLongPolling().setEnabled(false);
    return config;
  }

  @Test
  public void shouldActivateNoJobsWhenNonAvailable() {
    // given
    final String jobType = "testJob";
    final String worker = "testWorker";
    final int maxJobsToActivate = 13;
    final Duration timeout = Duration.ofMinutes(12);
    final List<String> fetchVariables = Arrays.asList("foo", "bar", "baz");

    // no jobs available
    final ActivateJobsStub stub = new ActivateJobsStub();
    stub.registerWith(brokerClient);
    stub.addAvailableJobs(jobType, 0);

    final ActivateJobsRequest request =
        ActivateJobsRequest.newBuilder()
            .setType(jobType)
            .setWorker(worker)
            .setMaxJobsToActivate(maxJobsToActivate)
            .setTimeout(timeout.toMillis())
            .addAllFetchVariable(fetchVariables)
            .build();

    // when
    final Iterator<ActivateJobsResponse> responses = client.activateJobs(request);

    // then no jobs activated
    assertThat(responses.hasNext()).isFalse();
  }
}
