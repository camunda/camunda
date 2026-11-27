/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.identity.webapp.controllers.AdminClientConfigController;
import io.camunda.security.configuration.AuthenticationConfiguration;
import io.camunda.security.configuration.MultiTenancyConfiguration;
import io.camunda.security.configuration.OidcAuthenticationConfiguration;
import io.camunda.security.configuration.ProvidersConfiguration;
import io.camunda.security.configuration.SaasConfiguration;
import io.camunda.security.configuration.SecurityConfiguration;
import io.camunda.security.entity.AuthenticationMethod;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

public class AdminClientConfigControllerTest {
  private MockMvc mockMvc;
  private final ObjectMapper objectMapper = new ObjectMapper();

  @BeforeEach
  void setUp() {
    // MockMvc will be setup for each test with a specific controller configuration
  }

  static Stream<Arguments> configurationProvider() {
    return Stream.of(
        // AuthMethod, GroupsClaim, MultiTenancyEnabled, ExpectedIsOidc, ExpectedCamundaGroups,
        // ExpectedTenantsApi, ExpectedOrganizationId, ExpectedClusterId
        Arguments.of(
            AuthenticationMethod.OIDC,
            null,
            true,
            "true",
            "true",
            "true",
            "test-org",
            "test-cluster"),
        Arguments.of(
            AuthenticationMethod.OIDC,
            null,
            false,
            "true",
            "true",
            "false",
            "test-org",
            "test-cluster"),
        Arguments.of(
            AuthenticationMethod.OIDC,
            "",
            false,
            "true",
            "true",
            "false",
            "test-org",
            "test-cluster"),
        Arguments.of(
            AuthenticationMethod.OIDC,
            "groups",
            true,
            "true",
            "false",
            "true",
            "test-org",
            "test-cluster"),
        Arguments.of(
            AuthenticationMethod.OIDC,
            "groups",
            false,
            "true",
            "false",
            "false",
            "test-org",
            "test-cluster"),
        Arguments.of(AuthenticationMethod.BASIC, null, true, "false", "true", "true", null, null),
        Arguments.of(AuthenticationMethod.BASIC, null, false, "false", "true", "false", null, null),
        Arguments.of(
            AuthenticationMethod.BASIC, "groups", true, "false", "true", "true", null, null),
        Arguments.of(
            AuthenticationMethod.BASIC, "groups", false, "false", "true", "false", null, null));
  }

  @ParameterizedTest(
      name =
          "AuthMethod={0}, GroupsClaim={1}, MultiTenancy={2} -> OIDC={3}, CamundaGroups={4}, TenantsApi={5}")
  @MethodSource("configurationProvider")
  void shouldReturnCorrectClientConfigForAllPermutations(
      final AuthenticationMethod authMethod,
      final String groupsClaim,
      final boolean multiTenancyEnabled,
      final String expectedIsOidc,
      final String expectedCamundaGroups,
      final String expectedTenantsApi,
      final String expectedOrganizationId,
      final String expectedClusterId)
      throws Exception {

    final var securityConfiguration =
        createSecurityConfiguration(
            authMethod,
            groupsClaim,
            multiTenancyEnabled,
            expectedOrganizationId,
            expectedClusterId);
    final var controller = new AdminClientConfigController(securityConfiguration);

    // Setup MockMvc with the controller for this specific test
    mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

    final String response =
        mockMvc
            .perform(get("/admin/config.js"))
            .andExpect(status().isOk())
            .andExpect(content().contentType("text/javascript;charset=UTF-8"))
            .andExpect(header().doesNotExist("Content-Security-Policy"))
            .andReturn()
            .getResponse()
            .getContentAsString();

    // Extract JSON configuration from JavaScript response
    final Map<String, String> configResponse = extractConfigFromResponse(response);

    // Assert all configuration values
    assertThat(configResponse)
        .containsEntry("isOidc", expectedIsOidc)
        .containsEntry("isCamundaGroupsEnabled", expectedCamundaGroups)
        .containsEntry("isTenantsApiEnabled", expectedTenantsApi)
        .containsEntry("organizationId", expectedOrganizationId)
        .containsEntry("clusterId", expectedClusterId);
  }

  @Test
  void shouldEnableCamundaGroupsWhenANamedProviderHasNoGroupsClaim() throws Exception {
    // given: one named provider reads groups from a claim, the other gets them from Camunda
    final var securityConfiguration =
        createSecurityConfiguration(AuthenticationMethod.OIDC, null, false, "test-org", "cluster");
    securityConfiguration
        .getAuthentication()
        .setProviders(
            providers(Map.of("keycloak", namedProvider("groups"), "partner", namedProvider(null))));

    // when
    final var config = configOf(securityConfiguration);

    // then: the partner's users need Camunda's group management
    assertThat(config).containsEntry("isCamundaGroupsEnabled", "true");
  }

  @Test
  void shouldDisableCamundaGroupsWhenTheOnlyNamedProviderHasAGroupsClaim() throws Exception {
    // given
    final var securityConfiguration =
        createSecurityConfiguration(AuthenticationMethod.OIDC, null, false, "test-org", "cluster");
    securityConfiguration
        .getAuthentication()
        .setProviders(providers(Map.of("keycloak", namedProvider("groups"))));

    // when
    final var config = configOf(securityConfiguration);

    // then
    assertThat(config).containsEntry("isCamundaGroupsEnabled", "false");
  }

  private Map<String, String> configOf(final SecurityConfiguration securityConfiguration)
      throws Exception {
    mockMvc =
        MockMvcBuilders.standaloneSetup(new AdminClientConfigController(securityConfiguration))
            .build();
    return extractConfigFromResponse(
        mockMvc.perform(get("/admin/config.js")).andReturn().getResponse().getContentAsString());
  }

  private static ProvidersConfiguration providers(
      final Map<String, OidcAuthenticationConfiguration> oidc) {
    final var providers = new ProvidersConfiguration();
    providers.setOidc(oidc);
    return providers;
  }

  private static OidcAuthenticationConfiguration namedProvider(final String groupsClaim) {
    final var provider = new OidcAuthenticationConfiguration();
    provider.setClientId("client");
    provider.setIssuerUri("https://" + UUID.randomUUID() + ".example.com");
    if (groupsClaim != null) {
      provider.setGroupsClaim(groupsClaim);
    }
    return provider;
  }

  private SecurityConfiguration createSecurityConfiguration(
      final AuthenticationMethod authMethod,
      final String groupsClaim,
      final boolean multiTenancyEnabled,
      final String organizationId,
      final String clusterId) {

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

    // Configure SaaS
    final var saasConfiguration = new SaasConfiguration();
    saasConfiguration.setOrganizationId(organizationId);
    saasConfiguration.setClusterId(clusterId);
    securityConfiguration.setSaas(saasConfiguration);

    return securityConfiguration;
  }

  private Map<String, String> extractConfigFromResponse(final String response) throws Exception {
    final var jsonConfigBodyStartIdx = response.indexOf('{');
    assertThat(jsonConfigBodyStartIdx).isNotEqualTo(-1);
    final var jsonConfigBodyEndIdx = response.lastIndexOf('}');
    assertThat(jsonConfigBodyEndIdx).isNotEqualTo(-1);

    return objectMapper.readValue(
        response.substring(jsonConfigBodyStartIdx, jsonConfigBodyEndIdx + 1), Map.class);
  }
}
