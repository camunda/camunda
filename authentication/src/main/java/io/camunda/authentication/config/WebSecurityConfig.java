/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.config;

import io.camunda.authentication.CamundaUserDetailsService;
import io.camunda.authentication.ConditionalOnAuthenticationMethod;
import io.camunda.authentication.ConditionalOnUnprotectedApi;
import io.camunda.authentication.filters.TenantRequestAttributeFilter;
import io.camunda.authentication.filters.WebApplicationAuthorizationCheckFilter;
import io.camunda.authentication.handler.AuthFailureHandler;
import io.camunda.authentication.handler.CustomMethodSecurityExpressionHandler;
import io.camunda.security.configuration.MultiTenancyConfiguration;
import io.camunda.security.configuration.SecurityConfiguration;
import io.camunda.security.entity.AuthenticationMethod;
import io.camunda.service.AuthorizationServices;
import io.camunda.service.RoleServices;
import io.camunda.service.TenantServices;
import io.camunda.service.UserServices;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@Profile("consolidated-auth")
public class WebSecurityConfig {
  public static final String SESSION_COOKIE = "camunda-session";

  private static final Logger LOG = LoggerFactory.getLogger(WebSecurityConfig.class);
  // Used for chains that grant unauthenticated access, always comes first.
  private static final int ORDER_UNPROTECTED = 0;
  // Used for chains that protect the APIs or Webapp paths.
  private static final int ORDER_WEBAPP_API = 1;
  // Intended for a "catch-all-unhandled"-chain protecting all resources by default
  private static final int ORDER_UNHANDLED = 2;
  private static final String LOGIN_URL = "/login";
  private static final String LOGOUT_URL = "/logout";
  private static final Set<String> API_PATHS = Set.of("/api/**", "/v1/**", "/v2/**");
  private static final Set<String> UNPROTECTED_API_PATHS =
      Set.of(
          // these v2 endpoints are public
          "/v2/license",
          // deprecated Tasklist v1 Public Endpoints
          "/v1/external/process/**");
  private static final Set<String> WEBAPP_PATHS =
      Set.of(
          "/sso-callback/**",
          "/login/**",
          "/logout",
          "/identity/**",
          "/operate/**",
          "/tasklist/**",
          "/");
  private static final Set<String> UNPROTECTED_PATHS =
      Set.of(
          // endpoint for failure forwarding
          "/error",
          // all actuator endpoints
          "/actuator/**",
          // endpoints defined in BrokerHealthRoutes
          "/ready",
          "/health",
          "/startup",
          // deprecated Tasklist v1 Public Endpoints
          "/new/**",
          "/favicon.ico");

  @Bean
  @ConditionalOnMissingBean(MethodSecurityExpressionHandler.class)
  public MethodSecurityExpressionHandler methodSecurityExpressionHandler(
      final AuthorizationServices authorizationServices) {
    return new CustomMethodSecurityExpressionHandler(authorizationServices);
  }

  @Bean
  @Order(ORDER_UNPROTECTED)
  public SecurityFilterChain unprotectedPathsSecurityFilterChain(final HttpSecurity httpSecurity)
      throws Exception {
    return httpSecurity
        .securityMatcher(UNPROTECTED_PATHS.toArray(String[]::new))
        .authorizeHttpRequests(
            (authorizeHttpRequests) -> authorizeHttpRequests.anyRequest().permitAll())
        .headers(WebSecurityConfig::setupStrictTransportSecurity)
        .csrf(AbstractHttpConfigurer::disable)
        .cors(AbstractHttpConfigurer::disable)
        .formLogin(AbstractHttpConfigurer::disable)
        .anonymous(AbstractHttpConfigurer::disable)
        .build();
  }

  @Bean
  @ConditionalOnUnprotectedApi
  @Order(ORDER_UNPROTECTED)
  public SecurityFilterChain unprotectedApiAuthSecurityFilterChain(final HttpSecurity httpSecurity)
      throws Exception {
    LOG.warn(
        "The API is unprotected. Please disable {} for any deployment.",
        AuthenticationProperties.API_UNPROTECTED);
    return httpSecurity
        .securityMatcher(API_PATHS.toArray(String[]::new))
        .authorizeHttpRequests(
            (authorizeHttpRequests) -> authorizeHttpRequests.anyRequest().permitAll())
        .headers(WebSecurityConfig::setupStrictTransportSecurity)
        .csrf(AbstractHttpConfigurer::disable)
        .cors(AbstractHttpConfigurer::disable)
        .formLogin(AbstractHttpConfigurer::disable)
        .anonymous(AbstractHttpConfigurer::disable)
        .build();
  }

