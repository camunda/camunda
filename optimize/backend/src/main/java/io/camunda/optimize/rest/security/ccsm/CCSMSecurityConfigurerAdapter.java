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
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.preauth.AbstractPreAuthenticatedProcessingFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

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
                    new AntPathRequestMatcher(PUBLIC_API_PATH),
                    new AntPathRequestMatcher(createApiPath(INGESTION_PATH, VARIABLE_SUB_PATH))));
    return applyPublicApiOptions(httpSecurityBuilder);
  }

  @Bean
  @Order(2)
  protected SecurityFilterChain configureWebSecurity(final HttpSecurity http) {
    try {
      return super.configureGenericSecurityOptions(http)
          // Then we configure the specific web security for CCSM
          .authorizeHttpRequests(
              requests ->
                  requests
                      // ready endpoint is public
                      .requestMatchers(new AntPathRequestMatcher(createApiPath(READYZ_PATH)))
                      .permitAll()
                      // Identity callback request handling is public
                      .requestMatchers(
                          new AntPathRequestMatcher(createApiPath(AUTHENTICATION_PATH + CALLBACK)))
                      .permitAll()
                      // Static resources
                      .requestMatchers(
                          new AntPathRequestMatcher("/*.ico"),
                          new AntPathRequestMatcher("/*.html"),
                          new AntPathRequestMatcher("/static/*.js"),
                          new AntPathRequestMatcher("/static/*.css"),
                          new AntPathRequestMatcher("/static/*.html"))
                      .permitAll()
                      // public share resources
                      .requestMatchers(
                          new AntPathRequestMatcher(EXTERNAL_SUB_PATH + "/"),
                          new AntPathRequestMatcher(EXTERNAL_SUB_PATH + "/index*"),
                          new AntPathRequestMatcher(
                              EXTERNAL_SUB_PATH + STATIC_RESOURCE_PATH + "/**"),
                          new AntPathRequestMatcher(EXTERNAL_SUB_PATH + "/*.js"),
                          new AntPathRequestMatcher(EXTERNAL_SUB_PATH + "/*.ico"))
                      .permitAll()
                      // public share related resources (API)
                      .requestMatchers(
                          new AntPathRequestMatcher(
                              createApiPath(EXTERNAL_SUB_PATH + DEEP_SUB_PATH_ANY)),
                          new AntPathRequestMatcher(EXTERNAL_SUB_PATH + "/api/**"))
                      .permitAll()
                      // common public api resources
                      .requestMatchers(
                          new AntPathRequestMatcher(createApiPath(UI_CONFIGURATION_PATH)),
                          new AntPathRequestMatcher(createApiPath(LOCALIZATION_PATH)))
                      .permitAll()
                      .requestMatchers(new AntPathRequestMatcher(ACTUATOR_ENDPOINT + "/**"))
                      .permitAll()
                      .anyRequest()
                      .authenticated())
          .addFilterBefore(
              ccsmAuthenticationCookieFilter(http), AbstractPreAuthenticatedProcessingFilter.class)
          .exceptionHandling(
              exceptionHandling ->
                  exceptionHandling
                      .defaultAuthenticationEntryPointFor(
                          new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                          new AntPathRequestMatcher(REST_API_PATH + "/**"))
                      .defaultAuthenticationEntryPointFor(
                          this::redirectToIdentity, new AntPathRequestMatcher("/**")))
          .build();
    } catch (final Exception e) {
      throw new OptimizeRuntimeException(e);
    }
  }

  private String getAudienceFromConfiguration() {
    return configurationService.getOptimizeApiConfiguration().getAudience();
  }

  @Override
  protected JwtDecoder publicApiJwtDecoder() {
    return Optional.ofNullable(configurationService.getOptimizeApiConfiguration().getJwtSetUri())
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
