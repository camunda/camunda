/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.rest.security.cloud;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.apache.commons.lang3.StringUtils;
import org.camunda.optimize.rest.security.AuthenticationCookieFilter;
import org.camunda.optimize.rest.security.CustomPreAuthenticatedAuthenticationProvider;
import org.camunda.optimize.rest.security.oauth.AudienceValidator;
import org.camunda.optimize.rest.security.oauth.ScopeValidator;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.security.AuthCookieService;
import org.camunda.optimize.service.security.SessionService;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.configuration.condition.CCSaaSCondition;
import org.camunda.optimize.service.util.configuration.security.CloudAuthConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestRedirectFilter;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.web.util.UriComponentsBuilder;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

import static org.camunda.optimize.OptimizeJettyServerCustomizer.EXTERNAL_SUB_PATH;
import static org.camunda.optimize.jetty.OptimizeResourceConstants.ACTUATOR_ENDPOINT;
import static org.camunda.optimize.jetty.OptimizeResourceConstants.REST_API_PATH;
import static org.camunda.optimize.jetty.OptimizeResourceConstants.STATIC_RESOURCE_PATH;
import static org.camunda.optimize.rest.HealthRestService.READYZ_PATH;
import static org.camunda.optimize.rest.LocalizationRestService.LOCALIZATION_PATH;
import static org.camunda.optimize.rest.UIConfigurationRestService.UI_CONFIGURATION_PATH;
import static org.camunda.optimize.rest.security.cloud.CCSaasAuth0WebSecurityConfig.AUTH_0_CLIENT_REGISTRATION_ID;
import static org.camunda.optimize.rest.security.cloud.CCSaasAuth0WebSecurityConfig.OAUTH_AUTH_ENDPOINT;
import static org.camunda.optimize.rest.security.cloud.CCSaasAuth0WebSecurityConfig.OAUTH_REDIRECT_ENDPOINT;
import static org.camunda.optimize.rest.security.platform.PlatformWebSecurityConfigurerAdapter.DEEP_SUB_PATH_ANY;

@Configuration
@RequiredArgsConstructor
@EnableWebSecurity
@Conditional(CCSaaSCondition.class)
@Order(2)
public class CCSaaSWebSecurityConfigurerAdapter extends WebSecurityConfigurerAdapter {

  private final SessionService sessionService;
  private final AuthCookieService authCookieService;
  private final ConfigurationService configurationService;
  private final CustomPreAuthenticatedAuthenticationProvider preAuthenticatedAuthenticationProvider;

  private final ClientRegistrationRepository clientRegistrationRepository;
  private final OAuth2AuthorizedClientService oAuth2AuthorizedClientService;

  @Override
  public void configure(AuthenticationManagerBuilder auth) throws Exception {
    auth.authenticationProvider(preAuthenticatedAuthenticationProvider);
  }

