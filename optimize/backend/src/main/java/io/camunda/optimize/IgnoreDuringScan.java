/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * This annotation is used to ignore annotated classes during component-scan.
 *
 * <p>Sample usage:
 *
 * <pre>{@code
 * @ComponentScan(excludeFilters = @ComponentScan.Filter(IgnoreDuringScan.class))
 * @Configuration
 * public class Main {
 * }
 * }</pre>
 */
@Retention(RetentionPolicy.RUNTIME)
public @interface IgnoreDuringScan {}
