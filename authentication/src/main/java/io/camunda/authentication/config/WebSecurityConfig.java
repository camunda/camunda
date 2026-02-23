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
import static java.util.stream.Collectors.toMap;

import io.camunda.authentication.ConditionalOnAuthenticationMethod;
import io.camunda.authentication.ConditionalOnProtectedApi;
import io.camunda.authentication.ConditionalOnUnprotectedApi;
import io.camunda.authentication.converter.OidcTokenAuthenticationConverter;
import io.camunda.authentication.converter.OidcUserAuthenticationConverter;
import io.camunda.authentication.converter.TokenClaimsConverter;
import io.camunda.authentication.converter.UsernamePasswordAuthenticationTokenConverter;
import io.camunda.authentication.csrf.CsrfProtectionRequestMatcher;
import io.camunda.authentication.exception.BasicAuthenticationNotSupportedException;
import io.camunda.authentication.filters.AdminUserCheckFilter;
import io.camunda.authentication.filters.OAuth2RefreshTokenFilter;
import io.camunda.authentication.filters.WebComponentAuthorizationCheckFilter;
import io.camunda.authentication.handler.AuthFailureHandler;
import io.camunda.authentication.handler.LoggingAuthenticationFailureHandler;
import io.camunda.authentication.handler.OAuth2AuthenticationExceptionHandler;
import io.camunda.authentication.service.MembershipService;
import io.camunda.security.auth.CamundaAuthenticationConverter;
import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.security.configuration.AuthenticationConfiguration;
import io.camunda.security.configuration.ConfiguredUser;
import io.camunda.security.configuration.OidcAuthenticationConfiguration;
import io.camunda.security.configuration.ProvidersConfiguration;
import io.camunda.security.configuration.SecurityConfiguration;
import io.camunda.security.configuration.headers.HeaderConfiguration;
import io.camunda.security.configuration.headers.values.FrameOptionMode;
import io.camunda.security.entity.AuthenticationMethod;
import io.camunda.security.reader.ResourceAccessProvider;
import io.camunda.service.GroupServices;
import io.camunda.service.RoleServices;
import io.camunda.service.TenantServices;
import io.camunda.spring.utils.ConditionalOnSecondaryStorageDisabled;
import io.camunda.spring.utils.ConditionalOnSecondaryStorageEnabled;
import io.micrometer.common.KeyValues;
import io.micrometer.observation.ObservationRegistry;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.actuate.logging.LoggersEndpoint;
import org.springframework.boot.security.autoconfigure.actuate.web.servlet.EndpointRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.FormHttpMessageConverter;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.ObjectPostProcessor;
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
import org.springframework.security.config.observation.SecurityObservationSettings;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProvider;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.endpoint.NimbusJwtClientAuthenticationParametersConverter;
import org.springframework.security.oauth2.client.endpoint.OAuth2RefreshTokenGrantRequest;
import org.springframework.security.oauth2.client.endpoint.RestClientRefreshTokenTokenResponseClient;
import org.springframework.security.oauth2.client.http.OAuth2ErrorResponseErrorHandler;
import org.springframework.security.oauth2.client.oidc.authentication.OidcIdTokenDecoderFactory;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.web.HttpSessionOAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.core.http.converter.OAuth2AccessTokenResponseHttpMessageConverter;
import org.springframework.security.oauth2.jose.jws.JwsAlgorithm;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoderFactory;
import org.springframework.security.oauth2.jwt.SupplierJwtDecoder;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.AuthenticationEntryPointFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.header.writers.CrossOriginEmbedderPolicyHeaderWriter.CrossOriginEmbedderPolicy;
import org.springframework.security.web.header.writers.CrossOriginOpenerPolicyHeaderWriter.CrossOriginOpenerPolicy;
import org.springframework.security.web.header.writers.CrossOriginResourcePolicyHeaderWriter.CrossOriginResourcePolicy;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.security.web.savedrequest.NullRequestCache;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.filter.OncePerRequestFilter;

