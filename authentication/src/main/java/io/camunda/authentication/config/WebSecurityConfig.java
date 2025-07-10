/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.config;

import static com.nimbusds.jose.JOSEObjectType.JWT;
import static io.camunda.security.configuration.headers.ContentSecurityPolicyConfig.DEFAULT_SAAS_SECURITY_POLICY;
import static io.camunda.security.configuration.headers.ContentSecurityPolicyConfig.DEFAULT_SM_SECURITY_POLICY;
import static org.springframework.security.oauth2.jose.jws.SignatureAlgorithm.ES256;
import static org.springframework.security.oauth2.jose.jws.SignatureAlgorithm.ES384;
import static org.springframework.security.oauth2.jose.jws.SignatureAlgorithm.ES512;
import static org.springframework.security.oauth2.jose.jws.SignatureAlgorithm.RS256;
import static org.springframework.security.oauth2.jose.jws.SignatureAlgorithm.RS384;
import static org.springframework.security.oauth2.jose.jws.SignatureAlgorithm.RS512;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.proc.DefaultJOSEObjectTypeVerifier;
import io.camunda.authentication.CamundaJwtAuthenticationConverter;
import io.camunda.authentication.CamundaUserDetailsService;
import io.camunda.authentication.ConditionalOnAuthenticationMethod;
import io.camunda.authentication.ConditionalOnProtectedApi;
import io.camunda.authentication.ConditionalOnUnprotectedApi;
import io.camunda.authentication.csrf.CsrfProtectionRequestMatcher;
import io.camunda.authentication.filters.AdminUserCheckFilter;
import io.camunda.authentication.filters.CertificateClientCredentialsFilter;
import io.camunda.authentication.filters.OAuth2RefreshTokenFilter;
import io.camunda.authentication.filters.WebApplicationAuthorizationCheckFilter;
import io.camunda.authentication.handler.AuthFailureHandler;
import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.security.configuration.OidcAuthenticationConfiguration;
import io.camunda.security.configuration.SecurityConfiguration;
import io.camunda.security.configuration.headers.HeaderConfiguration;
import io.camunda.security.configuration.headers.values.FrameOptionMode;
import io.camunda.security.entity.AuthenticationMethod;
import io.camunda.security.reader.ResourceAccessProvider;
import io.camunda.service.AuthorizationServices;
import io.camunda.service.GroupServices;
import io.camunda.service.RoleServices;
import io.camunda.service.TenantServices;
import io.camunda.service.UserServices;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.autoconfigure.security.servlet.EndpointRequest;
import org.springframework.boot.actuate.logging.LoggersEndpoint;
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
import org.springframework.security.config.annotation.web.configurers.CsrfConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer.CacheControlConfig;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer.ContentTypeOptionsConfig;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer.FrameOptionsConfig;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer.HstsConfig;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProvider;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.oidc.authentication.OidcIdTokenDecoderFactory;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.web.HttpSessionOAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest.Builder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoderFactory;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.header.writers.CrossOriginEmbedderPolicyHeaderWriter.CrossOriginEmbedderPolicy;
import org.springframework.security.web.header.writers.CrossOriginOpenerPolicyHeaderWriter.CrossOriginOpenerPolicy;
import org.springframework.security.web.header.writers.CrossOriginResourcePolicyHeaderWriter.CrossOriginResourcePolicy;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;
import org.springframework.web.filter.OncePerRequestFilter;

@Configuration
@EnableWebSecurity
@Profile("consolidated-auth")
public class WebSecurityConfig {
  public static final String SESSION_COOKIE = "camunda-session";
  public static final String X_CSRF_TOKEN = "X-CSRF-TOKEN";
  public static final String LOGIN_URL = "/login";
  public static final String LOGOUT_URL = "/logout";
  public static final Set<String> API_PATHS = Set.of("/api/**", "/v1/**", "/v2/**");
  public static final Set<String> UNPROTECTED_API_PATHS =
      Set.of(
          // these v2 endpoints are public
          "/v2/license",
          "/v2/setup/user",
          // deprecated Tasklist v1 Public Endpoints
          "/v1/external/process/**");
  public static final Set<String> WEBAPP_PATHS =
      Set.of(
          "/login/**",
          "/logout",
          "/operate/**",
          "/tasklist/**",
          "/",
          "/sso-callback/**",
          "/oauth2/authorization/**",
          // old Tasklist and Operate webapps routes
          "/processes",
          "/processes/*",
          "/*", // user task id (numeric patterns)
          "/processes/*/start",
          "/new/*",
          "/decisions",
          "/decisions/*",
          "/instances",
          "/instances/*");
  public static final Set<String> IDENTITY_PATHS = Set.of("/identity/**", "/v2/**");
  public static final Set<String> UNPROTECTED_PATHS =
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
  // We explicitly support the "at+jwt" JWT 'typ' header defined in
  // https://datatracker.ietf.org/doc/html/rfc9068#name-header
  static final JOSEObjectType AT_JWT = new JOSEObjectType("at+jwt");
  private static final Logger LOG = LoggerFactory.getLogger(WebSecurityConfig.class);
  // Used for chains that grant unauthenticated access, always comes first.
  private static final int ORDER_UNPROTECTED = 0;
  // Used for chains that protect the APIs or Webapp paths.
  private static final int ORDER_WEBAPP_API = 1;
  // Intended for a "catch-all-unhandled"-chain protecting all resources by default
  private static final int ORDER_UNHANDLED = 2;

