/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.config;

import static com.nimbusds.jose.JOSEObjectType.JWT;
import static io.camunda.authentication.config.WebSecurityConfig.AT_JWT;
import static io.camunda.authentication.config.WebSecurityConfig.OidcConfiguration.getTokenValidator;
import static org.springframework.security.oauth2.jose.jws.SignatureAlgorithm.ES256;
import static org.springframework.security.oauth2.jose.jws.SignatureAlgorithm.ES384;
import static org.springframework.security.oauth2.jose.jws.SignatureAlgorithm.ES512;
import static org.springframework.security.oauth2.jose.jws.SignatureAlgorithm.RS256;
import static org.springframework.security.oauth2.jose.jws.SignatureAlgorithm.RS384;
import static org.springframework.security.oauth2.jose.jws.SignatureAlgorithm.RS512;

import com.nimbusds.jose.proc.DefaultJOSEObjectTypeVerifier;
import io.camunda.security.configuration.SecurityConfiguration;
import java.util.List;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

public class JwtDecoderFactory {

  private final ClientRegistrationRepository clientRegistrationRepository;
  private final SecurityConfiguration securityConfiguration;

  public JwtDecoderFactory(
      final ClientRegistrationRepository clientRegistrationRepository,
      final SecurityConfiguration securityConfiguration) {
    this.clientRegistrationRepository = clientRegistrationRepository;
    this.securityConfiguration = securityConfiguration;
  }

  public JwtDecoder createDecoder(final String registrationId) {
    final var configuration =
        securityConfiguration.getAuthentication().getOidc().get(registrationId);
    // Do not rely on the configured uri, the client registration can automatically discover it
    // based on the issuer uri.
    final var jwkSetUri =
        clientRegistrationRepository
            .findByRegistrationId(registrationId)
            .getProviderDetails()
            .getJwkSetUri();

    final var decoder =
        NimbusJwtDecoder.withJwkSetUri(jwkSetUri)
            .jwsAlgorithms(
                algorithms -> algorithms.addAll(List.of(RS256, RS384, RS512, ES256, ES384, ES512)))
            .jwtProcessorCustomizer(
                // the default implementation supports only JOSEObjectType.JWT and null
                processor -> {
                  processor.setJWTClaimsSetAwareJWSKeySelector(new TenantJWSKeySelector(null));
                  processor.setJWSTypeVerifier(
                      new DefaultJOSEObjectTypeVerifier<>(JWT, AT_JWT, null));
                })
            .build();
    decoder.setJwtValidator(getTokenValidator(securityConfiguration));
    return decoder;
  }
}
