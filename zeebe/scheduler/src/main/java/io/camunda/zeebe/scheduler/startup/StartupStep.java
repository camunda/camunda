/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.scheduler.startup;

import io.camunda.zeebe.scheduler.future.ActorFuture;

/**
 * Base interface for startup (and shutdown) steps. Each step implements the startup logic in {@code
 * startup(...)}, as well as, the corresponding shutdown logic in {@code shutdown(...)}. Both
 * methods take a context object as argument and return a future of the same context object.<br>
 * Typically, a startup step will create resources and call setters on the context object, whereas a
 * shutdown step will shutdown resources and remove them from the context by setting them to {@code
 * null}. <br>
 * Contract:
 *
 * <ul>
 *   <li>Shutdown will never be called before startup
 *   <li>Startup will be called at most once
 *   <li>Shutdown may be called more than once with the expectation that the first call will trigger
 *       the shutdown and any subsequent calls shall do nothing
 *   <li>Shutdown will not be called while startup is running, unless {@link
 *       StartupStep#isInterruptible()} is true
 *   <li>Implementation classes can assume that methods of this interface are never called
 *       concurrently, unless {@link StartupStep#isInterruptible()} is true, in which case shutdown
 *       can be called while startup is still running.
 * </ul>
 *
 * @param <CONTEXT> context object for the startup and shutdown steps. During startup this context
 *     is used to collect the resources created during startup; during shutdown it is used to set
 *     resources that have been shut down to {@code null}
 */
public interface StartupStep<CONTEXT> {

  /**
   * Returns name for logging purposes
   *
   * @return name for logging purposes
   */
  String getName();

  /**
   * Executes the startup logic
   *
   * @param context the startup context at the start of this step
   * @return future with startup context at the end of this step
   */
  ActorFuture<CONTEXT> startup(final CONTEXT context);

  /**
   * Executes the shutdown logic
   *
   * @param context the shutdown context at the start of this step
   * @return future with the shutdown context at the end of this step.
   */
  ActorFuture<CONTEXT> shutdown(final CONTEXT context);

  /**
   * Whether the startup step is interruptible. If a startup step is interruptible, it can be
   * interrupted by a shutdown while it is still running. In this case, the shutdown will trigger
   * the shutdown logic of the step immediately, without waiting for the startup logic to complete.
   * If a startup step is not interruptible, it cannot be interrupted by a shutdown while it is
   * still running. In this case, the shutdown will wait for the startup logic to complete before
   * triggering the shutdown logic of the step.
   *
   * <p>If a startup step is interruptible, it should ensure that a shutdown can be triggered at any
   * point during the execution of the startup logic.
   *
   * @return {@code true} if the startup step is interruptible, {@code false} otherwise
   */
  default boolean isInterruptible() {
    return false;
  }
}
