/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.process.test.impl.runtime;

import io.camunda.process.test.impl.containers.ContainerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CamundaProcessTestGlobalContainerRuntime
   extends CamundaProcessTestContainerRuntime {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(CamundaProcessTestGlobalContainerRuntime.class);

  private static boolean isRuntimeStarted = false;

  public CamundaProcessTestGlobalContainerRuntime(final ContainerFactory containerFactory) {
    super(newBuilder(), containerFactory);
  }

  @Override
  public void start() {
    if (!isRuntimeStarted) {
      LOGGER.info("Starting CPT global container runtime.");
      super.start();
      isRuntimeStarted = true;
    } else {
      LOGGER.info("CPT global container runtime already started.");
    }
  }

  @Override
  public void close() throws Exception {
    LOGGER.info("You can't stop the music!");
  }
}
