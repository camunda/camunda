/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest.security.platform;

import static io.camunda.optimize.OptimizeJettyServerCustomizer.EXTERNAL_SUB_PATH;
import static io.camunda.optimize.jetty.OptimizeResourceConstants.INDEX_PAGE;
import static io.camunda.optimize.jetty.OptimizeResourceConstants.STATIC_RESOURCE_PATH;
import static io.camunda.optimize.rest.AuthenticationRestService.AUTHENTICATION_PATH;
import static io.camunda.optimize.rest.HealthRestService.READYZ_PATH;
import static io.camunda.optimize.rest.IngestionRestService.INGESTION_PATH;
import static io.camunda.optimize.rest.IngestionRestService.VARIABLE_SUB_PATH;
import static io.camunda.optimize.rest.LocalizationRestService.LOCALIZATION_PATH;
import static io.camunda.optimize.rest.UIConfigurationRestService.UI_CONFIGURATION_PATH;
import static org.springframework.http.HttpStatus.TEMPORARY_REDIRECT;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

import io.camunda.optimize.rest.security.AbstractSecurityConfigurerAdapter;
import io.camunda.optimize.rest.security.AuthenticationCookieFilter;
import io.camunda.optimize.rest.security.AuthenticationCookieRefreshFilter;
import io.camunda.optimize.rest.security.CustomPreAuthenticatedAuthenticationProvider;
import io.camunda.optimize.rest.security.oauth.AudienceValidator;
import io.camunda.optimize.service.security.AuthCookieService;
import io.camunda.optimize.service.security.SessionService;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.condition.CamundaPlatformCondition;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.NewCookie;
import jakarta.ws.rs.ext.RuntimeDelegate;
import java.util.Optional;
import lombok.SneakyThrows;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.preauth.AbstractPreAuthenticatedProcessingFilter;
import org.springframework.security.web.session.SessionManagementFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

@Configuration
@EnableWebSecurity
@Conditional(CamundaPlatformCondition.class)
public class PlatformSecurityConfigurerAdapter extends AbstractSecurityConfigurerAdapter {

  public static final String DEEP_SUB_PATH_ANY = "/**";
  private static final String CSV_SUFFIX = ".csv";
  private static final String SUB_PATH_ANY = "/*";
  private final AuthenticationCookieRefreshFilter authenticationCookieRefreshFilter;

  public PlatformSecurityConfigurerAdapter(
      final ConfigurationService configurationService,
      final CustomPreAuthenticatedAuthenticationProvider preAuthenticatedAuthenticationProvider,
      final SessionService sessionService,
      final AuthCookieService authCookieService,
      final AuthenticationCookieRefreshFilter authenticationCookieRefreshFilter) {
    super(
        configurationService,
        preAuthenticatedAuthenticationProvider,
        sessionService,
        authCookieService);
    this.authenticationCookieRefreshFilter = authenticationCookieRefreshFilter;
  }

  @Bean
  public AuthenticationCookieFilter authenticationCookieFilter(final HttpSecurity http)
      throws Exception {
    return new AuthenticationCookieFilter(
        sessionService,
        http.getSharedObject(AuthenticationManagerBuilder.class)
            .authenticationProvider(preAuthenticatedAuthenticationProvider)
            .build());
  }

  @SneakyThrows
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

  @SneakyThrows
  @Bean
  @Order(2)
  protected SecurityFilterChain configureWebSecurity(final HttpSecurity http) {
    return super.configureGenericSecurityOptions(http)
        // Then we configure the specific web security for CCSM
        .authorizeHttpRequests(
            requests ->
                requests
                    // static resources
                    .requestMatchers(
                        new AntPathRequestMatcher("/"),
                        new AntPathRequestMatcher("/index*"),
                        new AntPathRequestMatcher(STATIC_RESOURCE_PATH + "/**"),
                        new AntPathRequestMatcher("/*.js"),
                        new AntPathRequestMatcher("/*.ico"))
                    .permitAll()
                    // public resources
                    .requestMatchers(
                        new AntPathRequestMatcher(EXTERNAL_SUB_PATH + "/"),
                        new AntPathRequestMatcher(EXTERNAL_SUB_PATH + "/index*"),
                        new AntPathRequestMatcher(EXTERNAL_SUB_PATH + STATIC_RESOURCE_PATH + "/**"),
                        new AntPathRequestMatcher(EXTERNAL_SUB_PATH + "/*.js"),
                        new AntPathRequestMatcher(EXTERNAL_SUB_PATH + "/*.ico"))
                    .permitAll()
                    // public share related resources (API)
                    .requestMatchers(
                        new AntPathRequestMatcher(
                            createApiPath(EXTERNAL_SUB_PATH + DEEP_SUB_PATH_ANY)))
                    .permitAll()
                    // common public api resources
                    .requestMatchers(
                        new AntPathRequestMatcher(createApiPath(READYZ_PATH)),
                        new AntPathRequestMatcher(createApiPath(UI_CONFIGURATION_PATH)),
                        new AntPathRequestMatcher(createApiPath(LOCALIZATION_PATH)),
                        new AntPathRequestMatcher(createApiPath(AUTHENTICATION_PATH)))
                    .permitAll()
                    // everything else requires authentication
                    .anyRequest()
                    .authenticated())
        .addFilterBefore(
            authenticationCookieFilter(http), AbstractPreAuthenticatedProcessingFilter.class)
        .addFilterAfter(authenticationCookieRefreshFilter, SessionManagementFilter.class)
        .exceptionHandling(
            exceptionHandling -> exceptionHandling.authenticationEntryPoint(this::failureHandler))
        .build();
  }

  @Override
  protected JwtDecoder publicApiJwtDecoder() {
    return Optional.ofNullable(configurationService.getOptimizeApiConfiguration().getJwtSetUri())
        .map(this::createJwtDecoderWithAudience)
        .orElseGet(() -> new OptimizeStaticTokenDecoder(configurationService));
  }

  private JwtDecoder createJwtDecoderWithAudience(final String jwtSetUri) {
    final NimbusJwtDecoder jwtDecoder = NimbusJwtDecoder.withJwkSetUri(jwtSetUri).build();
    final OAuth2TokenValidator<Jwt> audienceValidator =
        new AudienceValidator(configurationService.getOptimizeApiConfiguration().getAudience());
    jwtDecoder.setJwtValidator(audienceValidator);
    return jwtDecoder;
  }

  private void failureHandler(
      final HttpServletRequest request,
      final HttpServletResponse response,
      final AuthenticationException ex) {
    if (isCSVRequest(request.getPathInfo())) {
      response.setStatus(TEMPORARY_REDIRECT.value());
      response.setHeader(HttpHeaders.LOCATION, INDEX_PAGE);
    } else {
      response.setStatus(UNAUTHORIZED.value());
      if (sessionService.isTokenPresent(request)) {
        // clear cookie
        response.addHeader(
            HttpHeaders.SET_COOKIE,
            RuntimeDelegate.getInstance()
                .createHeaderDelegate(NewCookie.class)
                .toString(authCookieService.createDeleteOptimizeAuthCookie(request.getScheme())));
      }
    }
  }

  private boolean isCSVRequest(final String path) {
    return path != null && path.endsWith(CSV_SUFFIX);
  }
}
