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
import io.camunda.security.api.model.config.AuthenticationMethod;
import io.camunda.security.configuration.AuthenticationConfiguration;
import io.camunda.security.configuration.MultiTenancyConfiguration;
import io.camunda.security.configuration.OidcAuthenticationConfiguration;
import io.camunda.security.configuration.SaasConfiguration;
import io.camunda.security.configuration.SecurityConfiguration;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
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
    final Map<String, Object> configResponse = extractConfigFromResponse(response);

    // Assert all configuration values
    assertThat(configResponse)
        .containsEntry("isOidc", expectedIsOidc)
        .containsEntry("isCamundaGroupsEnabled", expectedCamundaGroups)
        .containsEntry("isTenantsApiEnabled", expectedTenantsApi)
        .containsEntry("organizationId", expectedOrganizationId)
        .containsEntry("clusterId", expectedClusterId);
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

  @Test
  void shouldIncludeResourcePermissionsInClientConfig() throws Exception {
    // given
    final var controller =
        new AdminClientConfigController(
            createSecurityConfiguration(AuthenticationMethod.BASIC, null, false, null, null));
    mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

    // when
    final String response =
        mockMvc.perform(get("/admin/config.js")).andReturn().getResponse().getContentAsString();
    final var config = extractConfigFromResponse(response);

    // then
    assertThat(config).containsKey("resourcePermissions");
    assertThat(config.get("resourcePermissions")).isInstanceOf(Map.class);
  }

  @Test
  void shouldContainExactlyAllUserProvidedResourceTypesInResourcePermissions() throws Exception {
    // given
    final var controller =
        new AdminClientConfigController(
            createSecurityConfiguration(AuthenticationMethod.BASIC, null, false, null, null));
    mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

    // when
    final String response =
        mockMvc.perform(get("/admin/config.js")).andReturn().getResponse().getContentAsString();
    final var resourcePermissions = extractResourcePermissions(extractConfigFromResponse(response));

    // then
    final var expectedKeys =
        AuthorizationResourceType.getUserProvidedResourceTypes().stream()
            .map(Enum::name)
            .collect(Collectors.toSet());
    assertThat(resourcePermissions.keySet()).containsExactlyInAnyOrderElementsOf(expectedKeys);
    assertThat(resourcePermissions).doesNotContainKey(AuthorizationResourceType.UNSPECIFIED.name());
  }

  @Test
  void shouldReturnPermissionsMatchingAuthorizationResourceTypeDefinition() throws Exception {
    // given
    final var controller =
        new AdminClientConfigController(
            createSecurityConfiguration(AuthenticationMethod.BASIC, null, false, null, null));
    mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

    // when
    final String response =
        mockMvc.perform(get("/admin/config.js")).andReturn().getResponse().getContentAsString();
    final var resourcePermissions = extractResourcePermissions(extractConfigFromResponse(response));

    // then
    for (final var resourceType : AuthorizationResourceType.getUserProvidedResourceTypes()) {
      final var expectedPermissions =
          resourceType.getSupportedPermissionTypes().stream()
              .map(PermissionType::name)
              .collect(Collectors.toSet());
      assertThat(resourcePermissions.get(resourceType.name()))
          .as("permissions for resource type %s", resourceType.name())
          .containsExactlyInAnyOrderElementsOf(expectedPermissions);
    }
  }

  @Test
  void shouldReturnPermissionsSortedAlphabetically() throws Exception {
    // given
    final var controller =
        new AdminClientConfigController(
            createSecurityConfiguration(AuthenticationMethod.BASIC, null, false, null, null));
    mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

    // when
    final String response =
        mockMvc.perform(get("/admin/config.js")).andReturn().getResponse().getContentAsString();
    final var resourcePermissions = extractResourcePermissions(extractConfigFromResponse(response));

    // then
    resourcePermissions.forEach(
        (resourceType, permissions) ->
            assertThat(permissions)
                .as(
                    "permissions for resource type %s should be sorted alphabetically",
                    resourceType)
                .isSortedAccordingTo(String::compareTo));
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> extractConfigFromResponse(final String response) throws Exception {
    final var jsonConfigBodyStartIdx = response.indexOf('{');
    assertThat(jsonConfigBodyStartIdx).isNotEqualTo(-1);
    final var jsonConfigBodyEndIdx = response.lastIndexOf('}');
    assertThat(jsonConfigBodyEndIdx).isNotEqualTo(-1);

    return objectMapper.readValue(
        response.substring(jsonConfigBodyStartIdx, jsonConfigBodyEndIdx + 1), Map.class);
  }

  @SuppressWarnings("unchecked")
  private Map<String, List<String>> extractResourcePermissions(final Map<String, Object> config) {
    return (Map<String, List<String>>) config.get("resourcePermissions");
  }
}
