/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.config;

import static io.camunda.security.configuration.headers.ContentSecurityPolicyConfig.DEFAULT_SAAS_SECURITY_POLICY;
import static io.camunda.security.configuration.headers.ContentSecurityPolicyConfig.DEFAULT_SM_SECURITY_POLICY;

import io.camunda.authentication.CamundaUserDetailsService;
import io.camunda.authentication.ConditionalOnAuthenticationMethod;
import io.camunda.authentication.ConditionalOnProtectedApi;
import io.camunda.authentication.ConditionalOnUnprotectedApi;
import io.camunda.authentication.filters.AdminUserCheckFilter;
import io.camunda.authentication.handler.AuthFailureHandler;
import io.camunda.security.configuration.SecurityConfiguration;
import io.camunda.security.configuration.headers.HeaderConfiguration;
import io.camunda.security.configuration.headers.values.FrameOptionMode;
import io.camunda.security.entity.AuthenticationMethod;
import io.camunda.service.RoleServices;
import io.camunda.service.UserServices;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.LinkedList;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer.CacheControlConfig;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer.ContentTypeOptionsConfig;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer.FrameOptionsConfig;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer.HstsConfig;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.oauth2.client.oidc.authentication.OidcIdTokenDecoderFactory;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoderFactory;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.header.writers.CrossOriginEmbedderPolicyHeaderWriter.CrossOriginEmbedderPolicy;
import org.springframework.security.web.header.writers.CrossOriginOpenerPolicyHeaderWriter.CrossOriginOpenerPolicy;
import org.springframework.security.web.header.writers.CrossOriginResourcePolicyHeaderWriter.CrossOriginResourcePolicy;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;

