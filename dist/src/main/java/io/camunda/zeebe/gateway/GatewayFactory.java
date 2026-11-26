/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway;

import io.camunda.authentication.config.SaasTokenValidators;
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
   * adds the organization and cluster checks of the REST API.
   */
  public static Function<AuthenticationConfiguration, JwtDecoder> jwtDecoderFactory(
      final @Nullable ScopedJwtDecoderFactory scopedJwtDecoderFactory,
      final @Nullable CamundaSecurityLibraryProperties securityProperties) {
    return authConfig ->
        SaasTokenValidators.withSaasValidation(
            required(scopedJwtDecoderFactory, "ScopedJwtDecoderFactory")
                .buildIssuerAwareDecoder(authConfig),
            required(securityProperties, "CamundaSecurityLibraryProperties").getSaas());
  }

  private static <T> T required(final @Nullable T bean, final String name) {
    if (bean == null) {
      throw new IllegalStateException(
          "%s is not available, but the gateway needs it".formatted(name));
    }
    return bean;
  }
}
