/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest.security.cloud;

import static io.camunda.optimize.OptimizeTomcatConfig.EXTERNAL_SUB_PATH;
import static io.camunda.optimize.rest.HealthRestService.READYZ_PATH;
import static io.camunda.optimize.rest.IngestionRestService.INGESTION_PATH;
import static io.camunda.optimize.rest.IngestionRestService.VARIABLE_SUB_PATH;
import static io.camunda.optimize.rest.LocalizationRestService.LOCALIZATION_PATH;
import static io.camunda.optimize.rest.UIConfigurationRestService.UI_CONFIGURATION_PATH;
import static io.camunda.optimize.rest.security.cloud.CCSaasAuth0WebSecurityConfig.AUTH_0_CLIENT_REGISTRATION_ID;
import static io.camunda.optimize.rest.security.cloud.CCSaasAuth0WebSecurityConfig.OAUTH_AUTH_ENDPOINT;
import static io.camunda.optimize.rest.security.cloud.CCSaasAuth0WebSecurityConfig.OAUTH_REDIRECT_ENDPOINT;
import static io.camunda.optimize.tomcat.OptimizeResourceConstants.ACTUATOR_ENDPOINT;
import static io.camunda.optimize.tomcat.OptimizeResourceConstants.REST_API_PATH;
import static io.camunda.optimize.tomcat.OptimizeResourceConstants.STATIC_RESOURCE_PATH;

import io.camunda.optimize.rest.security.AbstractSecurityConfigurerAdapter;
import io.camunda.optimize.rest.security.AuthenticationCookieFilter;
import io.camunda.optimize.rest.security.CustomPreAuthenticatedAuthenticationProvider;
import io.camunda.optimize.rest.security.oauth.AudienceValidator;
import io.camunda.optimize.rest.security.oauth.CustomClaimValidator;
import io.camunda.optimize.rest.security.oauth.RoleValidator;
import io.camunda.optimize.rest.security.oauth.ScopeValidator;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.security.AuthCookieService;
import io.camunda.optimize.service.security.SessionService;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.condition.CCSaaSCondition;
import io.camunda.optimize.service.util.configuration.security.CloudAuthConfiguration;
import io.camunda.optimize.tomcat.CCSaasRequestAdjustmentFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestRedirectFilter;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.web.util.UriComponentsBuilder;

@Configuration
@EnableWebSecurity
@Conditional(CCSaaSCondition.class)
public class CCSaaSSecurityConfigurerAdapter extends AbstractSecurityConfigurerAdapter {

  public static final String CAMUNDA_CLUSTER_ID_CLAIM_NAME = "https://camunda.com/clusterId";
  private static final List<String> ALLOWED_ORG_ROLES = Arrays.asList("admin", "analyst", "owner");

  private static final Logger LOG = LoggerFactory.getLogger(CCSaaSSecurityConfigurerAdapter.class);
  private final ClientRegistrationRepository clientRegistrationRepository;
  private final OAuth2AuthorizedClientService oAuth2AuthorizedClientService;

