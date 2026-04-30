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
 * Marker for {@code @CamundaRestController}s that expose cluster-level concerns and therefore must
 * not be exposed under the per-physical-tenant path prefix ({@code
 * /v2/physical-tenants/{physicalTenantId}/...}).
 *
 * <p>Annotated controllers keep their original path mapping only.
 *
 * <p>Examples of cluster-scoped concerns: topology, status/health, license, system endpoints,
 * authentication, error handling.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Documented
public @interface ClusterScoped {}
