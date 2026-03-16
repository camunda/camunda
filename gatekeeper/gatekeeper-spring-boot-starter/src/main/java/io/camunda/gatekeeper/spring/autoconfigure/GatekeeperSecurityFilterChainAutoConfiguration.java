/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gatekeeper.spring.autoconfigure;

import io.camunda.gatekeeper.model.identity.AuthenticationMethod;
import io.camunda.gatekeeper.spi.CamundaAuthenticationProvider;
import io.camunda.gatekeeper.spi.SecurityPathProvider;
import io.camunda.gatekeeper.spring.condition.ConditionalOnAuthenticationMethod;
import io.camunda.gatekeeper.spring.condition.ConditionalOnProtectedApi;
import io.camunda.gatekeeper.spring.condition.ConditionalOnUnprotectedApi;
import io.camunda.gatekeeper.spring.config.GatekeeperProperties;
import io.camunda.gatekeeper.spring.config.HttpHeadersProperties;
import io.camunda.gatekeeper.spring.csrf.CsrfProtectionRequestMatcher;
import io.camunda.gatekeeper.spring.filter.OAuth2RefreshTokenFilter;
import io.camunda.gatekeeper.spring.filter.WebappFilterChainCustomizer;
import io.camunda.gatekeeper.spring.handler.AuthFailureHandler;
import io.camunda.gatekeeper.spring.handler.LoggingAuthenticationFailureHandler;
import io.camunda.gatekeeper.spring.handler.OAuth2AuthenticationExceptionHandler;
import io.camunda.gatekeeper.spring.oidc.ClientAwareOAuth2AuthorizationRequestResolver;
import io.camunda.gatekeeper.spring.oidc.OidcAuthenticationConfigurationRepository;
import io.camunda.gatekeeper.spring.oidc.OidcTokenEndpointCustomizer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashSet;
import java.util.LinkedHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.ObjectPostProcessor;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.AuthenticationEntryPointFailureHandler;
import org.springframework.security.web.authentication.DelegatingAuthenticationEntryPoint;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.authentication.logout.CompositeLogoutHandler;
import org.springframework.security.web.authentication.logout.CookieClearingLogoutHandler;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.header.writers.CrossOriginEmbedderPolicyHeaderWriter.CrossOriginEmbedderPolicy;
import org.springframework.security.web.header.writers.CrossOriginOpenerPolicyHeaderWriter.CrossOriginOpenerPolicy;
import org.springframework.security.web.header.writers.CrossOriginResourcePolicyHeaderWriter.CrossOriginResourcePolicy;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.security.web.savedrequest.NullRequestCache;
import org.springframework.security.web.util.matcher.RequestHeaderRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Auto-configuration for Spring Security filter chains. Defines filter chains for unprotected
 * paths, OIDC API/webapp, basic auth API/webapp, unprotected API, and a catch-all deny chain.
 *
 * <p>Ported from the monorepo's WebSecurityConfig with CSRF, OAuth2 refresh token, and secure
 * headers support.
 */
@AutoConfiguration(
    after = {
      GatekeeperAuthAutoConfiguration.class,
      GatekeeperOidcAutoConfiguration.class,
      GatekeeperBasicAuthAutoConfiguration.class
    })
@EnableWebSecurity
public final class GatekeeperSecurityFilterChainAutoConfiguration {

  public static final String SESSION_COOKIE = "camunda-session";
  public static final String X_CSRF_TOKEN = "X-CSRF-TOKEN";
  public static final String LOGIN_URL = "/login";
  public static final String LOGOUT_URL = "/logout";
  public static final String REDIRECT_URI = "/sso-callback";

  private static final Logger LOG =
      LoggerFactory.getLogger(GatekeeperSecurityFilterChainAutoConfiguration.class);

  // Order constants
  private static final int ORDER_UNPROTECTED = 0;
  private static final int ORDER_WEBAPP_API = 1;
  private static final int ORDER_UNHANDLED = 2;

  // ── Unprotected paths chain (always active) ──

