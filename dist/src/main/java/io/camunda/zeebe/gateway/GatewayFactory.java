/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway;

<<<<<<< HEAD
import io.camunda.authentication.config.SaasTokenValidators;
=======
import io.camunda.application.commons.security.EngineSecurityConfigFactory;
import io.camunda.authentication.config.SaasTokenValidators;
import io.camunda.configuration.physicaltenants.PhysicalTenantResolver;
import io.camunda.security.api.context.OidcClaimsProvider;
>>>>>>> dfc0b495 (fix: check SaaS organization and cluster claims on gRPC tokens)
import io.camunda.security.api.model.config.AuthenticationConfiguration;
import io.camunda.security.spring.CamundaSecurityLibraryProperties;
import io.camunda.security.spring.oidc.ScopedJwtDecoderFactory;
import java.util.function.Function;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.security.oauth2.jwt.JwtDecoder;

/** Builds the gRPC access-token decoders for the standalone gateway and the broker. */
@NullMarked
public final class GatewayFactory {

  private GatewayFactory() {}

  /**
   * Builds the gRPC access-token decoders. The CSL scoped decoder checks no SaaS claims, so this
   * adds the organization and cluster checks of the REST API. Build every gRPC decoder through this
   * method, not through {@link ScopedJwtDecoderFactory} directly.
   */
  public static Function<AuthenticationConfiguration, JwtDecoder> jwtDecoderFactory(
      final @Nullable ScopedJwtDecoderFactory scopedJwtDecoderFactory,
<<<<<<< HEAD
=======
      final @Nullable CamundaSecurityLibraryProperties securityProperties,
      final @Nullable ScopedOidcClaimsProviderFactory scopedOidcClaimsProviderFactory,
      final @Nullable ObservationRegistry observationRegistry) {
    final Function<AuthenticationConfiguration, JwtDecoder> jwtDecoderFactory =
        jwtDecoderFactory(scopedJwtDecoderFactory, securityProperties);
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
   * Builds the gRPC access-token decoders. The CSL scoped decoder checks no SaaS claims, so this
   * adds the organization and cluster checks of the REST API.
   */
  public static Function<AuthenticationConfiguration, JwtDecoder> jwtDecoderFactory(
      final @Nullable ScopedJwtDecoderFactory scopedJwtDecoderFactory,
>>>>>>> dfc0b495 (fix: check SaaS organization and cluster claims on gRPC tokens)
      final @Nullable CamundaSecurityLibraryProperties securityProperties) {
    return authConfig ->
        SaasTokenValidators.withSaasValidation(
            required(scopedJwtDecoderFactory, "ScopedJwtDecoderFactory")
                .buildIssuerAwareDecoder(authConfig),
            required(securityProperties, "CamundaSecurityLibraryProperties").getSaas());
<<<<<<< HEAD
=======
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
>>>>>>> dfc0b495 (fix: check SaaS organization and cluster claims on gRPC tokens)
  }

  private static <T> T required(final @Nullable T bean, final String name) {
    if (bean == null) {
      throw new IllegalStateException(
          "%s is not available, but the gateway needs it".formatted(name));
    }
    return bean;
  }
}
