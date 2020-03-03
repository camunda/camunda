/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.test.util.annotations;

import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * All test methods or classes annotated with this annotation will
 * only be executed if the engine the tests are run against are supporting
 * DMN as well.
 *
 * Will be removed with OPT-3372 once Optimize only supports engines with DMN 1.3 support.
 */
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(RunOnlyForDmn13EnginesCondition.class)
public @interface RunOnlyForDmn13Engines {
}