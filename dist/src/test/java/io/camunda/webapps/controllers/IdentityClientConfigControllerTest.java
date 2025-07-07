/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.controllers;

import static com.google.common.net.HttpHeaders.CONTENT_SECURITY_POLICY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.identity.webapp.controllers.IdentityClientConfigController;
import io.camunda.security.configuration.AuthenticationConfiguration;
import io.camunda.security.configuration.MultiTenancyConfiguration;
import io.camunda.security.configuration.OidcAuthenticationConfiguration;
import io.camunda.security.configuration.SecurityConfiguration;
import io.camunda.security.entity.AuthenticationMethod;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.web.servlet.DispatcherServletPath;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@WebMvcTest(controllers = IdentityClientConfigController.class)
@Import(IdentityClientConfigControllerParameterizedTest.TestConfig.class)
class IdentityClientConfigControllerParameterizedTest {

  @Autowired private WebApplicationContext webApplicationContext;
  private MockMvc mockMvc;
  private final ObjectMapper objectMapper = new ObjectMapper();

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
  }

  static Stream<Arguments> configurationProvider() {
    return Stream.of(
        // AuthMethod, GroupsClaim, MultiTenancyEnabled, ExpectedIsOidc, ExpectedInternalGroups,
        // ExpectedTenantsApi
        Arguments.of(AuthenticationMethod.OIDC, null, true, "true", "true", "true"),
        Arguments.of(AuthenticationMethod.OIDC, null, false, "true", "true", "false"),
        Arguments.of(AuthenticationMethod.OIDC, "groups", true, "true", "false", "true"),
        Arguments.of(AuthenticationMethod.OIDC, "groups", false, "true", "false", "false"),
        Arguments.of(AuthenticationMethod.BASIC, null, true, "false", "true", "true"),
        Arguments.of(AuthenticationMethod.BASIC, null, false, "false", "true", "false"),
        Arguments.of(AuthenticationMethod.BASIC, "groups", true, "false", "true", "true"),
        Arguments.of(AuthenticationMethod.BASIC, "groups", false, "false", "true", "false"));
  }

  @ParameterizedTest(
      name =
          "AuthMethod={0}, GroupsClaim={1}, MultiTenancy={2} -> OIDC={3}, InternalGroups={4}, TenantsApi={5}")
  @MethodSource("configurationProvider")
  void shouldReturnCorrectClientConfigForAllPermutations(
      AuthenticationMethod authMethod,
      String groupsClaim,
      boolean multiTenancyEnabled,
      String expectedIsOidc,
      String expectedInternalGroups,
      String expectedTenantsApi)
      throws Exception {

    // Create controller with specific configuration
    final var securityConfiguration =
        createSecurityConfiguration(authMethod, groupsClaim, multiTenancyEnabled);
    final var controller = new IdentityClientConfigController(securityConfiguration);

    // Rebuild MockMvc with the new controller
    mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

    final String response =
        mockMvc
            .perform(get("/identity/config.js"))
            .andExpect(status().isOk())
            .andExpect(content().contentType("text/javascript;charset=UTF-8"))
            .andExpect(header().doesNotExist(CONTENT_SECURITY_POLICY))
            .andReturn()
            .getResponse()
            .getContentAsString();

    // Extract JSON configuration from JavaScript response
    final Map<String, String> configResponse = extractConfigFromResponse(response);

    // Assert all configuration values
    assertThat(configResponse)
        .containsEntry("VITE_IS_OIDC", expectedIsOidc)
        .containsEntry("VITE_INTERNAL_GROUPS_ENABLED", expectedInternalGroups)
        .containsEntry("VITE_TENANTS_API_ENABLED", expectedTenantsApi);
  }

  private SecurityConfiguration createSecurityConfiguration(
      AuthenticationMethod authMethod, String groupsClaim, boolean multiTenancyEnabled) {

    final var securityConfiguration = new SecurityConfiguration();

    // Configure authentication
    final var authentication = new AuthenticationConfiguration();
    authentication.setMethod(authMethod);

    if (authMethod == AuthenticationMethod.OIDC) {
      final var oidcConfig = new OidcAuthenticationConfiguration();
      oidcConfig.setGroupsClaim(groupsClaim);
      authentication.setOidc(oidcConfig);
    }

    securityConfiguration.setAuthentication(authentication);

    // Configure multi-tenancy
    final var multiTenancy = new MultiTenancyConfiguration();
    multiTenancy.setApiEnabled(multiTenancyEnabled);
    securityConfiguration.setMultiTenancy(multiTenancy);

    return securityConfiguration;
  }

  private Map<String, String> extractConfigFromResponse(String response) throws Exception {
    final var jsonConfigBodyStartIdx = response.indexOf('{');
    assertThat(jsonConfigBodyStartIdx).isNotEqualTo(-1);
    final var jsonConfigBodyEndIdx = response.lastIndexOf('}');
    assertThat(jsonConfigBodyEndIdx).isNotEqualTo(-1);

    return objectMapper.readValue(
        response.substring(jsonConfigBodyStartIdx, jsonConfigBodyEndIdx + 1), Map.class);
  }

  @SpringBootApplication
  static class TestApplication {}

  @TestConfiguration
  static class TestConfig {
    @Bean
    public DispatcherServletPath dispatcherServletPath() {
      return () -> "";
    }
  }
}
