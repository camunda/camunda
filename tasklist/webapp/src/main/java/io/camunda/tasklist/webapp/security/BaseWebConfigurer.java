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
package io.camunda.tasklist.webapp.security;

import static io.camunda.tasklist.webapp.security.TasklistURIs.*;
import static org.apache.hc.core5.http.ContentType.APPLICATION_JSON;
import static org.springframework.http.HttpStatus.NO_CONTENT;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

import io.camunda.tasklist.property.TasklistProperties;
import jakarta.json.Json;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.autoconfigure.security.servlet.EndpointRequest;
import org.springframework.boot.actuate.logging.LoggersEndpoint;
import org.springframework.context.annotation.Bean;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.handler.HandlerMappingIntrospector;

public abstract class BaseWebConfigurer {

  protected final Logger logger = LoggerFactory.getLogger(getClass());

  @Autowired protected TasklistProperties tasklistProperties;

  @Autowired TasklistProfileService errorMessageService;
  final CookieCsrfTokenRepository cookieCsrfTokenRepository = new CookieCsrfTokenRepository();
  @Autowired private TasklistProfileService profileService;

  @Bean
  public SecurityFilterChain filterChain(
      final HttpSecurity http, final HandlerMappingIntrospector introspector) throws Exception {
    final var authenticationManagerBuilder =
        http.getSharedObject(AuthenticationManagerBuilder.class);

    applySecurityHeadersSettings(http);
    applySecurityFilterSettings(http, introspector);
    applyAuthenticationSettings(authenticationManagerBuilder);
    applyOAuth2Settings(http);

    return http.build();
  }

  protected abstract void applyOAuth2Settings(HttpSecurity http) throws Exception;

  protected void applySecurityHeadersSettings(final HttpSecurity http) throws Exception {
    http.headers()
        .contentSecurityPolicy(
            tasklistProperties.getSecurityProperties().getContentSecurityPolicy());
  }

  protected void applySecurityFilterSettings(
      final HttpSecurity http, final HandlerMappingIntrospector introspector) throws Exception {
    defaultFilterSettings(http, introspector);
  }

  private void defaultFilterSettings(
      final HttpSecurity http, final HandlerMappingIntrospector introspector) throws Exception {
    if (tasklistProperties.isCsrfPreventionEnabled()) {
      logger.info("CSRF Protection Enabled");
      configureCSRF(http);
    } else {
      http.csrf((csrf) -> csrf.disable());
    }
    http.authorizeRequests(
            (authorize) -> {
              authorize
                  .requestMatchers(TasklistURIs.getAuthWhitelist(introspector))
                  .permitAll()
                  .requestMatchers(
                      AntPathRequestMatcher.antMatcher(GRAPHQL_URL),
                      AntPathRequestMatcher.antMatcher(ALL_REST_V1_API),
                      AntPathRequestMatcher.antMatcher(ERROR_URL))
                  .authenticated()
                  .requestMatchers(AntPathRequestMatcher.antMatcher("/login"))
                  .authenticated();
            })
        .formLogin(
            (login) -> {
              login
                  .loginProcessingUrl(LOGIN_RESOURCE)
                  .successHandler(this::successHandler)
                  .failureHandler(this::failureHandler)
                  .permitAll();
            })
        .logout(
            (logout) -> {
              logout
                  .logoutUrl(LOGOUT_RESOURCE)
                  .logoutSuccessHandler(this::logoutSuccessHandler)
                  .permitAll()
                  .invalidateHttpSession(true)
                  .deleteCookies(COOKIE_JSESSIONID, X_CSRF_TOKEN);
            })
        .exceptionHandling(
            (handling) -> {
              handling.authenticationEntryPoint(this::failureHandler);
            });
  }

  protected void applyAuthenticationSettings(final AuthenticationManagerBuilder builder)
      throws Exception {
    // noop
  }

  private void logoutSuccessHandler(
      final HttpServletRequest request,
      final HttpServletResponse response,
      final Authentication authentication) {
    response.setStatus(NO_CONTENT.value());
  }

  private void failureHandler(
      final HttpServletRequest request,
      final HttpServletResponse response,
      final AuthenticationException ex)
      throws IOException {
    request.getSession().invalidate();
    sendJSONErrorMessage(response, profileService.getMessageByProfileFor(ex));
  }

  private void csrfHandler(
      final HttpServletRequest request,
      final HttpServletResponse response,
      final AccessDeniedException ex)
      throws IOException {
    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
    response.setContentType("application/json");
    response.setCharacterEncoding("UTF-8");
    final String jsonErrorResponse = "{\"error\": \"Access denied due to invalid CSRF token.\"}";
    response.getWriter().write(jsonErrorResponse);
  }

  public static void sendJSONErrorMessage(final HttpServletResponse response, final String message)
      throws IOException {
    response.reset();
    response.setCharacterEncoding(RESPONSE_CHARACTER_ENCODING);

    final PrintWriter writer = response.getWriter();
    final String jsonResponse =
        Json.createObjectBuilder().add("message", message).build().toString();

    writer.append(jsonResponse);

    response.setStatus(UNAUTHORIZED.value());
    response.setContentType(APPLICATION_JSON.getMimeType());
  }

  private void successHandler(
      final HttpServletRequest request,
      final HttpServletResponse response,
      final Authentication authentication) {
    addCSRFTokenWhenAvailable(request, response).setStatus(NO_CONTENT.value());
    response.setStatus(NO_CONTENT.value());
  }

  protected void configureCSRF(final HttpSecurity http) throws Exception {
    cookieCsrfTokenRepository.setHeaderName(X_CSRF_TOKEN);
    cookieCsrfTokenRepository.setCookieCustomizer(c -> c.httpOnly(true));
    cookieCsrfTokenRepository.setCookieName(X_CSRF_TOKEN);
    http.csrf(
            (csrf) ->
                csrf.csrfTokenRepository(cookieCsrfTokenRepository)
                    .requireCsrfProtectionMatcher(new CsrfRequireMatcher())
                    .ignoringRequestMatchers(EndpointRequest.to(LoggersEndpoint.class)))
        .addFilterAfter(getCSRFHeaderFilter(), CsrfFilter.class)
        .exceptionHandling(
            (handling) -> {
              handling.accessDeniedHandler(this::csrfHandler);
            });
  }

  protected OncePerRequestFilter getCSRFHeaderFilter() {
    return new OncePerRequestFilter() {
      @Override
      protected void doFilterInternal(
          final HttpServletRequest request,
          final HttpServletResponse response,
          final FilterChain filterChain)
          throws ServletException, IOException {
        filterChain.doFilter(request, addCSRFTokenWhenAvailable(request, response));
      }
    };
  }

  protected HttpServletResponse addCSRFTokenWhenAvailable(
      final HttpServletRequest request, final HttpServletResponse response) {
    if (shouldAddCSRF(request)) {
      final CsrfToken token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
      if (token != null) {
        response.setHeader(X_CSRF_TOKEN, token.getToken());
      }
    }
    return response;
  }

  boolean shouldAddCSRF(final HttpServletRequest request) {
    final Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    final String path = request.getRequestURI();
    final String method = request.getMethod();

    return (auth != null && auth.isAuthenticated())
        && (path == null || !path.contains(LOGOUT_RESOURCE))
        && ("GET".equalsIgnoreCase(method) || (path != null && (path.contains(LOGIN_RESOURCE))));
  }
}
