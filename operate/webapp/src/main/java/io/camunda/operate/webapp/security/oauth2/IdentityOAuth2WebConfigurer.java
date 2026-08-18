/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.security.oauth2;

import static com.nimbusds.jose.JOSEObjectType.JWT;
import static io.camunda.operate.OperateProfileService.IDENTITY_AUTH_PROFILE;
import static io.camunda.operate.webapp.security.BaseWebConfigurer.sendJSONErrorMessage;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.JWKSourceBuilder;
import com.nimbusds.jose.proc.DefaultJOSEObjectTypeVerifier;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jose.util.DefaultResourceRetriever;
import com.nimbusds.jwt.proc.ConfigurableJWTProcessor;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import io.camunda.identity.sdk.IdentityConfiguration;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;

@Component
@Profile(IDENTITY_AUTH_PROFILE)
public class IdentityOAuth2WebConfigurer {

  public static final String SPRING_SECURITY_OAUTH_2_RESOURCESERVER_JWT_ISSUER_URI =
      "spring.security.oauth2.resourceserver.jwt.issuer-uri";
  // Where to find the public key to validate signature,
  // which was created from authorization server's private key
  public static final String SPRING_SECURITY_OAUTH_2_RESOURCESERVER_JWT_JWK_SET_URI =
      "spring.security.oauth2.resourceserver.jwt.jwk-set-uri";

  public static final String JWKS_PATH = "/protocol/openid-connect/certs";

  private static final Logger LOGGER = LoggerFactory.getLogger(IdentityOAuth2WebConfigurer.class);

  private final Environment env;

  private final IdentityConfiguration identityConfiguration;

  private final IdentityJwt2AuthenticationTokenConverter jwtConverter;

  public IdentityOAuth2WebConfigurer(
      final Environment env,
      final IdentityConfiguration identityConfiguration,
      final IdentityJwt2AuthenticationTokenConverter jwtConverter) {
    this.env = env;
    this.identityConfiguration = identityConfiguration;
    this.jwtConverter = jwtConverter;
  }

  public void configure(final HttpSecurity http) throws Exception {
    if (isJWTEnabled()) {
      http.oauth2ResourceServer(
          serverCustomizer ->
              serverCustomizer
                  .authenticationEntryPoint(this::authenticationFailure)
                  .jwt(
                      jwtCustomizer ->
                          jwtCustomizer
                              .jwtAuthenticationConverter(jwtConverter)
                              .decoder(jwtDecoder())));
      LOGGER.info("Enabled OAuth2 JWT access to Operate API");
    }
  }

  // No timeout is applied at all today (Spring's default RestTemplate has none); this bounds
  // the connect/read phases to a realistic external IdP latency instead of hanging indefinitely.
  private static final int JWK_SOURCE_HTTP_CONNECT_TIMEOUT_MS = 2000;
  private static final int JWK_SOURCE_HTTP_READ_TIMEOUT_MS = 2000;

  // Nimbus's own default is 15,000ms; this still gives one full retry's worth of slack (roughly
  // 2x the connect+read timeout above) while failing much faster when the IdP is genuinely down.
  private static final long JWK_SOURCE_CACHE_REFRESH_TIMEOUT_MS = 5000;

  private static final Set<JWSAlgorithm> SUPPORTED_JWS_ALGORITHMS =
      Set.of(
          JWSAlgorithm.RS256,
          JWSAlgorithm.RS384,
          JWSAlgorithm.RS512,
          JWSAlgorithm.ES256,
          JWSAlgorithm.ES384,
          JWSAlgorithm.ES512);