  public CCSaaSSecurityConfigurerAdapter(
      final ConfigurationService configurationService,
      final CustomPreAuthenticatedAuthenticationProvider preAuthenticatedAuthenticationProvider,
      final SessionService sessionService,
      final AuthCookieService authCookieService,
      final ClientRegistrationRepository clientRegistrationRepository,
      final OAuth2AuthorizedClientService oAuth2AuthorizedClientService) {
    super(
        configurationService,
        preAuthenticatedAuthenticationProvider,
        sessionService,
        authCookieService);
    this.clientRegistrationRepository = clientRegistrationRepository;
    this.oAuth2AuthorizedClientService = oAuth2AuthorizedClientService;
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

  @Bean
  @Order(Ordered.HIGHEST_PRECEDENCE) /* order of loading */
  FilterRegistrationBean<CCSaasRequestAdjustmentFilter> requestAdjuster() {
    LOG.debug("Registering filter 'requestAdjuster' (SaaS)...");
    final CCSaasRequestAdjustmentFilter ccsaasRequestAdjustmentFilter =
        new CCSaasRequestAdjustmentFilter(
            configurationService.getAuthConfiguration().getCloudAuthConfiguration().getClusterId());
    final FilterRegistrationBean<CCSaasRequestAdjustmentFilter> registration =
        new FilterRegistrationBean<>();
    registration.setOrder(Ordered.HIGHEST_PRECEDENCE); /* position in the chain */
    registration.addUrlPatterns("/*");
    registration.setFilter(ccsaasRequestAdjustmentFilter);
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
          // Then we configure the specific web security for CCSaaS
          .authorizeHttpRequests(
              httpRequests ->
                  httpRequests
                      // ready endpoint is public for infra
                      .requestMatchers(new AntPathRequestMatcher(createApiPath(READYZ_PATH)))
                      .permitAll()
                      // public share resources
                      .requestMatchers(
                          new AntPathRequestMatcher(EXTERNAL_SUB_PATH + "/"),
                          new AntPathRequestMatcher("/index*"),
                          new AntPathRequestMatcher(EXTERNAL_SUB_PATH + "/index*"),
                          new AntPathRequestMatcher(STATIC_RESOURCE_PATH + "/**"),
                          new AntPathRequestMatcher(
                              EXTERNAL_SUB_PATH + STATIC_RESOURCE_PATH + "/**"),
                          new AntPathRequestMatcher(EXTERNAL_SUB_PATH + "/*.js"),
                          new AntPathRequestMatcher(EXTERNAL_SUB_PATH + "/*.ico"))
                      .permitAll()
                      // public share related resources (API)
                      .requestMatchers(
                          new AntPathRequestMatcher(
                              createApiPath(EXTERNAL_SUB_PATH + DEEP_SUB_PATH_ANY)),
                          new AntPathRequestMatcher(EXTERNAL_SUB_PATH + REST_API_PATH + "/**"))
                      .permitAll()
                      // common public api resources
                      .requestMatchers(
                          new AntPathRequestMatcher(createApiPath(UI_CONFIGURATION_PATH)),
                          new AntPathRequestMatcher(createApiPath(LOCALIZATION_PATH)))
                      .permitAll()
                      .requestMatchers(new AntPathRequestMatcher(ACTUATOR_ENDPOINT + "/**"))
                      .permitAll()
                      // everything else requires authentication
                      .anyRequest()
                      .authenticated())
          .oauth2Login(
              oauth2 ->
                  oauth2
                      .clientRegistrationRepository(clientRegistrationRepository)
                      .authorizedClientService(oAuth2AuthorizedClientService)
                      .authorizationEndpoint(
                          authorizationEndpointConfig ->
                              authorizationEndpointConfig
                                  .baseUri(OAUTH_AUTH_ENDPOINT)
                                  .authorizationRequestRepository(
                                      cookieOAuth2AuthorizationRequestRepository()))
                      .redirectionEndpoint(
                          redirectionEndpointConfig ->
                              redirectionEndpointConfig.baseUri(OAUTH_REDIRECT_ENDPOINT))
                      .successHandler(getAuthenticationSuccessHandler()))
          .addFilterBefore(
              authenticationCookieFilter(http), OAuth2AuthorizationRequestRedirectFilter.class)
          .exceptionHandling(
              exceptionHandling ->
                  exceptionHandling
                      .defaultAuthenticationEntryPointFor(
                          new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                          new AntPathRequestMatcher(REST_API_PATH + "/**"))
                      .defaultAuthenticationEntryPointFor(
                          new AddClusterIdSubPathToRedirectAuthenticationEntryPoint(
                              OAUTH_AUTH_ENDPOINT + "/auth0"),
                          new AntPathRequestMatcher("/**")))
          .oauth2ResourceServer(
              oauth2resourceServer ->
                  oauth2resourceServer.jwt(jwtConfigurer -> jwtConfigurer.decoder(jwtDecoder())))
          .build();
    } catch (final Exception e) {
      throw new OptimizeRuntimeException(e);
    }
  }

