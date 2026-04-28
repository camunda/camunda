/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.tomcat;

import static io.camunda.optimize.rest.HealthRestService.READYZ_PATH;
import static io.camunda.optimize.tomcat.OptimizeResourceConstants.REST_API_PATH;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.camunda.identity.sdk.Identity;
import io.camunda.identity.sdk.authentication.Authentication;
import io.camunda.identity.sdk.authentication.AuthorizeUriBuilder;
import io.camunda.identity.sdk.users.Users;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.ConfigurationServiceBuilder;
import io.camunda.optimize.tomcat.filter.support.FilterIntegrationTestBase;
import java.net.URI;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

@ActiveProfiles("ccsm")
@TestPropertySource(properties = {"server.servlet.context-path=/optimize"})
@Import(CcsmContextPathIT.CcsmConfig.class)
class CcsmContextPathIT extends FilterIntegrationTestBase {

  @Test
  void contextPathRootRedirectsToIdentityForUnauthenticatedUser() throws Exception {
    mvc.perform(get("/optimize/").contextPath("/optimize"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrlPattern("/identity/authorize*"));
  }

  @Test
  void contextPathRootIsServedForAuthenticatedUser() throws Exception {
    mvc.perform(get("/optimize/").contextPath("/optimize").with(user("test").roles("OPTIMIZE")))
        .andExpect(status().isOk());
  }

  @Test
  void deepSpaLinkUnderContextPathIsRedirectedToHash() throws Exception {
    // URLRedirectFilter strips /optimize and redirects /dashboard/1 to /# regardless of auth
    mvc.perform(get("/optimize/dashboard/1").contextPath("/optimize"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/#"));
  }

  @Test
  void apiUnderContextPathIsReachableAndPublic() throws Exception {
    mvc.perform(get(REST_API_PATH + READYZ_PATH)).andExpect(status().isOk());
  }

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

      return config;
    }

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
  }
}
