/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.gateway.interceptors.impl;

import io.camunda.zeebe.util.jar.ThreadContextUtil;
import java.util.concurrent.Executor;

/**
 * A utility executor which ensures all submitted tasks are executed with the right thread context
 * class loader set. This is useful for interceptors loaded by external JARs. These interceptors run
 * with an isolated class loader, and they (or their dependencies) may use the thread context class
 * loader to load classes only available in their JAR.
 *
 * <p>So any interceptor which needs to deal with asynchronous code can use this executor to handle
 * callbacks in a way that their callback will have access to the required classes.
 */
final class TclAwareExecutor implements Executor {
  private final ClassLoader classLoader;

  TclAwareExecutor(final ClassLoader classLoader) {
    this.classLoader = classLoader;
  }

  @SuppressWarnings("NullableProblems")
  @Override
  public void execute(final Runnable command) {
    ThreadContextUtil.runWithClassLoader(command, classLoader);
  }
}
