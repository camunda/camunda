/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.scim.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.unboundid.scim2.common.messages.ListResponse;
import com.unboundid.scim2.common.types.ResourceTypeResource;
import com.unboundid.scim2.common.types.SchemaResource;
import com.unboundid.scim2.common.types.ServiceProviderConfigResource;
import com.unboundid.scim2.common.utils.JsonUtils;
import io.camunda.gateway.scim.ScimAttributeProperties;
import io.camunda.gateway.scim.ScimJson;
import io.camunda.gateway.scim.mapper.ScimAttributeMapping;
import io.camunda.gateway.scim.mapper.ScimGroupMapper;
import io.camunda.gateway.scim.mapper.ScimUserMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.node.ObjectNode;

class ScimDiscoveryControllerTest {

  private final MockMvc mockMvc =
      MockMvcBuilders.standaloneSetup(new ScimDiscoveryController()).build();

  @Test
  void shouldAdvertiseSupportedCapabilities() throws Exception {
    // when
    final var response = perform("/scim/v2/ServiceProviderConfig");

    // then
    assertThat(response.getContentType()).startsWith("application/scim+json");
    final var config =
        ScimJson.read(response.getContentAsString(), ServiceProviderConfigResource.class);
    assertThat(config.getPatch().isSupported()).isTrue();
    assertThat(config.getFilter().isSupported()).isTrue();
    assertThat(config.getFilter().getMaxResults()).isEqualTo(100);
    assertThat(config.getBulk().isSupported()).isFalse();
    assertThat(config.getSort().isSupported()).isFalse();
    assertThat(config.getEtag().isSupported()).isFalse();
    assertThat(config.getChangePassword().isSupported()).isFalse();
  }

  @Test
  void shouldDescribeUserAndGroupResourceTypes() throws Exception {
    // when
    final var response = perform("/scim/v2/ResourceTypes");

    // then
    final var list = parseList(response, ResourceTypeResource.class);
    assertThat(list.getTotalResults()).isEqualTo(2);
    assertThat(list.getResources())
        .satisfiesExactlyInAnyOrder(
            type -> {
              assertThat(type.getName()).isEqualTo("User");
              assertThat(type.getEndpoint()).hasToString("/Users");
              assertThat(type.getSchema())
                  .hasToString("urn:ietf:params:scim:schemas:core:2.0:User");
            },
            type -> {
              assertThat(type.getName()).isEqualTo("Group");
              assertThat(type.getEndpoint()).hasToString("/Groups");
              assertThat(type.getSchema())
                  .hasToString("urn:ietf:params:scim:schemas:core:2.0:Group");
            });
  }

  @Test
  void shouldDescribeCoreUserAndGroupSchemas() throws Exception {
    // when
    final var response = perform("/scim/v2/Schemas");

    // then
    final var list = parseList(response, SchemaResource.class);
    assertThat(list.getResources())
        .satisfiesExactlyInAnyOrder(
            schema -> {
              assertThat(schema.getId()).isEqualTo("urn:ietf:params:scim:schemas:core:2.0:User");
              assertThat(schema.getAttributes())
                  .extracting(attribute -> attribute.getName())
                  .contains("userName", "externalId", "displayName", "name", "emails", "active");
            },
            schema -> {
              assertThat(schema.getId()).isEqualTo("urn:ietf:params:scim:schemas:core:2.0:Group");
              assertThat(schema.getAttributes())
                  .extracting(attribute -> attribute.getName())
                  .containsExactly("displayName", "externalId", "members");
            });
  }

  @Test
  void shouldListConfiguredExtensionUrnsAsSchemaExtensions() throws Exception {
    // given
    final var userUrn = "urn:ietf:params:scim:schemas:extension:acme:2.0:User";
    final var groupUrn = "urn:acme:group";
    final var mockMvc =
        MockMvcBuilders.standaloneSetup(
                new ScimDiscoveryController(
                    ScimAttributeMapping.of(
                        "p",
                        List.of(
                            new ScimAttributeProperties.Attribute("a", userUrn + ":a"),
                            new ScimAttributeProperties.Attribute("b", userUrn + ":b"),
                            new ScimAttributeProperties.Attribute("t", "title")),
                        ScimUserMapper.USER_SCHEMA),
                    ScimAttributeMapping.of(
                        "g",
                        List.of(new ScimAttributeProperties.Attribute("c", groupUrn + ":c")),
                        ScimGroupMapper.GROUP_SCHEMA)))
            .build();

    // when
    final var response = mockMvc.perform(get("/scim/v2/ResourceTypes")).andReturn().getResponse();

    // then
    final var resources =
        ScimJson.read(response.getContentAsString(), ObjectNode.class).get("Resources");
    for (final var type : resources) {
      final var expected = "User".equals(type.get("name").asString()) ? userUrn : groupUrn;
      assertThat(type.get("schemaExtensions")).hasSize(1);
      assertThat(type.get("schemaExtensions").get(0).get("schema").asString()).isEqualTo(expected);
      assertThat(type.get("schemaExtensions").get(0).get("required").asBoolean()).isFalse();
    }
  }

  @Test
  void shouldOmitSchemaExtensionsWithoutConfiguredExtensions() throws Exception {
    // when
    final var response = perform("/scim/v2/ResourceTypes");

    // then
    assertThat(response.getContentAsString()).doesNotContain("schemaExtensions");
  }

  private MockHttpServletResponse perform(final String path) throws Exception {
    return mockMvc.perform(get(path)).andReturn().getResponse();
  }

  private static <T> ListResponse<T> parseList(
      final MockHttpServletResponse response, final Class<T> type) throws Exception {
    final var mapper = JsonUtils.getObjectReader();
    return mapper
        .forType(mapper.getTypeFactory().constructParametricType(ListResponse.class, type))
        .readValue(response.getContentAsString());
  }
}
