/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest.security.ccsm;

import static io.camunda.optimize.OptimizeTomcatConfig.EXTERNAL_SUB_PATH;
import static io.camunda.optimize.rest.AuthenticationRestService.AUTHENTICATION_PATH;
import static io.camunda.optimize.rest.AuthenticationRestService.CALLBACK;
import static io.camunda.optimize.rest.AuthenticationRestService.LOGOUT;
import static io.camunda.optimize.rest.HealthRestService.READYZ_PATH;
import static io.camunda.optimize.rest.IngestionRestService.INGESTION_PATH;
import static io.camunda.optimize.rest.IngestionRestService.VARIABLE_SUB_PATH;
import static io.camunda.optimize.rest.LocalizationRestService.LOCALIZATION_PATH;
import static io.camunda.optimize.rest.UIConfigurationRestService.UI_CONFIGURATION_PATH;
import static io.camunda.optimize.tomcat.OptimizeResourceConstants.ACTUATOR_ENDPOINT;
import static io.camunda.optimize.tomcat.OptimizeResourceConstants.REST_API_PATH;
import static io.camunda.optimize.tomcat.OptimizeResourceConstants.STATIC_RESOURCE_PATH;

import io.camunda.optimize.rest.security.AbstractSecurityConfigurerAdapter;
import io.camunda.optimize.rest.security.CustomPreAuthenticatedAuthenticationProvider;
import io.camunda.optimize.rest.security.oauth.AudienceValidator;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.security.AuthCookieService;
import io.camunda.optimize.service.security.CCSMTokenService;
import io.camunda.optimize.service.security.SessionService;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.OptimizeApiConfiguration;
import io.camunda.optimize.service.util.configuration.condition.CCSMCondition;
import io.camunda.optimize.tomcat.CCSMRequestAdjustmentFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

@Configuration
@EnableWebSecurity
@Conditional(CCSMCondition.class)
public class CCSMSecurityConfigurerAdapter extends AbstractSecurityConfigurerAdapter {

  private static final Logger LOG = LoggerFactory.getLogger(CCSMSecurityConfigurerAdapter.class);

  private final CCSMTokenService ccsmTokenService;

  public CCSMSecurityConfigurerAdapter(
      final ConfigurationService configurationService,
      final CustomPreAuthenticatedAuthenticationProvider
          customPreAuthenticatedAuthenticationProvider,
      final SessionService sessionService,
      final AuthCookieService authCookieService,
      final CCSMTokenService ccsmTokenService) {
    super(
        configurationService,
        customPreAuthenticatedAuthenticationProvider,
        sessionService,
        authCookieService);
    this.ccsmTokenService = ccsmTokenService;
  }

  @Bean
  public FilterRegistrationBean registration(final CCSMAuthenticationCookieFilter filter) {
    final FilterRegistrationBean registration = new FilterRegistrationBean(filter);
    registration.setEnabled(false);
    return registration;
  }

  @Bean
  public CCSMAuthenticationCookieFilter ccsmAuthenticationCookieFilter(final HttpSecurity http)
      throws Exception {
    return new CCSMAuthenticationCookieFilter(
        configurationService,
        ccsmTokenService,
        http.getSharedObject(AuthenticationManagerBuilder.class)
            .authenticationProvider(preAuthenticatedAuthenticationProvider)
            .build());
  }

  @Bean
  FilterRegistrationBean<CCSMRequestAdjustmentFilter> requestAdjuster() {
    LOG.debug("Registering filter 'requestAdjuster' (CCSM)...");
    final FilterRegistrationBean<CCSMRequestAdjustmentFilter> registration =
        new FilterRegistrationBean<>();
    registration.setFilter(new CCSMRequestAdjustmentFilter());
    registration.addUrlPatterns("/*");
    return registration;
  }

