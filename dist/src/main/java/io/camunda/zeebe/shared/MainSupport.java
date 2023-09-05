/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.shared;

import io.camunda.zeebe.broker.Loggers;
import io.camunda.zeebe.util.error.FatalErrorHandler;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;

/**
 * Utility class for shared behavior in application `main`, i.e. program entry points. This helps
 * ensure all executables have some sane defaults (e.g. applying system properties, per thread
 * uncaught exception handler, etc.)
 */
public final class MainSupport {
  private MainSupport() {}

  /**
   * Sets global configuration for all executable classes:
   *
   * <ul>
   *   <li>Sets the default uncaught exception handler for all threads to {@link FatalErrorHandler}
   *   <li>Sets default system properties <em>if they haven't been overridden by the user</em>
   * </ul>
   */
  public static void setDefaultGlobalConfiguration() {
    Thread.setDefaultUncaughtExceptionHandler(
        FatalErrorHandler.uncaughtExceptionHandler(Loggers.SYSTEM_LOGGER));

    // some of the health indicators use scheduled tasks which rely on the reactor schedulers, which
    // by default have a very high bound. we only use the embedded server for management purposes,
    // not for actual business logic, so we don't need so many
    putSystemPropertyIfAbsent(
        "reactor.schedulers.defaultBoundedElasticSize",
        String.valueOf(2 * Runtime.getRuntime().availableProcessors()));
    // simplify bounding direct memory usage of Netty by ensuring all servers use the same allocator
    putSystemPropertyIfAbsent("io.grpc.netty.useCustomAllocator", Boolean.toString(false));
  }

  /**
   * Returns the default {@link SpringApplicationBuilder} for all executables in the distribution
   * module, with sane defaults.
   */
  public static SpringApplicationBuilder createDefaultApplicationBuilder() {
    return new SpringApplicationBuilder().web(WebApplicationType.REACTIVE).logStartupInfo(true);
  }

  /**
   * Sets system properties only if they haven't been set already, allowing users to override them
   * via CLI or other means
   */
  public static void putSystemPropertyIfAbsent(final String key, final String value) {
    if (System.getProperty(key) == null) {
      System.setProperty(key, value);
    }
  }
}