  /**
   * JwtDecoder that supports both the "jwt" (standard JWT) and "at+jwt" (Access Token JWT) JOSE
   * types for token validation.
   */
  private JwtDecoder jwtDecoder() {
    final JWKSource<SecurityContext> jwkSource = createJwkSource();
    final ConfigurableJWTProcessor<SecurityContext> jwtProcessor = new DefaultJWTProcessor<>();
    jwtProcessor.setJWSTypeVerifier(
        new DefaultJOSEObjectTypeVerifier<>(JWT, new JOSEObjectType("at+jwt"), null));
    jwtProcessor.setJWSKeySelector(
        new JWSVerificationKeySelector<>(SUPPORTED_JWS_ALGORITHMS, jwkSource));
    // Spring Security validates the claim set independently of Nimbus (see
    // NimbusJwtDecoder.JwkSetUriJwtDecoderBuilder#processor for the equivalent).
    jwtProcessor.setJWTClaimsSetVerifier((claims, context) -> {});
    return new NimbusJwtDecoder(jwtProcessor);
  }

  private JWKSource<SecurityContext> createJwkSource() {
    final URL jwkSetUri = toURL(getJwkSetUriProperty());
    final DefaultResourceRetriever retriever =
        new DefaultResourceRetriever(
            JWK_SOURCE_HTTP_CONNECT_TIMEOUT_MS,
            JWK_SOURCE_HTTP_READ_TIMEOUT_MS,
            JWKSourceBuilder.DEFAULT_HTTP_SIZE_LIMIT);
    // refreshAheadCache(true): refresh happens ahead of expiry, off the request path, so
    // concurrent requests are served the still-valid cached keys instead of blocking on a
    // synchronous refetch. outageTolerant is deliberately left at Nimbus's default (false): a
    // cache that is genuinely expired with no successful refresh still fails closed.
    // rateLimited(false): matches what the Spring-internal builder this replaces already set
    // (Nimbus's own default for this flag is true — omitting the call would silently enable it).
    return JWKSourceBuilder.create(jwkSetUri, retriever)
        .refreshAheadCache(true)
        .rateLimited(false)
        .cache(JWKSourceBuilder.DEFAULT_CACHE_TIME_TO_LIVE, JWK_SOURCE_CACHE_REFRESH_TIMEOUT_MS)
        .build();
  }

  private URL toURL(final String jwkSetUri) {
    try {
      return URI.create(jwkSetUri).toURL();
    } catch (final MalformedURLException | IllegalArgumentException e) {
      throw new IllegalArgumentException(
          "Invalid JWK Set URI '%s': %s".formatted(jwkSetUri, e.getMessage()), e);
    }
  }

  private String getJwkSetUriProperty() {
    final String backendUri;

    // If the SPRING_SECURITY_OAUTH_2_RESOURCESERVER_JWT_JWK_SET_URI is present, then it has already
    // been correctly
    // calculated and should be used as-is.
    if (env.containsProperty(SPRING_SECURITY_OAUTH_2_RESOURCESERVER_JWT_JWK_SET_URI)) {
      backendUri = env.getProperty(SPRING_SECURITY_OAUTH_2_RESOURCESERVER_JWT_JWK_SET_URI);
      LOGGER.info(
          "Using value in SPRING_SECURITY_OAUTH_2_RESOURCESERVER_JWT_JWK_SET_URI for issuer authentication");
    } else {
      backendUri = identityConfiguration.getIssuerBackendUrl() + JWKS_PATH;
      LOGGER.warn(
          "SPRING_SECURITY_OAUTH_2_RESOURCESERVER_JWT_JWK_SET_URI is not present, building issuer authentication uri from issuer backend url.");
    }

    LOGGER.info("Using {} for issuer authentication", backendUri);

    return backendUri;
  }

  private void authenticationFailure(
      final HttpServletRequest request,
      final HttpServletResponse response,
      final AuthenticationException e)
      throws IOException {
    sendJSONErrorMessage(response, e.getMessage());
  }

  protected boolean isJWTEnabled() {
    return env.containsProperty(SPRING_SECURITY_OAUTH_2_RESOURCESERVER_JWT_ISSUER_URI)
        || env.containsProperty(SPRING_SECURITY_OAUTH_2_RESOURCESERVER_JWT_JWK_SET_URI);
  }
}
