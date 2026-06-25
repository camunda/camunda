/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.util;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

/**
 * Composed annotation that marks a method as a {@link TestTemplate} whose invocations (one per DB
 * backend provided by {@link CamundaRdbmsInvocationContextProviderExtension}) run concurrently.
 * Since each invocation targets a completely separate database container, running them in parallel
 * is safe. Methods within a test class still execute sequentially, preventing data contamination
 * across methods that share the same container.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@TestTemplate
@Execution(ExecutionMode.CONCURRENT)
public @interface RdbmsTestTemplate {}
