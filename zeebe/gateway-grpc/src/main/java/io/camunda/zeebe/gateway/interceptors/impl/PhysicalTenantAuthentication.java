/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.interceptors.impl;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * What the {@link AuthenticationInterceptor} needs to know about one physical tenant.
 *
 * @param handler authenticates calls for the tenant; {@code null} on an unprotected API
 * @param metrics records authentication latency; {@code null} on an unprotected API
 * @param unavailableReason why calls for the tenant are rejected for now, e.g. while its module
 *     restarts; {@code null} when the tenant serves calls
 */
@NullMarked
public record PhysicalTenantAuthentication(
    @Nullable AuthenticationHandler handler,
    @Nullable AuthenticationMetrics metrics,
    @Nullable String unavailableReason) {}
