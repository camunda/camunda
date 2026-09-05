/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize;

import org.springframework.boot.SpringApplication;

/**
 * Shared guard for Optimize's {@code EnvironmentPostProcessor}s.
 *
 * <p>Spring Boot's {@code SpringFactoriesLoader} discovers every {@code EnvironmentPostProcessor}
 * on the classpath for every {@code SpringApplication} started in the JVM, independent of
 * {@code @ComponentScan} or which class is that application's primary source. {@code
 * camunda-authentication} and {@code camunda-zeebe} (dist) are test-scope dependencies of
 * optimize-backend (pulled in via zeebe-qa-util for the embedded broker Optimize's own CCSM ITs
 * boot), so Optimize's {@code META-INF/spring.factories} entries are also visible to that broker's
 * unrelated {@code SpringApplication} (camunda/camunda#60184). Every {@code
 * EnvironmentPostProcessor} that only makes sense for Optimize's own webapp must guard on this
 * before touching the environment.
 */
public final class OptimizeApplicationDetector {

  private OptimizeApplicationDetector() {}

  // True only when `application` is genuinely Optimize's own SpringApplication — i.e. Main.class
  // (the sole primary source of both Main#main and AbstractIT#startAndUseNewOptimizeInstance) is
  // among its sources. A null application (defensive: the interface contract allows it, and some
  // callers construct a post-processor directly without going through Spring Boot's own bootstrap)
  // is never treated as Optimize's own.
  public static boolean isOptimizeApplication(final SpringApplication application) {
    return application != null && application.getAllSources().contains(Main.class);
  }
}