  @Bean
  @Order(ORDER_UNPROTECTED)
  public SecurityFilterChain unprotectedPathsSecurityFilterChain(
      final HttpSecurity httpSecurity, final SecurityConfiguration securityConfiguration)
      throws Exception {
    LOG.debug("Setting up unprotectedPathsSecurityFilterChain with order: {}", ORDER_UNPROTECTED);
    return httpSecurity
        .securityMatcher(UNPROTECTED_PATHS.toArray(String[]::new))
        .addFilterBefore(
            new OncePerRequestFilter() {
              @Override
              protected void doFilterInternal(
                  final HttpServletRequest request,
                  final HttpServletResponse response,
                  final FilterChain filterChain)
                  throws ServletException, IOException {
                LOG.debug(
                    "unprotectedPathsSecurityFilterChain processing request: {}",
                    request.getRequestURI());
                filterChain.doFilter(request, response);
              }
            },
            AuthorizationFilter.class)
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
      final HttpSecurity httpSecurity,
      final SecurityConfiguration securityConfiguration,
      final AuthFailureHandler authFailureHandler,
      final CookieCsrfTokenRepository csrfTokenRepository)
      throws Exception {
    LOG.warn(
        "The API is unprotected. Please disable {} for any deployment.",
        AuthenticationProperties.API_UNPROTECTED);

    // Exclude IDENTITY_PATHS when using client credentials to avoid conflict with
    // identityClientCredentialsSecurityFilterChain
    final String[] apiPathsForThisChain;
    if ("client_credentials"
        .equals(securityConfiguration.getAuthentication().getOidc().getGrantType())) {
      // Only handle /api/** and /v1/** when using client credentials, let
      // identityClientCredentialsSecurityFilterChain handle IDENTITY_PATHS
      apiPathsForThisChain = new String[] {"/api/**", "/v1/**"};
      LOG.info(
          "unprotectedApiAuthSecurityFilterChain: Excluding IDENTITY_PATHS due to client_credentials grant type");
    } else {
      // Handle all API paths for normal flows
      apiPathsForThisChain = API_PATHS.toArray(new String[0]);
    }

    final var filterChainBuilder =
        httpSecurity
            .securityMatcher(apiPathsForThisChain)
            .addFilterBefore(
                new OncePerRequestFilter() {
                  @Override
                  protected void doFilterInternal(
                      final HttpServletRequest request,
                      final HttpServletResponse response,
                      final FilterChain filterChain)
                      throws ServletException, IOException {
                    LOG.debug(
                        "unprotectedApiAuthSecurityFilterChain processing request: {}",
                        request.getRequestURI());
                    filterChain.doFilter(request, response);
                  }
                },
                AuthorizationFilter.class)
            .authorizeHttpRequests(
                (authorizeHttpRequests) -> authorizeHttpRequests.anyRequest().permitAll())
            .headers(
                headers ->
                    setupSecureHeaders(
                        headers,
                        securityConfiguration.getHttpHeaders(),
                        securityConfiguration.getSaas().isConfigured()))
            .cors(AbstractHttpConfigurer::disable)
            .exceptionHandling(
                // this prevents the usage of the default BasicAuthenticationEntryPoint returning
                // a WWW-Authenticate header that causes browsers to prompt for basic login
                exceptionHandling -> exceptionHandling.accessDeniedHandler(authFailureHandler))
            .formLogin(AbstractHttpConfigurer::disable)
            .anonymous(AbstractHttpConfigurer::disable);

    applyCsrfConfiguration(httpSecurity, securityConfiguration, csrfTokenRepository);

    return filterChainBuilder.build();
  }

