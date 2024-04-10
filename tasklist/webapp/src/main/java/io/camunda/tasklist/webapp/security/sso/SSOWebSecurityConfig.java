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
package io.camunda.tasklist.webapp.security.sso;

import static io.camunda.tasklist.webapp.security.TasklistProfileService.SSO_AUTH_PROFILE;
import static io.camunda.tasklist.webapp.security.TasklistURIs.*;
import static org.apache.commons.lang3.StringUtils.containsAny;

import com.auth0.AuthenticationController;
import io.camunda.tasklist.webapp.security.BaseWebConfigurer;
import io.camunda.tasklist.webapp.security.oauth.OAuth2WebConfigurer;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.handler.HandlerMappingIntrospector;

@Profile(SSO_AUTH_PROFILE)
@Configuration
@EnableWebSecurity
@Component("webSecurityConfig")
public class SSOWebSecurityConfig extends BaseWebConfigurer {

  private static final Logger LOGGER = LoggerFactory.getLogger(SSOWebSecurityConfig.class);

  @Autowired private OAuth2WebConfigurer oAuth2WebConfigurer;

  @Bean
  public AuthenticationController authenticationController() {
    return AuthenticationController.newBuilder(
            tasklistProperties.getAuth0().getDomain(),
            tasklistProperties.getAuth0().getClientId(),
            tasklistProperties.getAuth0().getClientSecret())
        .build();
  }

  @Override
  protected void applyOAuth2Settings(HttpSecurity http) throws Exception {
    oAuth2WebConfigurer.configure(http);
  }

  @Override
  protected void applySecurityFilterSettings(
      HttpSecurity http, HandlerMappingIntrospector introspector) throws Exception {
    http.csrf((csrf) -> csrf.disable())
        .authorizeRequests(
            (authorize) -> {
              authorize
                  .requestMatchers(getAuthWhitelist(introspector))
                  .permitAll()
                  .requestMatchers(
                      AntPathRequestMatcher.antMatcher(GRAPHQL_URL),
                      AntPathRequestMatcher.antMatcher(ALL_REST_V1_API),
                      AntPathRequestMatcher.antMatcher(ERROR_URL))
                  .authenticated()
                  .requestMatchers(AntPathRequestMatcher.antMatcher(ROOT_URL))
                  .authenticated();
            })
        .exceptionHandling(
            (handling) -> {
              handling.authenticationEntryPoint(this::authenticationEntry);
            });
  }

  protected void authenticationEntry(
      HttpServletRequest req, HttpServletResponse res, AuthenticationException ex)
      throws IOException {

    String requestedUrl = req.getRequestURI();

    if (req.getQueryString() != null && !req.getQueryString().isEmpty()) {
      requestedUrl = requestedUrl + "?" + req.getQueryString();
    }

    if (containsAny(requestedUrl.toLowerCase(), GRAPHQL_URL, REST_V1_API)) {
      req.getSession().invalidate();
      sendJSONErrorMessage(res, ex.getMessage());
    } else {
      LOGGER.debug("Try to access protected resource {}. Save it for later redirect", requestedUrl);
      req.getSession().setAttribute(REQUESTED_URL, requestedUrl);
      res.sendRedirect(req.getContextPath() + LOGIN_RESOURCE);
    }
  }
}