  @Bean
  @Order(1)
  protected SecurityFilterChain configurePublicApi(final HttpSecurity http) {
    final HttpSecurity httpSecurityBuilder =
        http.securityMatchers(
            securityMatchers ->
                securityMatchers.requestMatchers(
                    PathPatternRequestMatcher.withDefaults().matcher(PUBLIC_API_PATH),
                    PathPatternRequestMatcher.withDefaults()
                        .matcher(createApiPath(INGESTION_PATH, VARIABLE_SUB_PATH))));
    return applyPublicApiOptions(httpSecurityBuilder);
  }

  @Bean
  @Order(2)
  protected SecurityFilterChain configureWebSecurity(final HttpSecurity http) {
    try {
      final var httpSecurity =
          super.configureGenericSecurityOptions(http)
              // Then we configure the specific web security for CCSM
              .authorizeHttpRequests(
                  requests ->
                      requests
                          // ready endpoint is public
                          .requestMatchers(
                              PathPatternRequestMatcher.withDefaults()
                                  .matcher(createApiPath(READYZ_PATH)))
                          .permitAll()
                          // Identity callback and logout are public — logout must work even
                          // when the session token has already expired
                          .requestMatchers(
                              PathPatternRequestMatcher.withDefaults()
                                  .matcher(createApiPath(AUTHENTICATION_PATH + CALLBACK)),
                              PathPatternRequestMatcher.withDefaults()
                                  .matcher(createApiPath(AUTHENTICATION_PATH + LOGOUT)))
                          .permitAll()
                          // Static resources
                          .requestMatchers(
                              PathPatternRequestMatcher.withDefaults().matcher("/*.ico"),
                              PathPatternRequestMatcher.withDefaults().matcher("/*.html"),
                              PathPatternRequestMatcher.withDefaults().matcher("/static/*.js"),
                              PathPatternRequestMatcher.withDefaults().matcher("/static/*.css"),
                              PathPatternRequestMatcher.withDefaults().matcher("/static/*.html"))
                          .permitAll()
                          // public share resources
                          .requestMatchers(
                              PathPatternRequestMatcher.withDefaults()
                                  .matcher(EXTERNAL_SUB_PATH + "/"),
                              PathPatternRequestMatcher.withDefaults()
                                  .matcher(EXTERNAL_SUB_PATH + "/index*"),
                              PathPatternRequestMatcher.withDefaults()
                                  .matcher(EXTERNAL_SUB_PATH + STATIC_RESOURCE_PATH + "/**"),
                              PathPatternRequestMatcher.withDefaults()
                                  .matcher(EXTERNAL_SUB_PATH + "/*.js"),
                              PathPatternRequestMatcher.withDefaults()
                                  .matcher(EXTERNAL_SUB_PATH + "/*.ico"))
                          .permitAll()
                          // public share related resources (API)
                          .requestMatchers(
                              PathPatternRequestMatcher.withDefaults()
                                  .matcher(createApiPath(EXTERNAL_SUB_PATH + DEEP_SUB_PATH_ANY)),
                              PathPatternRequestMatcher.withDefaults()
                                  .matcher(EXTERNAL_SUB_PATH + "/api/**"))
                          .permitAll()
                          // common public api resources
                          .requestMatchers(
                              PathPatternRequestMatcher.withDefaults()
                                  .matcher(createApiPath(UI_CONFIGURATION_PATH)),
                              PathPatternRequestMatcher.withDefaults()
                                  .matcher(createApiPath(LOCALIZATION_PATH)))
                          .permitAll()
                          .requestMatchers(
                              PathPatternRequestMatcher.withDefaults()
                                  .matcher(ACTUATOR_ENDPOINT + "/**"))
                          .permitAll()
                          .anyRequest()
                          .authenticated())
              // Register cookie filter before BearerTokenAuthenticationFilter (position 1100).
              // When jwtAuthForApiEnabled=true, oauth2ResourceServer adds
              // BearerTokenAuthenticationFilter at 1100 and the cookie filter sits just before it.
              // When jwtAuthForApiEnabled=false, no BearerTokenAuthenticationFilter instance is
              // registered, but Spring still uses BearerTokenAuthenticationFilter.class as the
              // anchor for ordering, so the cookie filter keeps this relative position.
              .addFilterBefore(
                  ccsmAuthenticationCookieFilter(http), BearerTokenAuthenticationFilter.class)
              .exceptionHandling(
                  exceptionHandling ->
                      exceptionHandling
                          .defaultAuthenticationEntryPointFor(
                              new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                              PathPatternRequestMatcher.withDefaults()
                                  .matcher(REST_API_PATH + "/**"))
                          .defaultAuthenticationEntryPointFor(
                              this::redirectToIdentity,
                              PathPatternRequestMatcher.withDefaults().matcher("/**")));

      // When the flag is on, enable Spring's native bearer-token support. This adds
      // BearerTokenAuthenticationFilter (order 1100) to the chain. The cookie filter registered
      // above still runs first at 1099, so it only acts as a no-op when no relevant auth cookies
      // are present (which is typical for M2M bearer-token clients). Spring's bearer-token filter
      // then handles auth and populates the SecurityContext before
      // AbstractPreAuthenticatedProcessingFilter (1500) can interfere.
      if (configurationService.getOptimizeApiConfiguration().isJwtAuthForApiEnabled()) {
        LOG.info(
            "JWT bearer-token authentication for /api/** is ENABLED "
                + "(api.jwtAuthForApiEnabled=true)");
        httpSecurity.oauth2ResourceServer(
            oauth2 -> oauth2.jwt(jwt -> jwt.decoder(publicApiJwtDecoder())));
      }

      return httpSecurity.build();
    } catch (final Exception e) {
      throw new OptimizeRuntimeException(e);
    }
  }

