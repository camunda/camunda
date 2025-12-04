/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.globallistener;

import io.camunda.zeebe.engine.EngineConfiguration;
import io.camunda.zeebe.engine.Loggers;
import io.camunda.zeebe.engine.state.globallistener.GlobalListenersState;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.stream.api.ReadonlyStreamProcessorContext;
import io.camunda.zeebe.stream.api.StreamProcessorLifecycleAware;
import org.slf4j.Logger;

public class GlobalListenersInitializer implements StreamProcessorLifecycleAware {

  private static final Logger LOG = Loggers.PROCESS_PROCESSOR_LOGGER;

  private final EngineConfiguration engineConfiguration;

  private final GlobalListenersState globalListenersState;

  public GlobalListenersInitializer(final EngineConfiguration engineConfiguration, final
      ProcessingState processingState) {
    this.engineConfiguration = engineConfiguration;
    globalListenersState = processingState.getGlobalListenersState();
  }

  @Override
  public void onRecovered(final ReadonlyStreamProcessorContext context) {
  }
}
