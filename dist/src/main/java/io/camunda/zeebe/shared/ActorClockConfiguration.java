/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.shared;

import io.camunda.zeebe.scheduler.clock.ActorClock;
import io.camunda.zeebe.scheduler.clock.ControlledActorClock;
import io.camunda.zeebe.shared.management.ActorClockService;
import io.camunda.zeebe.shared.management.ControlledActorClockService;
import java.util.Optional;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.ConstructorBinding;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.context.annotation.Bean;

@SuppressWarnings("unused")
@ConfigurationProperties("zeebe.clock")
public final class ActorClockConfiguration {

  private final Optional<ActorClock> clock;
  private final ActorClockService service;

  @ConstructorBinding
  public ActorClockConfiguration(@DefaultValue("false") final boolean controlled) {
    if (controlled) {
      final var controlledClock = new ControlledActorClock();
      service = new ControlledActorClockService(controlledClock);
      clock = Optional.of(controlledClock);
    } else {
      clock = Optional.empty();
      service = System::currentTimeMillis;
    }
  }

  @Bean
  public Optional<ActorClock> getClock() {
    return clock;
  }

  @Bean
  public ActorClockService getClockService() {
    return service;
  }
}