  @SneakyThrows
  @Override
  protected void configure(HttpSecurity http) {
    //@formatter:off
    http
      // csrf is not used but the same-site property of the auth cookie, see AuthCookieService#createNewOptimizeAuthCookie
      .csrf().disable()
      .httpBasic().disable()
      // disable frame options so embed links work, it's not a risk disabling this globally as click-jacking
      // is prevented by the samesite flag being set to `strict` on the authentication cookie
      .headers().frameOptions().disable()
      .and()
      // spring session management is not needed as we have stateless session handling using a JWT token stored as cookie
      .sessionManagement()
        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
      .and()
      .authorizeRequests()
        // ready endpoint is public for infra
        .antMatchers(createApiPath(READYZ_PATH)).permitAll()
        // public share resources
        .antMatchers(EXTERNAL_SUB_PATH + "/", EXTERNAL_SUB_PATH + "/index*",
                   EXTERNAL_SUB_PATH + STATIC_RESOURCE_PATH + "/**", EXTERNAL_SUB_PATH + "/*.js",
                   EXTERNAL_SUB_PATH + "/*.ico").permitAll()
        // public share related resources (API)
        .antMatchers(createApiPath(EXTERNAL_SUB_PATH + DEEP_SUB_PATH_ANY)).permitAll()
        // common public api resources
        .antMatchers(
          createApiPath(UI_CONFIGURATION_PATH),
          createApiPath(LOCALIZATION_PATH)
        ).permitAll()
        .antMatchers(ACTUATOR_ENDPOINT + "/**").permitAll()
        // everything else requires authentication
        .anyRequest().authenticated()
      .and()
      .oauth2Login()
        .clientRegistrationRepository(clientRegistrationRepository)
        .authorizedClientService(oAuth2AuthorizedClientService)
        .authorizationEndpoint(authorizationEndpointConfig -> authorizationEndpointConfig
          .baseUri(OAUTH_AUTH_ENDPOINT)
          .authorizationRequestRepository(cookieOAuth2AuthorizationRequestRepository())
        )
        .redirectionEndpoint(redirectionEndpointConfig -> redirectionEndpointConfig.baseUri(OAUTH_REDIRECT_ENDPOINT))
        .successHandler(getAuthenticationSuccessHandler())
      .and()
      .addFilterBefore(authenticationCookieFilter(), OAuth2AuthorizationRequestRedirectFilter.class)
      .exceptionHandling()
        .defaultAuthenticationEntryPointFor(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED), new AntPathRequestMatcher(REST_API_PATH + "/**"))
        .defaultAuthenticationEntryPointFor(new AddClusterIdSubPathToRedirectAuthenticationEntryPoint(OAUTH_AUTH_ENDPOINT + "/auth0"), new AntPathRequestMatcher("/**"))
      .and()
      .oauth2ResourceServer()
      .jwt(jwtConfigurer -> jwtConfigurer.decoder(jwtDecoder()));
    //@formatter:on
  }

  @Bean
  public AuthenticationCookieFilter authenticationCookieFilter() throws Exception {
    return new AuthenticationCookieFilter(sessionService, authenticationManager());
  }

  @Bean
  public HttpCookieOAuth2AuthorizationRequestRepository cookieOAuth2AuthorizationRequestRepository() {
    return new HttpCookieOAuth2AuthorizationRequestRepository(
      configurationService, new AuthorizationRequestCookieValueMapper()
    );
  }

  public JwtDecoder jwtDecoder() {
    NimbusJwtDecoder jwtDecoder = NimbusJwtDecoder.withJwkSetUri(readJwtSetUriFromConfig()).build();
    OAuth2TokenValidator<Jwt> audienceValidator =
      new AudienceValidator(
        configurationService.getAuthConfiguration().getCloudAuthConfiguration().getUserAccessTokenAudience().orElse(""));
    OAuth2TokenValidator<Jwt> clusterIdValidator = new ScopeValidator("profile");
    OAuth2TokenValidator<Jwt> audienceAndClusterIdValidation =
      new DelegatingOAuth2TokenValidator<>(audienceValidator, clusterIdValidator);
    jwtDecoder.setJwtValidator(audienceAndClusterIdValidation);
    return jwtDecoder;
  }

  private String readJwtSetUriFromConfig() {
    return Optional.ofNullable(configurationService.getOptimizeApiConfiguration().getJwtSetUri()).orElse("");
  }

  private AuthenticationSuccessHandler getAuthenticationSuccessHandler() {
    return (request, response, authentication) -> {
      final DefaultOidcUser user = (DefaultOidcUser) authentication.getPrincipal();
      final String userId = user.getIdToken().getSubject();
      final String sessionToken = sessionService.createAuthToken(userId);

      if (hasAccess(user)) {
        // spring security internally stores the access token as an authorized client, we retrieve it here to store it
        // in a cookie to allow potential other Optimize webapps to reuse it and keep the server stateless
        final OAuth2AccessToken serviceAccessToken = oAuth2AuthorizedClientService
          .loadAuthorizedClient(AUTH_0_CLIENT_REGISTRATION_ID, userId)
          .getAccessToken();

        final Instant cookieExpiryDate = determineCookieExpiryDate(sessionToken, serviceAccessToken)
          .orElseThrow(() -> new OptimizeRuntimeException(
            "Could not determine a cookie expiry date. This is likely a bug, please report."));
        response.addHeader(
          HttpHeaders.SET_COOKIE,
          authCookieService.createOptimizeServiceTokenCookie(serviceAccessToken, cookieExpiryDate, request.getScheme())
        );
        response.addHeader(
          HttpHeaders.SET_COOKIE,
          authCookieService.createNewOptimizeAuthCookie(sessionToken, cookieExpiryDate, request.getScheme())
        );

        // we can't redirect to the previously accesses path or the root of the application as the Optimize Cookie
        // won't be sent by the browser in this case. This is because the chain of requests that lead to the
        // authenticationOptimizeWebSecurityConfigurerAdapter success are initiated by the auth0 server and the
        // same-site:strict property prevents the cookie to be transmitted in such a case.
        // See https://stackoverflow.com/a/42220786
        // This static page breaks the redirect chain initiated from the auth0 login and forces the browser to start
        // a new request chain which then allows the Optimize auth cookie to be provided to be read by the
        // authenticationCookieFilter granting the user access.
        // This is also a technique documented at w3.org
        // https://www.w3.org/TR/WCAG20-TECHS/H76.html
        response.setContentType(MediaType.TEXT_HTML);
        response.getWriter()
          // @formatter:off
          .print(String.format(
            "<html>\n" +
              "<head><meta http-equiv=\"refresh\" content=\"1; URL='%s/'\"/></head>" +
              "<body>\n" +
                "<script>\n" +
                  "var path = '%s/';\n" +
                  "if (location.hash) {\n" +
                    "path += location.hash;\n" +
                  "}\n" +
                  "location = path;\n" +
                "</script>\n" +
                "<p align=\"center\">Successfully authenticated!</p>\n" +
                "<p align=\"center\">Click <a href=\"%s/\">here</a> if you don't get redirected automatically.</p>\n" +
              "</body>\n" +
            "</html>\n",
            getClusterIdPath(),
            getClusterIdPath(),
            getClusterIdPath()
          ));
        // @formatter:on
      } else {
        response.setStatus(Response.Status.FORBIDDEN.getStatusCode());
      }
    };
  }

  private Optional<Instant> determineCookieExpiryDate(final String sessionToken, final OAuth2AccessToken serviceAccessToken) {
    final Instant serviceTokenExpiry = serviceAccessToken.getExpiresAt();
    final Instant sessionTokenExpiry = authCookieService.getOptimizeAuthCookieTokenExpiryDate(sessionToken).orElse(null);
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
      accessGranted = organisations.stream()
        .map(orgEntry -> (String) orgEntry.get("id"))
        .anyMatch(organisationId -> getAuth0Configuration().getOrganizationId().equals(organisationId));
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

  private String createApiPath(final String... subPath) {
    return REST_API_PATH + String.join("", subPath);
  }

  /**
   * In Camunda Cloud environments all apps are served under a sub-path but there is no reverse proxy in place.
   * Thus any redirects issued within the app are required to contain the clusterId as sub-path.
   */
  public class AddClusterIdSubPathToRedirectAuthenticationEntryPoint extends LoginUrlAuthenticationEntryPoint {

    public AddClusterIdSubPathToRedirectAuthenticationEntryPoint(String loginFormUrl) {
      super(loginFormUrl);
    }

    @Override
    protected String determineUrlToUseForThisRequest(final HttpServletRequest request,
                                                     final HttpServletResponse response,
                                                     final AuthenticationException exception) {
      String redirect = super.determineUrlToUseForThisRequest(request, response, exception);
      return UriComponentsBuilder.fromPath(getClusterIdPath() + redirect).toUriString();
    }
  }

}
