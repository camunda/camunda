/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.webapp.security.oauth;

import static io.camunda.tasklist.util.CollectionUtil.firstOrDefault;
import static io.camunda.tasklist.util.CollectionUtil.getOrDefaultFromMap;
import static io.camunda.tasklist.webapp.security.TasklistProfileService.IDENTITY_AUTH_PROFILE;
import static io.camunda.tasklist.webapp.security.WebSecurityConfig.sendJSONErrorMessage;

import io.camunda.tasklist.property.ClientProperties;
import io.camunda.tasklist.property.TasklistProperties;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.core.convert.converter.Converter;
import org.springframework.core.env.Environment;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

@Component
@Profile("!" + IDENTITY_AUTH_PROFILE)
public class OAuth2WebConfigurer {

  public static final String SPRING_SECURITY_OAUTH_2_RESOURCESERVER_JWT_ISSUER_URI =
      "spring.security.oauth2.resourceserver.jwt.issuer-uri";
  public static final String SPRING_SECURITY_OAUTH_2_RESOURCESERVER_JWT_JWK_SET_URI =
      "spring.security.oauth2.resourceserver.jwt.jwk-set-uri";

  private static final Logger LOGGER = LoggerFactory.getLogger(OAuth2WebConfigurer.class);

  @Autowired private Environment env;

  @Autowired private TasklistProperties config;

  private final CustomJwtAuthenticationConverter jwtConverter =
      new CustomJwtAuthenticationConverter();

  public void configure(HttpSecurity http) throws Exception {
    if (isJWTEnabled()) {
      http.oauth2ResourceServer(
          serverCustomizer ->
              serverCustomizer
                  .authenticationEntryPoint(this::authenticationFailure)
                  .jwt(jwtCustomizer -> jwtCustomizer.jwtAuthenticationConverter(jwtConverter)));
      LOGGER.info("Enabled OAuth2 JWT access to GraphQL API");
    }
  }

  private void authenticationFailure(
      final HttpServletRequest request,
      final HttpServletResponse response,
      final AuthenticationException e)
      throws IOException {
    request.getSession().invalidate();
    sendJSONErrorMessage(response, e.getMessage());
  }

  protected boolean isJWTEnabled() {
    return env.containsProperty(SPRING_SECURITY_OAUTH_2_RESOURCESERVER_JWT_ISSUER_URI)
        || env.containsProperty(SPRING_SECURITY_OAUTH_2_RESOURCESERVER_JWT_JWK_SET_URI);
  }

  class CustomJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    public static final String AUDIENCE = "aud";
    public static final String SCOPE = "scope";

    private final JwtAuthenticationConverter delegate = new JwtAuthenticationConverter();

    @Override
    public AbstractAuthenticationToken convert(final Jwt jwt) {
      final JwtAuthenticationToken auth = (JwtAuthenticationToken) delegate.convert(jwt);
      final Map<String, Object> payload = auth.getTokenAttributes();
      if (isValid(payload)) {
        return auth;
      }
      throw new InvalidBearerTokenException("JWT payload validation failed");
    }

    private boolean isValid(final Map<String, Object> payload) {
      try {
        final String audience = getAudience(payload);
        final String scope = getScope(payload);
        final ClientProperties clientConfig = config.getClient();
        return clientConfig.getAudience().equals(audience)
            && clientConfig.getClusterId().equals(scope);
      } catch (Exception e) {
        LOGGER.warn("Validation of JWT payload failed. Request is not authenticated.");
        return false;
      }
    }

    private String getScope(final Map<String, Object> payload) {
      return (String) payload.get(SCOPE);
    }

    private String getAudience(final Map<String, Object> payload) {
      return firstOrDefault(
          (List<String>) getOrDefaultFromMap(payload, AUDIENCE, Collections.emptyList()), null);
    }
  }
}