  @Bean
  @Order(ORDER_UNHANDLED)
  public SecurityFilterChain protectedUnhandledPathsSecurityFilterChain(
      final HttpSecurity httpSecurity) throws Exception {
    // all resources not yet explicitly handled by any previous chain require an authenticated user
    // thus by default unhandled paths are always protected
    return httpSecurity
        .securityMatcher("/**")
        .authorizeHttpRequests(
            (authorizeHttpRequests) -> authorizeHttpRequests.anyRequest().denyAll())
        .build();
  }

  @Bean
  public FilterRegistrationBean<TenantRequestAttributeFilter>
      tenantRequestAttributeFilterRegistration(final MultiTenancyConfiguration configuration) {
    return new FilterRegistrationBean<>(new TenantRequestAttributeFilter(configuration));
  }

  @Bean
  public WebApplicationAuthorizationCheckFilter applicationAuthorizationFilterFilter(
      final SecurityConfiguration securityConfiguration) {
    return new WebApplicationAuthorizationCheckFilter(securityConfiguration);
  }

  private static void noContentSuccessHandler(
      final HttpServletRequest request,
      final HttpServletResponse response,
      final Authentication authentication) {
    response.setStatus(HttpStatus.NO_CONTENT.value());
  }

  private static void setupStrictTransportSecurity(final HeadersConfigurer<HttpSecurity> headers) {
    headers.httpStrictTransportSecurity(
        (httpStrictTransportSecurity) ->
            httpStrictTransportSecurity
                .includeSubDomains(true)
                .maxAgeInSeconds(63072000)
                .preload(true));
  }

  @Configuration
  @ConditionalOnAuthenticationMethod(AuthenticationMethod.BASIC)
  public static class BasicConfiguration {
    @Bean
    public CamundaUserDetailsService camundaUserDetailsService(
        final UserServices userServices,
        final AuthorizationServices authorizationServices,
        final RoleServices roleServices,
        final TenantServices tenantServices) {
      return new CamundaUserDetailsService(
          userServices, authorizationServices, roleServices, tenantServices);
    }

    @Bean
    @Order(ORDER_WEBAPP_API)
    public SecurityFilterChain httpBasicApiAuthSecurityFilterChain(
        final HttpSecurity httpSecurity, final AuthFailureHandler authFailureHandler)
        throws Exception {
      LOG.info("The API is protected by HTTP Basic authentication.");
      return httpSecurity
          .securityMatcher(API_PATHS.toArray(String[]::new))
          .authorizeHttpRequests(
              (authorizeHttpRequests) ->
                  authorizeHttpRequests
                      .requestMatchers(UNPROTECTED_API_PATHS.toArray(String[]::new))
                      .permitAll()
                      .anyRequest()
                      .authenticated())
          .headers(WebSecurityConfig::setupStrictTransportSecurity)
          .csrf(AbstractHttpConfigurer::disable)
          .cors(AbstractHttpConfigurer::disable)
          .formLogin(AbstractHttpConfigurer::disable)
          .anonymous(AbstractHttpConfigurer::disable)
          .httpBasic(Customizer.withDefaults())
          .exceptionHandling(
              // this prevents the usage of the default BasicAuthenticationEntryPoint returning
              // a WWW-Authenticate header that causes browsers to prompt for basic login
              exceptionHandling ->
                  exceptionHandling
                      .authenticationEntryPoint(authFailureHandler)
                      .accessDeniedHandler(authFailureHandler))
          // do not create a session on api authentication, that's to be done on webapp login only
          .sessionManagement(
              (sessionManagement) ->
                  sessionManagement.sessionCreationPolicy(SessionCreationPolicy.NEVER))
          .build();
    }

