/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.qa.util.cluster;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

@ZeebeIntegration
public class ZeebeIntegrationTest {

  private static final List<TestStandaloneBroker> ASSERTIONS = new ArrayList<>();

  @TestZeebe(autoStart = false)
  private TestStandaloneBroker testCluster;

  @AfterAll
  public static void checkAllClustersAreClosed() {
    ASSERTIONS.forEach(c -> assertThat(c.isStarted()).isFalse());
  }

  @Test
  public void shouldCloseCluster() {
    // given
    testCluster = new TestStandaloneBroker();
    testCluster.start();
    assertThat(testCluster.isStarted()).isTrue();

    // when
    ASSERTIONS.add(testCluster);

    // then
    // testCluster is closed in the @AfterEach callback.
    // It's asserted in the @AfterAll callback
  }
}
