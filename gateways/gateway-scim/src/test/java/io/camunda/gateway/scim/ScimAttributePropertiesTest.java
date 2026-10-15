/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.scim;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.gateway.scim.controller.ScimDiscoveryController;
import io.camunda.gateway.scim.mapper.ScimAttributeMapping;
import io.camunda.gateway.scim.mapper.ScimUserMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import tools.jackson.databind.node.ObjectNode;

class ScimAttributePropertiesTest {

  private static final String ACME = "urn:ietf:params:scim:schemas:extension:acme:2.0:User";

  @Test
  void shouldBindYamlShapedConfiguration() {
    // given
    final var source =
        new MapConfigurationPropertySource(
            Map.of(
                "camunda.scim.user-attributes[0].name", "clearance",
                "camunda.scim.user-attributes[0].path", ACME + ":clearance",
                "camunda.scim.user-attributes[1].name", "title",
                "camunda.scim.user-attributes[1].path", "title",
                "camunda.scim.group-attributes[0].name", "costCenter",
                "camunda.scim.group-attributes[0].path", "costCenter",
                "camunda.scim.enabled", "true"));

    // when
    final var properties =
        new Binder(source).bind("camunda.scim", ScimAttributeProperties.class).get();

    // then
    assertThat(properties.getUserAttributes())
        .extracting("name")
        .containsExactly("clearance", "title");
    assertThat(properties.getGroupAttributes()).extracting("path").containsExactly("costCenter");
  }

  @Test
  void shouldListConfiguredExtensionAttributesInSchemas() throws Exception {
    // given
    final var mapping =
        ScimAttributeMapping.of(
            "p",
            List.of(
                new ScimAttributeProperties.Attribute("clearance", ACME + ":clearance"),
                new ScimAttributeProperties.Attribute("title", "title")),
            ScimUserMapper.USER_SCHEMA);
    final var controller =
        new ScimDiscoveryController(
            mapping, ScimAttributeMapping.empty("urn:ietf:params:scim:schemas:core:2.0:Group"));

    // when
    final var body = ScimJson.read(controller.getSchemas().getBody(), ObjectNode.class);

    // then
    assertThat(body.get("totalResults").asInt()).isEqualTo(3);
    final var extension = body.get("Resources").get(2);
    assertThat(extension.get("id").asString()).isEqualTo(ACME);
    assertThat(extension.get("attributes").get(0).get("name").asString()).isEqualTo("clearance");
  }
}
