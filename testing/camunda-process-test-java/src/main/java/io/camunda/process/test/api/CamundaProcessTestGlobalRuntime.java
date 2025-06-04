/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.process.test.api;

import io.camunda.process.test.impl.containers.ContainerFactory;
import io.camunda.process.test.impl.runtime.CamundaProcessTestGlobalContainerRuntime;
import io.camunda.process.test.impl.runtime.CamundaProcessTestRuntime;

public enum CamundaProcessTestGlobalRuntime {
  INSTANCE;

  private CamundaProcessTestRuntime runtime = null;

  public CamundaProcessTestRuntime getRuntime() {
    if (runtime == null) {
      runtime = new CamundaProcessTestGlobalContainerRuntime(new ContainerFactory());
    }

    return runtime;
  }
}
