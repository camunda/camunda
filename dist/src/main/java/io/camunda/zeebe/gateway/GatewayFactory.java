/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway;

import io.camunda.application.commons.security.EngineSecurityConfigFactory;
import io.camunda.configuration.physicaltenants.PhysicalTenantResolver;
import io.camunda.security.api.context.OidcClaimsProvider;
import io.camunda.security.api.model.config.AuthenticationConfiguration;
import io.camunda.security.spring.oidc.ScopedJwtDecoderFactory;
import io.camunda.security.spring.oidc.ScopedOidcClaimsProviderFactory;
import io.camunda.service.UserServices;
import io.camunda.service.registry.ServiceRegistry;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.gateway.impl.configuration.GatewayCfg;
import io.camunda.zeebe.protocol.impl.stream.job.JobActivationProperties;
import io.camunda.zeebe.scheduler.ActorScheduler;
import io.camunda.zeebe.transport.stream.api.ClientStreamer;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import java.time.Duration;
import java.util.function.Function;
import org.agrona.CloseHelper;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;

/**
 * Builds the {@link Gateway} for both deployments: standalone ({@code GatewayModuleConfiguration})
 * and next to the broker ({@code BrokerEmbeddedGatewayConfiguration}). The caller owns the
 * lifecycle: it starts and closes the returned gateway.
 */
@NullMarked
public final class GatewayFactory {

  private GatewayFactory() {}

  /**
   * The security and service beans are only available when the matching features are active, so
   * they are looked up lazily and fail with a clear message when used without being available.
   */
  public static Gateway create(
      final Duration shutdownTimeout,
      final GatewayCfg gatewayCfg,
      final PhysicalTenantResolver physicalTenantResolver,
      final BrokerClient brokerClient,
      final ActorScheduler actorScheduler,
      final ClientStreamer<JobActivationProperties> jobStreamer,
      final PasswordEncoder passwordEncoder,
      final MeterRegistry meterRegistry,
      final int maxVariableNameLength,
      final @Nullable ServiceRegistry serviceRegistry,
      final @Nullable ScopedJwtDecoderFactory scopedJwtDecoderFactory,
      final @Nullable ScopedOidcClaimsProviderFactory scopedOidcClaimsProviderFactory,
      final @Nullable ObservationRegistry observationRegistry) {
    final Function<AuthenticationConfiguration, JwtDecoder> jwtDecoderFactory =
        authConfig ->
            required(scopedJwtDecoderFactory, "ScopedJwtDecoderFactory")
                .buildIssuerAwareDecoder(authConfig);
    final Function<AuthenticationConfiguration, OidcClaimsProvider> oidcClaimsProviderFactory =
        authConfig ->
            required(scopedOidcClaimsProviderFactory, "ScopedOidcClaimsProviderFactory")
                .buildClaimsProvider(authConfig);
    final Function<String, UserServices> userServicesForTenant =
        tenantId -> required(serviceRegistry, "ServiceRegistry").userServices(tenantId);

    return new Gateway(
        shutdownTimeout,
        gatewayCfg,
        physicalTenantResolver.mapValues(
            camunda -> EngineSecurityConfigFactory.create(camunda.getSecurity())),
        brokerClient,
        actorScheduler,
        jobStreamer,
        jwtDecoderFactory,
        oidcClaimsProviderFactory,
        userServicesForTenant,
        passwordEncoder,
        meterRegistry,
        maxVariableNameLength,
        physicalTenantResolver,
        observationRegistry != null ? observationRegistry : ObservationRegistry.NOOP);
  }

  /**
   * Runs the start steps and, if one fails, closes the resources created for them before
   * rethrowing. A bean whose creation fails is never registered with Spring, so its destroy method
   * does not run and nothing else would close them. {@code ActorFuture.join} can throw checked
   * exceptions as well, which are rethrown unchanged.
   */
  public static void startOrClose(final AutoCloseable resources, final Runnable startSteps) {
    try {
      startSteps.run();
    } catch (final Exception e) {
      CloseHelper.quietClose(resources);
      throw e;
    }
  }

  private static <T> T required(final @Nullable T bean, final String name) {
    if (bean == null) {
      throw new IllegalStateException(
          "%s is not available, but the gateway needs it".formatted(name));
    }
    return bean;
  }
}
