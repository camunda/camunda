/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.process.test.impl.runtime;

import org.junit.jupiter.api.Test;

public class EmbeddedRuntimeTest {

  @Test
  void shouldStartRuntime() throws Exception {
    final CamundaProcessTestEmbeddedRuntime runtime =
        new CamundaProcessTestEmbeddedRuntime(new CamundaProcessTestRuntimeBuilder());

    runtime.start();

    runtime.close();
  }

  @Test
  void shouldConfigureRuntime() throws Exception {
    final CamundaProcessTestEmbeddedRuntime runtime =
        new CamundaProcessTestEmbeddedRuntime(
            new CamundaProcessTestRuntimeBuilder()
                .withCamundaEnv("logging.level.liquibase", "error"));

    runtime.start();

    runtime.close();
  }
}
