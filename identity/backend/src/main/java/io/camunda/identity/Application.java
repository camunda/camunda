/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.identity;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * This application file is a temporary addition to support the development of the minimal feature
 * set of Identity. It will be removed once the monojar integration is clear and available.
 */
@SpringBootApplication(scanBasePackages = {"io.camunda.authentication", "io.camunda.identity"})
@ConfigurationPropertiesScan()
public class Application {

  public static void main(final String[] args) {
    SpringApplication.run(Application.class, args);
  }
}
