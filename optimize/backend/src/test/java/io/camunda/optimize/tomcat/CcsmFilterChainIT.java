/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.tomcat;

import static io.camunda.optimize.rest.HealthRestService.READYZ_PATH;
import static io.camunda.optimize.rest.LocalizationRestService.LOCALIZATION_PATH;
import static io.camunda.optimize.rest.UIConfigurationRestService.UI_CONFIGURATION_PATH;
import static io.camunda.optimize.tomcat.OptimizeResourceConstants.REST_API_PATH;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.camunda.identity.sdk.Identity;
import io.camunda.identity.sdk.authentication.Authentication;
import io.camunda.identity.sdk.authentication.AuthorizeUriBuilder;
import io.camunda.identity.sdk.users.Users;
import io.camunda.optimize.service.security.SessionService;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.ConfigurationServiceBuilder;
import io.camunda.optimize.tomcat.filter.support.FilterIntegrationTestBase;
import java.net.URI;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

@ActiveProfiles("ccsm")
@Import(CcsmFilterChainIT.CcsmConfig.class)
@TestPropertySource(properties = {"optimize.ccsm-filter-chain-it=true"})
class CcsmFilterChainIT extends FilterIntegrationTestBase {

  @TestConfiguration
  static class CcsmConfig {

    @Bean("configurationService")
    @Primary
    @ConditionalOnMissingBean(ConfigurationService.class)
    public ConfigurationService ccsmTestConfigurationService() {
      final ConfigurationService config =
          ConfigurationServiceBuilder.createConfiguration()
              .loadConfigurationFrom("service-config.yaml")
              .build();

      config.getOptimizeApiConfiguration().setJwtSetUri("http://localhost:0/dummy-jwks");

      final var ccsm = config.getAuthConfiguration().getCcsmAuthConfiguration();
      ccsm.setClientId("optimize-test-client");
      ccsm.setClientSecret("dummy-secret");
      ccsm.setRedirectRootUrl("http://localhost");

      // Inject security headers so ResponseSecurityHeaderFilter has something to add
      final var headers =
          config.getSecurityConfiguration().getResponseHeaders().getHeadersWithValues();
      headers.put(
          "Content-Security-Policy",
          "default-src 'self'; object-src 'none'; frame-ancestors 'none'; base-uri 'self'");
      headers.put("X-Content-Type-Options", "nosniff");
      headers.put("X-Frame-Options", "DENY");
      headers.put("Referrer-Policy", "no-referrer");
      headers.put("X-XSS-Protection", "0");

      return config;
    }

    /**
     * Provides a mock {@link AuthenticationManager} so that {@code CCSMAuthenticationCookieFilter}
     * (which extends {@code AbstractPreAuthenticatedProcessingFilter}) can satisfy its {@code
     * afterPropertiesSet()} contract without a real Identity/Keycloak server.
     */
    @Bean
    @Primary
    @ConditionalOnMissingBean(AuthenticationManager.class)
    public AuthenticationManager mockAuthenticationManager() {
      return mock(AuthenticationManager.class);
    }

    @Bean
    @Primary
    @ConditionalOnMissingBean(Identity.class)
    public Identity mockIdentity() {
      final Identity identity = mock(Identity.class);
      final Authentication auth = mock(Authentication.class);
      final AuthorizeUriBuilder builder = mock(AuthorizeUriBuilder.class);

      when(identity.authentication()).thenReturn(auth);
      when(auth.authorizeUriBuilder(anyString())).thenReturn(builder);
      when(builder.build()).thenReturn(URI.create("/identity/authorize?dummy=true"));

      final Users users = mock(Users.class);
      when(identity.users()).thenReturn(users);
      when(users.isAvailable()).thenReturn(true);

      return identity;
    }

    @Bean("sessionService")
    @ConditionalOnProperty(name = "optimize.ccsm-filter-chain-it", havingValue = "true")
    public SessionService mockSessionService(final ConfigurationService configurationService) {
      final SessionService sessionService = mock(SessionService.class);
      when(sessionService.getRequestUserOrFailNotAuthorized(any())).thenReturn("test");
      when(sessionService.getConfigurationService()).thenReturn(configurationService);
      return sessionService;
    }
  }

  // ---------------------------------------------------------------------------
  // Root path and home page handling
  // ---------------------------------------------------------------------------

  @Nested
  class RootPath {

    @Test
    void rootSlashRedirectsToIdentityForUnauthenticatedUser() throws Exception {
      mvc.perform(get("/"))
          .andExpect(status().is3xxRedirection())
          .andExpect(redirectedUrlPattern("/identity/authorize*"));
    }

    @Test
    void rootSlashIsServedForAuthenticatedUser() throws Exception {
      mvc.perform(get("/").with(user("test").roles("OPTIMIZE"))).andExpect(status().isOk());
    }

