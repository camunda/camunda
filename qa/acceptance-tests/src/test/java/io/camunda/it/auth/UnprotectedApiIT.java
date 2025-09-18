/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.auth;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.ConsistencyPolicy;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.qa.util.multidb.MultiDbTestApplication;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import org.junit.jupiter.api.Test;

@MultiDbTest
public class UnprotectedApiIT {
  @MultiDbTestApplication
  private static final TestStandaloneBroker BROKER =
      // it's the default right now, setting explicitly still given this test's purpose
      new TestStandaloneBroker().withUnauthenticatedAccess();

  private static CamundaClient camundaClient;

  @Test
  void anonymousRestTopologyApiAccess() {
    // when
    final var topology = camundaClient.newTopologyRequest().useRest().send().join();

    // then
    assertThat(topology.getBrokers()).hasSize(1);
  }

  @Test
  void anonymousGrpcTopologyApiAccess() {
    // when
    final var topology = camundaClient.newTopologyRequest().useGrpc().send().join();

    // then
    assertThat(topology.getBrokers()).hasSize(1);
  }

  @Test
  void anonymousRestUserTaskApiAccess() {
    // when
    final var tasks =
        camundaClient
            .newUserTaskSearchRequest()
            .consistencyPolicy(ConsistencyPolicy.noWait())
            .send()
            .join();

    // then
    assertThat(tasks.items()).hasSize(0);
  }
}