    @Bean
    @Order(ORDER_WEBAPP_API)
    public SecurityFilterChain httpBasicWebappAuthSecurityFilterChain(
        final HttpSecurity httpSecurity,
        final AuthFailureHandler authFailureHandler,
        final WebApplicationAuthorizationCheckFilter webApplicationAuthorizationCheckFilter)
        throws Exception {
      LOG.info("Web Applications Login/Logout is setup.");
      return httpSecurity
          .securityMatcher(WEBAPP_PATHS.toArray(String[]::new))
          // webapps are accessible without any authentication required
          .authorizeHttpRequests(
              (authorizeHttpRequests) -> authorizeHttpRequests.anyRequest().permitAll())
          .csrf(AbstractHttpConfigurer::disable)
          .cors(AbstractHttpConfigurer::disable)
          .anonymous(AbstractHttpConfigurer::disable)
          // http basic auth is possible to obtain a session
          .httpBasic(Customizer.withDefaults())
          // login/logout is still possible to obtain a session
          // the session grants access to the API as well, via #httpBasicApiAuthSecurityFilterChain
          .formLogin(
              formLogin ->
                  formLogin
                      .loginPage(LOGIN_URL)
                      .loginProcessingUrl(LOGIN_URL)
                      .failureHandler(authFailureHandler)
                      .successHandler(WebSecurityConfig::noContentSuccessHandler))
          .logout(
              (logout) ->
                  logout
                      .logoutUrl(LOGOUT_URL)
                      .logoutSuccessHandler(WebSecurityConfig::noContentSuccessHandler)
                      .deleteCookies(SESSION_COOKIE))
          .exceptionHandling(
              exceptionHandling ->
                  exceptionHandling
                      .authenticationEntryPoint(authFailureHandler)
                      .accessDeniedHandler(authFailureHandler))
          .addFilterAfter(webApplicationAuthorizationCheckFilter, AuthorizationFilter.class)
          .build();
    }
  }

  @Configuration
  @ConditionalOnAuthenticationMethod(AuthenticationMethod.OIDC)
  public static class OidcConfiguration {
    @Bean
    public ClientRegistrationRepository clientRegistrationRepository(
        final SecurityConfiguration securityConfiguration) {
      return new InMemoryClientRegistrationRepository(
          OidcClientRegistration.create(securityConfiguration.getAuthentication().getOidc()));
    }

    @Bean
    @Order(ORDER_WEBAPP_API)
    public SecurityFilterChain oidcHttpSecurity(
        final HttpSecurity httpSecurity,
        final AuthFailureHandler authFailureHandler,
        final ClientRegistrationRepository clientRegistrationRepository,
        final WebApplicationAuthorizationCheckFilter webApplicationAuthorizationCheckFilter)
        throws Exception {
      return httpSecurity
          .securityMatcher(API_PATHS.toArray(new String[0]))
          .securityMatcher(WEBAPP_PATHS.toArray(new String[0]))
          .authorizeHttpRequests(
              (authorizeHttpRequests) ->
                  authorizeHttpRequests
                      .requestMatchers(UNPROTECTED_API_PATHS.toArray(String[]::new))
                      .permitAll()
                      .anyRequest()
                      .authenticated())
          .headers(WebSecurityConfig::setupStrictTransportSecurity)
          .exceptionHandling(
              (exceptionHandling) -> exceptionHandling.accessDeniedHandler(authFailureHandler))
          .csrf(AbstractHttpConfigurer::disable)
          .cors(AbstractHttpConfigurer::disable)
          .formLogin(AbstractHttpConfigurer::disable)
          .anonymous(AbstractHttpConfigurer::disable)
          .oauth2ResourceServer(
              oauth2 ->
                  oauth2.jwt(
                      jwtConfigurer ->
                          jwtConfigurer.jwkSetUri(
                              clientRegistrationRepository
                                  .findByRegistrationId(OidcClientRegistration.REGISTRATION_ID)
                                  .getProviderDetails()
                                  .getJwkSetUri())))
          .oauth2Login(
              oauthLoginConfigurer -> {
                oauthLoginConfigurer
                    .clientRegistrationRepository(clientRegistrationRepository)
                    .redirectionEndpoint(
                        redirectionEndpointConfig ->
                            redirectionEndpointConfig.baseUri("/sso-callback"));
              })
          .oidcLogout(httpSecurityOidcLogoutConfigurer -> {})
          .logout(
              (logout) ->
                  logout
                      .logoutUrl(LOGOUT_URL)
                      .logoutSuccessHandler(WebSecurityConfig::noContentSuccessHandler)
                      .deleteCookies())
          .addFilterAfter(webApplicationAuthorizationCheckFilter, AuthorizationFilter.class)
          .build();
    }
  }
}