  @Bean
  public HttpCookieOAuth2AuthorizationRequestRepository
      cookieOAuth2AuthorizationRequestRepository() {
    return new HttpCookieOAuth2AuthorizationRequestRepository(
        configurationService, new AuthorizationRequestCookieValueMapper());
  }

  @SuppressWarnings("unchecked")
  private JwtDecoder jwtDecoder() {
    final NimbusJwtDecoder jwtDecoder =
        NimbusJwtDecoder.withJwkSetUri(
                configurationService.getOptimizeApiConfiguration().getJwtSetUri())
            .build();
    final OAuth2TokenValidator<Jwt> audienceValidator =
        new AudienceValidator(
            configurationService
                .getAuthConfiguration()
                .getCloudAuthConfiguration()
                .getUserAccessTokenAudience()
                .orElse(""));
    final OAuth2TokenValidator<Jwt> profileValidator = new ScopeValidator("profile");
    final OAuth2TokenValidator<Jwt> roleValidator = new RoleValidator(ALLOWED_ORG_ROLES);
    // The default validator already contains validation for timestamp and X509 thumbprint
    final OAuth2TokenValidator<Jwt> combinedValidatorWithDefaults =
        JwtValidators.createDefaultWithValidators(
            audienceValidator, profileValidator, roleValidator);
    jwtDecoder.setJwtValidator(combinedValidatorWithDefaults);
    return jwtDecoder;
  }

  @Override
  @SuppressWarnings("unchecked")
  protected JwtDecoder publicApiJwtDecoder() {
    final NimbusJwtDecoder jwtDecoder =
        NimbusJwtDecoder.withJwkSetUri(
                configurationService.getOptimizeApiConfiguration().getJwtSetUri())
            .build();
    final OAuth2TokenValidator<Jwt> audienceValidator =
        new AudienceValidator(getAuth0Configuration().getAudience());
    final OAuth2TokenValidator<Jwt> clusterIdValidator =
        new CustomClaimValidator(
            CAMUNDA_CLUSTER_ID_CLAIM_NAME, getAuth0Configuration().getClusterId());
    final OAuth2TokenValidator<Jwt> roleValidator = new RoleValidator(ALLOWED_ORG_ROLES);
    // The default validator already contains validation for timestamp and X509 thumbprint
    final OAuth2TokenValidator<Jwt> combinedValidatorWithDefaults =
        JwtValidators.createDefaultWithValidators(
            audienceValidator, clusterIdValidator, roleValidator);
    jwtDecoder.setJwtValidator(combinedValidatorWithDefaults);
    return jwtDecoder;
  }

