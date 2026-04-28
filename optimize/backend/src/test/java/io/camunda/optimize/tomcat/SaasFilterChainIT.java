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
import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

import io.camunda.optimize.Main;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.ConfigurationServiceBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;

/**
 * Integration tests for the full Servlet filter + Spring Security chain in <b>SaaS (Cloud)</b>
 * mode.
 *
 * <p>The Spring context is started with the {@code cloud} profile, which activates {@link
 * io.camunda.optimize.rest.security.cloud.CCSaaSSecurityConfigurerAdapter} and {@link
 * CCSaasRequestAdjustmentFilter}. Cloud-specific configuration (clusterId, Auth0 domain, etc.) is
 * provided via {@code saas-filter-it.yaml} on the test classpath, loaded by the inner {@link
 * SaasConfig} {@code @TestConfiguration}. No real database, Auth0, or Zeebe connection is required.
 *
 * <p>Test scenarios covered:
 *
 * <ul>
 *   <li>Bare {@code /<clusterId>} (no trailing slash) — redirect to {@code /<clusterId>/}
 *   <li>{@code /<clusterId>/} — cluster-ID stripped, SPA root passes through
 *   <li>{@code /} (no cluster prefix) — SPA root passes through directly
 *   <li>Deep SPA links under cluster-ID — redirected to {@code /#} after strip
 *   <li>Public endpoints under cluster-ID prefix — reachable without authentication
 *   <li>Protected API under cluster-ID prefix — returns {@code 401} without credentials
 *   <li>{@code /<clusterId>/external/api/} — rewritten to {@code /api/external/} by {@link
 *       CCSaasRequestAdjustmentFilter}; Security does not block it as 401
 *   <li>Actuator — publicly accessible
 *   <li>Security headers — present on every response
 *   <li>No-cache headers — present on API and SPA root responses
 * </ul>
 */
@Tag("ccsm-test")
@ActiveProfiles("cloud")
@SpringBootTest(
    classes = {Main.class},
    properties = {
      "optimize.integration-tests=true",
      "spring.task.scheduling.enabled=false",
      "optimize.import.enabled=false",
      "spring.main.allow-bean-definition-overriding=true",
      "optimize.saas-filter-chain-it=true",
    })
@Import(SaasFilterChainIT.SaasConfig.class)
public class SaasFilterChainIT {

  static final String CLUSTER_ID = "abc-123";
  private static final String CLUSTER_PREFIX = "/" + CLUSTER_ID;

  @Autowired private WebApplicationContext wac;
  private MockMvc mvc;

  @Autowired private FilterRegistrationBean<CCSaasRequestAdjustmentFilter> requestAdjuster;

  @Autowired
  private FilterRegistrationBean<?> responseHeadersInjector; // ResponseSecurityHeaderFilter

  @Autowired private FilterRegistrationBean<?> responseTimezoneFilter; // ResponseTimezoneFilter
  @Autowired private FilterRegistrationBean<?> urlRedirector; // URLRedirectFilter
  @Autowired private FilterRegistrationBean<?> noCachingFilterRegistrationBean; // NoCachingFilter

  @BeforeEach
  void setUpMockMvc() {
    final FilterChainProxy securityChain =
        wac.getBean("springSecurityFilterChain", FilterChainProxy.class);

    mvc =
        webAppContextSetup(wac)
            .addFilters(
                requireNonNull(
                    requestAdjuster
                        .getFilter()), //              CCSaasRequestAdjustmentFilter (MIN_VALUE)
                requireNonNull(
                    responseHeadersInjector
                        .getFilter()), //      ResponseSecurityHeaderFilter  (MAX_VALUE-5)
                requireNonNull(
                    responseTimezoneFilter
                        .getFilter()), //        ResponseTimezoneFilter         (MAX_VALUE-5)
                requireNonNull(urlRedirector.getFilter()), //                URLRedirectFilter
                // (MAX_VALUE-5)
                requireNonNull(
                    noCachingFilterRegistrationBean
                        .getFilter()), // NoCachingFilter             (MAX_VALUE-5)
                securityChain) //                                             Spring Security
            //        (MAX_VALUE) — LAST
            .build();
  }

  // ---------------------------------------------------------------------------
  // Cluster-ID prefix redirect and home page
  // ---------------------------------------------------------------------------

  @TestConfiguration
  static class SaasConfig {

    @Bean("configurationService")
    @Primary
    @ConditionalOnProperty(name = "optimize.saas-filter-chain-it", havingValue = "true")
    public ConfigurationService saasTestConfigurationService() {
      return ConfigurationServiceBuilder.createConfiguration()
          .loadConfigurationFrom("service-config.yaml", "saas-filter-it.yaml")
          .build();
    }
  }

  // ---------------------------------------------------------------------------
  // SPA deep-link redirect under cluster-ID prefix
  // ---------------------------------------------------------------------------

  @Nested
  class ClusterIdPrefixHandling {

    @Test
    void bareClusterIdRedirectsToTrailingSlash() throws Exception {
      // GET /<clusterId> — CCSaasRequestAdjustmentFilter redirects to /<clusterId>/
      mvc.perform(get(CLUSTER_PREFIX))
          .andExpect(status().is3xxRedirection())
          .andExpect(redirectedUrl(CLUSTER_PREFIX + "/"));
    }

    @Test
    void clusterIdRootPassesThroughToSpa() throws Exception {
      // GET /<clusterId>/ — cluster-ID stripped → / → SPA root
      mvc.perform(get(CLUSTER_PREFIX + "/").with(user("test").roles("OPTIMIZE")))
          .andExpect(status().isOk());
    }

