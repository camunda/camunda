/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to mark controllers or methods that require secondary storage to be enabled. When
 * secondary storage is not configured (camunda.database.type=none), endpoints with this annotation
 * will return HTTP 403 Forbidden with a clear error message.
 *
 * <p>This annotation is generated on API interface methods from two OpenAPI vendor extensions:
 *
 * <ul>
 *   <li>{@code x-eventually-consistent: true} — The endpoint reads from secondary storage and may
 *       return stale data until the secondary storage catches up with the primary.
 *   <li>{@code x-requires-secondary-storage: true} — The endpoint is a mutation that requires the
 *       secondary storage subsystem to be available (e.g., because it reads during authorization or
 *       operates on historical data stored only in secondary storage).
 * </ul>
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequiresSecondaryStorage {}
