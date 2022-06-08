/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * This annotation is used to ignore annotated classes during component-scan.
 * 
 * Sample usage:
 * <pre>{@code
 * @ComponentScan(excludeFilters = @ComponentScan.Filter(IgnoreDuringScan.class))
 * @Configuration
 * public class Main {
 * }</pre>
 */
@Retention(RetentionPolicy.RUNTIME)
public @interface IgnoreDuringScan {
}