    @Test
    void rootWithoutClusterIdAlsoPassesThrough() throws Exception {
      // GET / — no cluster-ID prefix, passes through directly to SPA
      mvc.perform(get("/").with(user("test").roles("OPTIMIZE"))).andExpect(status().isOk());
    }
  }

  // ---------------------------------------------------------------------------
  // Public endpoints under cluster-ID prefix
  // ---------------------------------------------------------------------------

  @Nested
  class SpaDeepLinkRedirect {

    @Test
    void deepLinkUnderClusterIdIsRedirectedToHash() throws Exception {
      // /<clusterId>/dashboard/1 → cluster-ID stripped → /dashboard/1 → URLRedirectFilter → /#
      mvc.perform(get(CLUSTER_PREFIX + "/dashboard/1"))
          .andExpect(status().is3xxRedirection())
          .andExpect(redirectedUrl("/#"));
    }

    @Test
    void deepLinkWithoutClusterIdIsRedirectedToHash() throws Exception {
      mvc.perform(get("/report/abc"))
          .andExpect(status().is3xxRedirection())
          .andExpect(redirectedUrl("/#"));
    }
  }

  // ---------------------------------------------------------------------------
  // Protected endpoints — require authentication (OAuth2 / session cookie)
  // ---------------------------------------------------------------------------

  @Nested
  class PublicEndpoints {

    @Test
    void readinessUnderClusterIdIsPublic() throws Exception {
      mvc.perform(get(CLUSTER_PREFIX + REST_API_PATH + READYZ_PATH)).andExpect(status().isOk());
    }

    @Test
    void readinessWithoutClusterIdIsPublic() throws Exception {
      mvc.perform(get(REST_API_PATH + READYZ_PATH)).andExpect(status().isOk());
    }

    @Test
    void actuatorHealthIsPublic() throws Exception {
      mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    void uiConfigUnderClusterIdIsPublic() throws Exception {
      mvc.perform(get(CLUSTER_PREFIX + REST_API_PATH + UI_CONFIGURATION_PATH))
          .andExpect(status().isOk());
    }

    @Test
    void localizationUnderClusterIdIsPublic() throws Exception {
      mvc.perform(get(CLUSTER_PREFIX + REST_API_PATH + LOCALIZATION_PATH))
          .andExpect(status().isOk());
    }
  }

  // ---------------------------------------------------------------------------
  // External-API path rewrite (CCSaasRequestAdjustmentFilter)
  // /<clusterId>/external/api/* → /api/external/* after strip + rewrite
  // ---------------------------------------------------------------------------

  @Nested
  class ProtectedEndpoints {

    @Test
    void apiDashboardUnderClusterIdRequiresAuthentication() throws Exception {
      mvc.perform(get(CLUSTER_PREFIX + REST_API_PATH + "/dashboard/1"))
          .andExpect(status().isUnauthorized());
    }

    @Test
    void apiDashboardWithoutClusterIdRequiresAuthentication() throws Exception {
      mvc.perform(get(REST_API_PATH + "/dashboard/1")).andExpect(status().isUnauthorized());
    }

    @Test
    void apiReportUnderClusterIdRequiresAuthentication() throws Exception {
      mvc.perform(get(CLUSTER_PREFIX + REST_API_PATH + "/report"))
          .andExpect(status().isUnauthorized());
    }
  }

  // ---------------------------------------------------------------------------
  // Security headers (ResponseSecurityHeaderFilter)
  // ---------------------------------------------------------------------------

  @Nested
  class ExternalApiRewrite {

    @Test
    void externalApiUnderClusterIdIsNotBlockedByAuth() throws Exception {
      // After strip + rewrite: /api/external/share/... is a public-share path — not 401.
      final int status =
          mvc.perform(get(CLUSTER_PREFIX + "/external/api/share/report/some-id/result"))
              .andReturn()
              .getResponse()
              .getStatus();
      assertThat(status)
          .as("Expected /<clusterId>/external/api/ to be rewritten and not blocked with 401")
          .isNotEqualTo(HttpStatus.UNAUTHORIZED.value());
    }

    @Test
    void externalApiWithoutClusterIdIsNotBlockedByAuth() throws Exception {
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
  // NoCachingFilter
  // ---------------------------------------------------------------------------

  @Nested
  class SecurityHeaders {

    @Test
    void contentSecurityPolicyOnPublicResponse() throws Exception {
      mvc.perform(get(CLUSTER_PREFIX + REST_API_PATH + READYZ_PATH))
          .andExpect(header().exists("Content-Security-Policy"));
    }

    @Test
    void securityHeadersOnProtectedEndpoint() throws Exception {
      mvc.perform(get(CLUSTER_PREFIX + REST_API_PATH + "/dashboard/1"))
          .andExpect(header().exists("Content-Security-Policy"));
    }
  }

  @Nested
  class NoCaching {

    @Test
    void apiResponseHasNoCacheHeader() throws Exception {
      mvc.perform(get(CLUSTER_PREFIX + REST_API_PATH + READYZ_PATH))
          .andExpect(
              header()
                  .string(
                      HttpHeaders.CACHE_CONTROL, "no-cache, no-store, max-age=0, must-revalidate"));
    }

    @Test
    void rootHtmlHasNoCacheHeader() throws Exception {
      mvc.perform(get(CLUSTER_PREFIX + "/"))
          .andExpect(
              header()
                  .string(
                      HttpHeaders.CACHE_CONTROL, "no-cache, no-store, max-age=0, must-revalidate"));
    }
  }
}
