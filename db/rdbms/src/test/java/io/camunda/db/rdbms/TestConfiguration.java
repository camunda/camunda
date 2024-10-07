/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms;

import io.camunda.zeebe.scheduler.ActorScheduler;
import org.springframework.context.annotation.Bean;

@org.springframework.boot.test.context.TestConfiguration
public class TestConfiguration {

  @Bean
  public ActorScheduler actorScheduler() {
    var scheduler = ActorScheduler.newActorScheduler().build();
    scheduler.start();
    return scheduler;
  }

}
