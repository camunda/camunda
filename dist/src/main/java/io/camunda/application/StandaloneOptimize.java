/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application;

import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.springframework.boot.SpringBootConfiguration;

@SpringBootConfiguration(proxyBeanMethods = false)
public class StandaloneOptimize {

  public static void main(final String[] args) {
    throw new OptimizeRuntimeException(
        "Standalone class not yet implemented. Use io.camunda.optimize.Main instead.");
  }
}