  @Bean
  @Order(ORDER_UNHANDLED)
  public SecurityFilterChain protectedUnhandledPathsSecurityFilterChain(
      final HttpSecurity httpSecurity) throws Exception {
    // all resources not yet explicitly handled by any previous chain require an authenticated user
    // thus by default unhandled paths are always protected
    return httpSecurity
        .securityMatcher("/**")
        .addFilterBefore(
            new OncePerRequestFilter() {
              @Override
              protected void doFilterInternal(
                  final HttpServletRequest request,
                  final HttpServletResponse response,
                  final FilterChain filterChain)
                  throws ServletException, IOException {
                LOG.debug(
                    "protectedUnhandledPathsSecurityFilterChain processing request: {}",
                    request.getRequestURI());
                filterChain.doFilter(request, response);
              }
            },
            AuthorizationFilter.class)
        .authorizeHttpRequests(
            (authorizeHttpRequests) -> authorizeHttpRequests.anyRequest().denyAll())
        .build();
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

  @Bean
  public CookieCsrfTokenRepository cookieCsrfTokenRepository() {
    final CookieCsrfTokenRepository repository = new CookieCsrfTokenRepository();
    repository.setHeaderName(X_CSRF_TOKEN);
    repository.setCookieCustomizer(
        cc -> cc.httpOnly(false).secure(false)); // Allow testing over HTTP
    repository.setCookieName(X_CSRF_TOKEN);
    return repository;
  }

  private static void configureCsrf(
      final CookieCsrfTokenRepository repository, final CsrfConfigurer<HttpSecurity> csrf) {
    csrf.csrfTokenRepository(repository)
        .requireCsrfProtectionMatcher(new CsrfProtectionRequestMatcher())
        .ignoringRequestMatchers(EndpointRequest.to(LoggersEndpoint.class));
  }

  private static OncePerRequestFilter csrfHeaderFilter() {
    return new OncePerRequestFilter() {

      @Override
      protected void doFilterInternal(
          final HttpServletRequest request,
          final HttpServletResponse response,
          final FilterChain filterChain)
          throws ServletException, IOException {
        filterChain.doFilter(request, addCsrfTokenWhenAvailable(request, response));
      }
    };
  }

  private static HttpServletResponse addCsrfTokenWhenAvailable(
      final HttpServletRequest request, final HttpServletResponse response) {
    if (shouldAddCsrf(request)) {
      final CsrfToken token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
      if (token != null) {
        response.setHeader(X_CSRF_TOKEN, token.getToken());
      }
    }
    return response;
  }

  private static boolean shouldAddCsrf(final HttpServletRequest request) {
    final Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    final String path = request.getRequestURI();
    final String method = request.getMethod();
    return (auth != null && auth.isAuthenticated())
        && (path == null || !path.contains(LOGOUT_URL))
        && ("GET".equalsIgnoreCase(method) || (path != null && (path.contains(LOGIN_URL))));
  }

  private static void applyCsrfConfiguration(
      final HttpSecurity httpSecurity,
      final SecurityConfiguration securityConfiguration,
      final CookieCsrfTokenRepository csrfTokenRepository)
      throws Exception {
    httpSecurity.csrf(
        securityConfiguration.getCsrf().isEnabled()
            ? csrf -> configureCsrf(csrfTokenRepository, csrf)
            : AbstractHttpConfigurer::disable);
    if (securityConfiguration.getCsrf().isEnabled()) {
      httpSecurity.addFilterAfter(csrfHeaderFilter(), CsrfFilter.class);
    }
  }

  @Bean
  @Order(ORDER_WEBAPP_API)
  @ConditionalOnAuthenticationMethod(AuthenticationMethod.BASIC)
  public SecurityFilterChain x509IdentitySecurityFilterChain(
      final HttpSecurity httpSecurity, final SecurityConfiguration securityConfiguration)
      throws Exception {
    // Require client certificate authentication for /identity/** only when using BASIC auth
    return httpSecurity
        .securityMatcher(IDENTITY_PATHS.toArray(String[]::new))
        .addFilterBefore(
            new OncePerRequestFilter() {
              @Override
              protected void doFilterInternal(
                  final HttpServletRequest request,
                  final HttpServletResponse response,
                  final FilterChain filterChain)
                  throws ServletException, IOException {
                LOG.debug(
                    "x509IdentitySecurityFilterChain processing request: {}",
                    request.getRequestURI());
                filterChain.doFilter(request, response);
              }
            },
            AuthorizationFilter.class)
        .authorizeHttpRequests(authz -> authz.anyRequest().authenticated())
        .x509(
            x509 ->
                x509.subjectPrincipalRegex(
                        "CN=(.*?)(?:,|$)") // Extract CN, handling both comma-separated and end of
                    .userDetailsService(
                        username -> User.withUsername(username).password("").roles("USER").build()))
        .headers(
            headers ->
                setupSecureHeaders(
                    headers,
                    securityConfiguration.getHttpHeaders(),
                    securityConfiguration.getSaas().isConfigured()))
        .csrf(AbstractHttpConfigurer::disable)
        .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.NEVER))
        .exceptionHandling(
            ex ->
                ex.authenticationEntryPoint(
                    (request, response, authException) -> {
                      response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                      response.setContentType("application/json");
                      response
                          .getWriter()
                          .write("{\"error\":\"Client certificate authentication required\"}");
                    }))
        .build();
  }

  @Configuration
  @ConditionalOnAuthenticationMethod(AuthenticationMethod.BASIC)
  public static class BasicConfiguration {
    @Bean
    @ConditionalOnMissingBean(UserDetailsService.class)
    public CamundaUserDetailsService camundaUserDetailsService(
        final UserServices userServices,
        final RoleServices roleServices,
        final TenantServices tenantServices,
        final GroupServices groupServices) {
      return new CamundaUserDetailsService(
          userServices, roleServices, tenantServices, groupServices);
    }

    @Bean
    @Order(ORDER_WEBAPP_API)
    @ConditionalOnProtectedApi
    public SecurityFilterChain httpBasicApiAuthSecurityFilterChain(
        final HttpSecurity httpSecurity,
        final AuthFailureHandler authFailureHandler,
        final SecurityConfiguration securityConfiguration,
        final CookieCsrfTokenRepository csrfTokenRepository)
        throws Exception {
      LOG.debug("Setting up httpBasicApiAuthSecurityFilterChain with order: {}", ORDER_WEBAPP_API);
      final var filterChainBuilder =
          httpSecurity
              .securityMatcher(API_PATHS.toArray(String[]::new))
              .addFilterBefore(
                  new OncePerRequestFilter() {
                    @Override
                    protected void doFilterInternal(
                        final HttpServletRequest request,
                        final HttpServletResponse response,
                        final FilterChain filterChain)
                        throws ServletException, IOException {
                      LOG.debug(
                          "httpBasicApiAuthSecurityFilterChain processing request: {}",
                          request.getRequestURI());
                      filterChain.doFilter(request, response);
                    }
                  },
                  AuthorizationFilter.class)
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
              // do not create a session on api authentication, that's to be done on webapp login
              // only
              .sessionManagement(
                  (sessionManagement) ->
                      sessionManagement.sessionCreationPolicy(SessionCreationPolicy.NEVER));

      applyCsrfConfiguration(httpSecurity, securityConfiguration, csrfTokenRepository);

      return filterChainBuilder.build();
    }

    @Bean
    @Order(ORDER_WEBAPP_API)
    public SecurityFilterChain httpBasicWebappAuthSecurityFilterChain(
        final HttpSecurity httpSecurity,
        final AuthFailureHandler authFailureHandler,
        final SecurityConfiguration securityConfiguration,
        final CamundaAuthenticationProvider authenticationProvider,
        final ResourceAccessProvider resourceAccessProvider,
        final RoleServices roleServices,
        final CookieCsrfTokenRepository csrfTokenRepository)
        throws Exception {
      LOG.debug(
          "Setting up httpBasicWebappAuthSecurityFilterChain with order: {}", ORDER_WEBAPP_API);
      final var filterChainBuilder =
          httpSecurity
              .securityMatcher(WEBAPP_PATHS.toArray(String[]::new))
              .addFilterBefore(
                  new OncePerRequestFilter() {
                    @Override
                    protected void doFilterInternal(
                        final HttpServletRequest request,
                        final HttpServletResponse response,
                        final FilterChain filterChain)
                        throws ServletException, IOException {
                      LOG.debug(
                          "httpBasicWebappAuthSecurityFilterChain processing request: {}",
                          request.getRequestURI());
                      filterChain.doFilter(request, response);
                    }
                  },
                  AuthorizationFilter.class)
              // webapps are accessible without any authentication required
              // reasoning: in basic auth setups, we redirect to the login page
              // on client side; for that to happen, we first need to deliver
              // the index html resource to the browser
              .authorizeHttpRequests(
                  (authorizeHttpRequests) -> authorizeHttpRequests.anyRequest().permitAll())
              .headers(
                  headers ->
                      setupSecureHeaders(
                          headers,
                          securityConfiguration.getHttpHeaders(),
                          securityConfiguration.getSaas().isConfigured()))
              .cors(AbstractHttpConfigurer::disable)
              .anonymous(AbstractHttpConfigurer::disable)
              // login/logout is still possible to obtain a session
              // the session grants access to the API as well, via
              // #httpBasicApiAuthSecurityFilterChain
              .formLogin(
                  formLogin ->
                      formLogin
                          .loginPage(LOGIN_URL)
                          .loginProcessingUrl(LOGIN_URL)
                          .failureHandler(authFailureHandler)
                          .successHandler(new NoContentWithCsrfTokenSuccessHandler()))
              .logout(
                  (logout) ->
                      logout
                          .logoutUrl(LOGOUT_URL)
                          .logoutSuccessHandler(new NoContentResponseHandler())
                          .deleteCookies(SESSION_COOKIE, X_CSRF_TOKEN))
              .exceptionHandling(
                  exceptionHandling ->
                      exceptionHandling
                          .authenticationEntryPoint(authFailureHandler)
                          .accessDeniedHandler(authFailureHandler))
              .addFilterAfter(
                  new WebApplicationAuthorizationCheckFilter(
                      securityConfiguration, authenticationProvider, resourceAccessProvider),
                  AuthorizationFilter.class)
              .addFilterBefore(
                  new AdminUserCheckFilter(securityConfiguration, roleServices),
                  AuthorizationFilter.class);

      applyCsrfConfiguration(httpSecurity, securityConfiguration, csrfTokenRepository);

      return filterChainBuilder.build();
    }
  }

  @Configuration
  @ConditionalOnAuthenticationMethod(AuthenticationMethod.OIDC)
  public static class OidcConfiguration {

    @Bean
    public CertificateClientAssertionService certificateClientAssertionService() {
      return new CertificateClientAssertionService();
    }

    /**
     * Maps certificate properties from Spring Boot configuration to OIDC authentication
     * configuration.
     */
    @Bean
    public OidcAuthenticationConfiguration enhancedOidcConfiguration(
        final SecurityConfiguration securityConfiguration,
        final CertificateOidcProperties certificateProperties) {
      final var oidcConfig = securityConfiguration.getAuthentication().getOidc();

      // Map certificate properties if they exist
      if (certificateProperties.getClientAssertionKeystorePath() != null) {
        oidcConfig.setClientAssertionKeystorePath(
            certificateProperties.getClientAssertionKeystorePath());
      }
      if (certificateProperties.getClientAssertionKeystorePassword() != null) {
        oidcConfig.setClientAssertionKeystorePassword(
            certificateProperties.getClientAssertionKeystorePassword());
      }
      if (certificateProperties.getClientAssertionKeystoreKeyAlias() != null) {
        oidcConfig.setClientAssertionKeystoreKeyAlias(
            certificateProperties.getClientAssertionKeystoreKeyAlias());
      }
      if (certificateProperties.getClientAssertionKeystoreKeyPassword() != null) {
        oidcConfig.setClientAssertionKeystoreKeyPassword(
            certificateProperties.getClientAssertionKeystoreKeyPassword());
      }

      return oidcConfig;
    }

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

      final var decoder =
          NimbusJwtDecoder.withJwkSetUri(jwkSetUri)
              .jwsAlgorithms(
                  algorithms ->
                      algorithms.addAll(List.of(RS256, RS384, RS512, ES256, ES384, ES512)))
              .jwtProcessorCustomizer(
                  // the default implementation supports only JOSEObjectType.JWT and null
                  processor ->
                      processor.setJWSTypeVerifier(
                          new DefaultJOSEObjectTypeVerifier<>(JWT, AT_JWT, null)))
              .build();
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
    public OAuth2AuthorizedClientRepository oAuth2AuthorizedClientRepository() {
      return new HttpSessionOAuth2AuthorizedClientRepository();
    }

    @Bean
    public OAuth2AuthorizedClientManager authorizedClientManager(
        final ClientRegistrationRepository clientRegistrationRepository,
        final OAuth2AuthorizedClientRepository authorizedClientRepository,
        final CertificateClientAssertionService certificateClientAssertionService,
        final SecurityConfiguration securityConfiguration) {

      final OAuth2AuthorizedClientProvider authorizedClientProvider;

      // Check if using client credentials with certificate
      if ("client_credentials"
          .equals(securityConfiguration.getAuthentication().getOidc().getGrantType())) {
        final var clientCredentialsTokenResponseClient =
            new CertificateBasedClientCredentialsTokenResponseClient(
                certificateClientAssertionService,
                securityConfiguration.getAuthentication().getOidc());

        authorizedClientProvider =
            OAuth2AuthorizedClientProviderBuilder.builder()
                .clientCredentials(
                    configurer ->
                        configurer.accessTokenResponseClient(clientCredentialsTokenResponseClient))
                .build();

        LOG.info("Configured OAuth2 client credentials flow with certificate-based authentication");
      } else {
        authorizedClientProvider =
            OAuth2AuthorizedClientProviderBuilder.builder()
                .authorizationCode()
                .refreshToken()
                .clientCredentials()
                .build();
      }

      final DefaultOAuth2AuthorizedClientManager clientManager =
          new DefaultOAuth2AuthorizedClientManager(
              clientRegistrationRepository, authorizedClientRepository);
      clientManager.setAuthorizedClientProvider(authorizedClientProvider);

      return clientManager;
    }

    @Bean
    @Order(ORDER_WEBAPP_API)
    @ConditionalOnProtectedApi
    public SecurityFilterChain oidcApiSecurity(
        final HttpSecurity httpSecurity,
        final AuthFailureHandler authFailureHandler,
        final JwtDecoder jwtDecoder,
        final CamundaJwtAuthenticationConverter converter,
        final SecurityConfiguration securityConfiguration,
        final CookieCsrfTokenRepository csrfTokenRepository,
        final OAuth2AuthorizedClientRepository authorizedClientRepository,
        final OAuth2AuthorizedClientManager authorizedClientManager)
        throws Exception {
      LOG.debug("Setting up oidcApiSecurity filter chain with order: {}", ORDER_WEBAPP_API);

      // Exclude /v2/** when using client credentials to avoid conflict with
      // identityClientCredentialsSecurityFilterChain
      final String[] apiPathsForThisChain;
      if ("client_credentials"
          .equals(securityConfiguration.getAuthentication().getOidc().getGrantType())) {
        // Only handle /api/** and /v1/** when using client credentials, let
        // identityClientCredentialsSecurityFilterChain handle /v2/**
        apiPathsForThisChain = new String[] {"/api/**", "/v1/**"};
        LOG.debug("oidcApiSecurity: Excluding /v2/** paths due to client_credentials grant type");
      } else {
        // Handle all API paths for normal OIDC flow
        apiPathsForThisChain = API_PATHS.toArray(new String[0]);
      }

      final var filterChainBuilder =
          httpSecurity
              .securityMatcher(apiPathsForThisChain)
              .addFilterBefore(
                  new OncePerRequestFilter() {
                    @Override
                    protected void doFilterInternal(
                        final HttpServletRequest request,
                        final HttpServletResponse response,
                        final FilterChain filterChain)
                        throws ServletException, IOException {
                      LOG.debug(
                          "oidcApiSecurity chain processing request: {}", request.getRequestURI());
                      filterChain.doFilter(request, response);
                    }
                  },
                  AuthorizationFilter.class)
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
              .cors(AbstractHttpConfigurer::disable)
              .formLogin(AbstractHttpConfigurer::disable)
              .anonymous(AbstractHttpConfigurer::disable)
              .oauth2ResourceServer(
                  oauth2 ->
                      oauth2.jwt(
                          jwtConfigurer ->
                              jwtConfigurer
                                  .decoder(jwtDecoder)
                                  .jwtAuthenticationConverter(converter)))
              .oauth2Login(AbstractHttpConfigurer::disable)
              .oidcLogout(AbstractHttpConfigurer::disable)
              .logout(AbstractHttpConfigurer::disable);

      applyOauth2RefreshTokenFilter(
          httpSecurity, authorizedClientRepository, authorizedClientManager);
      applyCsrfConfiguration(httpSecurity, securityConfiguration, csrfTokenRepository);

      return filterChainBuilder.build();
    }

    @Bean
    @Order(ORDER_UNPROTECTED - 1) // Highest priority to ensure it runs first
    @ConditionalOnAuthenticationMethod(AuthenticationMethod.OIDC)
    public SecurityFilterChain identityClientCredentialsSecurityFilterChain(
        final HttpSecurity httpSecurity,
        final SecurityConfiguration securityConfiguration,
        final CookieCsrfTokenRepository csrfTokenRepository,
        final OAuth2AuthorizedClientManager authorizedClientManager,
        final ClientRegistrationRepository clientRegistrationRepository,
        final AuthorizationServices authorizationServices)
        throws Exception {

      // Only apply when using client_credentials flow
      final String grantType = securityConfiguration.getAuthentication().getOidc().getGrantType();
      if (!"client_credentials".equals(grantType)) {
        return null; // Skip this bean
      }

      LOG.debug(
          "Setting up identityClientCredentialsSecurityFilterChain with order: {}",
          ORDER_UNPROTECTED - 1);

      final String[] identityPaths = IDENTITY_PATHS.toArray(String[]::new);

      return httpSecurity
          .securityMatcher(identityPaths)
          .addFilterBefore(
              new OncePerRequestFilter() {
                @Override
                protected void doFilterInternal(
                    final HttpServletRequest request,
                    final HttpServletResponse response,
                    final FilterChain filterChain)
                    throws ServletException, IOException {
                  LOG.debug(
                      "identityClientCredentialsSecurityFilterChain processing request: {}",
                      request.getRequestURI());
                  filterChain.doFilter(request, response);
                }
              },
              AuthorizationFilter.class)
          .authorizeHttpRequests(
              (authorizeHttpRequests) -> authorizeHttpRequests.anyRequest().permitAll())
          .headers(
              headers ->
                  setupSecureHeaders(
                      headers,
                      securityConfiguration.getHttpHeaders(),
                      securityConfiguration.getSaas().isConfigured()))
          .cors(
              cors ->
                  cors.configurationSource(
                      request -> {
                        final var corsConfig = new org.springframework.web.cors.CorsConfiguration();
                        corsConfig.setAllowedOriginPatterns(java.util.List.of("*"));
                        corsConfig.setAllowedMethods(
                            java.util.List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
                        corsConfig.setAllowedHeaders(java.util.List.of("*"));
                        corsConfig.setAllowCredentials(true);
                        corsConfig.setMaxAge(3600L);
                        LOG.info(
                            "CORS configuration applied for request: {} with credentials: {}",
                            request.getRequestURI(),
                            corsConfig.getAllowCredentials());
                        return corsConfig;
                      }))
          .formLogin(AbstractHttpConfigurer::disable)
          .oauth2Login(AbstractHttpConfigurer::disable)
          .anonymous(AbstractHttpConfigurer::disable)
          .addFilterBefore(
              new CertificateClientCredentialsFilter(
                  securityConfiguration,
                  authorizedClientManager,
                  clientRegistrationRepository,
                  csrfTokenRepository,
                  authorizationServices),
              AuthorizationFilter.class)
          .sessionManagement(
              session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
          .csrf(csrf -> configureCsrf(csrfTokenRepository, csrf))
          .build();
    }

    @Bean
    @ConditionalOnUnprotectedApi
    @Order(ORDER_UNPROTECTED)
    public SecurityFilterChain unprotectedIdentityAuthSecurityFilterChain(
        final HttpSecurity httpSecurity,
        final SecurityConfiguration securityConfiguration,
        final AuthFailureHandler authFailureHandler,
        final CookieCsrfTokenRepository csrfTokenRepository)
        throws Exception {
      LOG.warn(
          "The Identity endpoint is unprotected. Please disable {} for any deployment.",
          AuthenticationProperties.API_UNPROTECTED);

      return httpSecurity
          .securityMatcher(IDENTITY_PATHS.toArray(String[]::new))
          .addFilterBefore(
              new OncePerRequestFilter() {
                @Override
                protected void doFilterInternal(
                    final HttpServletRequest request,
                    final HttpServletResponse response,
                    final FilterChain filterChain)
                    throws ServletException, IOException {
                  LOG.debug(
                      "unprotectedIdentityAuthSecurityFilterChain processing request: {}",
                      request.getRequestURI());
                  filterChain.doFilter(request, response);
                }
              },
              AuthorizationFilter.class)
          .authorizeHttpRequests(
              (authorizeHttpRequests) -> authorizeHttpRequests.anyRequest().permitAll())
          .headers(
              headers ->
                  setupSecureHeaders(
                      headers,
                      securityConfiguration.getHttpHeaders(),
                      securityConfiguration.getSaas().isConfigured()))
          .cors(AbstractHttpConfigurer::disable)
          .exceptionHandling(
              // this prevents the usage of the default BasicAuthenticationEntryPoint
              // returning
              // a WWW-Authenticate header that causes browsers to prompt for basic login
              exceptionHandling -> exceptionHandling.accessDeniedHandler(authFailureHandler))
          .formLogin(AbstractHttpConfigurer::disable)
          .anonymous(AbstractHttpConfigurer::disable)
          .csrf(AbstractHttpConfigurer::disable)
          .build();
    }

    @Bean
    public CookieSerializer cookieSerializer() {
      final DefaultCookieSerializer serializer = new DefaultCookieSerializer();
      serializer.setSameSite("None");
      serializer.setUseSecureCookie(false); // Allow for HTTP localhost testing
      return serializer;
    }

    @Bean
    @Order(ORDER_WEBAPP_API)
    public SecurityFilterChain oidcWebappSecurity(
        final HttpSecurity httpSecurity,
        final AuthFailureHandler authFailureHandler,
        final ClientRegistrationRepository clientRegistrationRepository,
        final JwtDecoder jwtDecoder,
        final CamundaJwtAuthenticationConverter converter,
        final SecurityConfiguration securityConfiguration,
        final CamundaAuthenticationProvider authenticationProvider,
        final ResourceAccessProvider resourceAccessProvider,
        final CookieCsrfTokenRepository csrfTokenRepository,
        final OAuth2AuthorizedClientRepository authorizedClientRepository,
        final OAuth2AuthorizedClientManager authorizedClientManager,
        final CertificateClientAssertionService certificateClientAssertionService)
        throws Exception {
      final var filterChainBuilder =
          httpSecurity
              .securityMatcher(WEBAPP_PATHS.toArray(new String[0]))
              .addFilterBefore(
                  new OncePerRequestFilter() {
                    @Override
                    protected void doFilterInternal(
                        final HttpServletRequest request,
                        final HttpServletResponse response,
                        final FilterChain filterChain)
                        throws ServletException, IOException {
                      LOG.debug(
                          "oidcWebappSecurity processing request: {}", request.getRequestURI());
                      filterChain.doFilter(request, response);
                    }
                  },
                  AuthorizationFilter.class)
              .authorizeHttpRequests(
                  (authorizeHttpRequests) -> {
                    // If using client credentials flow, allow access without interactive login
                    if ("client_credentials"
                        .equals(
                            securityConfiguration.getAuthentication().getOidc().getGrantType())) {
                      authorizeHttpRequests.anyRequest().permitAll();
                    } else {
                      authorizeHttpRequests.anyRequest().authenticated();
                    }
                  })
              .headers(
                  headers ->
                      setupSecureHeaders(
                          headers,
                          securityConfiguration.getHttpHeaders(),
                          securityConfiguration.getSaas().isConfigured()))
              .cors(AbstractHttpConfigurer::disable)
              .formLogin(AbstractHttpConfigurer::disable)
              .anonymous(AbstractHttpConfigurer::disable)
              .oauth2ResourceServer(
                  oauth2 ->
                      oauth2.jwt(
                          jwtConfigurer ->
                              jwtConfigurer
                                  .decoder(jwtDecoder)
                                  .jwtAuthenticationConverter(converter)))
              .oauth2Login(
                  oauthLoginConfigurer -> {
                    oauthLoginConfigurer
                        .clientRegistrationRepository(clientRegistrationRepository)
                        .authorizedClientRepository(authorizedClientRepository)
                        .redirectionEndpoint(
                            redirectionEndpointConfig ->
                                redirectionEndpointConfig.baseUri("/sso-callback"))
                        .authorizationEndpoint(
                            authorization ->
                                authorization.authorizationRequestResolver(
                                    authorizationRequestResolver(
                                        clientRegistrationRepository, securityConfiguration)));

                    if (securityConfiguration
                        .getAuthentication()
                        .getOidc()
                        .isClientAssertionEnabled()) {
                      final var certificateTokenResponseClient =
                          new CertificateBasedOAuth2AccessTokenResponseClient(
                              certificateClientAssertionService,
                              securityConfiguration.getAuthentication().getOidc());
                      oauthLoginConfigurer.tokenEndpoint(
                          tokenEndpointConfig ->
                              tokenEndpointConfig.accessTokenResponseClient(
                                  certificateTokenResponseClient));
                    }
                  })
              .oidcLogout(httpSecurityOidcLogoutConfigurer -> {})
              .logout(
                  (logout) ->
                      logout
                          .logoutUrl(LOGOUT_URL)
                          .logoutSuccessHandler(new NoContentResponseHandler())
                          .deleteCookies(SESSION_COOKIE, X_CSRF_TOKEN))
              .addFilterAfter(
                  new WebApplicationAuthorizationCheckFilter(
                      securityConfiguration, authenticationProvider, resourceAccessProvider),
                  AuthorizationFilter.class);

      applyOauth2RefreshTokenFilter(
          httpSecurity, authorizedClientRepository, authorizedClientManager);
      applyCsrfConfiguration(httpSecurity, securityConfiguration, csrfTokenRepository);

      return filterChainBuilder.build();
    }

    private OAuth2AuthorizationRequestResolver authorizationRequestResolver(
        final ClientRegistrationRepository clientRegistrationRepository,
        final SecurityConfiguration securityConfiguration) {

      final var authorizationRequestResolver =
          new DefaultOAuth2AuthorizationRequestResolver(
              clientRegistrationRepository, "/oauth2/authorization");
      authorizationRequestResolver.setAuthorizationRequestCustomizer(
          authorizationRequestCustomizer(securityConfiguration));

      return authorizationRequestResolver;
    }

    private Consumer<Builder> authorizationRequestCustomizer(
        final SecurityConfiguration securityConfiguration) {
      return customizer -> {
        final var additionalParameters =
            securityConfiguration
                .getAuthentication()
                .getOidc()
                .getAuthorizeRequest()
                .getAdditionalParameters();

        if (additionalParameters != null && !additionalParameters.isEmpty()) {
          customizer.additionalParameters(additionalParameters);
        }
      };
    }

    // refresh token filter has to be registered after the ExceptionTranslationFilter
    // which is the exact spot for the AuthorizationFilter.
    // This is needed to ensure correct exception mapping happened for
    // earlier filters. See registration order at the
    // org.springframework.security.config.annotation.web.builders.FilterOrderRegistration
    private void applyOauth2RefreshTokenFilter(
        final HttpSecurity httpSecurity,
        final OAuth2AuthorizedClientRepository authorizedClientRepository,
        final OAuth2AuthorizedClientManager authorizedClientManager) {
      if (authorizedClientRepository != null && authorizedClientManager != null) {
        httpSecurity.addFilterAfter(
            new OAuth2RefreshTokenFilter(authorizedClientRepository, authorizedClientManager),
            AuthorizationFilter.class);
      } else {
        LOG.warn(
            "OAuth2RefreshTokenFilter is not registered because no OAuth2AuthorizedClientService or OAuth2AuthorizedClientManager is available.");
      }
    }
  }

  protected static class NoContentResponseHandler
      implements AuthenticationSuccessHandler, LogoutSuccessHandler {

    @Override
    public void onAuthenticationSuccess(
        final HttpServletRequest request,
        final HttpServletResponse response,
        final Authentication authentication)
        throws IOException, ServletException {
      response.setStatus(HttpStatus.NO_CONTENT.value());
    }

    @Override
    public void onLogoutSuccess(
        final HttpServletRequest request,
        final HttpServletResponse response,
        final Authentication authentication)
        throws IOException, ServletException {
      onAuthenticationSuccess(request, response, authentication);
    }
  }

  protected static class NoContentWithCsrfTokenSuccessHandler extends NoContentResponseHandler {
    @Override
    public void onAuthenticationSuccess(
        final HttpServletRequest request,
        final HttpServletResponse response,
        final Authentication authentication)
        throws IOException, ServletException {
      super.onAuthenticationSuccess(request, response, authentication);

      addCsrfTokenWhenAvailable(request, response);
    }
  }
}
