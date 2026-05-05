/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.annotation;

import io.camunda.zeebe.gateway.rest.context.PhysicalTenantContext;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a controller method parameter that should be populated with the resolved physical tenant id
 * for the current request.
 *
 * <p>Resolution is performed by {@code PhysicalTenantArgumentResolver}, which reads the value from
 * {@link PhysicalTenantContext} (set earlier by {@code PhysicalTenantInterceptor}). When no
 * physical-tenant prefix is present on the request, the value defaults to {@link
 * PhysicalTenantContext#DEFAULT_PHYSICAL_TENANT_ID}.
 *
 * <p>Usage:
 *
 * <pre>{@code
 * @CamundaPostMapping
 * public CompletableFuture<ResponseEntity<Object>> createProcessInstance(
 *     @RequestBody final ProcessInstanceCreationInstruction request,
 *     @PhysicalTenant final String physicalTenantId) { ... }
 * }</pre>
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface PhysicalTenant {}
