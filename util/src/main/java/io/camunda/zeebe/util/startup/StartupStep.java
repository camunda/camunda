/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.util.startup;

import java.util.concurrent.CompletableFuture;

/**
 * Base interface for startup (and shutdown) steps. Each step implements the startup logic in {@code
 * startup(...)}, as well as, the corresponding shutdown logic in {@code shutdown(...)}. Both
 * methods take a context object as argument and return a future of the same context object.<br>
 * Typically, a startup step will create resources and call setters on the context object, whereas a
 * shutdown step will shutdown resources and remove them from the context by setting them to {@code
 * null}. <br>
 * Extending {@link AbstractStartupStep} is the recommended way to implement this interface<br>
 * Contract:
 *
 * <ul>
 *   <li>Shutdown will never be called before startup
 *   <li>Startup will be called at most once
 *   <li>Shutdown may be called more than once with the expectation that the first call will trigger
 *       the shutdown and any subsequent calls shall do nothing
 *   <li>Shutdown may be called before the future of startup has completed with the expectation that
 *       it will either cancel the startup process, or it will wait until it terminates and then
 *       immediately begin the shutdown
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
  CompletableFuture<CONTEXT> startup(final CONTEXT context);

  /**
   * Executes the shutdown logic
   *
   * @param context the shutdown context at the start of this step
   * @return future with the shutdown context at the end of this step.
   */
  CompletableFuture<CONTEXT> shutdown(final CONTEXT context);
}
