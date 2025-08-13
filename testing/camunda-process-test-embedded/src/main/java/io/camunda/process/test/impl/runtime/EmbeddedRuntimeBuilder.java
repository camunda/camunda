/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.process.test.impl.runtime;

import io.camunda.process.test.api.CamundaProcessTestRuntimeMode;
import io.camunda.process.test.impl.configuration.CamundaProcessTestRuntimeConfiguration;

public class EmbeddedRuntimeBuilder implements CamundaSpringProcessTestRuntimeBuilder {

  private final CamundaSpringProcessTestRuntimeBuilder defaultRuntimeBuilder =
      new DefaultCamundaSpringProcessTestRuntimeBuilder();

  @Override
  public CamundaProcessTestRuntime buildRuntime(
      final CamundaProcessTestRuntimeBuilder runtimeBuilder,
      final CamundaProcessTestRuntimeConfiguration runtimeConfiguration) {

    if (runtimeConfiguration.getRuntimeMode() == CamundaProcessTestRuntimeMode.EMBEDDED) {
      runtimeBuilder
          .withRuntimeMode(runtimeConfiguration.getRuntimeMode())
          .withCamundaEnv(runtimeConfiguration.getCamundaEnvVars());

      return new CamundaProcessTestEmbeddedRuntime(runtimeBuilder);

    } else {
      return defaultRuntimeBuilder.buildRuntime(runtimeBuilder, runtimeConfiguration);
    }
  }
}
