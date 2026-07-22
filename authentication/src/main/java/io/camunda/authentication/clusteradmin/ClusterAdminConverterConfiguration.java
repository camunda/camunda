/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.clusteradmin;

import io.camunda.security.api.context.CamundaAuthenticationConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;

/**
 * Registers {@link ClusterAdminAuthenticationConverter} method-agnostically, so it is present for
 * whichever cluster-admin chain is active — the Basic chain ({@link
 * ClusterAdminBasicSecurityConfiguration}, {@code @ConditionalOnAuthenticationMethod(BASIC)}) or
 * the OIDC chain ({@link ClusterAdminOidcSecurityConfiguration},
 * {@code @ConditionalOnAuthenticationMethod(OIDC)}).
 *
 * <p>Registers this converter for OIDC too.
 *
 * <p>Without it, a matched cluster-admin bearer token could fall through to CSL's DB-backed
 * membership converter and inherit memberships from a colliding DB principal.
 *
 * <p>This converter only handles principals with {@link
 * ClusterAdminBasicSecurityConfiguration#CLUSTER_ADMIN_AUTHORITY}, so it is inactive when the
 * cluster-admin chain is disabled.
 */
@Configuration
public class ClusterAdminConverterConfiguration {

  @Bean
  @Order(Ordered.HIGHEST_PRECEDENCE)
  public CamundaAuthenticationConverter<Authentication> clusterAdminAuthenticationConverter() {
    return new ClusterAdminAuthenticationConverter();
  }
}
