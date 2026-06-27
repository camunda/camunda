/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.api.util;

import static io.camunda.configuration.api.physicaltenants.PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID;

import io.camunda.configuration.api.physicaltenants.PhysicalTenantIds;
import io.camunda.security.configuration.EngineSecurityConfig;
import io.camunda.service.UserServices;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.gateway.Gateway;
import io.camunda.zeebe.gateway.impl.configuration.GatewayCfg;
import io.camunda.zeebe.gateway.validation.VariableNameLengthValidator;
import io.camunda.zeebe.protocol.impl.stream.job.JobActivationProperties;
import io.camunda.zeebe.scheduler.ActorSchedulingService;
import io.camunda.zeebe.transport.stream.api.ClientStreamer;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.Map;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;

/** Test helper constructing {@link Gateway} for single-tenant callers. */
public final class GatewayTestFactory {

  private static final Duration DEFAULT_SHUTDOWN_TIMEOUT = Duration.ofSeconds(30);

  private GatewayTestFactory() {}

  /**
   * Builds a {@link Gateway} where {@code securityConfiguration}, {@code jwtDecoder} and {@code
   * userServices} are wired for the {@code default} physical tenant only; OIDC claims are passed
   * through unchanged.
   */
  public static Gateway create(
      final GatewayCfg gatewayCfg,
      final EngineSecurityConfig securityConfiguration,
      final BrokerClient brokerClient,
      final ActorSchedulingService actorSchedulingService,
      final ClientStreamer<JobActivationProperties> jobStreamer,
      final UserServices userServices,
      final PasswordEncoder passwordEncoder,
      final MeterRegistry meterRegistry,
      final JwtDecoder jwtDecoder) {
    return new Gateway(
        DEFAULT_SHUTDOWN_TIMEOUT,
        gatewayCfg,
        Map.of(DEFAULT_PHYSICAL_TENANT_ID, securityConfiguration),
        brokerClient,
        actorSchedulingService,
        jobStreamer,
        authConfig -> jwtDecoder,
        authConfig -> (jwtClaims, tokenValue) -> jwtClaims,
        tenantId -> userServices,
        passwordEncoder,
        meterRegistry,
        VariableNameLengthValidator.DEFAULT_MAX_NAME_FIELD_LENGTH,
        PhysicalTenantIds.DEFAULT);
  }
}
