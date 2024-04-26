/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE (“USE”), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * “Licensee” means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
 */
package io.camunda.tasklist.webapp.security.oauth;

import static io.camunda.tasklist.util.CollectionUtil.firstOrDefault;
import static io.camunda.tasklist.util.CollectionUtil.getOrDefaultFromMap;
import static io.camunda.tasklist.webapp.security.TasklistProfileService.IDENTITY_AUTH_PROFILE;
import static io.camunda.tasklist.webapp.security.WebSecurityConfig.sendJSONErrorMessage;

import io.camunda.tasklist.property.ClientProperties;
import io.camunda.tasklist.property.TasklistProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
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
      LOGGER.info("Enabled OAuth2 JWT access to REST API");
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
    public static final String CLUSTER_ID = "https://camunda.com/clusterId";

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
        final String clusterId = getClusterId(payload);
        final ClientProperties clientConfig = config.getClient();
        return clientConfig.getAudience().equals(audience)
            && clientConfig.getClusterId().equals(clusterId);
      } catch (Exception e) {
        LOGGER.warn("Validation of JWT payload failed. Request is not authenticated.");
        return false;
      }
    }

    private String getClusterId(final Map<String, Object> payload) {
      return (String) payload.get(CLUSTER_ID);
    }

    private String getAudience(final Map<String, Object> payload) {
      return firstOrDefault(
          (List<String>) getOrDefaultFromMap(payload, AUDIENCE, Collections.emptyList()), null);
    }
  }
}