  @Bean
  @Order(ORDER_UNPROTECTED)
  @ConditionalOnMissingBean(name = "unprotectedPathsSecurityFilterChain")
  public SecurityFilterChain unprotectedPathsSecurityFilterChain(
      final HttpSecurity http, final SecurityPathProvider pathProvider) throws Exception {
    return http.securityMatcher(pathProvider.unprotectedPaths().toArray(String[]::new))
        .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
        .csrf(AbstractHttpConfigurer::disable)
        .cors(AbstractHttpConfigurer::disable)
        .formLogin(AbstractHttpConfigurer::disable)
        .anonymous(AbstractHttpConfigurer::disable)
        .build();
  }

  // ── Unprotected API chain ──

  @Bean
  @ConditionalOnUnprotectedApi
  @Order(ORDER_UNPROTECTED)
  @ConditionalOnMissingBean(name = "unprotectedApiSecurityFilterChain")
  public SecurityFilterChain unprotectedApiSecurityFilterChain(
      final HttpSecurity http,
      final AuthFailureHandler authFailureHandler,
      final SecurityPathProvider pathProvider)
      throws Exception {
    LOG.warn(
        "The API is unprotected. This is intended for development only. API paths: {}",
        pathProvider.apiPaths());
    return http.securityMatcher(pathProvider.apiPaths().toArray(String[]::new))
        .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
        .cors(AbstractHttpConfigurer::disable)
        .exceptionHandling(eh -> eh.accessDeniedHandler(authFailureHandler))
        .formLogin(AbstractHttpConfigurer::disable)
        .anonymous(AbstractHttpConfigurer::disable)
        .csrf(AbstractHttpConfigurer::disable)
        .build();
  }

  // ── OIDC API chain ──

