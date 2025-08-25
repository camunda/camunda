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
import io.camunda.authentication.CamundaUserDetailsService;
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
import io.camunda.authentication.filters.CertificateBasedOAuth2Filter;
import io.camunda.authentication.filters.MutualTlsAuthenticationFilter;
import io.camunda.authentication.filters.OAuth2RefreshTokenFilter;
import io.camunda.authentication.filters.RequestScopedAuthenticationRestorationFilter;
import io.camunda.authentication.filters.SessionAuthenticationRefreshFilter;
import io.camunda.authentication.filters.WebComponentAuthorizationCheckFilter;
import io.camunda.authentication.handler.AuthFailureHandler;
import io.camunda.authentication.oauth.ClientAssertionConstants;
import io.camunda.authentication.service.MembershipService;
import io.camunda.security.auth.CamundaAuthenticationConverter;
import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.security.auth.OidcGroupsLoader;
import io.camunda.security.configuration.ConfiguredUser;
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
import io.camunda.spring.utils.ConditionalOnSecondaryStorageDisabled;
import io.camunda.spring.utils.ConditionalOnSecondaryStorageEnabled;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.autoconfigure.security.servlet.EndpointRequest;
import org.springframework.boot.actuate.logging.LoggersEndpoint;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
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
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.header.writers.CrossOriginEmbedderPolicyHeaderWriter.CrossOriginEmbedderPolicy;
import org.springframework.security.web.header.writers.CrossOriginOpenerPolicyHeaderWriter.CrossOriginOpenerPolicy;
import org.springframework.security.web.header.writers.CrossOriginResourcePolicyHeaderWriter.CrossOriginResourcePolicy;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;
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
  public static final Set<String> API_V2_PATHS = Set.of("/v2/**");
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
    // thus by default unhandled paths are always protected
    return httpSecurity
        .securityMatcher("/**")
        .authorizeHttpRequests(
            (authorizeHttpRequests) -> authorizeHttpRequests.anyRequest().denyAll())
        .build();
  }

  private static void setupSecureHeaders(
      final HeadersConfigurer<HttpSecurity> headers,
      final HeaderConfiguration headerConfig,
      final boolean isSaas) {

    // Configure SecurityContext to be inherited by child threads to handle async processing
    SecurityContextHolder.setStrategyName(SecurityContextHolder.MODE_INHERITABLETHREADLOCAL);

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
    repository.setCookieCustomizer(cc -> cc.httpOnly(true));
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

    @Bean
    public CamundaAuthenticationConverter<Authentication> usernamePasswordAuthenticationConverter(
        final RoleServices roleServices,
        final GroupServices groupServices,
        final TenantServices tenantServices) {
      return new UsernamePasswordAuthenticationTokenConverter(
          roleServices, groupServices, tenantServices);
    }

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
        final SecurityConfiguration securityConfiguration,
        final CookieCsrfTokenRepository csrfTokenRepository,
        final Optional<MutualTlsAuthenticationFilter> mutualTlsAuthenticationFilter)
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
                      sessionManagement.sessionCreationPolicy(SessionCreationPolicy.NEVER));

      // Add mTLS filter if enabled
      if (mutualTlsAuthenticationFilter.isPresent()) {
        filterChainBuilder.addFilterBefore(
            mutualTlsAuthenticationFilter.get(), AuthorizationFilter.class);
      }

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
        final CookieCsrfTokenRepository csrfTokenRepository,
        final Optional<MutualTlsAuthenticationFilter> mutualTlsAuthenticationFilter,
        final Optional<MutualTlsProperties> mtlsProperties)
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
                  new SessionAuthenticationRefreshFilter(
                      authenticationProvider, securityConfiguration.getAuthentication()),
                  SecurityContextHolderFilter.class)
              .addFilterAfter(
                  new WebComponentAuthorizationCheckFilter(
                      securityConfiguration, authenticationProvider, resourceAccessProvider),
                  AuthorizationFilter.class)
              .addFilterBefore(
                  new AdminUserCheckFilter(securityConfiguration, roleServices, mtlsProperties),
                  AuthorizationFilter.class);

      // Add mTLS filter if enabled
      if (mutualTlsAuthenticationFilter.isPresent()) {
        filterChainBuilder.addFilterBefore(
            mutualTlsAuthenticationFilter.get(), AuthorizationFilter.class);
      }

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
        final JwtDecoder jwtDecoder,
        final TokenClaimsConverter tokenClaimsConverter,
        final HttpServletRequest request) {
      return new OidcUserAuthenticationConverter(
          authorizedClientRepository, jwtDecoder, tokenClaimsConverter, request);
    }

    @Bean
    public CamundaAuthenticationConverter<Authentication>
        oidcUsernamePasswordAuthenticationConverter(
            final RoleServices roleServices,
            final GroupServices groupServices,
            final TenantServices tenantServices) {
      return new UsernamePasswordAuthenticationTokenConverter(
          roleServices, groupServices, tenantServices);
    }

    @Bean
    public CertificateClientAssertionService certificateClientAssertionService() {
      return new CertificateClientAssertionService();
    }

    @Bean
    public RestTemplate restTemplate() {
      return new RestTemplate();
    }

    @Bean
    @ConditionalOnExpression("${camunda.security.authentication.entra.enabled:false}")
    public CertificateBasedOAuth2Filter certificateBasedOAuth2Filter(
        final SecurityConfiguration securityConfiguration,
        final CertificateClientAssertionService certificateClientAssertionService,
        final RestTemplate restTemplate,
        final JwtDecoder jwtDecoder) {
      LOG.info(
          "Creating CertificateBasedOAuth2Filter - MS Entra certificate authentication enabled");
      return new CertificateBasedOAuth2Filter(
          securityConfiguration, certificateClientAssertionService, restTemplate, jwtDecoder);
    }

    // mTLS beans are now configured in MtlsConfig class with custom condition

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
      try {
        return new InMemoryClientRegistrationRepository(
            OidcClientRegistration.create(securityConfiguration.getAuthentication().getOidc()));
      } catch (final Exception e) {
        final String issuerUri = securityConfiguration.getAuthentication().getOidc().getIssuerUri();
        throw new IllegalStateException(
            "Unable to connect to the Identity Provider endpoint `"
                + issuerUri
                + "'. Double check that it is configured correctly, and if the problem persists, "
                + "contact your external Identity provider.",
            e);
      }
    }

    @Bean
    public OidcGroupsLoader oidcGroupsLoader(final SecurityConfiguration securityConfiguration) {
      final String groupsClaim =
          securityConfiguration.getAuthentication().getOidc().getGroupsClaim();
      return new OidcGroupsLoader(groupsClaim);
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
      if (ClientAssertionConstants.CLIENT_ASSERTION_GRANT_TYPE.equals(
          securityConfiguration.getAuthentication().getOidc().getGrantType())) {
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
        final SecurityConfiguration securityConfiguration,
        final CookieCsrfTokenRepository csrfTokenRepository,
        final OAuth2AuthorizedClientRepository authorizedClientRepository,
        final OAuth2AuthorizedClientManager authorizedClientManager,
        final Optional<CertificateBasedOAuth2Filter> certificateBasedOAuth2Filter,
        final Optional<MutualTlsAuthenticationFilter> mutualTlsAuthenticationFilter)
        throws Exception {
      final String[] apiPathsForThisChain;
      final boolean isClientCredentials =
          ClientAssertionConstants.CLIENT_ASSERTION_GRANT_TYPE.equals(
              securityConfiguration.getAuthentication().getOidc().getGrantType());
      final boolean isCertificateBasedAuth =
          isClientCredentials
              && securityConfiguration.getAuthentication().getOidc().isClientAssertionEnabled();

      if (isClientCredentials) {
        if (isCertificateBasedAuth) {
          // For certificate-based client credentials, exclude /v2/** as it's handled by
          // identityClientCredentialsSecurityFilterChain
          apiPathsForThisChain = new String[] {"/api/**", "/v1/**"};
        } else {
          // For non-certificate client credentials, include all paths
          apiPathsForThisChain = new String[] {"/api/**", "/v1/**", "/v2/**"};
        }
      } else {
        apiPathsForThisChain = API_PATHS.toArray(new String[0]);
      }

      final var filterChainBuilder =
          httpSecurity
              .securityMatcher(apiPathsForThisChain)
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
                  oauth2 -> oauth2.jwt(jwtConfigurer -> jwtConfigurer.decoder(jwtDecoder)))
              .oauth2Login(AbstractHttpConfigurer::disable)
              .oidcLogout(AbstractHttpConfigurer::disable)
              .logout(AbstractHttpConfigurer::disable);

      // Add filters only if they are enabled
      if (certificateBasedOAuth2Filter.isPresent()) {
        filterChainBuilder.addFilterBefore(
            certificateBasedOAuth2Filter.get(), AuthorizationFilter.class);
      }
      if (mutualTlsAuthenticationFilter.isPresent()) {
        filterChainBuilder.addFilterBefore(
            mutualTlsAuthenticationFilter.get(), AuthorizationFilter.class);
      }

      applyOauth2RefreshTokenFilter(
          filterChainBuilder, authorizedClientRepository, authorizedClientManager);
      applyCsrfConfiguration(filterChainBuilder, securityConfiguration, csrfTokenRepository);

      return filterChainBuilder.build();
    }

    @Bean
    @Order(ORDER_UNPROTECTED - 1) // Highest priority to ensure it runs first
    @ConditionalOnAuthenticationMethod(AuthenticationMethod.OIDC)
    public SecurityFilterChain identityClientCredentialsSecurityFilterChain(
        final HttpSecurity httpSecurity,
        final SecurityConfiguration securityConfiguration,
        final CamundaAuthenticationProvider authenticationProvider,
        final ResourceAccessProvider resourceAccessProvider,
        final CookieCsrfTokenRepository csrfTokenRepository,
        final OAuth2AuthorizedClientManager authorizedClientManager,
        final ClientRegistrationRepository clientRegistrationRepository,
        final AuthorizationServices authorizationServices,
        final Optional<CertificateBasedOAuth2Filter> certificateBasedOAuth2Filter,
        final Optional<MutualTlsAuthenticationFilter> mutualTlsAuthenticationFilter,
        final JwtDecoder jwtDecoder,
        @Value("${server.ssl.enabled:false}") final boolean sslEnabled)
        throws Exception {

      final String grantType = securityConfiguration.getAuthentication().getOidc().getGrantType();
      if (!ClientAssertionConstants.CLIENT_ASSERTION_GRANT_TYPE.equals(grantType)) {
        return null;
      }

      final String[] identityPaths = API_V2_PATHS.toArray(String[]::new);

      HttpSecurity http =
          httpSecurity
              .securityMatcher(identityPaths)
              .authorizeHttpRequests(
                  (authorizeHttpRequests) -> authorizeHttpRequests.anyRequest().authenticated())
              .exceptionHandling(
                  ex ->
                      ex.authenticationEntryPoint(
                              (request, response, authException) -> {
                                // Try to restore authentication from session before failing
                                try {
                                  final var session = request.getSession(false);
                                  if (session != null) {
                                    final var storedAuth =
                                        (Authentication)
                                            session.getAttribute(
                                                ClientAssertionConstants.SESSION_KEY);
                                    if (storedAuth != null && storedAuth.isAuthenticated()) {
                                      SecurityContextHolder.getContext()
                                          .setAuthentication(storedAuth);
                                      LOG.info(
                                          "Restored authentication from session in error handler: {} - Thread: {}",
                                          storedAuth.getName(),
                                          Thread.currentThread().getName());
                                      return;
                                    }
                                  }
                                } catch (final Exception e) {
                                  LOG.warn(
                                      "Failed to restore authentication from session in error handler",
                                      e);
                                }

                                LOG.error(
                                    "Authentication failed for certificate-based request: {} - Exception: {} - Thread: {} - SecurityContext: {}",
                                    request.getRequestURI(),
                                    authException.getMessage(),
                                    Thread.currentThread().getName(),
                                    SecurityContextHolder.getContext().hashCode());
                                final var currentAuth =
                                    SecurityContextHolder.getContext().getAuthentication();
                                LOG.error(
                                    "Current authentication state: Type: {}, Principal: {}, Authenticated: {}",
                                    currentAuth != null
                                        ? currentAuth.getClass().getSimpleName()
                                        : "null",
                                    currentAuth != null ? currentAuth.getName() : "null",
                                    currentAuth != null ? currentAuth.isAuthenticated() : false);
                                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                                response.setContentType("application/json");
                                response
                                    .getWriter()
                                    .write(
                                        "{\"error\":\"Certificate-based authentication required: "
                                            + authException.getMessage()
                                            + "\"}");
                              })
                          .accessDeniedHandler(
                              (request, response, accessDeniedException) -> {
                                LOG.error(
                                    "Access denied for certificate-based request: {} - Exception: {}",
                                    request.getRequestURI(),
                                    accessDeniedException.getMessage());
                                final var currentAuth =
                                    SecurityContextHolder.getContext().getAuthentication();
                                LOG.error(
                                    "Current authentication state during access denied: Type: {}, Principal: {}, Authenticated: {}",
                                    currentAuth != null
                                        ? currentAuth.getClass().getSimpleName()
                                        : "null",
                                    currentAuth != null ? currentAuth.getName() : "null",
                                    currentAuth != null ? currentAuth.isAuthenticated() : false);
                                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                                response.setContentType("application/json");
                                response
                                    .getWriter()
                                    .write(
                                        "{\"error\":\"Access denied: "
                                            + accessDeniedException.getMessage()
                                            + "\"}");
                              }))
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
                            final var corsConfig =
                                new org.springframework.web.cors.CorsConfiguration();

                            // For local development, allow all origins. For production, restrict
                            // origins
                            if (sslEnabled) {
                              // Production: Allow only same origin and specific domains
                              corsConfig.setAllowedOrigins(
                                  java.util.List.of(
                                      request.getScheme()
                                          + "://"
                                          + request.getServerName()
                                          + ":"
                                          + request.getServerPort()));
                            } else {
                              // Local development: Allow localhost origins
                              corsConfig.setAllowedOriginPatterns(
                                  java.util.List.of("http://localhost:*", "http://127.0.0.1:*"));
                            }

                            corsConfig.setAllowedMethods(
                                java.util.List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
                            corsConfig.setAllowedHeaders(java.util.List.of("*"));
                            corsConfig.setAllowCredentials(true);
                            corsConfig.setMaxAge(3600L);
                            return corsConfig;
                          }))
              .formLogin(AbstractHttpConfigurer::disable)
              .oauth2Login(AbstractHttpConfigurer::disable)
              .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
              .anonymous(AbstractHttpConfigurer::disable)
              .sessionManagement(
                  session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
              .securityContext(
                  securityContext ->
                      securityContext.securityContextRepository(
                          new HttpSessionSecurityContextRepository()));

      // Add conditional filters
      if (certificateBasedOAuth2Filter.isPresent()) {
        http = http.addFilterBefore(certificateBasedOAuth2Filter.get(), AuthorizationFilter.class);
      }
      if (mutualTlsAuthenticationFilter.isPresent()) {
        http = http.addFilterBefore(mutualTlsAuthenticationFilter.get(), AuthorizationFilter.class);
      }

      return http.addFilterBefore(
              new RequestScopedAuthenticationRestorationFilter(), AuthorizationFilter.class)
          .addFilterBefore(
              new WebComponentAuthorizationCheckFilter(
                  securityConfiguration, authenticationProvider, resourceAccessProvider),
              AuthorizationFilter.class)
          .csrf(
              securityConfiguration.getCsrf().isEnabled()
                  ? csrf -> configureCsrf(csrfTokenRepository, csrf)
                  : AbstractHttpConfigurer::disable)
          .build();
    }

    @Bean
    public CookieSerializer cookieSerializer(
        @Value("${server.ssl.enabled:false}") final boolean sslEnabled) {
      final DefaultCookieSerializer serializer = new DefaultCookieSerializer();
      // cross-origin cookie to allow identity frontend to work with backend API, otherwise 403
      serializer.setSameSite("None");

      // Use secure cookies only when SSL/HTTPS is enabled
      serializer.setUseSecureCookie(sslEnabled);
      return serializer;
    }

    @Bean
    @Order(ORDER_WEBAPP_API)
    @ConditionalOnSecondaryStorageEnabled
    public SecurityFilterChain oidcWebappSecurity(
        final HttpSecurity httpSecurity,
        final AuthFailureHandler authFailureHandler,
        final ClientRegistrationRepository clientRegistrationRepository,
        final JwtDecoder jwtDecoder,
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
              .authorizeHttpRequests(
                  (authorizeHttpRequests) -> {
                    // If using client credentials flow, allow access without interactive login
                    if (ClientAssertionConstants.CLIENT_ASSERTION_GRANT_TYPE.equals(
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
              .exceptionHandling(
                  (exceptionHandling) -> exceptionHandling.accessDeniedHandler(authFailureHandler))
              .cors(AbstractHttpConfigurer::disable)
              .formLogin(AbstractHttpConfigurer::disable)
              .anonymous(AbstractHttpConfigurer::disable)
              .oauth2ResourceServer(
                  oauth2 -> oauth2.jwt(jwtConfigurer -> jwtConfigurer.decoder(jwtDecoder)))
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
              .sessionManagement(
                  session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
              .securityContext(
                  securityContext ->
                      securityContext.securityContextRepository(
                          new HttpSessionSecurityContextRepository()))
              .addFilterAfter(
                  new SessionAuthenticationRefreshFilter(
                      authenticationProvider, securityConfiguration.getAuthentication()),
                  SecurityContextHolderFilter.class)
              .addFilterAfter(
                  new WebComponentAuthorizationCheckFilter(
                      securityConfiguration, authenticationProvider, resourceAccessProvider),
                  AuthorizationFilter.class);

      applyOauth2RefreshTokenFilter(
          filterChainBuilder, authorizedClientRepository, authorizedClientManager);
      applyCsrfConfiguration(filterChainBuilder, securityConfiguration, csrfTokenRepository);

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
