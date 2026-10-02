/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.connect.plugin;

/**
 * A unifying interface for every Search DB connector interceptor.
 *
 * <p>All connectors now use Apache HTTP Client 5.x, so this simply aliases its interceptor type.
 */
public interface CompatHttpRequestInterceptor
    extends org.apache.hc.core5.http.HttpRequestInterceptor {}