  private String getAudienceFromConfiguration() {
    return configurationService.getOptimizeApiConfiguration().getAudience();
  }

  @Bean
  @Override
  protected JwtDecoder publicApiJwtDecoder() {
    final OptimizeApiConfiguration apiConfig = configurationService.getOptimizeApiConfiguration();
    if (apiConfig.isJwtAuthForApiEnabled()) {
      final String jwtSetUri = apiConfig.getJwtSetUri();
      if (jwtSetUri == null || jwtSetUri.isEmpty()) {
        throw new IllegalStateException(
            "api.jwtAuthForApiEnabled is true but api.jwtSetUri is not configured");
      }
      return createJwtDecoderWithAudience(jwtSetUri);
    }
    return Optional.ofNullable(apiConfig.getJwtSetUri())
        .map(this::createJwtDecoderWithAudience)
        .orElseGet(() -> new OptimizeStaticTokenDecoder(configurationService));
  }

  @SuppressWarnings("unchecked")
  private JwtDecoder createJwtDecoderWithAudience(final String jwtSetUri) {
    final NimbusJwtDecoder jwtDecoder = NimbusJwtDecoder.withJwkSetUri(jwtSetUri).build();
    final OAuth2TokenValidator<Jwt> audienceValidator =
        new AudienceValidator(getAudienceFromConfiguration());
    // The default validator already contains validation for timestamp and X509 thumbprint
    final OAuth2TokenValidator<Jwt> combinedValidatorWithDefaults =
        JwtValidators.createDefaultWithValidators(audienceValidator);
    jwtDecoder.setJwtValidator(combinedValidatorWithDefaults);
    return jwtDecoder;
  }

  private void redirectToIdentity(
      final HttpServletRequest request,
      final HttpServletResponse response,
      final AuthenticationException e)
      throws IOException {
    final String authorizeUri =
        ccsmTokenService.buildAuthorizeUri(buildAuthorizeCallbackBaseUri(request)).toString();
    response.sendRedirect(authorizeUri);
  }

  private static String buildAuthorizeCallbackBaseUri(final HttpServletRequest req) {
    String redirectUri = req.getScheme() + "://" + req.getServerName();
    if ((req.getScheme().equals("http") && req.getServerPort() != 80)
        || (req.getScheme().equals("https") && req.getServerPort() != 443)) {
      redirectUri += ":" + req.getServerPort();
    }
    return redirectUri + req.getContextPath();
  }
}
