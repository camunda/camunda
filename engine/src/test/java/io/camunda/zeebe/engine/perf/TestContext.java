/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.perf;

import io.camunda.zeebe.scheduler.ActorScheduler;
import io.camunda.zeebe.test.util.AutoCloseableRule;
import org.junit.rules.TemporaryFolder;

/**
 * Containing infrastructure related dependencies which might be shared between TestEngines.
 *
 * @param actorScheduler the scheduler which is used during tests
 * @param temporaryFolder the temporary folder where the log and runtime is written to
 * @param autoCloseableRule a collector of all to managed resources, which should be cleaned up
 *     later
 */
public record TestContext(
    ActorScheduler actorScheduler,
    TemporaryFolder temporaryFolder,
    AutoCloseableRule autoCloseableRule) {}
