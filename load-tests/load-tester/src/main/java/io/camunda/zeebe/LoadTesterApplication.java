/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe;

import io.camunda.client.CamundaClient;
import io.camunda.zeebe.config.LoadTesterProperties;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;

@SpringBootApplication
@EnableConfigurationProperties(LoadTesterProperties.class)
public class LoadTesterApplication {

  public static void main(final String[] args) {
    SpringApplication.run(LoadTesterApplication.class, args);
  }

  @Bean
  @Profile("starter")
  CommandLineRunner starterRunner(
      final CamundaClient client, final LoadTesterProperties config, final MeterRegistry registry) {
    return args -> {
      final var starter = new Starter(client, config, registry);
      Runtime.getRuntime().addShutdownHook(new Thread(starter::close));
      starter.run();
    };
  }

  @Bean
  @Profile("worker")
  CommandLineRunner workerRunner(
      final CamundaClient client, final LoadTesterProperties config, final MeterRegistry registry) {
    return args -> {
      final var worker = new Worker(client, config, registry);
      Runtime.getRuntime().addShutdownHook(new Thread(worker::close));
      worker.run();
    };
  }
}