    @Test
    void emptyPathRedirectsToIdentityLogin() throws Exception {
      // URLRedirectFilter normalizes empty path to /, then Security redirects to Identity
      mvc.perform(get(""))
          .andExpect(status().is3xxRedirection())
          .andExpect(redirectedUrlPattern("/identity/authorize*"));
    }
  }

  // ---------------------------------------------------------------------------
  // SPA deep-link redirect (URLRedirectFilter)
  // URLRedirectFilter runs before Security; it redirects unknown SPA paths to /# for all users.
  // ---------------------------------------------------------------------------

  @Nested
  class SpaDeepLinkRedirect {

    @Test
    void spaDeepLinkIsRedirectedToHashRegardlessOfAuthentication() throws Exception {
      mvc.perform(get("/dashboard/1"))
          .andExpect(status().is3xxRedirection())
          .andExpect(redirectedUrl("/#"));
    }

    @Test
    void unknownPathIsRedirectedToHash() throws Exception {
      mvc.perform(get("/report/abc-123"))
          .andExpect(status().is3xxRedirection())
          .andExpect(redirectedUrl("/#"));
    }
  }

  // ---------------------------------------------------------------------------
  // Public endpoints — no auth required
  // ---------------------------------------------------------------------------

  @Nested
  class PublicEndpoints {

    @Test
    void readinessEndpointIsPublic() throws Exception {
      mvc.perform(get(REST_API_PATH + READYZ_PATH)).andExpect(status().isOk());
    }

    @Test
    void actuatorHealthIsPublic() throws Exception {
      mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    void uiConfigIsPublic() throws Exception {
      mvc.perform(get(REST_API_PATH + UI_CONFIGURATION_PATH)).andExpect(status().isOk());
    }

    @Test
    void localizationIsPublic() throws Exception {
      mvc.perform(get(REST_API_PATH + LOCALIZATION_PATH)).andExpect(status().isOk());
    }
  }

  // ---------------------------------------------------------------------------
  // Protected endpoints — require authentication
  // ---------------------------------------------------------------------------

  @Nested
  class ProtectedEndpoints {

    @Test
    void apiRequiresAuthentication() throws Exception {
      mvc.perform(get(REST_API_PATH + "/dashboard/1")).andExpect(status().isUnauthorized());
    }

    @Test
    void apiIsAccessibleWhenAuthenticated() throws Exception {
      mvc.perform(get(REST_API_PATH + "/report").with(user("test").roles("OPTIMIZE")))
          .andExpect(status().isOk());
    }
  }

  // ---------------------------------------------------------------------------
  // External-API path rewrite (CCSMRequestAdjustmentFilter)
  // /external/api/* → /api/external/* before Security
  // ---------------------------------------------------------------------------

  @Nested
  class ExternalApiRewrite {

    @Test
    void externalApiPathIsRewrittenAndNotBlockedByAuth() throws Exception {
      // After rewrite: /api/external/share/** is a public-share path — Security permits it.
      // The controller may return 404 (no real data), but must never return 401.
      final int status =
          mvc.perform(get("/external/api/share/report/some-id/result"))
              .andReturn()
              .getResponse()
              .getStatus();
      assertThat(status)
          .as("Expected /external/api/ to be rewritten and not blocked with 401")
          .isNotEqualTo(HttpStatus.UNAUTHORIZED.value());
    }
  }

  // ---------------------------------------------------------------------------
  // Static resources
  // ---------------------------------------------------------------------------

  @Nested
  class StaticResources {

    @Test
    void staticResourceIsNotBlocked() throws Exception {
      final int status = mvc.perform(get("/favicon.ico")).andReturn().getResponse().getStatus();
      assertThat(status)
          .as("/favicon.ico must not be blocked with 401")
          .isNotEqualTo(HttpStatus.UNAUTHORIZED.value());
    }
  }

  // ---------------------------------------------------------------------------
  // Security headers (ResponseSecurityHeaderFilter)
  // ---------------------------------------------------------------------------

  @Nested
  class SecurityHeaders {

    @Test
    void contentSecurityPolicyHeaderPresentOnUiResponse() throws Exception {
      mvc.perform(get("/index.html")).andExpect(header().exists("Content-Security-Policy"));
    }
  }

  // ---------------------------------------------------------------------------
  // NoCachingFilter
  // ---------------------------------------------------------------------------

  @Nested
  class NoCaching {

    @Test
    void apiResponseHasNoCacheHeader() throws Exception {
      mvc.perform(get(REST_API_PATH + READYZ_PATH))
          .andExpect(
              header().string(HttpHeaders.CACHE_CONTROL, Matchers.containsString("no-store")));
    }

    @Test
    void rootResponseHasNoCacheHeaderEvenWhenRedirecting() throws Exception {
      // NoCachingFilter runs before Security; the header is set before the Identity redirect fires
      mvc.perform(get("/"))
          .andExpect(
              header().string(HttpHeaders.CACHE_CONTROL, Matchers.containsString("no-store")));
    }
  }
}