@Configuration
@EnableWebSecurity
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
          "/v2/setup/user",
          // deprecated Tasklist v1 Public Endpoints
          "/v1/external/process/**");
  private static final Set<String> WEBAPP_PATHS =
      Set.of(
          "/login/**",
          "/logout",
          "/identity/**",
          "/operate/**",
          "/tasklist/**",
          "/",
          "/sso-callback/**",
          "/oauth2/authorization/**",
          // old Tasklist and Operate webapps routes
          "/processes",
          "/processes/*",
          "/{regex:[\\d]+}", // user task id
          "/processes/*/start",
          "/new/*",
          "/decisions",
          "/decisions/*",
          "/instances",
          "/instances/*");
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
          // swagger-ui endpoint
          "/swagger-ui/**",
          "/v3/api-docs/**",
          "/rest-api.yaml",
          // deprecated Tasklist v1 Public Endpoints
          "/new/**",
          "/favicon.ico");

  @Bean
  @Order(ORDER_UNPROTECTED)
  public SecurityFilterChain unprotectedPathsSecurityFilterChain(
      final HttpSecurity httpSecurity, final SecurityConfiguration securityConfiguration)
      throws Exception {
    return httpSecurity
        .securityMatcher(UNPROTECTED_PATHS.toArray(String[]::new))
        .authorizeHttpRequests(
            (authorizeHttpRequests) -> authorizeHttpRequests.anyRequest().permitAll())
        .headers(
            headers ->
                setupSecureHeaders(
                    headers,
                    securityConfiguration.getHttpHeaders(),
                    securityConfiguration.getSaas().isConfigured()))
        .csrf(AbstractHttpConfigurer::disable)
        .cors(AbstractHttpConfigurer::disable)
        .formLogin(AbstractHttpConfigurer::disable)
        .anonymous(AbstractHttpConfigurer::disable)
        .build();
  }

  @Bean
  @ConditionalOnUnprotectedApi
  @Order(ORDER_UNPROTECTED)
  public SecurityFilterChain unprotectedApiAuthSecurityFilterChain(
      final HttpSecurity httpSecurity, final SecurityConfiguration securityConfiguration)
      throws Exception {
    LOG.warn(
        "The API is unprotected. Please disable {} for any deployment.",
        AuthenticationProperties.API_UNPROTECTED);
    return httpSecurity
        .securityMatcher(API_PATHS.toArray(String[]::new))
        .authorizeHttpRequests(
            (authorizeHttpRequests) -> authorizeHttpRequests.anyRequest().permitAll())
        .headers(
            headers ->
                setupSecureHeaders(
                    headers,
                    securityConfiguration.getHttpHeaders(),
                    securityConfiguration.getSaas().isConfigured()))
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

  //  @Bean
  //  public WebApplicationAuthorizationCheckFilter applicationAuthorizationFilterFilter(
  //      final SecurityConfiguration securityConfiguration) {
  //    return new WebApplicationAuthorizationCheckFilter(securityConfiguration);
  //  }

  private static void noContentSuccessHandler(
      final HttpServletRequest request,
      final HttpServletResponse response,
      final Authentication authentication) {
    response.setStatus(HttpStatus.NO_CONTENT.value());
  }

  private static void setupSecureHeaders(
      final HeadersConfigurer<HttpSecurity> headers,
      final HeaderConfiguration headerConfig,
      final boolean isSaas) {

    if (headerConfig.getContentTypeOptions().isDisabled()) {
      headers.contentTypeOptions(ContentTypeOptionsConfig::disable);
    }

    if (headerConfig.getCacheControl().isDisabled()) {
      headers.cacheControl(CacheControlConfig::disable);
    }

    if (headerConfig.getHsts().isDisabled()) {
      headers.httpStrictTransportSecurity(HstsConfig::disable);
    } else {
      headers.httpStrictTransportSecurity(
          hsts ->
              hsts.includeSubDomains(headerConfig.getHsts().isIncludeSubDomains())
                  .maxAgeInSeconds(headerConfig.getHsts().getMaxAgeInSeconds())
                  .preload(headerConfig.getHsts().isPreload()));
    }

    if (headerConfig.getFrameOptions().disabled()) {
      headers.frameOptions(FrameOptionsConfig::disable);
    } else {
      if (headerConfig.getFrameOptions().getMode() == FrameOptionMode.DENY) {
        headers.frameOptions(FrameOptionsConfig::deny);
      }
      if (headerConfig.getFrameOptions().getMode() == FrameOptionMode.SAMEORIGIN) {
        headers.frameOptions(FrameOptionsConfig::sameOrigin);
      }
    }

    if (headerConfig.getContentSecurityPolicy().isEnabled()) {
      final String policy = getContentSecurityPolicy(headerConfig, isSaas);
      headers.contentSecurityPolicy(csp -> csp.policyDirectives(policy));
      if (headerConfig.getContentSecurityPolicy().isReportOnly()) {
        headers.contentSecurityPolicy(csp -> csp.reportOnly().policyDirectives(policy));
      }
    }

    headers.referrerPolicy(
        rp ->
            rp.policy(ReferrerPolicy.valueOf(headerConfig.getReferrerPolicy().getValue().name())));

    if (headerConfig.getPermissionsPolicy().getValue() != null
        && !headerConfig.getPermissionsPolicy().getValue().isBlank()) {
      headers.permissionsPolicyHeader(
          pp -> pp.policy(headerConfig.getPermissionsPolicy().getValue()));
    }

    headers.crossOriginOpenerPolicy(
        coop ->
            coop.policy(
                CrossOriginOpenerPolicy.valueOf(
                    headerConfig.getCrossOriginOpenerPolicy().getValue().name())));

    headers.crossOriginEmbedderPolicy(
        coep ->
            coep.policy(
                CrossOriginEmbedderPolicy.valueOf(
                    headerConfig.getCrossOriginEmbedderPolicy().getValue().name())));

    headers.crossOriginResourcePolicy(
        corp ->
            corp.policy(
                CrossOriginResourcePolicy.valueOf(
                    headerConfig.getCrossOriginResourcePolicy().getValue().name())));
  }

  private static String getContentSecurityPolicy(
      final HeaderConfiguration headerConfig, final boolean isSaas) {
    final String policy;
    if (headerConfig.getContentSecurityPolicy().getPolicyDirectives() == null
        || headerConfig.getContentSecurityPolicy().getPolicyDirectives().isEmpty()) {
      if (isSaas) {
        policy = DEFAULT_SAAS_SECURITY_POLICY;
      } else {
        policy = DEFAULT_SM_SECURITY_POLICY;
      }
    } else {
      policy = headerConfig.getContentSecurityPolicy().getPolicyDirectives();
    }
    return policy;
  }

  @Configuration
  @ConditionalOnAuthenticationMethod(AuthenticationMethod.BASIC)
  public static class BasicConfiguration {
    @Bean
    @ConditionalOnMissingBean(UserDetailsService.class)
    public CamundaUserDetailsService camundaUserDetailsService(final UserServices userServices) {
      return new CamundaUserDetailsService(userServices);
    }

    @Bean
    @Order(ORDER_WEBAPP_API)
    @ConditionalOnProtectedApi
    public SecurityFilterChain httpBasicApiAuthSecurityFilterChain(
        final HttpSecurity httpSecurity,
        final AuthFailureHandler authFailureHandler,
        final SecurityConfiguration securityConfiguration)
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
          .headers(
              headers ->
                  setupSecureHeaders(
                      headers,
                      securityConfiguration.getHttpHeaders(),
                      securityConfiguration.getSaas().isConfigured()))
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
        //        final WebApplicationAuthorizationCheckFilter
        // webApplicationAuthorizationCheckFilter,
        final SecurityConfiguration securityConfiguration,
        final RoleServices roleServices)
        throws Exception {
      LOG.info("Web Applications Login/Logout is setup.");
      return httpSecurity
          .securityMatcher(WEBAPP_PATHS.toArray(String[]::new))
          // webapps are accessible without any authentication required
          .authorizeHttpRequests(
              (authorizeHttpRequests) -> authorizeHttpRequests.anyRequest().permitAll())
          .headers(
              headers ->
                  setupSecureHeaders(
                      headers,
                      securityConfiguration.getHttpHeaders(),
                      securityConfiguration.getSaas().isConfigured()))
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
          //          .addFilterAfter(webApplicationAuthorizationCheckFilter,
          // AuthorizationFilter.class)
          .addFilterBefore(
              new AdminUserCheckFilter(securityConfiguration, roleServices),
              AuthorizationFilter.class)
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
    public JwtDecoderFactory<ClientRegistration> idTokenDecoderFactory(
        final SecurityConfiguration securityConfiguration) {
      final var decoderFactory = new OidcIdTokenDecoderFactory();
      decoderFactory.setJwtValidatorFactory(
          registration -> getTokenValidator(securityConfiguration));
      return decoderFactory;
    }

    @Bean
    public JwtDecoder jwtDecoder(
        final SecurityConfiguration securityConfiguration,
        final ClientRegistrationRepository clientRegistrationRepository) {
      // Do not rely on the configured uri, the client registration can automatically discover it
      // based on the issuer uri.
      final var jwkSetUri =
          clientRegistrationRepository
              .findByRegistrationId(OidcClientRegistration.REGISTRATION_ID)
              .getProviderDetails()
              .getJwkSetUri();

      final var decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
      decoder.setJwtValidator(getTokenValidator(securityConfiguration));
      return decoder;
    }

    private static OAuth2TokenValidator<Jwt> getTokenValidator(
        final SecurityConfiguration configuration) {
      final var validAudiences = configuration.getAuthentication().getOidc().getAudiences();
      final var validators = new LinkedList<OAuth2TokenValidator<Jwt>>();
      if (validAudiences != null) {
        validators.add(new AudienceValidator(validAudiences));
      }
      if (configuration.getSaas().isConfigured()) {
        validators.add(new OrganizationValidator(configuration.getSaas().getOrganizationId()));
        validators.add(new ClusterValidator(configuration.getSaas().getClusterId()));
      }

      if (!validators.isEmpty()) {
        return JwtValidators.createDefaultWithValidators(validators);
      }
      return JwtValidators.createDefault();
    }

    @Bean
    @Order(ORDER_WEBAPP_API)
    @ConditionalOnProtectedApi
    public SecurityFilterChain oidcApiSecurity(
        final HttpSecurity httpSecurity,
        final AuthFailureHandler authFailureHandler,
        final JwtDecoder jwtDecoder,
        //        final CamundaJwtAuthenticationConverter converter,
        final SecurityConfiguration securityConfiguration)
        throws Exception {
      return httpSecurity
          .securityMatcher(API_PATHS.toArray(new String[0]))
          .authorizeHttpRequests(
              (authorizeHttpRequests) ->
                  authorizeHttpRequests
                      .requestMatchers(UNPROTECTED_API_PATHS.toArray(String[]::new))
                      .permitAll()
                      .anyRequest()
                      .authenticated())
          .headers(
              headers ->
                  setupSecureHeaders(
                      headers,
                      securityConfiguration.getHttpHeaders(),
                      securityConfiguration.getSaas().isConfigured()))
          .exceptionHandling(
              (exceptionHandling) -> exceptionHandling.accessDeniedHandler(authFailureHandler))
          .csrf(AbstractHttpConfigurer::disable)
          .cors(AbstractHttpConfigurer::disable)
          .formLogin(AbstractHttpConfigurer::disable)
          .anonymous(AbstractHttpConfigurer::disable)
          .oauth2ResourceServer(
              oauth2 -> oauth2.jwt(jwtConfigurer -> jwtConfigurer.decoder(jwtDecoder)))
          .oauth2Login(AbstractHttpConfigurer::disable)
          .oidcLogout(AbstractHttpConfigurer::disable)
          .logout(AbstractHttpConfigurer::disable)
          .build();
    }

    @Bean
    @Order(ORDER_WEBAPP_API)
    public SecurityFilterChain oidcWebappSecurity(
        final HttpSecurity httpSecurity,
        final AuthFailureHandler authFailureHandler,
        final ClientRegistrationRepository clientRegistrationRepository,
        //        final WebApplicationAuthorizationCheckFilter
        // webApplicationAuthorizationCheckFilter,
        final JwtDecoder jwtDecoder,
        //        final CamundaJwtAuthenticationConverter converter,
        final SecurityConfiguration securityConfiguration)
        throws Exception {
      return httpSecurity
          .securityMatcher(WEBAPP_PATHS.toArray(new String[0]))
          .authorizeHttpRequests(
              (authorizeHttpRequests) ->
                  authorizeHttpRequests
                      .requestMatchers(UNPROTECTED_PATHS.toArray(String[]::new))
                      .permitAll()
                      .anyRequest()
                      .authenticated())
          .headers(
              headers ->
                  setupSecureHeaders(
                      headers,
                      securityConfiguration.getHttpHeaders(),
                      securityConfiguration.getSaas().isConfigured()))
          .exceptionHandling(
              (exceptionHandling) -> exceptionHandling.accessDeniedHandler(authFailureHandler))
          .csrf(AbstractHttpConfigurer::disable)
          .cors(AbstractHttpConfigurer::disable)
          .formLogin(AbstractHttpConfigurer::disable)
          .anonymous(AbstractHttpConfigurer::disable)
          .oauth2ResourceServer(
              oauth2 -> oauth2.jwt(jwtConfigurer -> jwtConfigurer.decoder(jwtDecoder)))
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
          //          .addFilterAfter(webApplicationAuthorizationCheckFilter,
          // AuthorizationFilter.class)
          .build();
    }
  }
}
