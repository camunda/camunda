/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.shared;

import io.camunda.zeebe.shared.management.ActorClockService;
import io.camunda.zeebe.shared.management.ControlledActorClockService;
import io.camunda.zeebe.util.sched.clock.ActorClock;
import io.camunda.zeebe.util.sched.clock.ControlledActorClock;
import io.camunda.zeebe.util.sched.clock.DefaultActorClock;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.ConstructorBinding;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.context.annotation.Bean;
import org.springframework.web.context.annotation.ApplicationScope;

@SuppressWarnings("unused")
@ConfigurationProperties("zeebe.clock")
public final class ActorClockConfiguration {

  private final ActorClock clock;
  private final ActorClockService service;

  @ConstructorBinding
  public ActorClockConfiguration(@DefaultValue("false") final boolean controlled) {
    if (controlled) {
      final var controlledClock = new ControlledActorClock();
      service = new ControlledActorClockService(controlledClock);
      clock = controlledClock;
    } else {
      clock = new DefaultActorClock();
      service = clock::getTimeMillis;
    }
  }

  @Bean
  @ApplicationScope
  public ActorClock getClock() {
    return clock;
  }

  @Bean
  @ApplicationScope
  public ActorClockService getClockService() {
    return service;
  }
}
