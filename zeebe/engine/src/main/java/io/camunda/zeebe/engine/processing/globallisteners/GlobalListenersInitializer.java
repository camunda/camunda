/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.globallisteners;

import io.camunda.zeebe.engine.EngineConfiguration;
import io.camunda.zeebe.engine.GlobalListenerConfiguration;
import io.camunda.zeebe.engine.GlobalListenersConfiguration;
import io.camunda.zeebe.engine.Loggers;
import io.camunda.zeebe.engine.state.globallisteners.GlobalListenersState;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.impl.record.value.globallisteners.GlobalListenerRecord;
import io.camunda.zeebe.protocol.impl.record.value.globallisteners.GlobalListenersRecord;
import io.camunda.zeebe.protocol.record.intent.GlobalListenersIntent;
import io.camunda.zeebe.stream.api.ReadonlyStreamProcessorContext;
import io.camunda.zeebe.stream.api.StreamProcessorLifecycleAware;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;

public class GlobalListenersInitializer implements StreamProcessorLifecycleAware {

  private static final Logger LOG = Loggers.PROCESS_PROCESSOR_LOGGER;

  private final EngineConfiguration engineConfiguration;

  private final GlobalListenersState globalListenersState;

  public GlobalListenersInitializer(final EngineConfiguration engineConfiguration, final
      ProcessingState processingState) {
    this.engineConfiguration = engineConfiguration;
    this.globalListenersState = processingState.getGlobalListenersState();
  }

  @Override
  public void onRecovered(final ReadonlyStreamProcessorContext context) {
  }
}
