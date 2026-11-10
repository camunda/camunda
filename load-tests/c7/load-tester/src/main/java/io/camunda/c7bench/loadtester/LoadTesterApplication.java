/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.c7bench.loadtester;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * One app, two roles, selected by Spring profile (same as the C8 load-tester):
 *
 * <ul>
 *   <li>{@code starter}: deploys the BPMN and starts process instances at a fixed rate via REST.
 *   <li>{@code worker}: completes external tasks with the official Java external task client.
 * </ul>
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class LoadTesterApplication {
  public static void main(final String[] args) {
    SpringApplication.run(LoadTesterApplication.class, args);
  }
}
