/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.config;

import io.camunda.authentication.service.PhysicalTenantMembershipContextPropagator;
import io.camunda.security.api.context.MembershipResolutionContextPropagator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers the host's {@link MembershipResolutionContextPropagator} once, method-agnostically, so
 * no auth chain can forget to wire it. CSL defines its own bean of this type that does nothing
 * ({@code identity()}); this one wins because this configuration is a plain {@code @Import} while
 * CSL loads later, via {@code @ImportAutoConfiguration}.
 */
@Configuration
public class MembershipResolutionContextPropagatorConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public MembershipResolutionContextPropagator membershipResolutionContextPropagator() {
    return new PhysicalTenantMembershipContextPropagator();
  }
}