@Configuration
@EnableWebSecurity
@Profile("consolidated-auth")
public class WebSecurityConfig {
  public static final String SESSION_COOKIE = "camunda-session";
  public static final String X_CSRF_TOKEN = "X-CSRF-TOKEN";
  public static final String LOGIN_URL = "/login";
  public static final String LOGOUT_URL = "/logout";
  public static final String REDIRECT_URI = "/sso-callback";
  public static final Set<String> API_PATHS = Set.of("/api/**", "/v1/**", "/v2/**", "/mcp/**");
  public static final Set<String> UNPROTECTED_API_PATHS =
      Set.of(
          // these v2 endpoints are public
          "/v2/license",
          "/v2/setup/user",
          "/v2/status",
          // deprecated Tasklist v1 Public Endpoints
          "/v1/external/process/**");
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
          // post logout decision endpoint
          "/post-logout",
          // swagger-ui endpoint
          "/swagger/**",
          "/swagger-ui/**",
          "/v3/api-docs/**",
          "/v2/rest-api.yaml",
          // deprecated Tasklist v1 Public Endpoints
          "/new/**",
          "/tasklist/new/**",
          "/favicon.ico");
  private static final String SPRING_DEFAULT_UI_CSS = "/default-ui.css";
  public static final Set<String> WEBAPP_PATHS =
      Set.of(
          "/login/**",
          "/logout",
          "/identity/**",
          "/admin/**",
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
          "/instances/*",
          SPRING_DEFAULT_UI_CSS);
  private static final Logger LOG = LoggerFactory.getLogger(WebSecurityConfig.class);
  // Used for chains that grant unauthenticated access, always comes first.
  private static final int ORDER_UNPROTECTED = 0;
  // Used for chains that protect the APIs or Webapp paths.
  private static final int ORDER_WEBAPP_API = 1;
  // Intended for a "catch-all-unhandled"-chain protecting all resources by default
  private static final int ORDER_UNHANDLED = 2;
  private static final String CAMUNDA_AUTHENTICATION_OBSERVATION_NAME =
      "camunda_authentication_external_requests";
  private static final KeyValues CAMUNDA_AUTHENTICATION_OBSERVATION_DOMAIN_IDENTITY_TAGS =
      KeyValues.of("domain", "identity");

  @Bean
  public SecurityObservationSettings defaultSecurityObservations() {
    return SecurityObservationSettings.withDefaults().build();
  }

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
      final HttpSecurity httpSecurity,
      final SecurityConfiguration securityConfiguration,
      final AuthFailureHandler authFailureHandler,
      final CookieCsrfTokenRepository csrfTokenRepository)
      throws Exception {
    LOG.warn(
        "The API is unprotected. Please disable {} for any deployment.",
        AuthenticationProperties.API_UNPROTECTED);
    final var filterChainBuilder =
        httpSecurity
            .securityMatcher(API_PATHS.toArray(String[]::new))
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
    // thus by default access to unhandled paths will always be denied
    return httpSecurity
        .securityMatcher("/**")
        .authorizeHttpRequests(
            authorizeHttpRequests -> authorizeHttpRequests.anyRequest().denyAll())
        .exceptionHandling(
            // for unhandled paths return a 404 instead of a 403 - improves UX to detect
            // misconfiguration of paths
            ex ->
                ex.accessDeniedHandler(
                    (request, response, accessDeniedException) ->
                        response.sendError(HttpServletResponse.SC_NOT_FOUND)))
        // disable csrf, anonymous auth to prevent session cookie creation on unhandled paths
        // avoiding follow-up request failures due to a session created by this chain
        .csrf(AbstractHttpConfigurer::disable)
        .cors(AbstractHttpConfigurer::disable)
        .formLogin(AbstractHttpConfigurer::disable)
        .anonymous(AbstractHttpConfigurer::disable)
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

  @Configuration
  @ConditionalOnAuthenticationMethod(AuthenticationMethod.BASIC)
  @ConditionalOnSecondaryStorageEnabled
  public static class BasicConfiguration {

    private final SecurityConfiguration securityConfiguration;

    public BasicConfiguration(final SecurityConfiguration securityConfiguration) {
      this.securityConfiguration = securityConfiguration;
    }

    @PostConstruct
    public void verifyBasicConfiguration() {
      if (isOidcConfigurationEnabled(securityConfiguration)) {
        throw new IllegalStateException(
            "Oidc configuration is not supported with `BASIC` authentication method");
      }
    }

    protected boolean isOidcConfigurationEnabled(
        final SecurityConfiguration securityConfiguration) {
      if (securityConfiguration.getAuthentication().getOidc() != null
          && securityConfiguration.getAuthentication().getOidc().isSet()) {
        return true;
      }

      return Optional.ofNullable(securityConfiguration.getAuthentication())
          .map(AuthenticationConfiguration::getProviders)
          .map(ProvidersConfiguration::getOidc)
          .map(Map::values)
          .map(values -> values.stream().anyMatch(OidcAuthenticationConfiguration::isSet))
          .orElse(false);
    }

    @Bean
    public CamundaAuthenticationConverter<Authentication> usernamePasswordAuthenticationConverter(
        final RoleServices roleServices,
        final GroupServices groupServices,
        final TenantServices tenantServices) {
      return new UsernamePasswordAuthenticationTokenConverter(
          roleServices, groupServices, tenantServices);
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
      LOG.info("The API is protected by HTTP Basic authentication.");
      final var filterChainBuilder =
          httpSecurity
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
                      sessionManagement.sessionCreationPolicy(SessionCreationPolicy.NEVER))
              .requestCache((cache) -> cache.requestCache(new NullRequestCache()));

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
      LOG.info("Web Applications Login/Logout is setup.");
      final var filterChainBuilder =
          httpSecurity
              .securityMatcher(WEBAPP_PATHS.toArray(String[]::new))
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
                  new WebComponentAuthorizationCheckFilter(
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

    private final SecurityConfiguration securityConfiguration;

    public OidcConfiguration(final SecurityConfiguration securityConfiguration) {
      this.securityConfiguration = securityConfiguration;
    }

    @PostConstruct
    public void verifyOidcConfiguration() {
      final List<ConfiguredUser> users = securityConfiguration.getInitialization().getUsers();
      if (users != null && !users.isEmpty()) {
        throw new IllegalStateException(
            "Creation of initial users is not supported with `OIDC` authentication method");
      }
    }

    @Bean
    public TokenClaimsConverter tokenClaimsConverter(
        final SecurityConfiguration securityConfiguration,
        final MembershipService membershipService) {
      return new TokenClaimsConverter(securityConfiguration, membershipService);
    }

    @Bean
    public CamundaAuthenticationConverter<Authentication> oidcTokenAuthenticationConverter(
        final TokenClaimsConverter tokenClaimsConverter) {
      return new OidcTokenAuthenticationConverter(tokenClaimsConverter);
    }

    @Bean
    public CamundaAuthenticationConverter<Authentication> oidcUserAuthenticationConverter(
        final OAuth2AuthorizedClientRepository authorizedClientRepository,
        final OidcAccessTokenDecoderFactory oidcAccessTokenDecoderFactory,
        final TokenClaimsConverter tokenClaimsConverter,
        final HttpServletRequest request) {
      return new OidcUserAuthenticationConverter(
          authorizedClientRepository, oidcAccessTokenDecoderFactory, tokenClaimsConverter, request);
    }

    @Bean
    public OidcAuthenticationConfigurationRepository oidcProviderRepository(
        final SecurityConfiguration securityConfiguration) {
      return new OidcAuthenticationConfigurationRepository(securityConfiguration);
    }

    @Bean
    public ClientRegistrationRepository clientRegistrationRepository(
        final OidcAuthenticationConfigurationRepository oidcProviderRepository) {
      final var clientRegistrations =
          oidcProviderRepository.getOidcAuthenticationConfigurations().entrySet().stream()
              .map(e -> createClientRegistration(e.getKey(), e.getValue()))
              .toList();
      return new InMemoryClientRegistrationRepository(clientRegistrations);
    }

    private ClientRegistration createClientRegistration(
        final String registrationId, final OidcAuthenticationConfiguration configuration) {
      try {
        return ClientRegistrationFactory.createClientRegistration(registrationId, configuration);
      } catch (final Exception e) {
        final String issuerUri = configuration.getIssuerUri();
        throw new IllegalStateException(
            "Unable to connect to the Identity Provider endpoint `"
                + issuerUri
                + "'. Double check that it is configured correctly, and if the problem persists, "
                + "contact your external Identity provider.",
            e);
      }
    }

    @Bean
    public TokenValidatorFactory tokenValidatorFactory(
        final SecurityConfiguration securityConfiguration,
        final OidcAuthenticationConfigurationRepository oidcAuthenticationConfigurationRepository) {
      return new TokenValidatorFactory(
          securityConfiguration, oidcAuthenticationConfigurationRepository);
    }

    @Bean
    public JwtDecoderFactory<ClientRegistration> idTokenDecoderFactory(
        final TokenValidatorFactory tokenValidatorFactory,
        final OidcAuthenticationConfigurationRepository oidcAuthenticationConfigurationRepository,
        final ClientRegistrationRepository clientRegistrationRepository) {
      final var decoderFactory = new OidcIdTokenDecoderFactory();
      decoderFactory.setJwtValidatorFactory(tokenValidatorFactory::createTokenValidator);

      final Map<String, OidcAuthenticationConfiguration> oidcAuthenticationConfigurations =
          oidcAuthenticationConfigurationRepository.getOidcAuthenticationConfigurations();
      final Map<ClientRegistration, JwsAlgorithm> clientRegistrationToAlgorithmMap =
          oidcAuthenticationConfigurations.entrySet().stream()
              .collect(
                  toMap(
                      e -> clientRegistrationRepository.findByRegistrationId(e.getKey()),
                      e -> parseAlgorithm(e.getValue().getIdTokenAlgorithm())));
      decoderFactory.setJwsAlgorithmResolver(clientRegistrationToAlgorithmMap::get);
      return decoderFactory;
    }

    private SignatureAlgorithm parseAlgorithm(final String algorithm) {
      final SignatureAlgorithm value = SignatureAlgorithm.from(algorithm);
      if (value == null) {
        throw new IllegalStateException("Unsupported signature algorithm: " + algorithm);
      }
      return value;
    }

    @Bean
    public JWSKeySelectorFactory jwsKeySelectorFactory() {
      return new JWSKeySelectorFactory();
    }

    @Bean
    public OidcAccessTokenDecoderFactory accessTokenDecoderFactory(
        final JWSKeySelectorFactory jwsKeySelectorFactory,
        final TokenValidatorFactory tokenValidatorFactory) {
      return new OidcAccessTokenDecoderFactory(jwsKeySelectorFactory, tokenValidatorFactory);
    }

    @Bean
    public JwtDecoder jwtDecoder(
        final OidcAccessTokenDecoderFactory oidcAccessTokenDecoderFactory,
        final ClientRegistrationRepository clientRegistrationRepository) {
      final var repository = (Iterable<ClientRegistration>) clientRegistrationRepository;
      final var clientRegistrations =
          StreamSupport.stream(repository.spliterator(), false).toList();

      if (clientRegistrations.size() == 1) {
        final var clientRegistration = clientRegistrations.getFirst();
        LOG.info(
            "Create Access Token JWT Decoder for OIDC Provider: {}",
            clientRegistration.getRegistrationId());
        return new SupplierJwtDecoder(
            () -> oidcAccessTokenDecoderFactory.createAccessTokenDecoder(clientRegistration));
      } else {
        LOG.info(
            "Create Issuer Aware JWT Decoder for multiple OIDC Providers: [{}]",
            clientRegistrations.stream()
                .map(ClientRegistration::getRegistrationId)
                .collect(Collectors.joining(", ")));
        return new SupplierJwtDecoder(
            () ->
                oidcAccessTokenDecoderFactory.createIssuerAwareAccessTokenDecoder(
                    clientRegistrations));
      }
    }

    @Bean
    public OAuth2AuthorizedClientRepository oAuth2AuthorizedClientRepository() {
      return new HttpSessionOAuth2AuthorizedClientRepository();
    }

    @Bean
    public AssertionJwkProvider assertionJwkProvider(
        final OidcAuthenticationConfigurationRepository oidcAuthenticationConfigurationRepository) {
      return new AssertionJwkProvider(oidcAuthenticationConfigurationRepository);
    }

    @Bean
    public OidcTokenEndpointCustomizer oidcTokenEndpointCustomizer(
        final OidcAuthenticationConfigurationRepository oidcAuthenticationConfigurationRepository,
        final AssertionJwkProvider assertionJwkProvider,
        final ObjectProvider<ObservationRegistry> observationRegistry) {
      return new OidcTokenEndpointCustomizer(
          oidcAuthenticationConfigurationRepository,
          assertionJwkProvider,
          restClient(observationRegistry.getIfAvailable(() -> ObservationRegistry.NOOP)));
    }

    @Bean
    public OAuth2AuthorizedClientManager authorizedClientManager(
        final ClientRegistrationRepository registrations,
        final OAuth2AuthorizedClientRepository authorizedClientRepository,
        final AssertionJwkProvider assertionJwkProvider,
        final ObjectProvider<ObservationRegistry> observationRegistry) {

      final var manager =
          new DefaultOAuth2AuthorizedClientManager(registrations, authorizedClientRepository);

      // we build a refresh token flow client manually to add support for private_key_jwt
      final var refreshClient = new RestClientRefreshTokenTokenResponseClient();
      refreshClient.setRestClient(
          restClient(observationRegistry.getIfAvailable(() -> ObservationRegistry.NOOP)));
      final var assertionConverter =
          new NimbusJwtClientAuthenticationParametersConverter<OAuth2RefreshTokenGrantRequest>(
              registration -> assertionJwkProvider.createJwk(registration.getRegistrationId()));
      refreshClient.addParametersConverter(assertionConverter);

      final OAuth2AuthorizedClientProvider provider =
          OAuth2AuthorizedClientProviderBuilder.builder()
              .authorizationCode()
              .refreshToken(c -> c.accessTokenResponseClient(refreshClient))
              .clientCredentials()
              .build();

      manager.setAuthorizedClientProvider(provider);
      return manager;
    }

    @Bean
    public OidcUserService oidcUserService(
        final ObjectProvider<ObservationRegistry> observationRegistry) {
      final var oauthUserService = new DefaultOAuth2UserService();
      final var oidcUserService = new OidcUserService();
      oidcUserService.setOauth2UserService(oauthUserService);

      // see DefaultOAuth2UserService#setRestOperations for the minimum handlers/converters required
      final var restTemplate = new RestTemplate();
      restTemplate.setErrorHandler(new OAuth2ErrorResponseErrorHandler());

      // instrument user service requests so we can track them via metrics
      restTemplate.setObservationConvention(
          new CustomDefaultClientRequestObservationConvention(
              CAMUNDA_AUTHENTICATION_OBSERVATION_NAME,
              CAMUNDA_AUTHENTICATION_OBSERVATION_DOMAIN_IDENTITY_TAGS));
      restTemplate.setObservationRegistry(
          observationRegistry.getIfAvailable(() -> ObservationRegistry.NOOP));

      oauthUserService.setRestOperations(restTemplate);
      return oidcUserService;
    }

    @Bean
    @Order(ORDER_WEBAPP_API)
    @ConditionalOnProtectedApi
    public SecurityFilterChain oidcApiSecurity(
        final HttpSecurity httpSecurity,
        final AuthFailureHandler authFailureHandler,
        final JwtDecoder jwtDecoder,
        final SecurityConfiguration securityConfiguration,
        final CookieCsrfTokenRepository csrfTokenRepository,
        final OAuth2AuthorizedClientRepository authorizedClientRepository,
        final OAuth2AuthorizedClientManager authorizedClientManager)
        throws Exception {
      final var filterChainBuilder =
          httpSecurity
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
              // do not create a session on api authentication, that's to be done on webapp login
              // only
              .sessionManagement(
                  (sessionManagement) ->
                      sessionManagement.sessionCreationPolicy(SessionCreationPolicy.NEVER))
              .requestCache((cache) -> cache.requestCache(new NullRequestCache()))
              .exceptionHandling(
                  (exceptionHandling) -> exceptionHandling.accessDeniedHandler(authFailureHandler))
              .sessionManagement(
                  configurer -> configurer.sessionCreationPolicy(SessionCreationPolicy.NEVER))
              .cors(AbstractHttpConfigurer::disable)
              .formLogin(AbstractHttpConfigurer::disable)
              .anonymous(AbstractHttpConfigurer::disable)
              .oauth2ResourceServer(
                  oauth2 ->
                      oauth2
                          .jwt(jwtConfigurer -> jwtConfigurer.decoder(jwtDecoder))
                          .withObjectPostProcessor(postProcessBearerTokenFailureHandler()))
              .oauth2Login(AbstractHttpConfigurer::disable)
              .oidcLogout(AbstractHttpConfigurer::disable)
              .logout(AbstractHttpConfigurer::disable);

      applyOauth2RefreshTokenFilter(
          httpSecurity, authorizedClientRepository, authorizedClientManager);
      applyCsrfConfiguration(httpSecurity, securityConfiguration, csrfTokenRepository);

      return filterChainBuilder.build();
    }

    @Bean
    @ConditionalOnSecondaryStorageEnabled
    public WebappRedirectStrategy webappRedirectStrategy() {
      return new WebappRedirectStrategy();
    }

    @Bean
    @ConditionalOnSecondaryStorageEnabled
    public LogoutSuccessHandler oidcLogoutSuccessHandler(
        final WebappRedirectStrategy redirectStrategy,
        final ClientRegistrationRepository repository,
        final SecurityConfiguration config) {
      final var oidcConfig = config.getAuthentication().getOidc();
      if (!oidcConfig.isIdpLogoutEnabled()) {
        return new NoContentResponseHandler();
      }

      final var handler = new CamundaOidcLogoutSuccessHandler(repository);
      handler.setPostLogoutRedirectUri("{baseUrl}/post-logout");
      handler.setRedirectStrategy(redirectStrategy);
      return handler;
    }

    @Bean
    @Order(ORDER_WEBAPP_API)
    @ConditionalOnSecondaryStorageEnabled
    public SecurityFilterChain oidcWebappSecurity(
        final HttpSecurity httpSecurity,
        final AuthFailureHandler authFailureHandler,
        final ClientRegistrationRepository clientRegistrationRepository,
        final OidcAuthenticationConfigurationRepository oidcProviderRepository,
        final JwtDecoder jwtDecoder,
        final SecurityConfiguration securityConfiguration,
        final CamundaAuthenticationProvider authenticationProvider,
        final ResourceAccessProvider resourceAccessProvider,
        final CookieCsrfTokenRepository csrfTokenRepository,
        final OAuth2AuthorizedClientRepository authorizedClientRepository,
        final OAuth2AuthorizedClientManager authorizedClientManager,
        final OidcTokenEndpointCustomizer tokenEndpointCustomizer,
        final LogoutSuccessHandler logoutSuccessHandler,
        final OidcUserService oidcUserService)
        throws Exception {
      final var filterChainBuilder =
          httpSecurity
              .securityMatcher(WEBAPP_PATHS.toArray(new String[0]))
              .authorizeHttpRequests(
                  (authorizeHttpRequests) ->
                      authorizeHttpRequests
                          .requestMatchers(SPRING_DEFAULT_UI_CSS)
                          .permitAll()
                          .requestMatchers(
                              "/tasklist/assets/**",
                              "/tasklist/client-config.js",
                              "/tasklist/custom.css",
                              "/tasklist/favicon.ico")
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
                      oauth2
                          .jwt(jwtConfigurer -> jwtConfigurer.decoder(jwtDecoder))
                          .withObjectPostProcessor(postProcessBearerTokenFailureHandler()))
              .oauth2Login(
                  oauthLoginConfigurer -> {
                    oauthLoginConfigurer
                        .clientRegistrationRepository(clientRegistrationRepository)
                        .authorizedClientRepository(authorizedClientRepository)
                        .userInfoEndpoint(c -> c.oidcUserService(oidcUserService))
                        .redirectionEndpoint(
                            redirectionEndpointConfig ->
                                redirectionEndpointConfig.baseUri(REDIRECT_URI))
                        .authorizationEndpoint(
                            authorization ->
                                authorization.authorizationRequestResolver(
                                    authorizationRequestResolver(
                                        clientRegistrationRepository, oidcProviderRepository)))
                        .tokenEndpoint(tokenEndpointCustomizer)
                        .failureHandler(new OAuth2AuthenticationExceptionHandler());
                  })
              .oidcLogout(httpSecurityOidcLogoutConfigurer -> {})
              .logout(
                  (logout) ->
                      logout
                          .logoutUrl(LOGOUT_URL)
                          .deleteCookies(SESSION_COOKIE, X_CSRF_TOKEN)
                          .invalidateHttpSession(true)
                          .logoutSuccessHandler(logoutSuccessHandler))
              .addFilterAfter(
                  new WebComponentAuthorizationCheckFilter(
                      securityConfiguration, authenticationProvider, resourceAccessProvider),
                  AuthorizationFilter.class);

      applyOauth2RefreshTokenFilter(
          httpSecurity, authorizedClientRepository, authorizedClientManager);
      applyCsrfConfiguration(httpSecurity, securityConfiguration, csrfTokenRepository);

      return filterChainBuilder.build();
    }

    private OAuth2AuthorizationRequestResolver authorizationRequestResolver(
        final ClientRegistrationRepository clientRegistrationRepository,
        final OidcAuthenticationConfigurationRepository oidcAuthenticationConfigurationRepository) {
      return new ClientAwareOAuth2AuthorizationRequestResolver(
          clientRegistrationRepository, oidcAuthenticationConfigurationRepository);
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

    private static ObjectPostProcessor<BearerTokenAuthenticationFilter>
        postProcessBearerTokenFailureHandler() {
      return new ObjectPostProcessor<>() {
        @Override
        public <O extends BearerTokenAuthenticationFilter> O postProcess(final O filter) {
          // the same failure handler as instantiated by spring per default.
          final AuthenticationEntryPointFailureHandler defaultFailureHandler =
              new AuthenticationEntryPointFailureHandler(new BearerTokenAuthenticationEntryPoint());
          // decorated with logging technical exceptions on WARN
          final LoggingAuthenticationFailureHandler loggingFailureHandler =
              new LoggingAuthenticationFailureHandler(defaultFailureHandler);
          filter.setAuthenticationFailureHandler(loggingFailureHandler);
          return filter;
        }
      };
    }

    private RestClient restClient(final ObservationRegistry observationRegistry) {
      // The message converters are taken from the expected rest client in
      // AbstractRestClientOAuth2AccessTokenResponseClient
      return RestClient.builder()
          .observationConvention(
              new CustomDefaultClientRequestObservationConvention(
                  CAMUNDA_AUTHENTICATION_OBSERVATION_NAME,
                  CAMUNDA_AUTHENTICATION_OBSERVATION_DOMAIN_IDENTITY_TAGS))
          .messageConverters(
              (messageConverters) -> {
                messageConverters.clear();
                messageConverters.add(new FormHttpMessageConverter());
                messageConverters.add(new OAuth2AccessTokenResponseHttpMessageConverter());
              })
          .defaultStatusHandler(new OAuth2ErrorResponseErrorHandler())
          .observationRegistry(observationRegistry)
          .build();
    }
  }

  /**
   * Configuration that provides fail-fast behavior when Basic Authentication is configured but
   * secondary storage is disabled (camunda.database.type=none). This prevents misleading security
   * flows and provides clear error messages.
   */
  @Configuration
  @ConditionalOnAuthenticationMethod(AuthenticationMethod.BASIC)
  @ConditionalOnSecondaryStorageDisabled
  public static class BasicAuthenticationNoDbConfiguration {

    @Bean
    public BasicAuthenticationNoDbFailFastBean basicAuthenticationNoDbFailFastBean() {
      throw new BasicAuthenticationNotSupportedException();
    }
  }

  /** Marker bean for Basic Auth no-db fail-fast configuration. */
  public static class BasicAuthenticationNoDbFailFastBean {}

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
