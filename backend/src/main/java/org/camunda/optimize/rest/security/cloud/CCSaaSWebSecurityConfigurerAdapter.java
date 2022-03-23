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
import org.camunda.optimize.rest.security.AuthenticationCookieRefreshFilter;
import org.camunda.optimize.rest.security.CustomPreAuthenticatedAuthenticationProvider;
import org.camunda.optimize.service.security.AuthCookieService;
import org.camunda.optimize.service.security.SessionService;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.configuration.condition.CCSaaSCondition;
import org.camunda.optimize.service.util.configuration.security.CloudAuthConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.client.InMemoryOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestRedirectFilter;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.session.SessionManagementFilter;
import org.springframework.web.util.UriComponentsBuilder;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.camunda.optimize.jetty.OptimizeResourceConstants.REST_API_PATH;
import static org.camunda.optimize.rest.HealthRestService.READYZ_PATH;

@Configuration
@RequiredArgsConstructor
@EnableWebSecurity
@Conditional(CCSaaSCondition.class)
@Order(2)
public class CCSaaSWebSecurityConfigurerAdapter extends WebSecurityConfigurerAdapter {

  public static final String AUTH0_JWKS_ENDPOINT = "/.well-known/jwks.json";
  public static final String URL_TEMPLATE = "https://%s%s";
  private static final String OAUTH_AUTH_ENDPOINT = "/sso";
  private static final String OAUTH_REDIRECT_ENDPOINT = "/sso-callback";
  private static final String AUTH0_AUTH_ENDPOINT = "/authorize";
  private static final String AUTH0_TOKEN_ENDPOINT = "/oauth/token";
  private static final String AUTH0_USERINFO_ENDPOINT = "/userinfo";

  private final SessionService sessionService;
  private final AuthCookieService authCookieService;
  private final ConfigurationService configurationService;
  private final AuthenticationCookieRefreshFilter authenticationCookieRefreshFilter;
  private final CustomPreAuthenticatedAuthenticationProvider preAuthenticatedAuthenticationProvider;

  @Override
  public void configure(AuthenticationManagerBuilder auth) throws Exception {
    auth.authenticationProvider(preAuthenticatedAuthenticationProvider);
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

  @Bean
  public ClientRegistrationRepository clientRegistrationRepository() {
    final ClientRegistration.Builder builder = ClientRegistration.withRegistrationId("auth0")
      .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
      .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
      // For allowed redirect urls auth0 is not supporting wildcards within the actual path.
      // Thus the clusterId is passed along as query parameter and will get picked up by the cloud ingress proxy
      // which redirects the callback to the particular Optimize instance of the cluster the login was issued from.
      .redirectUri("{baseUrl}" + OAUTH_REDIRECT_ENDPOINT + "?uuid=" + getAuth0Configuration().getClusterId())
      .authorizationUri(buildAuth0CustomDomainUrl(AUTH0_AUTH_ENDPOINT))
      .tokenUri(buildAuth0DomainUrl(AUTH0_TOKEN_ENDPOINT))
      .userInfoUri(buildAuth0DomainUrl(AUTH0_USERINFO_ENDPOINT))
      .scope("openid", "profile")
      .userNameAttributeName(getAuth0Configuration().getUserIdAttributeName())
      .clientId(getAuth0Configuration().getClientId())
      .clientSecret(getAuth0Configuration().getClientSecret())
      .jwkSetUri(String.format(URL_TEMPLATE, getAuth0Configuration().getDomain(), AUTH0_JWKS_ENDPOINT));
    return new InMemoryClientRegistrationRepository(List.of(builder.build()));
  }

  @Bean
  public OAuth2AuthorizedClientService authorizedClientService() {
    return new InMemoryOAuth2AuthorizedClientService(
      clientRegistrationRepository()
    );
  }

  @SneakyThrows
  @Override
  protected void configure(HttpSecurity http) {
    //@formatter:off
    http
      // csrf is not used but the same-site property of the auth cookie, see AuthCookieService#createNewOptimizeAuthCookie
      .csrf().disable()
      .httpBasic().disable()
      // spring session management is not needed as we have stateless session handling using a JWT token stored as cookie
      .sessionManagement()
        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
      .and()
      .authorizeRequests()
        // ready endpoint is public for infra
        .antMatchers(createApiPath(READYZ_PATH)).permitAll()
        // everything else requires authentication
        .anyRequest().authenticated()
      .and()
      .oauth2Login()
        .clientRegistrationRepository(clientRegistrationRepository())
        .authorizedClientService(authorizedClientService())
        .authorizationEndpoint(authorizationEndpointConfig -> authorizationEndpointConfig
          .baseUri(OAUTH_AUTH_ENDPOINT)
          .authorizationRequestRepository(cookieOAuth2AuthorizationRequestRepository())
        )
        .redirectionEndpoint(redirectionEndpointConfig -> redirectionEndpointConfig.baseUri(OAUTH_REDIRECT_ENDPOINT))
        .successHandler(getAuthenticationSuccessHandler())
      .and()
      .addFilterBefore(authenticationCookieFilter(), OAuth2AuthorizationRequestRedirectFilter.class)
      .addFilterAfter(authenticationCookieRefreshFilter, SessionManagementFilter.class)
      .exceptionHandling().authenticationEntryPoint(new AddClusterIdSubPathToRedirectAuthenticationEntryPoint(OAUTH_AUTH_ENDPOINT + "/auth0"));
    //@formatter:on
  }

  private AuthenticationSuccessHandler getAuthenticationSuccessHandler() {
    return (request, response, authentication) -> {
      final DefaultOidcUser user = (DefaultOidcUser) authentication.getPrincipal();
      final String authToken = sessionService.createAuthToken(user.getIdToken().getSubject());

      if (hasAccess(user)) {
        final String optimizeAuthCookie =
          authCookieService.createNewOptimizeAuthCookie(authToken, request.getScheme());
        response.addHeader(HttpHeaders.SET_COOKIE, optimizeAuthCookie);

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
          .print(
            String.format(
            "<html>" +
              "<head><meta http-equiv=\"refresh\" content=\"0; URL='%s/'\"/></head>" +
              "<body>" +
              "<p align=\"center\">Successfully authenticated!</p>" +
              "<p align=\"center\">Click <a href=\"%s/\">here</a> if you don't get redirected automatically.</p>" +
              "</body>" +
            "</html>",
            getClusterIdPath(),
            getClusterIdPath()
            ));
        // @formatter:on
      } else {
        response.setStatus(Response.Status.FORBIDDEN.getStatusCode());
      }
    };
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

  private String buildAuth0DomainUrl(final String path) {
    return String.format(URL_TEMPLATE, getAuth0Configuration().getDomain(), path);
  }

  private String buildAuth0CustomDomainUrl(final String path) {
    return String.format(URL_TEMPLATE, getAuth0Configuration().getCustomDomain(), path);
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