  private AuthenticationSuccessHandler getAuthenticationSuccessHandler() {
    return (request, response, authentication) -> {
      final DefaultOidcUser user = (DefaultOidcUser) authentication.getPrincipal();
      final String userId = user.getIdToken().getSubject();
      final String sessionToken = sessionService.createAuthToken(userId);

      if (hasAccess(user)) {
        // spring security internally stores the access token as an authorized client, we retrieve
        // it here to store it
        // in a cookie to allow potential other Optimize webapps to reuse it and keep the server
        // stateless
        final OAuth2AccessToken serviceAccessToken =
            oAuth2AuthorizedClientService
                .loadAuthorizedClient(AUTH_0_CLIENT_REGISTRATION_ID, userId)
                .getAccessToken();

        final Instant cookieExpiryDate =
            determineCookieExpiryDate(sessionToken, serviceAccessToken)
                .orElseThrow(
                    () ->
                        new OptimizeRuntimeException(
                            "Could not determine a cookie expiry date. This is likely a bug, please report."));
        authCookieService
            .createOptimizeServiceTokenCookies(
                serviceAccessToken, cookieExpiryDate, request.getScheme())
            .forEach(response::addCookie);
        authCookieService
            .createOptimizeAuthCookies(sessionToken, cookieExpiryDate, request.getScheme())
            .forEach(response::addCookie);

        // we can't redirect to the previously accesses path or the root of the application as the
        // Optimize Cookie
        // won't be sent by the browser in this case. This is because the chain of requests that
        // lead to the
        // authenticationOptimizeWebSecurityConfigurerAdapter success are initiated by the auth0
        // server and the
        // same-site:strict property prevents the cookie to be transmitted in such a case.
        // See https://stackoverflow.com/a/42220786
        // This static page breaks the redirect chain initiated from the auth0 login and forces the
        // browser to start
        // a new request chain which then allows the Optimize auth cookie to be provided to be read
        // by the
        // authenticationCookieFilter granting the user access.
        // This is also a technique documented at w3.org
        // https://www.w3.org/TR/WCAG20-TECHS/H76.html
        response.setContentType(MediaType.TEXT_HTML_VALUE);
        response
            .getWriter()
            // @formatter:off
            .print(
                String.format(
                    """
            <html>
              <head><meta http-equiv="refresh" content="1; URL='%s/'"/></head>
              <body>
                <script>
                  var path = '%s/';
                  if (location.hash) {
                    path += location.hash;
                  }
                  location = path;
                </script>
                <p align="center">Successfully authenticated!</p>
                <p align="center">Click <a href="%s/">here</a> if you don't get redirected automatically.</p>
              </body>
            </html>
            """,
                    getClusterIdPath(), getClusterIdPath(), getClusterIdPath()));
        // @formatter:on
      } else {
        response.setStatus(HttpStatus.FORBIDDEN.value());
      }
    };
  }

  private Optional<Instant> determineCookieExpiryDate(
      final String sessionToken, final OAuth2AccessToken serviceAccessToken) {
    final Instant serviceTokenExpiry = serviceAccessToken.getExpiresAt();
    final Instant sessionTokenExpiry =
        authCookieService.getOptimizeAuthCookieTokenExpiryDate(sessionToken).orElse(null);
    return Stream.of(serviceTokenExpiry, sessionTokenExpiry)
        .filter(Objects::nonNull)
        .min(Instant::compareTo);
  }

  private boolean hasAccess(final DefaultOidcUser user) {
    boolean accessGranted = false;
    final OidcUserInfo userInfo = user.getUserInfo();
    final String organizationClaimName = getAuth0Configuration().getOrganizationClaimName();
    if (userInfo.getClaim(organizationClaimName) instanceof List) {
      final List<Map<String, Object>> organisations = userInfo.getClaim(organizationClaimName);
      accessGranted =
          organisations.stream()
              .map(orgEntry -> (String) orgEntry.get("id"))
              .anyMatch(
                  organisationId ->
                      getAuth0Configuration().getOrganizationId().equals(organisationId));
    }
    return accessGranted;
  }

  private String getClusterIdPath() {
    return Optional.ofNullable(getAuth0Configuration().getClusterId())
        .filter(StringUtils::isNotBlank)
        .map(id -> "/" + id)
        .orElse("");
  }

  private CloudAuthConfiguration getAuth0Configuration() {
    return configurationService.getAuthConfiguration().getCloudAuthConfiguration();
  }

  /**
   * In Camunda Cloud environments all apps are served under a sub-path but there is no reverse
   * proxy in place. Thus any redirects issued within the app are required to contain the clusterId
   * as sub-path.
   */
  public class AddClusterIdSubPathToRedirectAuthenticationEntryPoint
      extends LoginUrlAuthenticationEntryPoint {

    public AddClusterIdSubPathToRedirectAuthenticationEntryPoint(final String loginFormUrl) {
      super(loginFormUrl);
    }

    @Override
    protected String determineUrlToUseForThisRequest(
        final HttpServletRequest request,
        final HttpServletResponse response,
        final AuthenticationException exception) {
      final String redirect = super.determineUrlToUseForThisRequest(request, response, exception);
      return UriComponentsBuilder.fromPath(getClusterIdPath() + redirect).toUriString();
    }
  }
}