  @Bean
  @Order(ORDER_WEBAPP_API)
  @ConditionalOnAuthenticationMethod(AuthenticationMethod.OIDC)
  @ConditionalOnProtectedApi
  @ConditionalOnMissingBean(name = "oidcApiSecurityFilterChain")
  public SecurityFilterChain oidcApiSecurityFilterChain(
      final HttpSecurity http,
      final AuthFailureHandler authFailureHandler,
      final JwtDecoder jwtDecoder,
      final SecurityPathProvider pathProvider)
      throws Exception {
    LOG.info("The API is protected by OIDC JWT authentication.");
    return http.securityMatcher(pathProvider.apiPaths().toArray(String[]::new))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers(pathProvider.unprotectedApiPaths().toArray(String[]::new))
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .oauth2ResourceServer(
            oauth2 ->
                oauth2
                    .jwt(jwt -> jwt.decoder(jwtDecoder))
                    .authenticationEntryPoint(authFailureHandler)
                    .accessDeniedHandler(authFailureHandler)
                    .withObjectPostProcessor(postProcessBearerTokenFailureHandler()))
        .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.NEVER))
        .requestCache(cache -> cache.requestCache(new NullRequestCache()))
        .cors(AbstractHttpConfigurer::disable)
        .formLogin(AbstractHttpConfigurer::disable)
        .anonymous(AbstractHttpConfigurer::disable)
        .oauth2Login(AbstractHttpConfigurer::disable)
        .oidcLogout(AbstractHttpConfigurer::disable)
        .logout(AbstractHttpConfigurer::disable)
        .csrf(AbstractHttpConfigurer::disable)
        .build();
  }

  // ── OIDC Webapp chain ──

  @Bean
  @Order(ORDER_WEBAPP_API)
  @ConditionalOnAuthenticationMethod(AuthenticationMethod.OIDC)
  @ConditionalOnMissingBean(name = "oidcWebappSecurityFilterChain")
  public SecurityFilterChain oidcWebappSecurityFilterChain(
      final HttpSecurity http,
      final AuthFailureHandler authFailureHandler,
      final ClientRegistrationRepository clientRegistrationRepository,
      final JwtDecoder jwtDecoder,
      final OAuth2AuthorizedClientRepository authorizedClientRepository,
      final OAuth2AuthorizedClientManager authorizedClientManager,
      final OidcTokenEndpointCustomizer tokenEndpointCustomizer,
      final OidcAuthenticationConfigurationRepository oidcProviderRepository,
      final ObjectProvider<LogoutSuccessHandler> logoutSuccessHandlerProvider,
      final ObjectProvider<OidcUserService> oidcUserServiceProvider,
      final ObjectProvider<CamundaAuthenticationProvider> authenticationProvider,
      final ObjectProvider<WebappFilterChainCustomizer> filterChainCustomizers,
      final GatekeeperProperties properties,
      final SecurityPathProvider pathProvider)
      throws Exception {
    final var authConfig = properties.toAuthenticationConfig();

    final var filterChainBuilder =
        http.securityMatcher(pathProvider.webappPaths().toArray(String[]::new))
            .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
            .exceptionHandling(
                eh ->
                    eh.authenticationEntryPoint(oidcWebappAuthenticationEntryPoint())
                        .accessDeniedHandler(authFailureHandler))
            .cors(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)
            .anonymous(AbstractHttpConfigurer::disable)
            .oauth2ResourceServer(
                oauth2 ->
                    oauth2
                        .jwt(jwt -> jwt.decoder(jwtDecoder))
                        .withObjectPostProcessor(postProcessBearerTokenFailureHandler()))
            .oauth2Login(
                oauthLogin -> {
                  oauthLogin
                      .clientRegistrationRepository(clientRegistrationRepository)
                      .authorizedClientRepository(authorizedClientRepository)
                      .redirectionEndpoint(
                          redirectionEndpoint -> redirectionEndpoint.baseUri(REDIRECT_URI))
                      .authorizationEndpoint(
                          authorization ->
                              authorization.authorizationRequestResolver(
                                  new ClientAwareOAuth2AuthorizationRequestResolver(
                                      clientRegistrationRepository, oidcProviderRepository)))
                      .tokenEndpoint(tokenEndpointCustomizer)
                      .failureHandler(new OAuth2AuthenticationExceptionHandler());
                  oidcUserServiceProvider.ifAvailable(
                      service -> oauthLogin.userInfoEndpoint(c -> c.oidcUserService(service)));
                })
            .oidcLogout(oidcLogout -> {})
            .logout(
                logout -> {
                  logout
                      .logoutUrl(LOGOUT_URL)
                      .deleteCookies(SESSION_COOKIE, X_CSRF_TOKEN)
                      .invalidateHttpSession(true);
                  logoutSuccessHandlerProvider.ifAvailable(logout::logoutSuccessHandler);
                });

    // Apply consumer-provided filter chain customizations
    filterChainCustomizers
        .orderedStream()
        .forEach(
            customizer -> {
              try {
                customizer.customize(filterChainBuilder);
              } catch (final Exception e) {
                throw new IllegalStateException(
                    "Failed to apply webapp filter chain customizer", e);
              }
            });

    // Register the refresh token filter after AuthorizationFilter so that expired access tokens
    // are transparently refreshed before downstream filters see them. The filter needs a
    // LogoutHandler to force-logout users whose refresh tokens have also expired.
    final var logoutHandler =
        new CompositeLogoutHandler(
            new CookieClearingLogoutHandler(SESSION_COOKIE, X_CSRF_TOKEN),
            new SecurityContextLogoutHandler());
    filterChainBuilder.addFilterAfter(
        new OAuth2RefreshTokenFilter(
            authorizedClientRepository, authorizedClientManager, logoutHandler),
        AuthorizationFilter.class);

    applyCsrfConfiguration(filterChainBuilder, properties, pathProvider);
    setupSecureHeaders(filterChainBuilder, properties.getHttpHeaders());

    return filterChainBuilder.build();
  }

  // ── Basic auth API chain ──

  @Bean
  @Order(ORDER_WEBAPP_API)
  @ConditionalOnAuthenticationMethod(AuthenticationMethod.BASIC)
  @ConditionalOnProtectedApi
  @ConditionalOnMissingBean(name = "basicAuthApiSecurityFilterChain")
  public SecurityFilterChain basicAuthApiSecurityFilterChain(
      final HttpSecurity http,
      final AuthFailureHandler authFailureHandler,
      final SecurityPathProvider pathProvider)
      throws Exception {
    LOG.info("The API is protected by HTTP Basic authentication.");
    return http.securityMatcher(pathProvider.apiPaths().toArray(String[]::new))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers(pathProvider.unprotectedApiPaths().toArray(String[]::new))
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .cors(AbstractHttpConfigurer::disable)
        .formLogin(AbstractHttpConfigurer::disable)
        .anonymous(AbstractHttpConfigurer::disable)
        .httpBasic(Customizer.withDefaults())
        .exceptionHandling(
            eh ->
                eh.authenticationEntryPoint(authFailureHandler)
                    .accessDeniedHandler(authFailureHandler))
        .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.NEVER))
        .requestCache(cache -> cache.requestCache(new NullRequestCache()))
        .csrf(AbstractHttpConfigurer::disable)
        .build();
  }

  // ── Basic auth Webapp chain ──

  @Bean
  @Order(ORDER_WEBAPP_API)
  @ConditionalOnAuthenticationMethod(AuthenticationMethod.BASIC)
  @ConditionalOnMissingBean(name = "basicAuthWebappSecurityFilterChain")
  public SecurityFilterChain basicAuthWebappSecurityFilterChain(
      final HttpSecurity http,
      final AuthFailureHandler authFailureHandler,
      final ObjectProvider<CamundaAuthenticationProvider> authenticationProvider,
      final ObjectProvider<WebappFilterChainCustomizer> filterChainCustomizers,
      final GatekeeperProperties properties,
      final SecurityPathProvider pathProvider)
      throws Exception {
    LOG.info("Web Applications Login/Logout is set up with Basic Authentication.");
    final var authConfig = properties.toAuthenticationConfig();

    final var filterChainBuilder =
        http.securityMatcher(pathProvider.webappPaths().toArray(String[]::new))
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
            .cors(AbstractHttpConfigurer::disable)
            .anonymous(AbstractHttpConfigurer::disable)
            .formLogin(
                formLogin ->
                    formLogin
                        .loginPage(LOGIN_URL)
                        .loginProcessingUrl(LOGIN_URL)
                        .failureHandler(authFailureHandler))
            .logout(
                logout -> logout.logoutUrl(LOGOUT_URL).deleteCookies(SESSION_COOKIE, X_CSRF_TOKEN))
            .exceptionHandling(
                eh ->
                    eh.authenticationEntryPoint(authFailureHandler)
                        .accessDeniedHandler(authFailureHandler));

    // Apply consumer-provided filter chain customizations
    filterChainCustomizers
        .orderedStream()
        .forEach(
            customizer -> {
              try {
                customizer.customize(filterChainBuilder);
              } catch (final Exception e) {
                throw new IllegalStateException(
                    "Failed to apply webapp filter chain customizer", e);
              }
            });

    applyCsrfConfiguration(filterChainBuilder, properties, pathProvider);
    setupSecureHeaders(filterChainBuilder, properties.getHttpHeaders());

    return filterChainBuilder.build();
  }

  // ── Catch-all deny chain ──

  @Bean
  @Order(ORDER_UNHANDLED)
  @ConditionalOnMissingBean(name = "protectedUnhandledPathsSecurityFilterChain")
  public SecurityFilterChain protectedUnhandledPathsSecurityFilterChain(final HttpSecurity http)
      throws Exception {
    return http.securityMatcher("/**")
        .authorizeHttpRequests(auth -> auth.anyRequest().denyAll())
        .exceptionHandling(
            eh ->
                eh.accessDeniedHandler(
                    (request, response, accessDeniedException) ->
                        response.sendError(HttpServletResponse.SC_NOT_FOUND)))
        .csrf(AbstractHttpConfigurer::disable)
        .cors(AbstractHttpConfigurer::disable)
        .formLogin(AbstractHttpConfigurer::disable)
        .anonymous(AbstractHttpConfigurer::disable)
        .build();
  }

  // ── CSRF support ──

  @Bean
  @ConditionalOnMissingBean
  public CookieCsrfTokenRepository cookieCsrfTokenRepository() {
    final CookieCsrfTokenRepository repository = new CookieCsrfTokenRepository();
    repository.setHeaderName(X_CSRF_TOKEN);
    repository.setCookieName(X_CSRF_TOKEN);
    return repository;
  }

  /**
   * Applies CSRF configuration to a webapp filter chain. When CSRF is enabled, configures a {@link
   * CookieCsrfTokenRepository} with a {@link CsrfProtectionRequestMatcher} and adds a response
   * header filter that includes the CSRF token on authenticated GET/login responses. When disabled,
   * CSRF protection is turned off entirely.
   */
  private void applyCsrfConfiguration(
      final HttpSecurity http,
      final GatekeeperProperties properties,
      final SecurityPathProvider pathProvider)
      throws Exception {
    if (!properties.getCsrf().isEnabled()) {
      http.csrf(AbstractHttpConfigurer::disable);
      return;
    }

    final var allowedPaths = new HashSet<String>();
    allowedPaths.addAll(pathProvider.unprotectedPaths());
    allowedPaths.addAll(pathProvider.unprotectedApiPaths());
    allowedPaths.add(LOGIN_URL);
    allowedPaths.add(LOGOUT_URL);

    final var csrfTokenRepository = cookieCsrfTokenRepository();
    http.csrf(
        csrf ->
            csrf.csrfTokenRepository(csrfTokenRepository)
                .requireCsrfProtectionMatcher(new CsrfProtectionRequestMatcher(allowedPaths)));
    http.addFilterAfter(csrfTokenResponseHeaderFilter(), CsrfFilter.class);
  }

  /**
   * Filter that adds the CSRF token to the response header for authenticated GET requests and login
   * POST responses. This allows browser-based clients to read the token from the response header
   * and include it in subsequent state-changing requests.
   */
  private static OncePerRequestFilter csrfTokenResponseHeaderFilter() {
    return new OncePerRequestFilter() {
      @Override
      protected void doFilterInternal(
          final HttpServletRequest request,
          final HttpServletResponse response,
          final FilterChain filterChain)
          throws ServletException, IOException {
        filterChain.doFilter(request, response);
        final Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
          return;
        }
        final String path = request.getRequestURI();
        final String method = request.getMethod();
        final boolean isGetOrLogin =
            "GET".equalsIgnoreCase(method) || (path != null && path.contains(LOGIN_URL));
        final boolean isLogout = path != null && path.contains(LOGOUT_URL);
        if (isGetOrLogin && !isLogout) {
          final CsrfToken token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
          if (token != null) {
            response.setHeader(X_CSRF_TOKEN, token.getToken());
          }
        }
      }
    };
  }

  /**
   * Configures HTTP security response headers based on {@link HttpHeadersProperties}. Each header
   * can be individually enabled/disabled and customized via {@code
   * camunda.security.http-headers.*}.
   */
  private static void setupSecureHeaders(
      final HttpSecurity http, final HttpHeadersProperties headerConfig) throws Exception {
    http.headers(
        headers -> {
          if (!headerConfig.getContentTypeOptions().isEnabled()) {
            headers.contentTypeOptions(c -> c.disable());
          }

          if (!headerConfig.getCacheControl().isEnabled()) {
            headers.cacheControl(c -> c.disable());
          }

          final var hsts = headerConfig.getHsts();
          if (!hsts.isEnabled()) {
            headers.httpStrictTransportSecurity(h -> h.disable());
          } else {
            headers.httpStrictTransportSecurity(
                h ->
                    h.includeSubDomains(hsts.isIncludeSubDomains())
                        .maxAgeInSeconds(hsts.getMaxAgeInSeconds())
                        .preload(hsts.isPreload()));
          }

          final var frame = headerConfig.getFrameOptions();
          if (!frame.isEnabled()) {
            headers.frameOptions(f -> f.disable());
          } else if ("DENY".equalsIgnoreCase(frame.getMode())) {
            headers.frameOptions(f -> f.deny());
          } else {
            headers.frameOptions(f -> f.sameOrigin());
          }

          final var csp = headerConfig.getContentSecurityPolicy();
          if (csp.isEnabled()) {
            final var policy =
                csp.getPolicyDirectives() != null
                    ? csp.getPolicyDirectives()
                    : HttpHeadersProperties.DEFAULT_CSP_POLICY;
            if (csp.isReportOnly()) {
              headers.contentSecurityPolicy(c -> c.reportOnly().policyDirectives(policy));
            } else {
              headers.contentSecurityPolicy(c -> c.policyDirectives(policy));
            }
          }

          headers.referrerPolicy(
              rp -> rp.policy(ReferrerPolicy.valueOf(headerConfig.getReferrerPolicy())));

          if (headerConfig.getPermissionsPolicy() != null
              && !headerConfig.getPermissionsPolicy().isBlank()) {
            headers.permissionsPolicyHeader(pp -> pp.policy(headerConfig.getPermissionsPolicy()));
          }

          headers.crossOriginOpenerPolicy(
              coop ->
                  coop.policy(
                      CrossOriginOpenerPolicy.valueOf(headerConfig.getCrossOriginOpenerPolicy())));

          headers.crossOriginEmbedderPolicy(
              coep ->
                  coep.policy(
                      CrossOriginEmbedderPolicy.valueOf(
                          headerConfig.getCrossOriginEmbedderPolicy())));

          headers.crossOriginResourcePolicy(
              corp ->
                  corp.policy(
                      CrossOriginResourcePolicy.valueOf(
                          headerConfig.getCrossOriginResourcePolicy())));
        });
  }

  /**
   * Creates a delegating authentication entry point for the OIDC webapp chain. Requests with an
   * {@code Authorization} header receive a 401 via {@code BearerTokenAuthenticationEntryPoint}. All
   * other requests (browser navigations) are redirected to the OAuth2 authorization endpoint.
   *
   * <p>This is necessary because both {@code oauth2ResourceServer} and {@code oauth2Login} register
   * their own entry points, and in Spring Security 7.x the resource server's entry point takes
   * precedence — causing browser requests to receive 401 instead of a 302 redirect to the IdP.
   */
  private static AuthenticationEntryPoint oidcWebappAuthenticationEntryPoint() {
    final var bearerEntryPoint =
        new org.springframework.security.oauth2.server.resource.web
            .BearerTokenAuthenticationEntryPoint();
    final var oauthRedirectEntryPoint =
        new LoginUrlAuthenticationEntryPoint(
            "/oauth2/authorization/" + OidcAuthenticationConfigurationRepository.REGISTRATION_ID);
    final var entryPoints = new LinkedHashMap<RequestMatcher, AuthenticationEntryPoint>();
    entryPoints.put(new RequestHeaderRequestMatcher("Authorization"), bearerEntryPoint);
    final var delegatingEntryPoint = new DelegatingAuthenticationEntryPoint(entryPoints);
    delegatingEntryPoint.setDefaultEntryPoint(oauthRedirectEntryPoint);
    return delegatingEntryPoint;
  }

  private static ObjectPostProcessor<BearerTokenAuthenticationFilter>
      postProcessBearerTokenFailureHandler() {
    return new ObjectPostProcessor<>() {
      @Override
      public <O extends BearerTokenAuthenticationFilter> O postProcess(final O filter) {
        final var defaultFailureHandler =
            new AuthenticationEntryPointFailureHandler(
                new org.springframework.security.oauth2.server.resource.web
                    .BearerTokenAuthenticationEntryPoint());
        final var loggingFailureHandler =
            new LoggingAuthenticationFailureHandler(defaultFailureHandler);
        filter.setAuthenticationFailureHandler(loggingFailureHandler);
        return filter;
      }
    };
  }
}
